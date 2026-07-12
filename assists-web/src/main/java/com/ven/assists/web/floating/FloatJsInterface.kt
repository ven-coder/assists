package com.ven.assists.web.floating

import android.util.Base64
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.SizeUtils
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ven.assists.AssistsCore
import com.ven.assists.base.R as BaseR
import com.ven.assists.base.databinding.AssistsWindowLayoutWrapperBinding
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.utils.runMain
import com.ven.assists.web.CallInterceptResult
import com.ven.assists.web.CallRequest
import com.ven.assists.web.CallResponse
import com.ven.assists.web.R
import com.ven.assists.web.createResponse
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowManager.ViewWrapper
import com.ven.assists.window.AssistsWindowManager.nonTouchableByWrapper
import com.ven.assists.window.AssistsWindowManager.overlayToast
import com.ven.assists.window.AssistsWindowManager.touchableByWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

/**
 * 浮窗独立 JsInterface，与 ASJavascriptInterfaceAsync 平级
 * 通过 assistsxFloat.call(json) 调用，回调 assistsxFloatCallback。
 * 封装 [AssistsWindowManager] 的全局能力与当前 Web 浮窗（[R.id.web_view]）相关操作。
 *
 * 单位约定：
 * - 窗口位置/尺寸（open / move / refresh 的 x,y,width,height,min/max，getBounds）：默认 px，可通过 unit=dp|px 切换
 * - 脚手架组件尺寸（headerHeight、*Size 等）：默认 dp，可通过 scaffoldUnit=dp|px 切换
 * - 标题文字大小 titleTextSize：单位固定为 sp
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
                FloatCallMethod.getBounds -> getBounds(request)
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

    /** 是否按 dp 解析；仅当显式传 "dp" 时为 true */
    private fun isDpUnit(unit: String?): Boolean =
        unit?.equals("dp", ignoreCase = true) == true

    /** 读取布尔参数；优先新字段名，兼容 initial* 旧名 */
    private fun boolArg(args: JsonObject?, key: String, legacyKey: String): Boolean? {
        args?.get(key)?.takeIf { !it.isJsonNull }?.let { return it.asBoolean }
        args?.get(legacyKey)?.takeIf { !it.isJsonNull }?.let { return it.asBoolean }
        return null
    }

    /**
     * 解析 open 时的全屏居中（center / initialCenter）：
     * - 显式传了则用传值
     * - 若只传了水平/垂直居中标志，则默认 false，避免覆盖单轴居中
     * - 否则保持兼容默认 true（屏幕居中）
     */
    private fun resolveOpenCenter(args: JsonObject?): Boolean {
        boolArg(args, "center", "initialCenter")?.let { return it }
        val hasAxis =
            boolArg(args, "centerHorizontal", "initialCenterHorizontal") != null ||
                boolArg(args, "centerVertical", "initialCenterVertical") != null
        return !hasAxis
    }

    /**
     * 解析尺寸参数为 px。
     * @param useDp true 时将数值视为 dp 并转换；false 时直接作为 px
     */
    private fun sizeArg(el: JsonElement?, useDp: Boolean): Int? {
        if (el == null || el.isJsonNull) return null
        val value = el.asFloat
        return if (useDp) SizeUtils.dp2px(value) else value.toInt()
    }

    /** 将内部 px 转为输出单位 */
    private fun fromPx(px: Int, useDp: Boolean): Int =
        if (useDp) SizeUtils.px2dp(px.toFloat()) else px

    /** 设置方形按钮边长（已转换为 px） */
    private fun applyViewSizePx(view: View, sizePx: Int) {
        val lp = view.layoutParams ?: ViewGroup.LayoutParams(sizePx, sizePx)
        lp.width = sizePx
        lp.height = sizePx
        view.layoutParams = lp
    }

    /** 设置栏高度（已转换为 px），宽度保持原样 */
    private fun applyBarHeightPx(view: View, heightPx: Int) {
        val lp = view.layoutParams ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
        lp.height = heightPx
        view.layoutParams = lp
    }

    /** 解析当前 JS 所在 Web 浮窗的 [ViewWrapper] */
    private fun findWrapperForWebView(): ViewWrapper? =
        AssistsWindowManager.viewList.values.find { it.view.findViewById<View>(R.id.web_view) == webView }

    /** 关闭当前浮窗 */
    private suspend fun close(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val result = runMain {
            findWrapperForWebView()?.let { wrapper ->
                wrapper.view.findViewById<WebView>(R.id.web_view)?.let { wv ->
                    FloatWindowOpener.destroyContentWebView(wv)
                    AssistsCore.clearKeepScreenOn()
                }
                AssistsWindowManager.removeWindow(wrapper.view)
                true
            }
        }
        result?.let { return request.createResponse(0, data = it) }
        return request.createResponse(0, data = false)
    }

    /** 加载浮窗；窗口尺寸/位置默认 px，传 unit=dp 时按 dp 转换；可同时传入 refresh 同款脚手架配置 */
    private suspend fun open(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val args = request.arguments
        val useDp = isDpUnit(args?.get("unit")?.asString)
        val options = FloatWindowOpenOptions(
            url = args?.get("url")?.asString ?: "",
            initialWidth = sizeArg(args?.get("initialWidth"), useDp) ?: (ScreenUtils.getScreenWidth() * 0.8).toInt(),
            initialHeight = sizeArg(args?.get("initialHeight"), useDp) ?: (ScreenUtils.getScreenHeight() * 0.5).toInt(),
            initialX = sizeArg(args?.get("initialX"), useDp) ?: 0,
            initialY = sizeArg(args?.get("initialY"), useDp) ?: 0,
            minWidth = sizeArg(args?.get("minWidth"), useDp) ?: (ScreenUtils.getScreenWidth() * 0.5).toInt(),
            minHeight = sizeArg(args?.get("minHeight"), useDp) ?: (ScreenUtils.getScreenHeight() * 0.5).toInt(),
            maxWidth = sizeArg(args?.get("maxWidth"), useDp) ?: -1,
            maxHeight = sizeArg(args?.get("maxHeight"), useDp) ?: -1,
            // 若显式传了水平/垂直居中，则不再默认 center=true，避免覆盖单轴居中
            initialCenter = resolveOpenCenter(args),
            initialCenterHorizontal = boolArg(args, "centerHorizontal", "initialCenterHorizontal") ?: false,
            initialCenterVertical = boolArg(args, "centerVertical", "initialCenterVertical") ?: false,
            keepScreenOn = args?.get("keepScreenOn")?.asBoolean ?: false,
            showTopOperationArea = args?.get("showTopOperationArea")?.asBoolean ?: true,
            showBottomOperationArea = args?.get("showBottomOperationArea")?.asBoolean ?: false,
            backgroundColor = FloatWindowOpener.parseBackgroundColor(args?.get("backgroundColor")),
        )
        val added = runMain {
            FloatWindowOpener.open(options)?.also { wrapper ->
                // 打开后应用与 refresh 相同的脚手架/背景等可选配置
                applyViewConfig(wrapper, args, applyWindowLayout = false)
            }
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

    /** 移动浮窗：相对位移，默认 px，传 unit=dp 时按 dp 转换 */
    private suspend fun move(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val args = request.arguments
        val xEl = args?.get("x") ?: return request.createResponse(-1, message = "x 不能为空")
        val yEl = args?.get("y") ?: return request.createResponse(-1, message = "y 不能为空")
        val useDp = isDpUnit(args.get("unit")?.asString)
        val dx = sizeArg(xEl, useDp) ?: return request.createResponse(-1, message = "x 不能为空")
        val dy = sizeArg(yEl, useDp) ?: return request.createResponse(-1, message = "y 不能为空")
        val wrapper = findWrapperForWebView() ?: return request.createResponse(-1, message = "未找到对应浮窗")
        withContext(Dispatchers.Main) {
            wrapper.layoutParams.x += dx
            wrapper.layoutParams.y += dy
            AssistsWindowManager.updateViewLayout(wrapper.view, wrapper.layoutParams)
        }
        return request.createResponse(0, data = true)
    }

    /** 获取当前浮窗位置与尺寸；默认返回 px，传 unit=dp 时返回 dp */
    private suspend fun getBounds(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val wrapper = findWrapperForWebView() ?: return request.createResponse(-1, message = "未找到对应浮窗")
        val useDp = isDpUnit(request.arguments?.get("unit")?.asString)
        val data = withContext(Dispatchers.Main) {
            val aw = wrapper.assistsWindowWrapper
            JsonObject().apply {
                addProperty("x", fromPx(wrapper.layoutParams.x, useDp))
                addProperty("y", fromPx(wrapper.layoutParams.y, useDp))
                addProperty("width", fromPx(wrapper.layoutParams.width, useDp))
                addProperty("height", fromPx(wrapper.layoutParams.height, useDp))
                addProperty("minWidth", if (aw == null || aw.minWidth < 0) -1 else fromPx(aw.minWidth, useDp))
                addProperty("minHeight", if (aw == null || aw.minHeight < 0) -1 else fromPx(aw.minHeight, useDp))
                addProperty("maxWidth", if (aw == null || aw.maxWidth < 0) -1 else fromPx(aw.maxWidth, useDp))
                addProperty("maxHeight", if (aw == null || aw.maxHeight < 0) -1 else fromPx(aw.maxHeight, useDp))
                addProperty("unit", if (useDp) "dp" else "px")
            }
        }
        return request.createResponse(0, data = data)
    }

    /**
     * 刷新浮窗配置。
     * 窗口位置/尺寸默认 px（unit=dp|px）；脚手架组件尺寸默认 dp（scaffoldUnit=dp|px）；省略字段则不变。
     */
    private suspend fun refresh(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val wrapper = findWrapperForWebView() ?: return request.createResponse(-1, message = "未找到对应浮窗")
        withContext(Dispatchers.Main) {
            applyViewConfig(wrapper, request.arguments, applyWindowLayout = true)
            AssistsWindowManager.updateViewLayout(wrapper.view, wrapper.layoutParams)
        }
        return request.createResponse(0, data = true)
    }

    /**
     * 应用脚手架显隐/尺寸、背景及可选的窗口布局。
     * @param applyWindowLayout 为 true 时同时应用 width/height、center*、x/y、min/max（refresh）；open 时为 false
     */
    private fun applyViewConfig(
        wrapper: ViewWrapper,
        args: JsonObject?,
        applyWindowLayout: Boolean,
    ) {
        args ?: return
        val windowUseDp = isDpUnit(args.get("unit")?.asString)
        val scaffoldUnit = args.get("scaffoldUnit")?.takeIf { !it.isJsonNull }?.asString
        val scaffoldUseDp = scaffoldUnit == null || isDpUnit(scaffoldUnit)

        AssistsWindowLayoutWrapperBinding.bind(wrapper.view).apply {
            args.get("showTopOperationArea")?.takeIf { !it.isJsonNull }?.asBoolean?.let { flHeader.isVisible = it }
            args.get("showBottomOperationArea")?.takeIf { !it.isJsonNull }?.asBoolean?.let { llBottomBar.isVisible = it }
            args.get("showMove")?.takeIf { !it.isJsonNull }?.asBoolean?.let { ivMove.isVisible = it }
            args.get("showClose")?.takeIf { !it.isJsonNull }?.asBoolean?.let { ivClose.isVisible = it }
            args.get("showTitle")?.takeIf { !it.isJsonNull }?.asBoolean?.let { tvTitle.isVisible = it }
            args.get("showScale")?.takeIf { !it.isJsonNull }?.asBoolean?.let { ivScale.isVisible = it }
            args.get("showMaximize")?.takeIf { !it.isJsonNull }?.asBoolean?.let { ivMaximize.isVisible = it }
            args.get("showMinimize")?.takeIf { !it.isJsonNull }?.asBoolean?.let { ivMinimize.isVisible = it }
            args.get("showWebBack")?.takeIf { !it.isJsonNull }?.asBoolean?.let { ivWebBack.isVisible = it }
            args.get("showWebForward")?.takeIf { !it.isJsonNull }?.asBoolean?.let { ivWebForward.isVisible = it }
            args.get("showWebRefresh")?.takeIf { !it.isJsonNull }?.asBoolean?.let { ivWebRefresh.isVisible = it }

            args.get("titleTextSize")?.takeIf { !it.isJsonNull }?.asFloat?.let {
                tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, it)
            }

            sizeArg(args.get("headerHeight"), scaffoldUseDp)?.let { applyBarHeightPx(flHeader, it) }
            sizeArg(args.get("bottomBarHeight"), scaffoldUseDp)?.let { applyBarHeightPx(llBottomBar, it) }
            sizeArg(args.get("moveSize"), scaffoldUseDp)?.let { applyViewSizePx(ivMove, it) }
            sizeArg(args.get("closeSize"), scaffoldUseDp)?.let { applyViewSizePx(ivClose, it) }
            sizeArg(args.get("scaleSize"), scaffoldUseDp)?.let { applyViewSizePx(ivScale, it) }
            sizeArg(args.get("maximizeSize"), scaffoldUseDp)?.let { applyViewSizePx(ivMaximize, it) }
            sizeArg(args.get("minimizeSize"), scaffoldUseDp)?.let { applyViewSizePx(ivMinimize, it) }
            sizeArg(args.get("webBackSize"), scaffoldUseDp)?.let { applyViewSizePx(ivWebBack, it) }
            sizeArg(args.get("webForwardSize"), scaffoldUseDp)?.let { applyViewSizePx(ivWebForward, it) }
            sizeArg(args.get("webRefreshSize"), scaffoldUseDp)?.let { applyViewSizePx(ivWebRefresh, it) }
        }

        args.get("showBackground")?.takeIf { !it.isJsonNull }?.asBoolean?.let { show ->
            if (show) {
                wrapper.view.setBackgroundResource(BaseR.drawable.bg_1)
            } else {
                wrapper.view.background = null
            }
            wrapper.assistsWindowWrapper?.showBackground = show
        }

        args.get("backgroundColor")?.takeIf { !it.isJsonNull }?.let { arg ->
            when {
                arg.isJsonPrimitive && arg.asJsonPrimitive.isString -> {
                    val s = arg.asString
                    if (s == "default") {
                        wrapper.view.setBackgroundResource(BaseR.drawable.bg_1)
                    } else if (!s.isNullOrBlank()) {
                        try {
                            wrapper.view.setBackgroundColor(s.toColorInt())
                        } catch (_: Exception) {
                        }
                    }
                }
                arg.isJsonPrimitive && arg.asJsonPrimitive.isNumber -> {
                    wrapper.view.setBackgroundColor(arg.asInt)
                }
                else -> {
                }
            }
        }

        if (applyWindowLayout) {
            sizeArg(args.get("width"), windowUseDp)?.let { wrapper.layoutParams.width = it }
            sizeArg(args.get("height"), windowUseDp)?.let { wrapper.layoutParams.height = it }

            // center / centerHorizontal / centerVertical（兼容 initialCenter*）；居中优先于 x/y
            val centerBoth = boolArg(args, "center", "initialCenter")
            val centerHFlag = boolArg(args, "centerHorizontal", "initialCenterHorizontal")
            val centerVFlag = boolArg(args, "centerVertical", "initialCenterVertical")
            val doCenterH = centerBoth == true || centerHFlag == true
            val doCenterV = centerBoth == true || centerVFlag == true
            val lp = wrapper.layoutParams
            if (doCenterH) {
                lp.x = ScreenUtils.getScreenWidth() / 2 - lp.width / 2
            } else {
                sizeArg(args.get("x"), windowUseDp)?.let { lp.x = it }
            }
            if (doCenterV) {
                lp.y = ScreenUtils.getScreenHeight() / 2 - lp.height / 2
            } else {
                sizeArg(args.get("y"), windowUseDp)?.let { lp.y = it }
            }

            wrapper.assistsWindowWrapper?.let { aw ->
                sizeArg(args.get("minWidth"), windowUseDp)?.let { aw.minWidth = it }
                sizeArg(args.get("minHeight"), windowUseDp)?.let { aw.minHeight = it }
                sizeArg(args.get("maxWidth"), windowUseDp)?.let { aw.maxWidth = it }
                sizeArg(args.get("maxHeight"), windowUseDp)?.let { aw.maxHeight = it }
            }
        }
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
