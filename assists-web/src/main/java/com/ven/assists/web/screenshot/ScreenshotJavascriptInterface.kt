package com.ven.assists.web.screenshot

import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ven.assists.screenshot.AssistsScreenshot
import com.ven.assists.web.CallRequest
import com.ven.assists.web.CallResponse
import com.ven.assists.web.createResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

/**
 * 截图专用的 JavascriptInterface
 * H5 调用 assistsxScreenshot.call(json)，通过 assistsxScreenshotCallback(base64) 接收结果
 */
class ScreenshotJavascriptInterface(private val webView: WebView) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    fun <T> callbackResponse(result: CallResponse<T>) {
        coroutineScope.launch {
            runCatching {
                val json = GsonUtils.toJson(result)
                callback(json)
            }.onFailure {
                LogUtils.e(it)
            }
        }
    }

    fun callback(result: String) {
        val encoded = Base64.encodeToString(result.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val js = String.format("javascript:assistsxScreenshotCallback('%s')", encoded)
        webView.evaluateJavascript(js, null)
    }

    @JavascriptInterface
    fun call(originJson: String): String {
        val result = GsonUtils.toJson(CallResponse<Any>(code = 0))
        coroutineScope.launch(Dispatchers.IO) {
            processCall(originJson)
        }
        return result
    }

    private suspend fun processCall(originJson: String) {
        val request = GsonUtils.fromJson<CallRequest<JsonObject>>(
            originJson,
            object : TypeToken<CallRequest<JsonObject>>() {}.type,
        )
        runCatching {
            val response = when (request.method) {
                ScreenshotCallMethod.takeScreenshotBase64 -> handleTakeScreenshotBase64(request)
                ScreenshotCallMethod.takeNodeScreenshotBase64 -> handleTakeNodeScreenshotBase64(request)
                ScreenshotCallMethod.takeScreenshotNodesBase64 -> handleTakeScreenshotNodesBase64(request)
                else -> request.createResponse(-1, message = "方法未支持: ${request.method}", data = null)
            }
            callbackResponse(response)
        }.onFailure {
            LogUtils.e(it)
            callbackResponse(request.createResponse(-1, message = "执行失败: ${it.message}", data = null))
        }
    }

    private suspend fun handleTakeScreenshotBase64(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val delayMillis = request.arguments?.get("overlayHiddenScreenshotDelayMillis")?.asLong ?: 250L
        val format = AssistsScreenshot.parseCompressFormat(request.arguments?.get("format")?.asString)
        val withDataUrlPrefix = request.arguments?.get("withDataUrlPrefix")?.asBoolean ?: true

        val base64 = ScreenshotCaptureHelper.captureFullScreenBase64(
            overlayHiddenScreenshotDelayMillis = delayMillis,
            format = format,
            withDataUrlPrefix = withDataUrlPrefix,
        )

        if (base64.isNullOrEmpty()) {
            return request.createResponse(-1, message = "截图失败", data = null)
        }

        return request.createResponse(0, message = "截图成功", data = buildBase64Data(base64, format, withDataUrlPrefix))
    }

    private suspend fun handleTakeNodeScreenshotBase64(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val nodeId = request.node?.nodeId
        if (nodeId.isNullOrEmpty()) {
            return request.createResponse(-1, message = "node.nodeId 不能为空", data = null)
        }

        val delayMillis = request.arguments?.get("overlayHiddenScreenshotDelayMillis")?.asLong ?: 250L
        val format = AssistsScreenshot.parseCompressFormat(request.arguments?.get("format")?.asString)
        val withDataUrlPrefix = request.arguments?.get("withDataUrlPrefix")?.asBoolean ?: true

        val base64 = ScreenshotCaptureHelper.captureNodeScreenshotBase64(
            nodeId = nodeId,
            overlayHiddenScreenshotDelayMillis = delayMillis,
            format = format,
            withDataUrlPrefix = withDataUrlPrefix,
        )

        if (base64.isNullOrEmpty()) {
            return request.createResponse(-1, message = "节点截图失败", data = null)
        }

        return request.createResponse(0, message = "截图成功", data = buildBase64Data(base64, format, withDataUrlPrefix))
    }

    private suspend fun handleTakeScreenshotNodesBase64(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val delayMillis = request.arguments?.get("overlayHiddenScreenshotDelayMillis")?.asLong ?: 250L
        val format = AssistsScreenshot.parseCompressFormat(request.arguments?.get("format")?.asString)
        val withDataUrlPrefix = request.arguments?.get("withDataUrlPrefix")?.asBoolean ?: true
        val nodeIds = request.nodes?.mapNotNull { it.nodeId } ?: emptyList()

        val images = ScreenshotCaptureHelper.captureNodesScreenshotBase64(
            nodeIds = nodeIds,
            overlayHiddenScreenshotDelayMillis = delayMillis,
            format = format,
            withDataUrlPrefix = withDataUrlPrefix,
        )

        if (images.isEmpty()) {
            return request.createResponse(-1, message = "截图失败", data = null)
        }

        return request.createResponse(0, message = "截图成功", data = JsonObject().apply {
            add("images", JsonArray().apply {
                images.forEach { add(it) }
            })
        })
    }

    private fun buildBase64Data(
        base64: String,
        format: android.graphics.Bitmap.CompressFormat,
        withDataUrlPrefix: Boolean,
    ): JsonObject {
        val mimeType = AssistsScreenshot.compressFormatToMimeType(format)
        val rawBase64 = if (withDataUrlPrefix && base64.startsWith("data:")) {
            val commaIndex = base64.indexOf(',')
            if (commaIndex >= 0) base64.substring(commaIndex + 1) else base64
        } else {
            base64
        }
        return JsonObject().apply {
            addProperty("base64", rawBase64)
            addProperty("dataUrl", if (withDataUrlPrefix) base64 else "data:$mimeType;base64,$rawBase64")
            addProperty("mimeType", mimeType)
        }
    }
}
