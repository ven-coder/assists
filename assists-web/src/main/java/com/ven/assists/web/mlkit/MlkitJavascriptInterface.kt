package com.ven.assists.web.mlkit

import android.graphics.Rect
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ven.assists.web.CallRequest
import com.ven.assists.web.CallResponse
import com.ven.assists.web.createResponse
import com.ven.assists.window.AssistsWindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

/**
 * ML Kit 文字识别相关的 JavascriptInterface
 * 提供识别屏幕中指定词组位置、识别屏幕文字内容位置的能力
 */
class MlkitJavascriptInterface(val webView: WebView) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    /**
     * 回调响应给 JavaScript
     */
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

    /**
     * 执行回调
     */
    fun callback(result: String) {
        val encoded = Base64.encodeToString(result.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val js = String.format("javascript:assistsxMlkitCallback('%s')", encoded)
        webView.evaluateJavascript(js, null)
    }

    @JavascriptInterface
    fun call(originJson: String): String {
        val result = GsonUtils.toJson(CallResponse<Any>(code = 0))
        coroutineScope.launch(Dispatchers.Main) {
            processCall(originJson)
        }
        return result
    }

    /**
     * 处理调用请求
     */
    private suspend fun processCall(originJson: String) {
        val request = GsonUtils.fromJson<CallRequest<JsonObject>>(
            originJson,
            object : TypeToken<CallRequest<JsonObject>>() {}.type
        )
        runCatching {
            val response = when (request.method) {
                MlkitCallMethod.findPhrasePositions -> {
                    handleFindPhrasePositions(request)
                }

                MlkitCallMethod.getScreenTextPositions -> {
                    handleGetScreenTextPositions(request)
                }

                MlkitCallMethod.findPhrasePositionsOnScreenAsJson -> {
                    handleFindPhrasePositionsOnScreenAsJson(request)
                }

                MlkitCallMethod.getScreenTextPositionsAsJson -> {
                    handleGetScreenTextPositionsAsJson(request)
                }

                else -> {
                    request.createResponse(-1, message = "方法未支持: ${request.method}")
                }
            }
            callbackResponse(response)
        }.onFailure {
            LogUtils.e(it)
            callbackResponse(request.createResponse(-1, message = "执行失败: ${it.message}", data = null))
        }
    }

    /**
     * 识别前隐藏浮窗，识别完成后恢复显示；识别过程中执行 [block]
     * @param request 用于解析 overlayHiddenScreenshotDelayMillis、restoreOverlay
     * @param block 识别逻辑，返回响应
     */
    private suspend fun withOverlayHiddenForRecognition(
        request: CallRequest<JsonObject>,
        block: suspend () -> CallResponse<JsonObject>
    ): CallResponse<JsonObject> {
        val restoreOverlay = request.arguments?.get("restoreOverlay")?.asBoolean ?: true
        val delayMillis = request.arguments?.get("overlayHiddenScreenshotDelayMillis")?.asLong ?: 250L
        if (restoreOverlay) {
            AssistsWindowManager.hideAll()
        }
        delay(delayMillis)
        return try {
            block()
        } finally {
            if (restoreOverlay) {
                AssistsWindowManager.showTop()
            }
        }
    }

    /**
     * 解析可选的 region 参数：{ left, top, right, bottom }
     */
    private fun parseRegion(arguments: JsonObject?): Rect? {
        val left = arguments?.get("left")?.asInt ?: return null
        val top = arguments?.get("top")?.asInt ?: return null
        val right = arguments?.get("right")?.asInt ?: return null
        val bottom = arguments?.get("bottom")?.asInt ?: return null
        val rect = Rect(left, top, right, bottom)
        return if (rect.isEmpty) null else rect
    }

    /**
     * 处理识别屏幕中指定词组位置请求
     */
    private suspend fun handleFindPhrasePositions(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        return withOverlayHiddenForRecognition(request) {
            val targetText = request.arguments?.get("targetText")?.asString
            if (targetText.isNullOrBlank()) {
                return@withOverlayHiddenForRecognition request.createResponse(-1, message = "targetText 不能为空", data = null)
            }

            val region = parseRegion(request.arguments)
            val rotationDegrees = request.arguments?.get("rotationDegrees")?.asInt ?: 0

            val result = MlkitScreenTextUtils.findPhrasePositionsOnScreen(
                targetText = targetText,
                region = region,
                rotationDegrees = rotationDegrees
            )

            result.fold(
            onSuccess = { recognition ->
                val positionsArray = com.google.gson.JsonArray().apply {
                    recognition.targetPositions.forEach { pos ->
                        add(JsonObject().apply {
                            addProperty("text", pos.text)
                            addProperty("left", pos.left)
                            addProperty("top", pos.top)
                            addProperty("right", pos.right)
                            addProperty("bottom", pos.bottom)
                        })
                    }
                }
                val data = JsonObject().apply {
                    addProperty("fullText", recognition.fullText)
                    add("positions", positionsArray)
                    addProperty("processingTimeMillis", recognition.processingTimeMillis)
                }
                request.createResponse(0, data = data, message = "识别完成")
            },
            onFailure = { e ->
                LogUtils.e(e)
                request.createResponse(-1, message = "识别失败: ${e.message}", data = null)
            }
            )
        }
    }

    /**
     * 处理识别屏幕所有文字位置请求
     */
    private suspend fun handleGetScreenTextPositions(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        return withOverlayHiddenForRecognition(request) {
            val region = parseRegion(request.arguments)
            val rotationDegrees = request.arguments?.get("rotationDegrees")?.asInt ?: 0

            val result = MlkitScreenTextUtils.getScreenTextPositions(
                region = region,
                rotationDegrees = rotationDegrees
            )

            result.fold(
            onSuccess = { recognition ->
                val positionsArray = com.google.gson.JsonArray().apply {
                    recognition.positions.forEach { pos ->
                        add(JsonObject().apply {
                            addProperty("text", pos.text)
                            addProperty("left", pos.left)
                            addProperty("top", pos.top)
                            addProperty("right", pos.right)
                            addProperty("bottom", pos.bottom)
                        })
                    }
                }
                val data = JsonObject().apply {
                    addProperty("fullText", recognition.fullText)
                    add("positions", positionsArray)
                    addProperty("processingTimeMillis", recognition.processingTimeMillis)
                }
                request.createResponse(0, data = data, message = "识别完成")
            },
            onFailure = { e ->
                LogUtils.e(e)
                request.createResponse(-1, message = "识别失败: ${e.message}", data = null)
            }
            )
        }
    }

    /**
     * 处理识别屏幕中指定词组位置请求，直接返回 JSON 字符串
     */
    private suspend fun handleFindPhrasePositionsOnScreenAsJson(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        return withOverlayHiddenForRecognition(request) {
            val targetText = request.arguments?.get("targetText")?.asString
            if (targetText.isNullOrBlank()) {
                return@withOverlayHiddenForRecognition request.createResponse(-1, message = "targetText 不能为空", data = null)
            }

            val region = parseRegion(request.arguments)
            val rotationDegrees = request.arguments?.get("rotationDegrees")?.asInt ?: 0

            val result = MlkitScreenTextUtils.findPhrasePositionsOnScreenAsJson(
                targetText = targetText,
                region = region,
                rotationDegrees = rotationDegrees
            )

            result.fold(
            onSuccess = { jsonStr ->
                val data = JsonObject().apply {
                    addProperty("jsonResult", jsonStr)
                }
                request.createResponse(0, data = data, message = "识别完成")
            },
            onFailure = { e ->
                LogUtils.e(e)
                request.createResponse(-1, message = "识别失败: ${e.message}", data = null)
            }
            )
        }
    }

    /**
     * 处理识别屏幕所有文字位置请求，直接返回 JSON 字符串
     */
    private suspend fun handleGetScreenTextPositionsAsJson(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        return withOverlayHiddenForRecognition(request) {
            val region = parseRegion(request.arguments)
            val rotationDegrees = request.arguments?.get("rotationDegrees")?.asInt ?: 0

            val result = MlkitScreenTextUtils.getScreenTextPositionsAsJson(
                region = region,
                rotationDegrees = rotationDegrees
            )

            result.fold(
            onSuccess = { jsonStr ->
                val data = JsonObject().apply {
                    addProperty("jsonResult", jsonStr)
                }
                request.createResponse(0, data = data, message = "识别完成")
            },
            onFailure = { e ->
                LogUtils.e(e)
                request.createResponse(-1, message = "识别失败: ${e.message}", data = null)
            }
            )
        }
    }
}
