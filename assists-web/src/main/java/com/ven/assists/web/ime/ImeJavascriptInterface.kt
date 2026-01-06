package com.ven.assists.web.ime

import android.util.Base64
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ven.assists.web.CallRequest
import com.ven.assists.web.CallResponse
import com.ven.assists.web.JavascriptInterfaceContext
import com.ven.assists.web.createResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rkr.simplekeyboard.inputmethod.latin.LatinIME
import java.nio.charset.StandardCharsets

/**
 * 输入法相关的 JavascriptInterface
 * 提供输入法操作相关的功能，如执行编辑器动作（搜索等）
 */
class ImeJavascriptInterface(val webView: WebView) {
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
        val js = String.format("javascript:assistsxImeCallback('%s')", encoded)
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
                ImeCallMethod.performEditorAction -> {
                    handlePerformEditorAction(request)
                }

                ImeCallMethod.openInputMethodSettings -> {
                    handleOpenInputMethodSettings(request)
                }

                ImeCallMethod.isInputMethodEnabled -> {
                    handleIsInputMethodEnabled(request)
                }

                ImeCallMethod.isCurrentInputMethod -> {
                    handleIsCurrentInputMethod(request)
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
     * 处理执行编辑器动作请求
     */
    private fun handlePerformEditorAction(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        // 获取 actionId 参数，默认为 IME_ACTION_SEARCH
        val actionId = request.arguments?.get("actionId")?.asInt ?: EditorInfo.IME_ACTION_SEARCH

        // 执行编辑器动作
        val success = LatinIME.performEditorAction(actionId)

        val result = JsonObject().apply {
            addProperty("success", success)
            addProperty("actionId", actionId)
        }

        return request.createResponse(
            code = if (success) 0 else -1,
            data = result,
            message = if (success) "执行成功" else "执行失败，请确保当前有输入框聚焦且输入法已激活"
        )
    }

    /**
     * 处理跳转到输入法管理页面请求
     */
    private fun handleOpenInputMethodSettings(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val context = JavascriptInterfaceContext.getContext()
        if (context == null) {
            return request.createResponse(
                code = -1,
                data = null,
                message = "无法获取 Context"
            )
        }

        // 跳转到输入法管理页面
        val success = LatinIME.openInputMethodSettings(context)

        val result = JsonObject().apply {
            addProperty("success", success)
        }

        return request.createResponse(
            code = if (success) 0 else -1,
            data = result,
            message = if (success) "已跳转到输入法管理页面" else "跳转失败"
        )
    }

    /**
     * 处理检查输入法是否启用请求
     */
    private fun handleIsInputMethodEnabled(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val context = JavascriptInterfaceContext.getContext()
        if (context == null) {
            return request.createResponse(
                code = -1,
                data = null,
                message = "无法获取 Context"
            )
        }

        // 检查输入法是否启用
        val isEnabled = LatinIME.isInputMethodEnabled(context)

        val result = JsonObject().apply {
            addProperty("enabled", isEnabled)
        }

        return request.createResponse(
            code = 0,
            data = result,
            message = if (isEnabled) "输入法已启用" else "输入法未启用"
        )
    }

    /**
     * 处理检查当前选中的输入法是否是当前输入法请求
     */
    private fun handleIsCurrentInputMethod(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val context = JavascriptInterfaceContext.getContext()
        if (context == null) {
            return request.createResponse(
                code = -1,
                data = null,
                message = "无法获取 Context"
            )
        }

        // 检查当前选中的输入法是否是当前输入法
        val isCurrent = LatinIME.isCurrentInputMethod(context)

        val result = JsonObject().apply {
            addProperty("isCurrent", isCurrent)
        }

        return request.createResponse(
            code = 0,
            data = result,
            message = if (isCurrent) "当前选中的输入法是当前输入法" else "当前选中的输入法不是当前输入法"
        )
    }
}

