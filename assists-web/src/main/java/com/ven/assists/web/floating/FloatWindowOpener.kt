package com.ven.assists.web.floating

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.blankj.utilcode.util.ScreenUtils
import com.google.gson.JsonElement
import com.ven.assists.AssistsCore
import com.ven.assists.web.ASWebView
import com.ven.assists.web.JavascriptInterfaceContext
import com.ven.assists.web.R
import com.ven.assists.web.databinding.WebFloatingWindowBinding
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowManager.ViewWrapper
import com.ven.assists.window.AssistsWindowWrapper

/** 打开 Web 浮窗的参数 */
data class FloatWindowOpenOptions(
    val url: String = "",
    val initialWidth: Int = (ScreenUtils.getScreenWidth() * 0.8).toInt(),
    val initialHeight: Int = (ScreenUtils.getScreenHeight() * 0.5).toInt(),
    val initialX: Int = 0,
    val initialY: Int = 0,
    val minWidth: Int = (ScreenUtils.getScreenWidth() * 0.5).toInt(),
    val minHeight: Int = (ScreenUtils.getScreenHeight() * 0.5).toInt(),
    val initialCenter: Boolean = true,
    val keepScreenOn: Boolean = false,
    val showTopOperationArea: Boolean = true,
    val showBottomOperationArea: Boolean = false,
    val backgroundColor: Int? = null,
)

/**
 * 浮窗 Web 打开公共逻辑（FloatJsInterface.open / loadWebViewOverlay 共用）
 */
object FloatWindowOpener {

    fun createContentWebView(context: Context): WebView {
        val webView = FloatWindowBridge.webViewProvider?.invoke(context) ?: ASWebView(context)
        webView.id = R.id.web_view
        return webView
    }

    fun parseBackgroundColor(arg: JsonElement?): Int? {
        if (arg == null || arg.isJsonNull) return null
        return when {
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

    fun destroyContentWebView(webView: WebView) {
        webView.loadUrl("about:blank")
        webView.stopLoading()
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
        if (webView is ViewGroup) {
            webView.removeAllViews()
        }
        AssistsWindowManager.removeWindow(webView)
    }

    /**
     * 打开浮窗并返回 [ViewWrapper]（失败返回 null）
     */
    fun open(options: FloatWindowOpenOptions): ViewWrapper? {
        val context = JavascriptInterfaceContext.requireContext()
        val contentWebView = createContentWebView(context).apply {
            setBackgroundColor(0)
            loadUrl(options.url)
        }
        val binding = WebFloatingWindowBinding.inflate(LayoutInflater.from(context)).apply {
            webViewContainer.removeAllViews()
            webViewContainer.addView(
                contentWebView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        return AssistsWindowManager.add(
            windowWrapper = AssistsWindowWrapper(
                wmLayoutParams = AssistsWindowManager.createLayoutParams().apply {
                    width = options.initialWidth
                    height = options.initialHeight
                },
                view = binding.root,
                onClose = {
                    destroyContentWebView(contentWebView)
                    binding.root.removeAllViews()
                    (it as ViewGroup).removeAllViews()
                    AssistsWindowManager.removeWindow(it)
                    AssistsCore.clearKeepScreenOn()
                },
            ).apply {
                viewBinding.ivWebBack.isVisible = options.showBottomOperationArea
                viewBinding.ivWebBack.setOnClickListener { contentWebView.goBack() }
                viewBinding.ivWebForward.isVisible = options.showBottomOperationArea
                viewBinding.ivWebForward.setOnClickListener { contentWebView.goForward() }
                viewBinding.ivWebRefresh.isVisible = options.showBottomOperationArea
                viewBinding.ivWebRefresh.setOnClickListener { contentWebView.reload() }
                viewBinding.flHeader.isVisible = options.showTopOperationArea
                viewBinding.llBottomBar.isVisible = options.showBottomOperationArea
                options.backgroundColor?.let { viewBinding.root.setBackgroundColor(it) }
                (contentWebView as? ASWebView)?.onReceivedTitle = { viewBinding.tvTitle.text = it }
                minWidth = options.minWidth
                minHeight = options.minHeight
                initialCenter = options.initialCenter
                initialX = options.initialX
                initialY = options.initialY
                if (options.keepScreenOn) AssistsCore.keepScreenOn()
            },
        )
    }
}
