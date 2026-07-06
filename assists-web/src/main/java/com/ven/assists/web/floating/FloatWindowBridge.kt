package com.ven.assists.web.floating

import android.content.Context
import android.webkit.WebView

/**
 * 浮窗 WebView 外部注入桥接
 */
object FloatWindowBridge {

    /** 浮窗内容 WebView 工厂；null 时 fallback 为 [com.ven.assists.web.ASWebView] */
    @JvmStatic
    var webViewProvider: ((Context) -> WebView)? = null
}
