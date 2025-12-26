package com.ven.assists.web

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Base64
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ven.assists.AssistsCore
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.utils.runMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

@SuppressLint("SetJavaScriptEnabled")
open class ASWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    companion object {
        val globalJavascriptCallIntercepts = arrayListOf<(json: String) -> CallInterceptResult>()
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    var onReceivedTitle: ((title: String) -> Unit)? = null

    var callIntercept: ((json: String) -> CallInterceptResult)? = null

    val eventFilters = arrayListOf<AccessibilityEventFilter>()

    val javascriptCallIntercept: (json: String) -> CallInterceptResult = intercept@{ json: String ->
        var requestJson = json

        globalJavascriptCallIntercepts.forEach {
            val interceptResult = it.invoke(json)
            if (interceptResult.intercept) {
                return@intercept interceptResult
            } else {
                requestJson = interceptResult.result
            }
        }

        callIntercept?.invoke(requestJson)?.let {
            if (it.intercept) {
                return@intercept it
            } else {
                requestJson = it.result
            }
        }

        val request = GsonUtils.fromJson<CallRequest<JsonObject>>(requestJson, object : TypeToken<CallRequest<JsonObject>>() {}.type)
        var callInterceptResult = CallInterceptResult(false, requestJson)
        when (request.method) {
            CallMethod.setAccessibilityEventFilters -> {

                request.arguments?.get("value")?.asJsonArray?.let {

                    GsonUtils.fromJson<List<AccessibilityEventFilter>>(
                        GsonUtils.toJson(it),
                        GsonUtils.getListType(AccessibilityEventFilter::class.java)
                    ).apply {
                        eventFilters.clear()
                        eventFilters.addAll(this)
                    }

                }
                var result = GsonUtils.toJson(CallResponse<Any>(code = -1))

                callInterceptResult = CallInterceptResult(true, result)
            }

            CallMethod.addAccessibilityEventFilter -> {
                request.arguments?.get("value")?.asJsonObject?.let {
                    GsonUtils.fromJson(
                        GsonUtils.toJson(it),
                        AccessibilityEventFilter::class.java
                    ).apply {
                        eventFilters.add(this)
                    }
                }
                var result = GsonUtils.toJson(CallResponse<Any>(code = -1))

                callInterceptResult = CallInterceptResult(true, result)
            }
        }


        callInterceptResult
    }
    val javascriptInterface = ASJavascriptInterface(webView = this).apply {
        callIntercept = javascriptCallIntercept
    }
    val javascriptInterfaceAsync = ASJavascriptInterfaceAsync(webView = this).apply {
        callIntercept = javascriptCallIntercept
    }


    val assistsServiceListener = object : AssistsServiceListener {
        override fun onAccessibilityEvent(event: AccessibilityEvent) {
            if (eventFilters.isEmpty()) return
            eventFilters.find {
                val eventType = event.eventType
                val eventTypeValue = it.eventTypes?.contains(eventType)
                val packageName = event.packageName
//                Log.d(LogUtils.getConfig().globalTag, "$eventType/$eventTypeValue, $packageName/${it.packageName}")
                return@find it.packageName == packageName && eventTypeValue == true
            }?.let {
                if (it.processInBackground) {
                    coroutineScope.launch(Dispatchers.IO) {
                        processEvent(event)?.let { runMain { onAccessibilityEvent(CallResponse(code = 0, data = it)) } }
                    }
                } else {
                    processEvent(event)?.let { onAccessibilityEvent(CallResponse(code = 0, data = it)) }
                }
            }
        }

        private fun processEvent(event: AccessibilityEvent): JsonObject? {
            return runCatching {
                val node = event.source?.toNode()
                val jsonObject = JsonObject().apply {
                    addProperty("packageName", event.packageName?.toString() ?: "")
                    addProperty("className", event.className?.toString() ?: "")
                    addProperty("eventType", event.eventType)
                    addProperty("action", event.action)
                    add("texts", JsonArray().apply {
                        event.text.forEach { text -> this.add(text.toString()) }
                    })
                    node?.let {
                        val element = GsonUtils.getGson().toJsonTree(node)
                        add("node", element.asJsonObject)
                    }
                }
//                    if (LogUtils.getConfig().isLogSwitch) {
//                        Log.d(LogUtils.getConfig().globalTag, jsonObject.toString())
//                    }
                jsonObject
            }.onFailure {
                LogUtils.e(it)
            }.getOrNull()

        }
    }


    init {
        // 初始化WebView设置
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            // 启用内置缩放控件
            builtInZoomControls = true
            // 隐藏缩放控件
            displayZoomControls = false
            // 支持viewport
            useWideViewPort = true
            // 加载内容自适应屏幕
            loadWithOverviewMode = true
            allowUniversalAccessFromFileURLs = true
            allowFileAccessFromFileURLs = true
            domStorageEnabled = true
            databaseEnabled = true
            setWebContentsDebuggingEnabled(true)
        }

        // 设置WebViewClient
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 注入辅助函数到JS环境
            }
        }

        // 设置WebChromeClient用于处理JS的alert等
        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                onReceivedTitle?.invoke(title ?: "")
            }
        }

        // 启用软键盘支持
        requestFocus()
        isFocusableInTouchMode = true
        isFocusable = true
        addJavascriptInterface(javascriptInterface, "assistsx")
        addJavascriptInterface(javascriptInterfaceAsync, "assistsxAsync")
        AssistsService.listeners.add(assistsServiceListener)
    }

    fun <T> onAccessibilityEvent(result: CallResponse<T>) {
        runCatching {
            val json = GsonUtils.toJson(result)

            val encoded = Base64.encodeToString(json.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

            evaluateJavascript(
                """
            try {
                if (typeof onAccessibilityEvent === 'function') {
                    onAccessibilityEvent("$encoded");
                }
            } catch (e) {
                console.error('Error calling onAccessibilityEvent:', e);
            }
            """.trimIndent(),
                null
            )
        }.onFailure {
            LogUtils.e("Failed to call onAccessibilityEvent: ${it.message}")
        }
    }


    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        AssistsService.listeners.remove(assistsServiceListener)
    }
}