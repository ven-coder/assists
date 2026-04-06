package com.ven.assists.web.floating

import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.view.isVisible
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ven.assists.AssistsCore
import com.ven.assists.web.ASWebView
import com.ven.assists.web.CallInterceptResult
import com.ven.assists.web.CallRequest
import com.ven.assists.web.CallResponse
import com.ven.assists.web.JavascriptInterfaceContext
import com.ven.assists.web.R
import com.ven.assists.web.createResponse
import com.ven.assists.web.databinding.WebFloatingWindowBinding
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.utils.runMain
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowManager.ViewWrapper
import com.ven.assists.window.AssistsWindowManager.nonTouchableByWrapper
import com.ven.assists.window.AssistsWindowManager.overlayToast
import com.ven.assists.window.AssistsWindowManager.touchableByWrapper
import com.ven.assists.window.AssistsWindowWrapper
import com.ven.assists.base.databinding.AssistsWindowLayoutWrapperBinding
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

/**
 * 浮窗独立 JsInterface，与 ASJavascriptInterfaceAsync 平级
 * 通过 assistsxFloat.call(json) 调用，回调 assistsxFloatCallback。
 * 封装 [AssistsWindowManager] 的全局能力与当前 Web 浮窗（[R.id.web_view]）相关操作。
 */
class FloatJsInterface(val webView: WebView) {
    var callIntercept: ((json: String) -> CallInterceptResult)? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun <T> callbackResponse(result: CallResponse<T>) {
        scope.launch {
            runCatching {
                val json = GsonUtils.toJson(result)
                callback(json)
            }.onFailure { LogUtils.e(it) }
        }
    }

