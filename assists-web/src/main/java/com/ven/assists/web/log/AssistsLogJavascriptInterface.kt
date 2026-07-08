package com.ven.assists.web.log

import android.graphics.Bitmap.CompressFormat
import android.os.Build
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.RequiresApi
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ven.assists.log.AssistsLog
import com.ven.assists.log.AssistsLogDiagnostics
import com.ven.assists.log.AssistsLogPaths
import com.ven.assists.log.AssistsLogTarget
import com.ven.assists.log.AssistsLogUploadResult
import com.ven.assists.web.ASWebView
import com.ven.assists.web.CallInterceptResult
import com.ven.assists.web.CallRequest
import com.ven.assists.web.CallResponse
import com.ven.assists.web.createResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * AssistsLog 的 JavascriptInterface：读写、Flow 订阅、[AssistsLogDiagnostics.uploadLogs] 上传、
 * [AssistsLogCallMethod.getLogServiceBaseUrl] 获取日志服务当前域名（origin）。
 * 支持可选 arguments：`dirPath`（绝对路径目录）、`fileName`（不含 .txt 后缀）；均未传时默认 `log-default.txt`。
 * uploadLogs 请求体可含 `uploadKey`（非空则覆盖本次上传使用的 X-Upload-Key）。
 * H5 调用 assistsxLog.call(json)，实现 assistsxLogCallback(base64) 接收结果。
 */
