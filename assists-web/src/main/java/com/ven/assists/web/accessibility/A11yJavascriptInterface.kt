package com.ven.assists.web.accessibility

import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.GsonBuilder
import com.ven.assists.AssistsCore
import com.ven.assists.web.CallRequest
import com.ven.assists.web.CallRequestParser
import com.ven.assists.web.CallResponse
import com.ven.assists.web.createResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

/**
 * 无障碍相关的 JavascriptInterface
 * 提供检查无障碍服务是否开启、打开无障碍设置页面的能力
 */
class A11yJavascriptInterface(val webView: WebView) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    /**
     * 回调响应给 JavaScript
     */
    fun <T> callbackResponse(result: CallResponse<T>, includeNulls: Boolean = false) {
        coroutineScope.launch {
            runCatching {
                val json = if (includeNulls) GsonBuilder().serializeNulls().create().toJson(result) else GsonUtils.toJson(result)
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
        val js = String.format("javascript:assistsxA11yCallback('%s')", encoded)
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
        val request = CallRequestParser.parse(originJson) ?: run {
            callbackResponse(CallResponse<Any>(code = -1, message = "请求解析失败", data = null))
            return
        }
        runCatching {
            val response = when (request.method) {
                AccessibilityCallMethod.isA11yEnabled -> {
                    handleIsA11yEnabled(request)
                }

                AccessibilityCallMethod.getSelectToSpeakStatus -> {
                    handleSelectToSpeakStatus(request)
                }

                AccessibilityCallMethod.openSystemSettings -> {
                    handleOpenSystemSettings(request)
                }

                AccessibilityCallMethod.openAccessibilitySetting -> {
                    handleOpenAccessibilitySetting(request)
                }

                else -> {
                    request.createResponse(-1, message = "方法未支持: ${request.method}")
                }
            }
            callbackResponse(response, includeNulls = request.method == AccessibilityCallMethod.getSelectToSpeakStatus)
        }.onFailure {
            LogUtils.e(it)
            callbackResponse(request.createResponse(-1, message = "执行失败: ${it.message}", data = null))
        }
    }

    /**
     * 处理检查无障碍服务是否已开启请求
     */
    private fun handleIsA11yEnabled(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val enabled = AssistsCore.isA11yEnabled()
        val result = JsonObject().apply {
            addProperty("enabled", enabled)
        }
        return request.createResponse(
            code = 0,
            data = result,
            message = if (enabled) "无障碍服务已开启" else "无障碍服务未开启"
        )
    }

    /** 返回完整快照，保留无法读取的设置项为 null。 */
    private fun handleSelectToSpeakStatus(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val status = AssistsCore.getSelectToSpeakStatus(webView.context)
        val settings = JsonObject().apply {
            status.shortcutSettings.forEach { (key, value) ->
                if (value == null) add(key, JsonNull.INSTANCE) else addProperty(key, value)
            }
        }
        fun stringArray(values: List<String>) = JsonArray().apply { values.forEach { add(it) } }
        val result = JsonObject().apply {
            addProperty("googleServiceEnabled", status.googleServiceEnabled)
            add("matchingServiceIds", stringArray(status.matchingServiceIds))
            add("enabledServiceIds", stringArray(status.enabledServiceIds))
            addProperty("accessibilityButton", status.accessibilityButton.name)
            addProperty("volumeKeys", status.volumeKeys.name)
            addProperty("quickSettings", status.quickSettings.name)
            add("shortcutSettings", settings)
        }
        return request.createResponse(code = 0, data = result)
    }

    /** 启动指定设置页面，失败通过 success 返回。 */
    private fun handleOpenSystemSettings(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val args = request.arguments
        fun stringArg(name: String): String? = args?.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        val action = stringArg("action")
        if (action.isNullOrBlank()) return request.createResponse(-1, message = "Settings action is required")
        val success = AssistsCore.openSystemSettings(
            webView.context, action, stringArg("dataUri"), stringArg("accessibilityServiceComponent")
        )
        return request.createResponse(code = 0, data = JsonObject().apply { addProperty("success", success) })
    }

    /**
     * 处理打开无障碍设置页面请求
     */
    private fun handleOpenAccessibilitySetting(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        return runCatching {
            AssistsCore.openAccessibilitySetting()
            val result = JsonObject().apply {
                addProperty("success", true)
            }
            request.createResponse(
                code = 0,
                data = result,
                message = "已跳转到无障碍设置页面"
            )
        }.getOrElse { e ->
            LogUtils.e(e)
            val result = JsonObject().apply {
                addProperty("success", false)
            }
            request.createResponse(
                code = -1,
                data = result,
                message = "跳转失败: ${e.message}"
            )
        }
    }
}
