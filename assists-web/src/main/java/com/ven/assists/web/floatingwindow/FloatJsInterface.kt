package com.ven.assists.web.floatingwindow

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
import com.ven.assists.window.AssistsWindowManager.overlayToast
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
 * 通过 assistsxFloat.call(json) 调用，回调 assistsxFloatCallback
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
                else -> request.createResponse(-1, message = "方法未支持")
            }
            callbackResponse(response)
        }.onFailure {
            LogUtils.e(it)
            callbackResponse(request.createResponse(-1, message = it.message, data = null))
        }
    }

    /** 关闭当前浮窗 */
    private suspend fun close(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val result = runMain {
            AssistsWindowManager.viewList.values.find {
                it.view.findViewById<View>(R.id.web_view) == webView
            }?.let { wrapper ->
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
        runMain {
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
        return request.createResponse(0, data = true)
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
        val wrapper = AssistsWindowManager.viewList.values.find {
            it.view.findViewById<View>(R.id.web_view) == webView
        } ?: return request.createResponse(-1, message = "未找到对应浮窗")
        withContext(Dispatchers.Main) {
            wrapper.layoutParams.x += dx
            wrapper.layoutParams.y += dy
            AssistsWindowManager.updateViewLayout(wrapper.view, wrapper.layoutParams)
        }
        return request.createResponse(0, data = true)
    }

    /** 刷新浮窗 view 配置：接受 showTopOperationArea、showBottomOperationArea、backgroundColor、width、height、x、y，参数为空则不赋值 */
    private suspend fun refresh(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val wrapper = AssistsWindowManager.viewList.values.find {
            it.view.findViewById<View>(R.id.web_view) == webView
        } ?: return request.createResponse(-1, message = "未找到对应浮窗")
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
}