class AssistsLogJavascriptInterface(private val webView: WebView) {

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + supervisorJob)
    private val subscriptionJobs = ConcurrentHashMap<String, Job>()

    fun dispose() {
        subscriptionJobs.values.forEach { it.cancel() }
        subscriptionJobs.clear()
        supervisorJob.cancel()
    }

    fun <T> callbackResponse(result: CallResponse<T>) {
        scope.launch {
            runCatching {
                val json = GsonUtils.toJson(result)
                callback(json)
            }.onFailure { LogUtils.e(it) }
        }
    }

    private fun callback(result: String) {
        val encoded = Base64.encodeToString(result.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val js = String.format("javascript:assistsxLogCallback('%s')", encoded)
        webView.evaluateJavascript(js, null)
    }

    @JavascriptInterface
    fun call(originJson: String): String {
        val placeholder = GsonUtils.toJson(CallResponse<Any>(code = 0))
        scope.launch(Dispatchers.IO) {
            processCall(originJson)
        }
        return placeholder
    }

    private suspend fun processCall(originJson: String) {
        var requestJson = originJson
        val interceptedEarly = runCatching {
            ASWebView.globalLogCallIntercepts.forEach { intercept ->
                val result = intercept.invoke(requestJson)
                if (result.intercept) {
                    callback(result.result)
                    return@runCatching true
                } else {
                    requestJson = result.result
                }
            }
            false
        }.onFailure { LogUtils.e(it) }.getOrDefault(false)
        if (interceptedEarly) return

        val request = runCatching {
            GsonUtils.fromJson<CallRequest<JsonObject>>(
                requestJson,
                object : TypeToken<CallRequest<JsonObject>>() {}.type
            )
        }.getOrElse {
            LogUtils.e(it)
            callbackResponse(CallResponse<JsonObject>(code = -1, message = it.message ?: "parse error", data = null))
            return
        }

        runCatching {
            if (request.method == AssistsLogCallMethod.subscribe) {
                handleSubscribe(request)
                return@runCatching
            }

            val target = parseLogTarget(request.arguments)
            val response: CallResponse<JsonObject> = when (request.method) {
                AssistsLogCallMethod.readAllText -> {
                    val text = withContext(Dispatchers.IO) { AssistsLog.readAllText(target) }
                    request.createResponse(
                        code = 0,
                        data = JsonObject().apply { addProperty("text", text) }
                    )
                }

                AssistsLogCallMethod.clear -> {
                    withContext(Dispatchers.IO) { AssistsLog.clear(target) }
                    request.createResponse(code = 0, data = JsonObject())
                }

                AssistsLogCallMethod.refreshFromFile -> {
                    withContext(Dispatchers.IO) { AssistsLog.refreshFromFile(target) }
                    request.createResponse(code = 0, data = JsonObject())
                }

                AssistsLogCallMethod.appendLine -> {
                    val line = request.arguments?.get("line")?.asString ?: ""
                    val maxLength = request.arguments?.get("maxLength")?.asInt
                        ?: AssistsLog.DEFAULT_MAX_FILE_LENGTH
                    val prepend = request.arguments?.get("prepend")?.asBoolean ?: false
                    withContext(Dispatchers.IO) {
                        AssistsLog.appendLine(line, maxLength, target, prepend)
                    }
                    request.createResponse(code = 0, data = JsonObject())
                }

                AssistsLogCallMethod.appendTimestampedEntry -> {
                    val message = request.arguments?.get("message")?.asString ?: ""
                    val prepend = request.arguments?.get("prepend")?.asBoolean ?: false
                    withContext(Dispatchers.IO) {
                        AssistsLog.appendTimestampedEntry(message, target, prepend)
                    }
                    request.createResponse(code = 0, data = JsonObject())
                }

                AssistsLogCallMethod.replaceAll -> {
                    val content = request.arguments?.get("content")?.asString ?: ""
                    withContext(Dispatchers.IO) {
                        AssistsLog.replaceAll(content, target)
                    }
                    request.createResponse(code = 0, data = JsonObject())
                }

                AssistsLogCallMethod.resolveLogPath -> {
                    val logFilePath = withContext(Dispatchers.IO) {
                        AssistsLogPaths.resolveLogFilePath(target, ensureWritable = true)
                    }
                    request.createResponse(
                        code = 0,
                        data = JsonObject().apply { addProperty("logFilePath", logFilePath) }
                    )
                }

                AssistsLogCallMethod.unsubscribe -> handleUnsubscribe(request)

                AssistsLogCallMethod.uploadLogs -> handleUploadLogs(request, target)

                AssistsLogCallMethod.getLogServiceBaseUrl -> {
                    val baseUrl = AssistsLogDiagnostics.adminWebBaseUrl()
                    request.createResponse(
                        code = 0,
                        data = JsonObject().apply {
                            addProperty("baseUrl", baseUrl)
                        }
                    )
                }

                else -> request.createResponse(code = -1, message = "unsupported method", data = null)
            }
            callbackResponse(response)
        }.onFailure {
            LogUtils.e(it)
            callbackResponse(request.createResponse(code = -1, message = it.message, data = null))
        }
    }

    private fun handleSubscribe(request: CallRequest<JsonObject>) {
        val stream = request.arguments?.get("stream")?.asString ?: ""
        if (stream != STREAM_LATEST_LINE && stream != STREAM_ENTIRE_LOG_TEXT) {
            callbackResponse(request.createResponse(code = -1, message = "invalid stream", data = null))
            return
        }
        if (!supervisorJob.isActive) {
            callbackResponse(request.createResponse(code = -1, message = "disposed", data = null))
            return
        }
        val target = parseLogTarget(request.arguments)
        val logStream = when (stream) {
            STREAM_LATEST_LINE -> AssistsLog.LogStream.LATEST_LINE
            STREAM_ENTIRE_LOG_TEXT -> AssistsLog.LogStream.ENTIRE_LOG_TEXT
            else -> {
                callbackResponse(request.createResponse(code = -1, message = "invalid stream", data = null))
                return
            }
        }
        val id = UUID.randomUUID().toString()
        val callbackId = request.callbackId
        val logFilePath = AssistsLogPaths.resolveLogFilePath(target)
        val job = scope.launch {
            callbackResponse(
                CallResponse(
                    code = 0,
                    data = JsonObject().apply {
                        addProperty("subscriptionId", id)
                        addProperty("stream", stream)
                        addProperty("event", "subscribed")
                        addProperty("logFilePath", logFilePath)
                    },
                    callbackId = callbackId
                )
            )
            try {
                AssistsLog.flowFor(target, logStream).collect { text ->
                    emitUpdate(id, stream, text, callbackId, logFilePath)
                }
            } catch (_: CancellationException) {
                // 正常取消
            } finally {
                subscriptionJobs.remove(id)
            }
        }
        subscriptionJobs[id] = job
    }

    private fun emitUpdate(
        subscriptionId: String,
        stream: String,
        text: String,
        callbackId: String?,
        logFilePath: String,
    ) {
        callbackResponse(
            CallResponse(
                code = 0,
                data = JsonObject().apply {
                    addProperty("subscriptionId", subscriptionId)
                    addProperty("stream", stream)
                    addProperty("text", text)
                    addProperty("event", "update")
                    addProperty("logFilePath", logFilePath)
                },
                callbackId = callbackId
            )
        )
    }

    private suspend fun handleUploadLogs(
        request: CallRequest<JsonObject>,
        target: AssistsLogTarget,
    ): CallResponse<JsonObject> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return request.createResponse(
                code = -1,
                message = "uploadLogs requires API ${Build.VERSION_CODES.R}+",
                data = null
            )
        }
        val args = request.arguments
        val baseUrl = args?.get("baseUrl")?.asString
        val format = parseCompressFormat(args?.get("format")?.asString)
        val prettyPrint = args?.get("prettyPrint")?.asBoolean ?: true
        val overlayHiddenDelayMillis = args?.get("overlayHiddenDelayMillis")?.asLong ?: 250L
        val uploadKey = args?.get("uploadKey")?.asString

        val result = invokeUploadLogs(
            baseUrl = baseUrl,
            format = format,
            prettyPrint = prettyPrint,
            overlayHiddenDelayMillis = overlayHiddenDelayMillis,
            uploadKey = uploadKey,
            target = target,
        )
        val code = if (result.success) 0 else -1
        return request.createResponse(
            code = code,
            message = result.message,
            data = assistsLogUploadResultToJson(result)
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun invokeUploadLogs(
        baseUrl: String?,
        format: CompressFormat,
        prettyPrint: Boolean,
        overlayHiddenDelayMillis: Long,
        uploadKey: String?,
        target: AssistsLogTarget,
    ): AssistsLogUploadResult {
        return if (baseUrl.isNullOrBlank()) {
            AssistsLogDiagnostics.uploadLogs(
                format = format,
                prettyPrint = prettyPrint,
                overlayHiddenDelayMillis = overlayHiddenDelayMillis,
                uploadKey = uploadKey,
                target = target,
            )
        } else {
            AssistsLogDiagnostics.uploadLogs(
                baseUrl = baseUrl,
                format = format,
                prettyPrint = prettyPrint,
                overlayHiddenDelayMillis = overlayHiddenDelayMillis,
                uploadKey = uploadKey,
                target = target,
            )
        }
    }

    private fun parseLogTarget(args: JsonObject?): AssistsLogTarget {
        val dirPath = args?.get("dirPath")?.takeIf { !it.isJsonNull }?.asString
        val fileName = args?.get("fileName")?.takeIf { !it.isJsonNull }?.asString
        return AssistsLogTarget(
            dirPath = dirPath?.trim()?.takeIf { it.isNotEmpty() },
            fileName = fileName?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun parseCompressFormat(raw: String?): CompressFormat {
        return when (raw?.uppercase()) {
            "JPEG", "JPG" -> CompressFormat.JPEG
            "WEBP" -> CompressFormat.WEBP
            else -> CompressFormat.PNG
        }
    }

    /** 将上传结果转为 JSON（避免 Throwable 直接序列化问题） */
    private fun assistsLogUploadResultToJson(r: AssistsLogUploadResult): JsonObject {
        return JsonObject().apply {
            addProperty("success", r.success)
            addProperty("message", r.message)
            r.httpCode?.let { addProperty("httpCode", it) }
            r.responseBody?.let { addProperty("responseBody", it) }
            r.data?.let { add("data", GsonUtils.getGson().toJsonTree(it)) }
            r.localLogFilePath?.let { addProperty("localLogFilePath", it) }
            r.localScreenshotFilePath?.let { addProperty("localScreenshotFilePath", it) }
            r.localNodeTreeFilePath?.let { addProperty("localNodeTreeFilePath", it) }
            r.cause?.message?.let { addProperty("causeMessage", it) }
        }
    }

    private fun handleUnsubscribe(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val sid = request.arguments?.get("subscriptionId")?.asString ?: ""
        if (sid.isEmpty()) {
            return request.createResponse(code = -1, message = "subscriptionId required", data = null)
        }
        val removed = subscriptionJobs.remove(sid)
        removed?.cancel()
        return request.createResponse(
            code = if (removed != null) 0 else -1,
            message = if (removed == null) "unknown subscriptionId" else "",
            data = JsonObject().apply { addProperty("cancelled", removed != null) }
        )
    }

    companion object {
        private const val STREAM_LATEST_LINE = "latestLine"
        private const val STREAM_ENTIRE_LOG_TEXT = "entireLogText"
    }
}
