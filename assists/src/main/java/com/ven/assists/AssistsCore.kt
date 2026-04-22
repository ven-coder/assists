package com.ven.assists

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ImageUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.PathUtils
import com.blankj.utilcode.util.ScreenUtils
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.utils.NodeClassValue
import com.ven.assists.utils.runMain
import com.ven.assists.window.AssistsWindowManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt
import com.blankj.utilcode.util.SizeUtils
import com.ven.assists.window.AssistsWindowManager.nonTouchableByWrapper
import androidx.core.graphics.createBitmap
import com.blankj.utilcode.util.FileUtils
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.ven.assists.ui.ClipboardActivity
import com.ven.assists.utils.BitmapUtils

/**
 * 无障碍服务核心类
 * 提供对AccessibilityService的封装和扩展功能
 */
object AssistsCore {
    /** 日志标签 */
    var LOG_TAG = "assists_log"

    /** [init] 传入的 Application，供 [isA11yEnabled] 无参默认 [Context] 使用 */
    private var initApplication: Application? = null

    /** 当前应用在屏幕中的位置信息缓存 */
    private var appRectInScreen: Rect? = null

    /**
     * 以下是一系列用于快速判断元素类型的扩展函数
     * 通过比对元素的className来判断元素类型
     */

    /** 判断元素是否是FrameLayout */
    fun AccessibilityNodeInfo.isFrameLayout(): Boolean {

        return className == NodeClassValue.FrameLayout
    }

    /** 判断元素是否是ViewGroup */
    fun AccessibilityNodeInfo.isViewGroup(): Boolean {
        return className == NodeClassValue.ViewGroup
    }

    /** 判断元素是否是View */
    fun AccessibilityNodeInfo.isView(): Boolean {
        return className == NodeClassValue.View
    }

    /** 判断元素是否是ImageView */
    fun AccessibilityNodeInfo.isImageView(): Boolean {
        return className == NodeClassValue.ImageView
    }

    /** 判断元素是否是TextView */
    fun AccessibilityNodeInfo.isTextView(): Boolean {
        return className == NodeClassValue.TextView
    }

    /** 判断元素是否是LinearLayout */
    fun AccessibilityNodeInfo.isLinearLayout(): Boolean {
        return className == NodeClassValue.LinearLayout
    }

    /** 判断元素是否是RelativeLayout */
    fun AccessibilityNodeInfo.isRelativeLayout(): Boolean {
        return className == NodeClassValue.RelativeLayout
    }

    /** 判断元素是否是Button */
    fun AccessibilityNodeInfo.isButton(): Boolean {
        return className == NodeClassValue.Button
    }

    /** 判断元素是否是ImageButton */
    fun AccessibilityNodeInfo.isImageButton(): Boolean {
        return className == NodeClassValue.ImageButton
    }

    /** 判断元素是否是EditText */
    fun AccessibilityNodeInfo.isEditText(): Boolean {
        return className == NodeClassValue.EditText
    }

    /**
     * 获取元素的文本内容
     * @return 元素的text属性值，如果为空则返回空字符串
     */
    fun AccessibilityNodeInfo.txt(): String {
        return text?.toString() ?: ""
    }

    /**
     * 获取元素的描述内容
     * @return 元素的contentDescription属性值，如果为空则返回空字符串
     */
    fun AccessibilityNodeInfo.des(): String {
        return contentDescription?.toString() ?: ""
    }

    /**
     * 初始化AssistsCore
     * @param application Application实例
     */
    fun init(application: Application) {
        initApplication = application
        LogUtils.getConfig().globalTag = LOG_TAG
    }

    private fun requireInitApplicationContext(): Context {
        return initApplication?.applicationContext
            ?: throw IllegalStateException("Call AssistsCore.init(application) before using default context in isA11yEnabled().")
    }

    /**
     * 节点查找范围：仅活动窗口根，或 [AccessibilityService.getWindows] 下各窗口根（需 manifest 中 flagRetrieveInteractiveWindows）
     */
    enum class NodeLookupScope {
        /** 与 [AccessibilityService.getRootInActiveWindow] 单根一致 */
        ActiveWindow,

        /** 遍历 [AccessibilityService.getWindows] 中各 [android.view.accessibility.AccessibilityWindowInfo] 的 [android.view.accessibility.AccessibilityWindowInfo.getRoot] */
        AllWindows,
    }

    /**
     * 按范围获取用于节点遍历的根节点列表（统一入口）
     * @param scope 查找范围，默认 [NodeLookupScope.AllWindows]
     */
    @JvmOverloads
    fun getAccessibilityRootNodes(scope: NodeLookupScope = NodeLookupScope.AllWindows): List<AccessibilityNodeInfo> {
        val service = AssistsService.getOrNull() ?: return emptyList()
        return when (scope) {
            NodeLookupScope.ActiveWindow -> listOfNotNull(service.rootInActiveWindow)
            NodeLookupScope.AllWindows -> {
                val windows = service.windows ?: return emptyList()
                val roots = arrayListOf<AccessibilityNodeInfo>()
                for (w in windows) {
                    try {
                        w.root?.let { roots.add(it) }
                    } finally {
                        w.recycle()
                    }
                }
                roots
            }
        }
    }

