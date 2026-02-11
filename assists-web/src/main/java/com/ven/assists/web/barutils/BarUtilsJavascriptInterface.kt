package com.ven.assists.web.barutils

import android.app.Activity
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.blankj.utilcode.util.BarUtils
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
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

/**
 * 栏相关（状态栏、导航栏、ActionBar 等）的 JavascriptInterface
 * 提供 BarUtils 能力，需在 Activity 环境下调用
 */
class BarUtilsJavascriptInterface(val webView: WebView) {
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
        val js = String.format("javascript:assistsxBarUtilsCallback('%s')", encoded)
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
            object : TypeToken<CallRequest<JsonObject>>() {}.type
        )
        runCatching {
            val response = when (request.method) {
                // 状态栏
                BarUtilsCallMethod.getStatusBarHeight -> {
                    val height = BarUtils.getStatusBarHeight()
                    request.createResponse(0, data = JsonObject().apply { addProperty("height", height) })
                }
                BarUtilsCallMethod.setStatusBarVisibility -> withContext(Dispatchers.Main) {
                    val activity = getActivity()
                    if (activity == null) request.createResponse(-1, message = "Activity not available")
                    else {
                        val isVisible = request.arguments?.get("isVisible")?.asBoolean ?: true
                        BarUtils.setStatusBarVisibility(activity, isVisible)
                        request.createResponse(0)
                    }
                }
                BarUtilsCallMethod.isStatusBarVisible -> {
                    val activity = getActivity()
                    if (activity == null) request.createResponse(-1, message = "Activity not available")
                    else {
                        val visible = BarUtils.isStatusBarVisible(activity)
                        request.createResponse(0, data = JsonObject().apply { addProperty("visible", visible) })
                    }
                }
                BarUtilsCallMethod.setStatusBarLightMode -> withContext(Dispatchers.Main) {
                    val activity = getActivity()
                    if (activity == null) request.createResponse(-1, message = "Activity not available")
                    else {
                        val isLightMode = request.arguments?.get("isLightMode")?.asBoolean ?: true
                        BarUtils.setStatusBarLightMode(activity, isLightMode)
                        request.createResponse(0)
                    }
                }
                BarUtilsCallMethod.isStatusBarLightMode -> {
                    val activity = getActivity()
                    if (activity == null) request.createResponse(-1, message = "Activity not available")
                    else {
                        val isLightMode = BarUtils.isStatusBarLightMode(activity)
                        request.createResponse(0, data = JsonObject().apply { addProperty("isLightMode", isLightMode) })
                    }
                }
                BarUtilsCallMethod.setStatusBarColor -> withContext(Dispatchers.Main) {
                    val activity = getActivity()
                    val color = request.arguments?.get("color")?.asInt
                    when {
                        activity == null -> request.createResponse(-1, message = "Activity not available")
                        color == null -> request.createResponse(-1, message = "color is required")
                        else -> {
                            val isDecor = request.arguments?.get("isDecor")?.asBoolean ?: false
                            BarUtils.setStatusBarColor(activity, color, isDecor)
                            request.createResponse(0)
                        }
                    }
                }
                BarUtilsCallMethod.transparentStatusBar -> withContext(Dispatchers.Main) {
                    val activity = getActivity()
                    if (activity == null) request.createResponse(-1, message = "Activity not available")
                    else {
                        BarUtils.transparentStatusBar(activity)
                        request.createResponse(0)
                    }
                }

                // ActionBar
                BarUtilsCallMethod.getActionBarHeight -> {
                    val height = BarUtils.getActionBarHeight()
                    request.createResponse(0, data = JsonObject().apply { addProperty("height", height) })
                }

                // 导航栏
                BarUtilsCallMethod.getNavBarHeight -> {
                    val height = BarUtils.getNavBarHeight()
                    request.createResponse(0, data = JsonObject().apply { addProperty("height", height) })
                }
                BarUtilsCallMethod.setNavBarVisibility -> withContext(Dispatchers.Main) {
                    val activity = getActivity()
                    if (activity == null) request.createResponse(-1, message = "Activity not available")
                    else {
                        val isVisible = request.arguments?.get("isVisible")?.asBoolean ?: true
                        BarUtils.setNavBarVisibility(activity, isVisible)
                        request.createResponse(0)
                    }
                }
                BarUtilsCallMethod.isNavBarVisible -> {
                    val activity = getActivity()
                    if (activity == null) request.createResponse(-1, message = "Activity not available")
                    else {
                        val visible = BarUtils.isNavBarVisible(activity)
                        request.createResponse(0, data = JsonObject().apply { addProperty("visible", visible) })
                    }
                }
                BarUtilsCallMethod.setNavBarColor -> withContext(Dispatchers.Main) {
                    val activity = getActivity()
                    val color = request.arguments?.get("color")?.asInt
                    when {
                        activity == null -> request.createResponse(-1, message = "Activity not available")
                        color == null -> request.createResponse(-1, message = "color is required")
                        else -> {
                            BarUtils.setNavBarColor(activity, color)
                            request.createResponse(0)
                        }
                    }
                }
                BarUtilsCallMethod.getNavBarColor -> {
                    val activity = getActivity()
                    if (activity == null) request.createResponse(-1, message = "Activity not available")
                    else {
                        val color = BarUtils.getNavBarColor(activity)
                        request.createResponse(0, data = JsonObject().apply { addProperty("color", color) })
                    }
                }
                BarUtilsCallMethod.isSupportNavBar -> {
                    val support = BarUtils.isSupportNavBar()
                    request.createResponse(0, data = JsonObject().apply { addProperty("support", support) })
                }
                BarUtilsCallMethod.setNavBarLightMode -> withContext(Dispatchers.Main) {
                    val activity = getActivity()
                    if (activity == null) request.createResponse(-1, message = "Activity not available")
                    else {
                        val isLightMode = request.arguments?.get("isLightMode")?.asBoolean ?: true
                        BarUtils.setNavBarLightMode(activity, isLightMode)
                        request.createResponse(0)
                    }
                }
                BarUtilsCallMethod.isNavBarLightMode -> {
                    val activity = getActivity()
                    if (activity == null) request.createResponse(-1, message = "Activity not available")
                    else {
                        val isLightMode = BarUtils.isNavBarLightMode(activity)
                        request.createResponse(0, data = JsonObject().apply { addProperty("isLightMode", isLightMode) })
                    }
                }
                BarUtilsCallMethod.transparentNavBar -> withContext(Dispatchers.Main) {
                    val activity = getActivity()
                    if (activity == null) request.createResponse(-1, message = "Activity not available")
                    else {
                        BarUtils.transparentNavBar(activity)
                        request.createResponse(0)
                    }
                }

                else -> request.createResponse(-1, message = "Method not supported")
            }
            callbackResponse(response)
        }.onFailure {
            LogUtils.e(it)
            callbackResponse(request.createResponse(-1, message = it.message, data = null))
        }
    }

    /** BarUtils 需要 Activity，统一从 JavascriptInterfaceContext 获取 */
    private fun getActivity(): Activity? = JavascriptInterfaceContext.getActivity()
}