    fun callback(result: String) {
        val encoded = Base64.encodeToString(result.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val js = String.format("javascript:assistsxFloatCallback('%s')", encoded)
        webView.evaluateJavascript(js, null)
    }

    @JavascriptInterface
    fun call(originJson: String): String {
        val result = GsonUtils.toJson(CallResponse<Any>(code = 0))
        scope.launch(Dispatchers.IO) {
            processCall(originJson)
        }
        return result
    }

    private suspend fun processCall(originJson: String) {
        var requestJson = originJson
        val intercepted = runCatching {
            callIntercept?.invoke(originJson)?.let {
                if (it.intercept) {
                    callback(it.result)
                    true
                } else {
                    requestJson = it.result
                    false
                }
            } ?: false
        }.onFailure { LogUtils.e(it) }
        if (intercepted.getOrNull() == true) return

        val request = GsonUtils.fromJson<CallRequest<JsonObject>>(requestJson, object : TypeToken<CallRequest<JsonObject>>() {}.type)
        runCatching {
            val response = when (request.method) {
                FloatCallMethod.open -> open(request)
                FloatCallMethod.close -> close(request)
                FloatCallMethod.setFlags -> setFlags(request)
                FloatCallMethod.toast -> toast(request)
                FloatCallMethod.move -> move(request)
                FloatCallMethod.refresh -> refresh(request)
                FloatCallMethod.hideAll -> hideAll(request)
                FloatCallMethod.hideTop -> hideTop(request)
                FloatCallMethod.showAll -> showAll(request)
                FloatCallMethod.showTop -> showTop(request)
                FloatCallMethod.temporarilyHideAll -> temporarilyHideAll(request)
                FloatCallMethod.touchableByAll -> touchableByAll(request)
                FloatCallMethod.nonTouchableByAll -> nonTouchableByAll(request)
                FloatCallMethod.pop -> pop(request)
                FloatCallMethod.removeAllWindows -> removeAllWindows(request)
                FloatCallMethod.hideCurrent -> hideCurrent(request)
                FloatCallMethod.showCurrent -> showCurrent(request)
                FloatCallMethod.isCurrentVisible -> isCurrentVisible(request)
                FloatCallMethod.containsCurrent -> containsCurrent(request)
                else -> request.createResponse(-1, message = "方法未支持")
            }
            callbackResponse(response)
        }.onFailure {
            LogUtils.e(it)
            callbackResponse(request.createResponse(-1, message = it.message, data = null))
        }
    }

    /** 解析当前 JS 所在 Web 浮窗的 [ViewWrapper] */
    private fun findWrapperForWebView(): ViewWrapper? =
        AssistsWindowManager.viewList.values.find { it.view.findViewById<View>(R.id.web_view) == webView }

    /** 关闭当前浮窗 */
    private suspend fun close(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val result = runMain {
            findWrapperForWebView()?.let { wrapper ->
                wrapper.view.findViewById<ASWebView>(R.id.web_view)?.let { wv ->
                    wv.loadUrl("about:blank")
                    wv.stopLoading()
                    wv.clearHistory()
                    wv.removeAllViews()
                    wv.destroy()
                    (wv as ViewGroup).removeAllViews()
                    AssistsWindowManager.removeWindow(wv)
                    AssistsCore.clearKeepScreenOn()
                }
                AssistsWindowManager.removeWindow(wrapper.view)
                true
            }
        }
        result?.let { return request.createResponse(0, data = it) }
        return request.createResponse(0, data = false)
    }

    /** 加载浮窗 */
    private suspend fun open(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val url = request.arguments?.get("url")?.asString ?: ""
        val initialWidth = request.arguments?.get("initialWidth")?.asInt ?: (ScreenUtils.getScreenWidth() * 0.8).toInt()
        val initialHeight = request.arguments?.get("initialHeight")?.asInt ?: (ScreenUtils.getScreenHeight() * 0.5).toInt()
        val initialX = request.arguments?.get("initialX")?.asInt ?: 0
        val initialY = request.arguments?.get("initialY")?.asInt ?: 0
        val minWidth = request.arguments?.get("minWidth")?.asInt ?: (ScreenUtils.getScreenWidth() * 0.5).toInt()
        val minHeight = request.arguments?.get("minHeight")?.asInt ?: (ScreenUtils.getScreenHeight() * 0.5).toInt()
        val initialCenter = request.arguments?.get("initialCenter")?.asBoolean ?: true
        val keepScreenOn = request.arguments?.get("keepScreenOn")?.asBoolean ?: false
        val showTopOperationArea = request.arguments?.get("showTopOperationArea")?.asBoolean ?: true
        val showBottomOperationArea = request.arguments?.get("showBottomOperationArea")?.asBoolean ?: false
        val backgroundColor = request.arguments?.get("backgroundColor")?.let { arg ->
            when {
                arg.isJsonPrimitive && arg.asJsonPrimitive.isString -> {
                    val s = arg.asString
                    if (s.isNullOrBlank()) null else try {
                        s.toColorInt()
                    } catch (_: Exception) {
                        null
                    }
                }

                arg.isJsonPrimitive && arg.asJsonPrimitive.isNumber -> arg.asInt
                else -> null
            }
        }
        val added = runMain {
            val binding = WebFloatingWindowBinding.inflate(LayoutInflater.from(JavascriptInterfaceContext.requireContext())).apply {
                this.webView.loadUrl(url)
                this.webView.setBackgroundColor(0)
            }
            AssistsWindowManager.add(
                windowWrapper = AssistsWindowWrapper(
                    wmLayoutParams = AssistsWindowManager.createLayoutParams().apply {
                        width = initialWidth
                        height = initialHeight
                    },
                    view = binding.root,
                    onClose = {
                        binding.webView.loadUrl("about:blank")
                        binding.webView.stopLoading()
                        binding.webView.clearHistory()
                        binding.webView.removeAllViews()
                        binding.webView.destroy()
                        binding.root.removeAllViews()
                        (it as ViewGroup).removeAllViews()
                        AssistsWindowManager.removeWindow(it)
                        AssistsCore.clearKeepScreenOn()
                    }
                ).apply {
                    viewBinding.ivWebBack.isVisible = showBottomOperationArea
                    viewBinding.ivWebBack.setOnClickListener { binding.webView.goBack() }
                    viewBinding.ivWebForward.isVisible = showBottomOperationArea
                    viewBinding.ivWebForward.setOnClickListener { binding.webView.goForward() }
                    viewBinding.ivWebRefresh.isVisible = showBottomOperationArea
                    viewBinding.ivWebRefresh.setOnClickListener { binding.webView.reload() }
                    viewBinding.flHeader.isVisible = showTopOperationArea
                    viewBinding.llBottomBar.isVisible = showBottomOperationArea
                    backgroundColor?.let { viewBinding.root.setBackgroundColor(it) }
                    binding.webView.onReceivedTitle = { viewBinding.tvTitle.text = it }
                    this.minWidth = minWidth
                    this.minHeight = minHeight
                    this.initialCenter = initialCenter
                    this.initialX = initialX
                    this.initialY = initialY
                    if (keepScreenOn) AssistsCore.keepScreenOn()
                }
            )
        }
        val data = JsonObject().apply {
            addProperty("success", true)
            added?.let { addProperty("uniqueId", it.uniqueId) }
        }
        return request.createResponse(0, data = data)
    }

    /** 设置浮窗标志位 */
    private fun setFlags(request: CallRequest<JsonObject>): CallResponse<Any?> {
        request.arguments?.apply {
            val flagList = arrayListOf<Int>()
            get("flags")?.asJsonArray?.forEach { flagList.add(it.asInt) }
            val flags = flagList.reduce { a, b -> a or b }
            CoroutineWrapper.launch { AssistsWindowManager.setFlags(flags) }
        }
        return request.createResponse(0, data = true)
    }

    /** Toast */
    private fun toast(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val text = request.arguments?.get("text")?.asString ?: ""
        val delay = request.arguments?.get("delay")?.asLong ?: 2000L
        text.overlayToast(delay)
        return request.createResponse(0, data = true)
    }

    /** 移动浮窗：传入 x/y 为移动距离，在当前位置基础上偏移后更新 layoutParams */
    private suspend fun move(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val dx = request.arguments?.get("x")?.asInt ?: return request.createResponse(-1, message = "x 不能为空")
        val dy = request.arguments?.get("y")?.asInt ?: return request.createResponse(-1, message = "y 不能为空")
        val wrapper = findWrapperForWebView() ?: return request.createResponse(-1, message = "未找到对应浮窗")
        withContext(Dispatchers.Main) {
            wrapper.layoutParams.x += dx
            wrapper.layoutParams.y += dy
            AssistsWindowManager.updateViewLayout(wrapper.view, wrapper.layoutParams)
        }
        return request.createResponse(0, data = true)
    }

    /** 刷新浮窗 view 配置：接受 showTopOperationArea、showBottomOperationArea、backgroundColor、width、height、x、y，参数为空则不赋值 */
    private suspend fun refresh(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val wrapper = findWrapperForWebView() ?: return request.createResponse(-1, message = "未找到对应浮窗")
        withContext(Dispatchers.Main) {
            request.arguments?.let { args ->
                AssistsWindowLayoutWrapperBinding.bind(wrapper.view).apply {
                    args.get("showTopOperationArea")?.takeIf { !it.isJsonNull }?.asBoolean?.let { flHeader.isVisible = it }
                    args.get("showBottomOperationArea")?.takeIf { !it.isJsonNull }?.asBoolean?.let { llBottomBar.isVisible = it }
                }
                args.get("backgroundColor")?.takeIf { !it.isJsonNull }?.let { arg ->
                    when {
                        arg.isJsonPrimitive && arg.asJsonPrimitive.isString -> {
                            val s = arg.asString
                            if (s == "default") {
                                wrapper.view.setBackgroundResource(R.drawable.bg_1)
                            } else if (!s.isNullOrBlank()) {
                                try {
                                    wrapper.view.setBackgroundColor(s.toColorInt())
                                } catch (_: Exception) { }
                            }
                        }
                        arg.isJsonPrimitive && arg.asJsonPrimitive.isNumber -> {
                            wrapper.view.setBackgroundColor(arg.asInt)
                        }
                        else -> { }
                    }
                }
                args.get("width")?.takeIf { !it.isJsonNull }?.asInt?.let { wrapper.layoutParams.width = it }
                args.get("height")?.takeIf { !it.isJsonNull }?.asInt?.let { wrapper.layoutParams.height = it }
                args.get("x")?.takeIf { !it.isJsonNull }?.asInt?.let { wrapper.layoutParams.x = it }
                args.get("y")?.takeIf { !it.isJsonNull }?.asInt?.let { wrapper.layoutParams.y = it }
            }
            AssistsWindowManager.updateViewLayout(wrapper.view, wrapper.layoutParams)
        }
        return request.createResponse(0, data = true)
    }

    /** 对应 [AssistsWindowManager.hideAll] */
    private suspend fun hideAll(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val isTouchable = request.arguments?.get("isTouchable")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
        AssistsWindowManager.hideAll(isTouchable)
        return request.createResponse(0, data = true)
    }

    /** 对应 [AssistsWindowManager.hideTop] */
    private suspend fun hideTop(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val isTouchable = request.arguments?.get("isTouchable")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
        AssistsWindowManager.hideTop(isTouchable)
        return request.createResponse(0, data = true)
    }

    /** 对应 [AssistsWindowManager.showAll] */
    private suspend fun showAll(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val isTouchable = request.arguments?.get("isTouchable")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
        AssistsWindowManager.showAll(isTouchable)
        return request.createResponse(0, data = true)
    }

    /** 对应 [AssistsWindowManager.showTop] */
    private suspend fun showTop(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val isTouchable = request.arguments?.get("isTouchable")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
        AssistsWindowManager.showTop(isTouchable)
        return request.createResponse(0, data = true)
    }

    /** 对应 [AssistsWindowManager.temporarilyHideAll] */
    private fun temporarilyHideAll(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val durationMs = request.arguments?.get("durationMs")?.asLong ?: 500L
        val isTouchable = request.arguments?.get("isTouchable")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
        AssistsWindowManager.temporarilyHideAll(durationMs, isTouchable, emptyList())
        return request.createResponse(0, data = true)
    }

    /** 对应 [AssistsWindowManager.touchableByAll] */
    private suspend fun touchableByAll(request: CallRequest<JsonObject>): CallResponse<Any?> {
        AssistsWindowManager.touchableByAll()
        return request.createResponse(0, data = true)
    }

    /** 对应 [AssistsWindowManager.nonTouchableByAll] */
    private suspend fun nonTouchableByAll(request: CallRequest<JsonObject>): CallResponse<Any?> {
        AssistsWindowManager.nonTouchableByAll()
        return request.createResponse(0, data = true)
    }

    /** 对应 [AssistsWindowManager.pop]：移除栈顶浮窗 */
    private suspend fun pop(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val showTopArg = request.arguments?.get("showTop")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
        AssistsWindowManager.pop(showTopArg)
        return request.createResponse(0, data = true)
    }

    /** 对应 [AssistsWindowManager.removeAllWindow]，必须传 confirm: true */
    private fun removeAllWindows(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val confirm = request.arguments?.get("confirm")?.asBoolean == true
        if (!confirm) return request.createResponse(-1, message = "需要 confirm: true")
        AssistsWindowManager.removeAllWindow()
        return request.createResponse(0, data = true)
    }

    /** 对应 [AssistsWindowManager.hide]：仅作用于当前 Web 浮窗 */
    private suspend fun hideCurrent(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val wrapper = findWrapperForWebView() ?: return request.createResponse(-1, message = "未找到对应浮窗")
        val isTouchable = request.arguments?.get("isTouchable")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
        AssistsWindowManager.hide(wrapper.view, isTouchable)
        return request.createResponse(0, data = true)
    }

    /** 显示当前 Web 浮窗（与 showTop 中单窗逻辑一致） */
    private suspend fun showCurrent(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val wrapper = findWrapperForWebView() ?: return request.createResponse(-1, message = "未找到对应浮窗")
        val isTouchable = request.arguments?.get("isTouchable")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
        withContext(Dispatchers.Main) {
            wrapper.view.isVisible = true
            if (isTouchable) wrapper.touchableByWrapper() else wrapper.nonTouchableByWrapper()
        }
        return request.createResponse(0, data = true)
    }

    /** 对应 [AssistsWindowManager.isVisible]：当前 Web 浮窗 */
    private suspend fun isCurrentVisible(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val wrapper = findWrapperForWebView() ?: return request.createResponse(-1, message = "未找到对应浮窗")
        val visible = withContext(Dispatchers.Main) {
            AssistsWindowManager.isVisible(wrapper.view)
        }
        return request.createResponse(0, data = visible)
    }

    /** 对应 [AssistsWindowManager.contains]：当前 Web 浮窗是否已加入管理器 */
    private suspend fun containsCurrent(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val wrapper = findWrapperForWebView() ?: return request.createResponse(-1, message = "未找到对应浮窗")
        val c = withContext(Dispatchers.Main) {
            AssistsWindowManager.contains(wrapper.view)
        }
        return request.createResponse(0, data = c)
    }
}