    /**
     * 打开系统的无障碍服务设置页面
     * 用于引导用户开启无障碍服务
     */
    fun openAccessibilitySetting() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        ActivityUtils.startActivity(intent)
    }

    /**
     * 检查无障碍服务是否已开启
     * @return true表示服务已开启，false表示服务未开启
     */
    @Deprecated(
        message = "依赖 rootInActiveWindow，界面切换或锁屏时易误判；请使用 isA11yEnabled()",
        replaceWith = ReplaceWith("isA11yEnabled()"),
    )
    fun isAccessibilityServiceEnabled(): Boolean {
        return runCatching {
            val roots = getAccessibilityRootNodes(NodeLookupScope.ActiveWindow)
            LogUtils.d(roots)
            roots.isNotEmpty()
        }.onFailure {
            LogUtils.e(it)
        }.getOrElse { false }
    }

    /**
     * 可靠判断本应用的无障碍服务是否已在系统设置中开启（用户已为该组件打开无障碍）。
     *
     * 与 [isAccessibilityServiceEnabled] 不同：后者依赖 [android.view.accessibility.AccessibilityNodeInfo]
     * 活动窗口根节点，在无焦点窗口、锁屏或界面切换时 [AccessibilityService.getRootInActiveWindow] 常为 null，
     * 易被误判为未开启。本方法依据无障碍总开关、[Settings.Secure] 已启用组件列表，以及
     * [AccessibilityManager.getEnabledAccessibilityServiceList] 进行校验，并互为补充以降低 OEM 差异带来的误判。
     *
     * 判定方式：在本应用包名下，若任一已启用服务的实现类 [java.lang.Class.isAssignableFrom] 自 [serviceClass]（含子类），
     * 即视为已开启。故 manifest 中 `android:name` 为 [AssistsService] 的子类时，默认 [serviceClass] 为 [AssistsService] 即可匹配。
     *
     * @param context 用于读取系统设置；省略时使用 [init] 传入的 [Application] 的 applicationContext
     * @param serviceClass 期望的无障碍服务基类；子类实例在系统中启用时同样返回 true
     */
    @JvmOverloads
    fun isA11yEnabled(
        context: Context = requireInitApplicationContext(),
        serviceClass: Class<out AccessibilityService> = AssistsService::class.java,
    ): Boolean {
        if (!isAccessibilityMasterSwitchOn(context)) return false
        val pkg = context.packageName
        val classNames = linkedSetOf<String>()
        runCatching {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).forEach { info ->
                val si = info.resolveInfo.serviceInfo
                if (si.packageName == pkg) classNames.add(si.name)
            }
        }.onFailure { LogUtils.e(it) }
        val enabledSecure = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: ""
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledSecure)
        for (flat in splitter) {
            val cn = ComponentName.unflattenFromString(flat) ?: continue
            if (cn.packageName == pkg) classNames.add(cn.className)
        }
        for (name in classNames) {
            if (isAccessibilityServiceAssignable(name, serviceClass)) return true
        }
        return false
    }

    /** 无障碍功能总开关是否打开 */
    private fun isAccessibilityMasterSwitchOn(context: Context): Boolean {
        return runCatching {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        }.getOrElse {
            LogUtils.e(it)
            false
        }
    }

    /**
     * 已启用无障碍实现类是否可赋给 [expectedBase]（用于识别 manifest 中的 [AssistsService] 子类）
     */
    private fun isAccessibilityServiceAssignable(
        implementationClassName: String,
        expectedBase: Class<out AccessibilityService>,
    ): Boolean {
        return runCatching {
            val impl = Class.forName(implementationClassName)
            expectedBase.isAssignableFrom(impl)
        }.getOrElse {
            LogUtils.e(it)
            false
        }
    }

    /**
     * 获取当前窗口所属的应用包名
     * @param scope 查找范围，默认 [NodeLookupScope.AllWindows]；[NodeLookupScope.AllWindows] 时优先 [android.view.accessibility.AccessibilityWindowInfo] 的 isFocused 窗口根之 packageName，无则回退为活动根再回退为全窗口根列表中第一个
     * @return 当前窗口的包名，如果获取失败则返回空字符串
     */
    @JvmOverloads
    fun getPackageName(scope: NodeLookupScope = NodeLookupScope.AllWindows): String {
        if (scope == NodeLookupScope.ActiveWindow) {
            return getAccessibilityRootNodes(NodeLookupScope.ActiveWindow).firstOrNull()?.packageName?.toString() ?: ""
        }
        val service = AssistsService.getOrNull() ?: return ""
        val windows = service.windows
            ?: return getPackageName(NodeLookupScope.ActiveWindow)
        for (w in windows) {
            var pkg: String? = null
            try {
                if (w.isFocused) {
                    pkg = w.root?.packageName?.toString()
                }
            } finally {
                w.recycle()
            }
            if (pkg != null) return pkg
        }
        val fallbackActive = getPackageName(NodeLookupScope.ActiveWindow)
        if (fallbackActive.isNotEmpty()) return fallbackActive
        return getAccessibilityRootNodes(NodeLookupScope.AllWindows).firstOrNull()?.packageName?.toString() ?: ""
    }

    /**
     * 屏幕保持常亮
     */
    fun keepScreenOn(tip: String = "屏幕保持常亮") {
        if (AssistsWindowManager.contains("keepScreenOn")) return
        AssistsService.getOrNull()?.let {
            AssistsWindowManager.add(FrameLayout(it).apply {
                setBackgroundColor("#80000000".toColorInt())
                addView(TextView(it).apply {
                    text = tip
                    setTextColor("#80FF0000".toColorInt())
                    textSize = 20f
                    layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                        gravity = Gravity.CENTER
                    }
                })
                keepScreenOn = true
            }, viewTag = "keepScreenOn").apply {
                CoroutineWrapper.launch { this@apply?.nonTouchableByWrapper() }
            }
        }
    }

    /**
     * 关闭屏幕保持常亮
     */
    fun clearKeepScreenOn() {
        AssistsWindowManager.removeWindow("keepScreenOn")
    }

    /**
     * 通过id查找所有符合条件的元素
     * @param id 元素的资源id
     * @param filterText 可选的文本过滤条件
     * @param filterDes 可选的描述文本过滤条件
     * @param filterClass 可选的类名过滤条件
     * @param scope 根节点来源：活动窗口或全部窗口，默认 [NodeLookupScope.AllWindows]
     * @return 符合条件的元素列表
     */
    @JvmOverloads
    fun findById(
        id: String,
        filterText: String? = null,
        filterDes: String? = null,
        filterClass: String? = null,
        scope: NodeLookupScope = NodeLookupScope.AllWindows,
    ): List<AccessibilityNodeInfo> {
        val nodes = arrayListOf<AccessibilityNodeInfo>()
        getAccessibilityRootNodes(scope).forEach { root ->
            nodes.addAll(root.findById(id))
        }
        return filterNodes(nodes, filterText = filterText, filterDes = filterDes, filterClass = filterClass)
    }

    /**
     * 在指定元素范围内通过id查找所有符合条件的元素
     * @param id 元素的资源id
     * @param filterText 可选的文本过滤条件
     * @param filterDes 可选的描述文本过滤条件
     * @param filterClass 可选的类名过滤条件
     * @return 符合条件的元素列表
     */
    fun AccessibilityNodeInfo?.findById(
        id: String,
        filterText: String? = null,
        filterDes: String? = null,
        filterClass: String? = null
    ): List<AccessibilityNodeInfo> {
        val nodes = this?.findAccessibilityNodeInfosByViewId(id) ?: arrayListOf()
        val filterNodes = filterNodes(nodes, filterText = filterText, filterDes = filterDes, filterClass = filterClass)
        return filterNodes
    }

    /**
     * 通过文本内容查找所有符合条件的元素
     * @param text 要查找的文本内容
     * @param filterViewId 可选的资源id过滤条件
     * @param filterDes 可选的描述文本过滤条件
     * @param filterClass 可选的类名过滤条件
     * @param scope 根节点来源：活动窗口或全部窗口，默认 [NodeLookupScope.AllWindows]
     * @return 符合条件的元素列表
     */
    @JvmOverloads
    fun findByText(
        text: String,
        filterViewId: String? = null,
        filterDes: String? = null,
        filterClass: String? = null,
        scope: NodeLookupScope = NodeLookupScope.AllWindows,
    ): List<AccessibilityNodeInfo> {
        val nodes = arrayListOf<AccessibilityNodeInfo>()
        getAccessibilityRootNodes(scope).forEach { root ->
            nodes.addAll(root.findByText(text))
        }
        return filterNodes(nodes, filterViewId = filterViewId, filterDes = filterDes, filterClass = filterClass)
    }

    /**
     * 查找所有文本完全匹配的元素
     * @param text 要匹配的文本内容
     * @param filterViewId 可选的资源id过滤条件
     * @param filterDes 可选的描述文本过滤条件
     * @param filterClass 可选的类名过滤条件
     * @param scope 根节点来源：活动窗口或全部窗口，默认 [NodeLookupScope.AllWindows]
     * @return 文本完全匹配的元素列表
     */
    @JvmOverloads
    fun findByTextAllMatch(
        text: String,
        filterViewId: String? = null,
        filterDes: String? = null,
        filterClass: String? = null,
        scope: NodeLookupScope = NodeLookupScope.AllWindows,
    ): List<AccessibilityNodeInfo> {
        val nodes = arrayListOf<AccessibilityNodeInfo>()
        getAccessibilityRootNodes(scope).forEach { root ->
            nodes.addAll(root.findByText(text))
        }
        return filterNodes(nodes, filterViewId = filterViewId, filterDes = filterDes, filterClass = filterClass)
    }

    /**
     * 在指定元素范围内通过文本查找所有符合条件的元素
     * @param text 要查找的文本内容
     * @param filterViewId 可选的资源id过滤条件
     * @param filterDes 可选的描述文本过滤条件
     * @param filterClass 可选的类名过滤条件
     * @return 符合条件的元素列表
     */
    fun AccessibilityNodeInfo?.findByText(
        text: String,
        filterViewId: String? = null,
        filterDes: String? = null,
        filterClass: String? = null
    ): List<AccessibilityNodeInfo> {

        val nodes = this?.findAccessibilityNodeInfosByText(text) ?: arrayListOf()

        val filterNodes = filterNodes(nodes, filterViewId = filterViewId, filterDes = filterDes, filterClass = filterClass)
        return filterNodes
    }

    /**
     * 根据指定条件过滤元素列表
     * @param nodes 要过滤的元素列表
     * @param filterViewId 可选的资源id过滤条件
     * @param filterDes 可选的描述文本过滤条件
     * @param filterClass 可选的类名过滤条件
     * @param filterText 可选的文本过滤条件
     * @return 过滤后的元素列表
     */
    private fun filterNodes(
        nodes: List<AccessibilityNodeInfo>,
        filterViewId: String? = null,
        filterDes: String? = null,
        filterClass: String? = null,
        filterText: String? = null
    ): List<AccessibilityNodeInfo> {
        val filterNodes = nodes.filter { node ->

            filterViewId?.let {
                if (it.isEmpty()) return@let
                return@filter node.viewIdResourceName?.equals(filterViewId) == true
            }
            filterText?.let {
                if (it.isEmpty()) return@let
                return@filter node.text?.toString()?.equals(filterText) == true
            }
            filterDes?.let {
                if (it.isEmpty()) return@let
                return@filter node.contentDescription?.toString()?.equals(filterDes) == true
            }
            filterClass?.let {
                if (it.isEmpty()) return@let
                return@filter node.className?.toString()?.equals(filterClass) == true
            }

            true
        }
        return filterNodes
    }

    /**
     * 判断元素是否包含指定文本
     * @param text 要检查的文本内容
     * @return true表示包含指定文本，false表示不包含
     */
    fun AccessibilityNodeInfo?.containsText(text: String): Boolean {
        if (this == null) return false
        getText()?.let {
            if (it.contains(text)) return true
        }
        contentDescription?.let {
            if (it.contains(text)) return true
        }
        return false
    }

    /**
     * 获取元素的所有文本内容（包括text和contentDescription）
     * @return 包含所有文本内容的列表
     */
    fun AccessibilityNodeInfo?.getAllText(): ArrayList<String> {
        if (this == null) return arrayListOf()
        val texts = arrayListOf<String>()
        texts.filter { false }
        getText()?.let {
            texts.add(it.toString())
        }
        contentDescription?.let {
            texts.add(it.toString())
        }
        return texts
    }

    /**
     * 根据多个条件查找元素
     * @param className 元素的类名
     * @param viewId 可选的资源id过滤条件
     * @param text 可选的文本过滤条件
     * @param des 可选的描述文本过滤条件
     * @param scope 与 [getAllNodes] 相同，控制全树扫描范围，默认 [NodeLookupScope.AllWindows]
     * @return 符合所有条件的元素列表
     */
    @JvmOverloads
    fun findByTags(
        className: String,
        viewId: String? = null,
        text: String? = null,
        des: String? = null,
        scope: NodeLookupScope = NodeLookupScope.AllWindows,
    ): List<AccessibilityNodeInfo> {
        var nodeList = arrayListOf<AccessibilityNodeInfo>()
        getAllNodes(scope = scope).forEach {
            if (TextUtils.equals(className, it.className)) {
                nodeList.add(it)
            }
        }
        nodeList = viewId?.let {
            if (it.isEmpty()) return@let nodeList
            return@let arrayListOf<AccessibilityNodeInfo>().apply {
                addAll(nodeList.filter {
                    return@filter it.viewIdResourceName == viewId
                })
            }
        } ?: let {
            return@let nodeList
        }

        nodeList = text?.let {
            if (it.isEmpty()) return@let nodeList

            return@let arrayListOf<AccessibilityNodeInfo>().apply {
                addAll(nodeList.filter {
                    return@filter it.txt() == text
                })
            }
        } ?: let { return@let nodeList }
        nodeList = des?.let {
            if (it.isEmpty()) return@let nodeList

            return@let arrayListOf<AccessibilityNodeInfo>().apply {
                addAll(nodeList.filter {
                    return@filter it.des() == des
                })
            }
        } ?: let { return@let nodeList }
        return nodeList
    }

    /**
     * 在指定元素范围内根据多个条件查找元素
     * @param className 元素的类名
     * @param viewId 可选的资源id过滤条件
     * @param text 可选的文本过滤条件
     * @param des 可选的描述文本过滤条件
     * @return 符合所有条件的元素列表
     */
    fun AccessibilityNodeInfo.findByTags(
        className: String,
        viewId: String? = null,
        text: String? = null,
        des: String? = null
    ): List<AccessibilityNodeInfo> {
        var nodeList = arrayListOf<AccessibilityNodeInfo>()
        getNodes().forEach {
            if (TextUtils.equals(className, it.className)) {
                nodeList.add(it)
            }
        }
        nodeList = viewId?.let {
            if (it.isEmpty()) return@let nodeList
            return@let arrayListOf<AccessibilityNodeInfo>().apply {
                addAll(nodeList.filter {
                    return@filter it.viewIdResourceName == viewId
                })
            }
        } ?: let {
            return@let nodeList
        }

        nodeList = text?.let {
            if (it.isEmpty()) return@let nodeList
            return@let arrayListOf<AccessibilityNodeInfo>().apply {
                addAll(nodeList.filter {
                    return@filter it.txt() == text
                })
            }
        } ?: let { return@let nodeList }
        nodeList = des?.let {
            if (it.isEmpty()) return@let nodeList
            return@let arrayListOf<AccessibilityNodeInfo>().apply {
                addAll(nodeList.filter {
                    return@filter it.des() == des
                })
            }
        } ?: let { return@let nodeList }

        return nodeList
    }

    /**
     * 查找第一个符合指定类型的父元素
     * @param className 要查找的父元素类名
     * @return 找到的父元素，如果未找到则返回null
     */
    fun AccessibilityNodeInfo.findFirstParentByTags(className: String): AccessibilityNodeInfo? {
        val nodeList = arrayListOf<AccessibilityNodeInfo>()
        findFirstParentByTags(className, nodeList)
        return nodeList.firstOrNull()
    }

    /**
     * 递归查找符合指定类型的父元素
     * @param className 要查找的父元素类名
     * @param container 用于存储查找结果的列表
     */
    fun AccessibilityNodeInfo.findFirstParentByTags(className: String, container: ArrayList<AccessibilityNodeInfo>) {
        getParent()?.let {
            if (TextUtils.equals(className, it.className)) {
                container.add(it)
            } else {
                it.findFirstParentByTags(className, container)
            }
        }
    }

    /**
     * 获取当前窗口中的所有元素
     * @param filterViewId 可选的资源id过滤条件
     * @param filterDes 可选的描述文本过滤条件
     * @param filterClass 可选的类名过滤条件
     * @param filterText 可选的文本过滤条件
     * @param scope 根节点来源：活动窗口或全部窗口，默认 [NodeLookupScope.AllWindows]；多根时整次收集共享 10000 个节点的全局上限
     * @return 包含所有元素的列表
     */
    @JvmOverloads
    fun getAllNodes(
        filterViewId: String? = null,
        filterDes: String? = null,
        filterClass: String? = null,
        filterText: String? = null,
        scope: NodeLookupScope = NodeLookupScope.AllWindows,
    ): List<AccessibilityNodeInfo> {
        val nodeList = arrayListOf<AccessibilityNodeInfo>()
        getAccessibilityRootNodes(scope).forEach { root ->
            if (nodeList.size >= 10000) return@forEach
            root.getNodes(nodeList)
        }
        val filterNodes = filterNodes(nodeList, filterViewId, filterDes, filterClass, filterText)
        return filterNodes
    }

    /**
     * 获取指定元素下的所有子元素
     * @return 包含所有子元素的列表
     */
    fun AccessibilityNodeInfo.getNodes(): ArrayList<AccessibilityNodeInfo> {
        val nodeList = arrayListOf<AccessibilityNodeInfo>()
        this.getNodes(nodeList)
        return nodeList
    }

    /**
     * 递归获取元素的所有子元素
     * @param nodeList 用于存储子元素的列表
     */
    private fun AccessibilityNodeInfo.getNodes(nodeList: ArrayList<AccessibilityNodeInfo>) {
        nodeList.add(this)
        if (nodeList.size > 10000) return // 防止无限递归
        for (index in 0 until this.childCount) {
            getChild(index)?.getNodes(nodeList)
        }
    }

    /**
     * 查找元素的第一个可点击的父元素
     * @return 找到的可点击父元素，如果未找到则返回null
     */
    fun AccessibilityNodeInfo.findFirstParentClickable(): AccessibilityNodeInfo? {
        arrayOfNulls<AccessibilityNodeInfo>(1).apply {
            findFirstParentClickable(this)
            return this[0]
        }
    }

    /**
     * 递归查找可点击的父元素
     * @param nodeInfo 用于存储查找结果的数组
     */
    private fun AccessibilityNodeInfo.findFirstParentClickable(nodeInfo: Array<AccessibilityNodeInfo?>) {
        if (parent?.isClickable == true) {
            nodeInfo[0] = parent
            return
        } else {
            parent?.findFirstParentClickable(nodeInfo)
        }
    }

    /**
     * 获取元素的直接子元素（不包括子元素的子元素）
     * @return 包含直接子元素的列表
     */
    fun AccessibilityNodeInfo.getChildren(): ArrayList<AccessibilityNodeInfo> {
        val nodes = arrayListOf<AccessibilityNodeInfo>()
        for (i in 0 until this.childCount) {
            val child = getChild(i)
            nodes.add(child)
        }
        return nodes
    }

    /**
     * 执行手势操作
     * @param gesture 手势描述对象
     * @param nonTouchableWindowDelay 窗口变为不可触摸后的延迟时间
     * @return 手势是否执行成功
     */
    suspend fun dispatchGesture(
        gesture: GestureDescription,
        nonTouchableWindowDelay: Long = 100,
    ): Boolean {
        return runCatching {
            val completableDeferred = CompletableDeferred<Boolean>()

            val gestureResultCallback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    CoroutineWrapper.launch { AssistsWindowManager.touchableByAll() }
                    completableDeferred.complete(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    CoroutineWrapper.launch { AssistsWindowManager.touchableByAll() }
                    completableDeferred.complete(false)
                }
            }
            val runResult = AssistsService.getOrNull()?.let {
                AssistsWindowManager.nonTouchableByAll()
                delay(nonTouchableWindowDelay)
                runMain { it.dispatchGesture(gesture, gestureResultCallback, null) }
            } ?: let {
                return false
            }
            if (!runResult) return false
            return@runCatching completableDeferred.await()
        }.getOrDefault(false)

    }

    /**
     * 执行点击或滑动手势
     * @param startLocation 起始位置坐标
     * @param endLocation 结束位置坐标
     * @param startTime 开始延迟时间
     * @param duration 手势持续时间
     * @return 手势是否执行成功
     */
    suspend fun gesture(
        startLocation: FloatArray,
        endLocation: FloatArray,
        startTime: Long,
        duration: Long,
    ): Boolean {
        return runCatching {
            val path = Path()
            path.moveTo(startLocation[0], startLocation[1])
            path.lineTo(endLocation[0], endLocation[1])
            return@runCatching gesture(path, startTime, duration)
        }.getOrDefault(false)
    }

    /**
     * 执行自定义路径的手势
     * @param path 手势路径
     * @param startTime 开始延迟时间
     * @param duration 手势持续时间
     * @return 手势是否执行成功
     */
    suspend fun gesture(
        path: Path,
        startTime: Long,
        duration: Long,
    ): Boolean {
        return runCatching {
            val builder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, startTime, duration)
            val gestureDescription = builder.addStroke(strokeDescription).build()
            val deferred = CompletableDeferred<Boolean>()
            val runResult = runMain {
                return@runMain AssistsService.getOrNull()?.dispatchGesture(gestureDescription, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        deferred.complete(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription) {
                        deferred.complete(false)
                    }
                }, null) ?: let {
                    return@runMain false
                }
            }
            if (!runResult) return false
            val result = deferred.await()
            return result
        }.getOrDefault(false)

    }

    /**
     * 获取元素在屏幕中的位置信息
     * @return 包含元素位置信息的Rect对象
     */
    fun AccessibilityNodeInfo.getBoundsInScreen(): Rect {
        val boundsInScreen = Rect()
        getBoundsInScreen(boundsInScreen)
        boundsInScreen.centerX()
        boundsInScreen.centerY()
        boundsInScreen.width()
        boundsInScreen.height()
        return boundsInScreen
    }

    /**
     * 获取元素在父容器中的位置信息
     * @return 包含元素在父容器中位置信息的Rect对象
     */
    fun AccessibilityNodeInfo.getBoundsInParent(): Rect {
        val rect = Rect()
        getBoundsInParent(rect)
        return rect
    }

    /**
     * 点击元素
     * @return 点击操作是否成功
     */
    fun AccessibilityNodeInfo.click(): Boolean {
        return performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * 长按元素
     * @return 长按操作是否成功
     */
    fun AccessibilityNodeInfo.longClick(): Boolean {
        return performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    /**
     * 在指定坐标位置执行点击手势
     * @param x 横坐标
     * @param y 纵坐标
     * @param duration 点击持续时间
     * @return 手势是否执行成功
     */
    suspend fun gestureClick(
        x: Float,
        y: Float,
        duration: Long = 10
    ): Boolean {
        return gesture(
            floatArrayOf(x, y), floatArrayOf(x, y),
            0,
            duration,
        )
    }

    /**
     * 通过手势长按
     */
    suspend fun longPressByGesture(
        x: Float,
        y: Float,
        duration: Long = 600
    ): Boolean {
        return gesture(
            floatArrayOf(x, y), floatArrayOf(x, y),
            0,
            duration,
        )
    }

    /**
     * 在元素位置执行点击手势
     * @param offsetX X轴偏移量
     * @param offsetY Y轴偏移量
     * @param switchWindowIntervalDelay 窗口切换延迟时间
     * @param duration 点击持续时间
     * @return 手势是否执行成功
     */
    suspend fun AccessibilityNodeInfo.nodeGestureClick(
        offsetX: Float = ScreenUtils.getScreenWidth() * 0.01953f,
        offsetY: Float = ScreenUtils.getScreenWidth() * 0.01953f,
        switchWindowIntervalDelay: Long = 250,
        duration: Long = 25
    ): Boolean {
        return runCatching {
            runMain { AssistsWindowManager.nonTouchableByAll() }
            delay(switchWindowIntervalDelay)
            val rect = getBoundsInScreen()
            val result = gesture(
                floatArrayOf(rect.left.toFloat() + offsetX, rect.top.toFloat() + offsetY),
                floatArrayOf(rect.left.toFloat() + offsetX, rect.top.toFloat() + offsetY),
                0,
                duration,
            )
            delay(switchWindowIntervalDelay)
            runMain { AssistsWindowManager.touchableByAll() }
            return@runCatching result
        }.getOrDefault(false)
    }

    /**
     * 在元素位置执行双击手势
     * @param offsetX X轴偏移量
     * @param offsetY Y轴偏移量
     * @param switchWindowIntervalDelay 窗口切换延迟时间
     * @param clickDuration 单次点击持续时间
     * @param clickInterval 两次点击之间的间隔时间
     * @return 手势是否执行成功
     */
    suspend fun AccessibilityNodeInfo.nodeGestureClickByDouble(
        offsetX: Float = ScreenUtils.getScreenWidth() * 0.01953f,
        offsetY: Float = ScreenUtils.getScreenWidth() * 0.01953f,
        switchWindowIntervalDelay: Long = 250,
        clickDuration: Long = 25,
        clickInterval: Long = 25,
    ): Boolean {
        return runCatching {
            AssistsWindowManager.nonTouchableByAll()
            delay(switchWindowIntervalDelay)
            val bounds = getBoundsInScreen()

            val x = bounds.centerX().toFloat() + offsetX
            val y = bounds.centerY().toFloat() + offsetY

            AssistsCore.gestureClick(x, y, clickDuration)
            delay(clickInterval)
            AssistsCore.gestureClick(x, y, clickDuration)
            AssistsWindowManager.touchableByAll()
            return@runCatching true
        }.getOrDefault(true)
    }

    /**
     * 判断元素是否可见
     * @param compareNode 可选的比较节点，用于判断当前元素是否被该节点遮挡
     * @param isFullyByCompareNode 是否要求完全不被比较节点遮挡
     * @return true表示元素可见，false表示元素不可见或被遮挡
     */
    fun AccessibilityNodeInfo.isVisible(
        compareNode: AccessibilityNodeInfo? = null,
        isFullyByCompareNode: Boolean = false,
    ): Boolean {
        compareNode?.let {

            if (!isVisibleToUser) return@let false

            val compareNodeBounds = it.getBoundsInScreen()
            val nodeBounds = getBoundsInScreen()
            if (isFullyByCompareNode) {

                if (compareNodeBounds.contains(nodeBounds)) {
                    return false
                }
                if (Rect.intersects(compareNodeBounds, nodeBounds)) {
                    return false
                }
                return true

            } else {

                if (compareNodeBounds.contains(nodeBounds)) {
                    return false
                }

                return true

            }

        }
        return isVisibleToUser
    }

    /**
     * 执行返回操作
     * @return 返回操作是否成功
     */
    fun back(): Boolean {
        return AssistsService.getOrNull()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false
    }

    /**
     * 返回主屏幕
     * @return 返回主屏幕操作是否成功
     */
    fun home(): Boolean {
        return AssistsService.getOrNull()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false
    }

    /**
     * 打开通知栏
     * @return 打开通知栏操作是否成功
     */
    fun notifications(): Boolean {
        return AssistsService.getOrNull()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) ?: false
    }

    /**
     * 显示最近任务
     * @return 显示最近任务操作是否成功
     */
    fun recentApps(): Boolean {
        return AssistsService.getOrNull()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false
    }

    /**
     * 向元素粘贴文本
     * @param text 要粘贴的文本
     * @return 粘贴操作是否成功
     */
    fun AccessibilityNodeInfo.paste(text: String?): Boolean {
        performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        AssistsService.getOrNull()?.let {
            val clipboard = it.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("label", text)
            clipboard.setPrimaryClip(clip)
            return performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
        return false
    }

    fun AccessibilityNodeInfo.focus(): Boolean {
        return performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    /**
     * 长按当前节点并自动粘贴
     * ⚠️当前仅对小米10，澎湃OS 1.0.5.0进行测试，其他机型系统版本请自行测试
     */
    suspend fun AccessibilityNodeInfo.longPressGestureAutoPaste(
        text: String,
        matchedText: String = "粘贴",
        matchedPackageName: String? = null,
        timeoutMillis: Long = 1500,
        longPressDuration: Long = 600,
    ): Boolean {

        val rect = getBoundsInScreen()

        return this@AssistsCore.longPressGestureAutoPaste(
            x = rect.left.toFloat() + rect.width() / 2,
            y = rect.top.toFloat() + rect.height() / 2,
            text = text,
            matchedText = matchedText,
            matchedPackageName = matchedPackageName,
            timeoutMillis = timeoutMillis,
            longPressDuration = longPressDuration,
        )
    }

    /**
     * 长按并自动粘贴
     * ⚠️当前仅对小米10，澎湃OS 1.0.5.0进行测试，其他机型系统版本请自行测试
     */
    suspend fun longPressGestureAutoPaste(
        x: Float,
        y: Float,
        text: String,
        matchedText: String = "粘贴",
        matchedPackageName: String? = null,
        timeoutMillis: Long = 1500,
        longPressDuration: Long = 600,
    ): Boolean {

        val completableDeferred = CompletableDeferred<Boolean>()

        val serviceListener = object : AssistsServiceListener {
            override fun onAccessibilityEvent(event: AccessibilityEvent) {
                runCatching {
                    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                        event.text.contains(matchedText) &&
                        (matchedPackageName == null || event.packageName.toString() == matchedPackageName)
                    ) {
                        val source = event.source
                        source?.getNodes()?.forEach { node ->
                            if (node.isClickable && node.containsText(matchedText)) {
                                val result = node.click()
                                completableDeferred.complete(result)
                                return@runCatching result
                            }
                        }
                    }
                    return@runCatching false
                }
            }
        }
        AssistsService.listeners.add(serviceListener)
        val timeoutJob = CoroutineWrapper.launch {
            delay(timeoutMillis)
            completableDeferred.complete(false)
        }
        AssistsService.getOrNull()?.let {
            val clipboard = it.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("label", text)
            clipboard.setPrimaryClip(clip)
        }
        CoroutineWrapper.launch {
            val result = longPressByGesture(x, y, duration = longPressDuration)
            if (!result) {
                completableDeferred.complete(false)
            }
        }
        val result = completableDeferred.await()
        AssistsService.listeners.remove(serviceListener)
        timeoutJob.cancel()
        return result
    }

    /**
     * 选择元素中的文本
     * @param selectionStart 选择起始位置
     * @param selectionEnd 选择结束位置
     * @return 文本选择操作是否成功
     */
    fun AccessibilityNodeInfo.selectionText(selectionStart: Int, selectionEnd: Int): Boolean {
        val selectionArgs = Bundle()
        selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, selectionStart)
        selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, selectionEnd)
        return performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
    }

    /**
     * 设置元素的文本内容
     * @param text 要设置的文本
     * @return 设置文本操作是否成功
     */
    fun AccessibilityNodeInfo.setNodeText(text: String?): Boolean {
        text ?: return false
        return performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundleOf().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        })
    }

    /**
     * 根据基准宽度计算实际X坐标
     * @param baseWidth 基准宽度
     * @param x 原始X坐标
     * @return 计算后的实际X坐标
     */
    fun getX(baseWidth: Int, x: Int): Int {
        val screenWidth = ScreenUtils.getScreenWidth()
        return (x / baseWidth.toFloat() * screenWidth).toInt()
    }

    /**
     * 根据基准高度计算实际Y坐标
     * @param baseHeight 基准高度
     * @param y 原始Y坐标
     * @return 计算后的实际Y坐标
     */
    fun getY(baseHeight: Int, y: Int): Int {
        var screenHeight = ScreenUtils.getScreenHeight()
        if (screenHeight > baseHeight) {
            screenHeight = baseHeight
        }
        return (y.toFloat() / baseHeight * screenHeight).toInt()
    }

    /**
     * 获取当前应用在屏幕中的位置
     * @return 应用窗口的位置信息，如果未找到则返回null
     */
    fun getAppBoundsInScreen(): Rect? {
        return AssistsService.getOrNull()?.let {
            return@let findById("android:id/content").firstOrNull()?.getBoundsInScreen()
        }
    }

    /**
     * 初始化并缓存当前应用在屏幕中的位置
     * @return 应用窗口的位置信息
     */
    fun initAppBoundsInScreen(): Rect? {
        return getAppBoundsInScreen().apply {
            appRectInScreen = this
        }
    }

    /**
     * 获取当前应用在屏幕中的宽度
     * @return 应用窗口的宽度
     */
    fun getAppWidthInScreen(): Int {
        return appRectInScreen?.let {
            return@let it.right - it.left
        } ?: ScreenUtils.getScreenWidth()
    }

    /**
     * 获取当前应用在屏幕中的高度
     * @return 应用窗口的高度
     */
    fun getAppHeightInScreen(): Int {
        return appRectInScreen?.let {
            return@let it.bottom - it.top
        } ?: ScreenUtils.getScreenHeight()
    }

    /**
     * 向前滚动可滚动元素
     * @return 滚动操作是否成功
     */
    fun AccessibilityNodeInfo.scrollForward(): Boolean {
        return performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    /**
     * 向后滚动可滚动元素
     * @return 滚动操作是否成功
     */
    fun AccessibilityNodeInfo.scrollBackward(): Boolean {
        return performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    /**
     * 通过Intent启动应用
     * @param intent 要启动的应用Intent
     * @return 启动操作是否成功
     */
    suspend fun launchApp(intent: Intent): Boolean {
        val completableDeferred = CompletableDeferred<Boolean>()
        val view = View(AssistsService.getOrNull()).apply {
            setOnClickListener {
                runCatching {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    AssistsService.getOrNull()?.startActivity(intent)
                    completableDeferred.complete(true)
                }.onFailure {
                    completableDeferred.complete(false)
                }
            }
        }
        runMain { AssistsWindowManager.add(view) }
        CoroutineWrapper.launch {
            delay(250)
            val clickResult = gestureClick(ScreenUtils.getScreenWidth() / 2.toFloat(), ScreenUtils.getScreenHeight() / 2.toFloat())
            if (!clickResult) {
                completableDeferred.complete(false)
            }
            delay(250)
            runMain { AssistsWindowManager.removeView(view) }
        }
        return completableDeferred.await()
    }

    /**
     * 通过包名启动应用
     * @param packageName 要启动的应用包名
     * @return 启动操作是否成功
     */
    suspend fun launchApp(packageName: String): Boolean {
        val completableDeferred = CompletableDeferred<Boolean>()
        val view = View(AssistsService.getOrNull()).apply {
            setOnClickListener {
                runCatching {
                    val intent = AssistsService.getOrNull()?.packageManager?.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    AssistsService.getOrNull()?.startActivity(intent)
                    completableDeferred.complete(true)
                }.onFailure {
                    completableDeferred.complete(false)
                }
            }
        }
        runMain { AssistsWindowManager.add(view) }
        CoroutineWrapper.launch {
            delay(250)
            val clickResult = gestureClick(ScreenUtils.getScreenWidth() / 2.toFloat(), ScreenUtils.getScreenHeight() / 2.toFloat())
            if (!clickResult) {
                completableDeferred.complete(false)
            }
            delay(250)
            runMain { AssistsWindowManager.removeView(view) }
        }
        return completableDeferred.await()
    }

    /**
     * 截取指定元素的屏幕截图并保存到文件
     * @param file 保存截图的文件，默认为应用内部文件路径下的时间戳命名文件
     * @param format 图片压缩格式，默认为PNG
     * @return 保存成功时返回文件对象，失败时返回null
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun AccessibilityNodeInfo.takeScreenshotSave(
        file: File = File(PathUtils.getInternalAppFilesPath() + "/${System.currentTimeMillis()}.png"),
        format: CompressFormat = Bitmap.CompressFormat.PNG
    ): File? {
        takeScreenshot()?.let {
            val result = ImageUtils.save(it, file, format)
            if (result) {
                return file
            }
        }
        return null
    }

    /**
     * 截取整个屏幕并保存到文件
     * @param file 保存截图的文件，默认为应用内部文件路径下的时间戳命名文件
     * @param format 图片压缩格式，默认为PNG
     * @return 保存成功时返回文件对象，失败时返回null
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun takeScreenshotSave(
        file: File = File(PathUtils.getInternalAppFilesPath() + "/${System.currentTimeMillis()}.png"),
        format: CompressFormat = Bitmap.CompressFormat.PNG
    ): File? {
        takeScreenshot()?.let {
            val result = ImageUtils.save(it, file, format)
            if (result) {
                return file
            }
        }
        return null
    }

    /**
     * 截取指定元素的屏幕截图
     * @return 截图成功时返回Bitmap对象，失败时返回null
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun AccessibilityNodeInfo.takeScreenshot(screenshot: Bitmap? = null): Bitmap? {
        val result = runCatching {

            val bitmap = screenshot?.let {
                return@let getBoundsInScreen().let { nodeBounds ->
                    val bitmap = Bitmap.createBitmap(it, nodeBounds.left, nodeBounds.top, nodeBounds.width(), nodeBounds.height())
                    return@let bitmap
                }
            } ?: let {
                return@let AssistsCore.takeScreenshot()?.let {
                    return@let getBoundsInScreen().let { nodeBounds ->
                        val bitmap = Bitmap.createBitmap(it, nodeBounds.left, nodeBounds.top, nodeBounds.width(), nodeBounds.height())
                        return@let bitmap
                    }
                }
            }
            return@runCatching bitmap
        }
        return result.getOrNull()
    }

    /**
     * 截取整个屏幕
     * @return 截图成功时返回Bitmap对象，失败时返回null
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun takeScreenshot(): Bitmap? {
        val completableDeferred = CompletableDeferred<Bitmap?>()
        AssistsService.getOrNull()?.takeScreenshot(Display.DEFAULT_DISPLAY, Executors.newSingleThreadExecutor(), object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                Bitmap.wrapHardwareBuffer(
                    screenshot.hardwareBuffer,
                    screenshot.colorSpace
                )?.let {

                    // 转成软件 bitmap，调试器能查看
                    val bitmap = it.copy(Bitmap.Config.ARGB_8888, false)

                    completableDeferred.complete(bitmap)
                } ?: let {
                    completableDeferred.complete(null)
                }
            }

            override fun onFailure(errorCode: Int) {
                completableDeferred.complete(null)
            }
        })
        return completableDeferred.await()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun AccessibilityNodeInfo.getMD5(
        scale: Float = 1f,          // 0 < scale <= 1
        cornerRatio: Float = 0f,     // 0.0 ~ 1.0
        file: File = File(PathUtils.getInternalAppFilesPath() + "/node-md5-temporary.png"),
        format: CompressFormat = Bitmap.CompressFormat.PNG
    ): String {
        runCatching {
            // 先保存节点的位置信息，确保使用一致的位置（避免多次调用getBoundsInScreen导致的位置变化）
            val nodeBounds = getBoundsInScreen()

            // 截取整个屏幕
            val fullScreenshot = AssistsCore.takeScreenshot()
            fullScreenshot ?: throw RuntimeException("bitmap is null")

            // 从完整截图中裁剪出节点对应的区域（使用已保存的位置信息）
            val nodeBitmap = Bitmap.createBitmap(
                fullScreenshot,
                nodeBounds.left,
                nodeBounds.top,
                nodeBounds.width(),
                nodeBounds.height()
            )

            // 清理完整截图（不再需要）
            fullScreenshot.recycle()

            // 对裁剪后的bitmap进行处理（缩放和圆角）
            val cropBitmap = BitmapUtils.cropCenterWithCornerRatio(
                nodeBitmap,
                scale = scale,
                cornerRatio = cornerRatio
            )

            // 清理节点bitmap（如果和cropBitmap不同）
            if (nodeBitmap != cropBitmap) {
                nodeBitmap.recycle()
            }

            val result = ImageUtils.save(cropBitmap, file, format)
            cropBitmap.recycle()

            if (result) {
                val md5 = FileUtils.getFileMD5ToString(file)
                return md5
            } else {
                throw RuntimeException("save bitmap failed")
            }
        }.onFailure {
            LogUtils.e(it)
        }
        return ""
    }

    /**
     * 在日志中输出元素的详细信息
     * @param tag 日志标签
     */
    fun AccessibilityNodeInfo.logNode(tag: String = LOG_TAG) {
        StringBuilder().apply {
            val rect = getBoundsInScreen()
            append(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n")
            append("位置:left=${rect.left}, top=${rect.top}, right=${rect.right}, bottom=${rect.bottom} \n")
            append("文本:$text \n")
            append("内容描述:$contentDescription \n")
            append("id:$viewIdResourceName \n")
            append("类型:${className} \n")
            append("是否已经获取到到焦点:$isFocused \n")
            append("是否可滚动:$isScrollable \n")
            append("是否可点击:$isClickable \n")
            append("是否可用:$isEnabled \n")
            append("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n")
            Log.d(tag, toString())
        }
    }

    /**
     * 节点边界信息数据类
     * 包含节点在屏幕中的位置和尺寸信息
     */
    data class NodeBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val width: Int,
        val height: Int,
        val centerX: Int,
        val centerY: Int,
        val exactCenterX: Float,
        val exactCenterY: Float,
        val isEmpty: Boolean,
    )

    /**
     * 节点树数据类
     * 包含节点的所有属性和子节点列表，用于生成树形结构的JSON
     */
    data class NodeTree(
        val packageName: String,
        val text: String,
        val des: String,
        val viewId: String,
        val className: String,
        val isScrollable: Boolean,
        val isClickable: Boolean,
        val isEnabled: Boolean,
        val boundsInScreen: NodeBounds,
        val hintText: String,
        val isCheckable: Boolean,
        val isChecked: Boolean,
        val isFocusable: Boolean,
        val isFocused: Boolean,
        val isLongClickable: Boolean,
        val isPassword: Boolean,
        val isSelected: Boolean,
        val isVisibleToUser: Boolean,
        val drawingOrder: Int,
        val children: List<NodeTree>
    )

    /**
     * 将AccessibilityNodeInfo转换为NodeBounds
     * @return 节点边界信息
     */
    private fun AccessibilityNodeInfo.toNodeBounds(): NodeBounds {
        val rect = getBoundsInScreen()
        return NodeBounds(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            width = rect.width(),
            height = rect.height(),
            centerX = rect.centerX(),
            centerY = rect.centerY(),
            exactCenterX = rect.exactCenterX(),
            exactCenterY = rect.exactCenterY(),
            isEmpty = rect.isEmpty
        )
    }

    /**
     * 将AccessibilityNodeInfo转换为NodeTree（递归包含子节点）
     * @return 节点树对象
     */
    fun AccessibilityNodeInfo.toNodeTree(): NodeTree {
        val children = mutableListOf<NodeTree>()
        for (i in 0 until childCount) {
            getChild(i)?.let { child ->
                children.add(child.toNodeTree())
            }
        }

        return NodeTree(
            packageName = packageName?.toString() ?: "",
            text = text?.toString() ?: "",
            des = contentDescription?.toString() ?: "",
            viewId = viewIdResourceName ?: "",
            className = className?.toString() ?: "",
            isScrollable = isScrollable,
            isClickable = isClickable,
            isEnabled = isEnabled,
            boundsInScreen = toNodeBounds(),
            hintText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) hintText?.toString() ?: "" else "",
            isCheckable = isCheckable,
            isChecked = isChecked,
            isFocusable = isFocusable,
            isFocused = isFocused,
            isLongClickable = isLongClickable,
            isPassword = isPassword,
            isSelected = isSelected,
            isVisibleToUser = isVisibleToUser,
            drawingOrder = drawingOrder,
            children = children
        )
    }

    /**
     * 多窗口 [NodeLookupScope.AllWindows] 下 [getRootNodeTree] 使用的合成根（[className] 为 [MULTI_ROOT_CLASS_NAME]），子节点为各窗口根之 [toNodeTree]；与单活动窗口时仅一层根相比，JSON/树结构多一层
     */
    private fun buildMultiWindowRootNodeTree(roots: List<AccessibilityNodeInfo>): NodeTree {
        val emptyBounds = NodeBounds(
            left = 0,
            top = 0,
            right = 0,
            bottom = 0,
            width = 0,
            height = 0,
            centerX = 0,
            centerY = 0,
            exactCenterX = 0f,
            exactCenterY = 0f,
            isEmpty = true,
        )
        return NodeTree(
            packageName = "multi_root",
            text = "",
            des = "",
            viewId = "",
            className = MULTI_ROOT_CLASS_NAME,
            isScrollable = false,
            isClickable = false,
            isEnabled = true,
            boundsInScreen = emptyBounds,
            hintText = "",
            isCheckable = false,
            isChecked = false,
            isFocusable = false,
            isFocused = false,
            isLongClickable = false,
            isPassword = false,
            isSelected = false,
            isVisibleToUser = false,
            drawingOrder = 0,
            children = roots.map { it.toNodeTree() },
        )
    }

    /**
     * 获取当前窗口所有节点的树形结构
     * @param scope 默认 [NodeLookupScope.AllWindows]；[NodeLookupScope.AllWindows] 且多根时为各窗口根 [toNodeTree] 之列表包在合成根下（[className] 为 [MULTI_ROOT_CLASS_NAME]），与单 [NodeLookupScope.ActiveWindow] 时 JSON 形状不同
     * @return 根节点的NodeTree对象，如果获取失败则返回null
     */
    @JvmOverloads
    fun getRootNodeTree(scope: NodeLookupScope = NodeLookupScope.AllWindows): NodeTree? {
        return when (scope) {
            NodeLookupScope.ActiveWindow -> {
                getAccessibilityRootNodes(NodeLookupScope.ActiveWindow).firstOrNull()?.toNodeTree()
            }
            NodeLookupScope.AllWindows -> {
                val roots = getAccessibilityRootNodes(NodeLookupScope.AllWindows)
                when (roots.size) {
                    0 -> null
                    1 -> roots[0].toNodeTree()
                    else -> buildMultiWindowRootNodeTree(roots)
                }
            }
        }
    }

    private const val MULTI_ROOT_CLASS_NAME: String = "__assists_multi_root__"

    /**
     * 获取当前窗口所有节点的JSON字符串（树形结构）
     * @param prettyPrint 是否格式化输出JSON，默认为false
     * @param scope 与 [getRootNodeTree] 相同，默认 [NodeLookupScope.AllWindows]
     * @return JSON字符串，如果获取失败则返回空字符串
     */
    @JvmOverloads
    fun getRootNodeTreeJson(
        prettyPrint: Boolean = false,
        scope: NodeLookupScope = NodeLookupScope.AllWindows,
    ): String {
        val nodeTree = getRootNodeTree(scope) ?: return ""
        val gson = if (prettyPrint) {
            GsonBuilder().setPrettyPrinting().create()
        } else {
            Gson()
        }
        return gson.toJson(nodeTree)
    }

    /**
     * 将指定节点及其所有子节点转换为JSON字符串（树形结构）
     * @param prettyPrint 是否格式化输出JSON，默认为false
     * @return JSON字符串
     */
    fun AccessibilityNodeInfo.toNodeTreeJson(prettyPrint: Boolean = false): String {
        val nodeTree = toNodeTree()
        val gson = if (prettyPrint) {
            GsonBuilder().setPrettyPrinting().create()
        } else {
            Gson()
        }
        return gson.toJson(nodeTree)
    }

    /**
     * 获取当前窗口所有节点的JSON字符串并保存到文件
     * @param file 保存JSON的文件，默认为应用内部文件路径下的时间戳命名文件
     * @param prettyPrint 是否格式化输出JSON，默认为true
     * @param scope 与 [getRootNodeTree] 相同，默认 [NodeLookupScope.AllWindows]
     * @return 保存成功时返回文件对象，失败时返回null
     */
    @JvmOverloads
    fun saveRootNodeTreeJson(
        file: File = File(PathUtils.getInternalAppFilesPath() + "/node_tree_${System.currentTimeMillis()}.json"),
        prettyPrint: Boolean = true,
        scope: NodeLookupScope = NodeLookupScope.AllWindows,
    ): File? {
        val json = getRootNodeTreeJson(prettyPrint, scope)
        if (json.isEmpty()) return null
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json)
            file
        }.getOrNull()
    }

    /**
     * 获取剪贴板内容
     * 如果应用在前台，直接获取剪贴板内容
     * 如果应用在后台，启动透明Activity获取剪贴板内容
     * @return 剪贴板文本内容，如果获取失败或为空则返回null
     */
    suspend fun getClipboardText(): CharSequence? {
        return try {
            // 判断应用是否在前台
            val isAppForeground = AppUtils.isAppForeground()

            if (isAppForeground) {
                // 应用在前台，直接获取剪贴板
                val text = ClipboardUtils.getText()
                LogUtils.d(LOG_TAG, "getClipboardText (foreground): $text")
                text
            } else {
                // 应用在后台，启动透明Activity获取剪贴板
                LogUtils.d(LOG_TAG, "getClipboardText (background): 启动透明Activity")
                val deferred = CompletableDeferred<CharSequence?>()
                ClipboardActivity.setClipboardResult(deferred)

                // 使用AssistsService的上下文启动Activity
                AssistsService.getOrNull()?.let { service ->
                    val intent = Intent(service, ClipboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    service.startActivity(intent)

                    // 等待Activity返回结果，设置超时时间为5秒
                    val result = withTimeoutOrNull(2500) {
                        deferred.await()
                    }
                    LogUtils.d(LOG_TAG, "getClipboardText (background result): $result")
                    result
                } ?: run {
                    LogUtils.e(LOG_TAG, "getClipboardText: AssistsService.getOrNull() is null")
                    deferred.complete(null)
                    null
                }
            }
        } catch (e: Exception) {
            LogUtils.e(LOG_TAG, "getClipboardText error: ${e.message}")
            null
        }
    }
}