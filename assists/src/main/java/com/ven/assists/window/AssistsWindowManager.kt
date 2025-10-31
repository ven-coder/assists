package com.ven.assists.window

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.SizeUtils
import com.ven.assists.service.AssistsService
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.utils.runIO
import com.ven.assists.utils.runMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.UUID

/**
 * 浮窗管理器
 * 提供全局浮窗的添加、删除、显示、隐藏等管理功能
 */
object AssistsWindowManager {
    /** 系统窗口管理器 */
    private lateinit var windowManager: WindowManager

    /** 显示度量信息 */
    private lateinit var mDisplayMetrics: DisplayMetrics

    /**
     * 浮窗视图列表，使用线程安全的集合
     */
    val viewList = Collections.synchronizedMap(mutableMapOf<String, ViewWrapper>())

    /**
     * 初始化窗口管理器
     * @param accessibilityService 无障碍服务实例
     */
    fun init(accessibilityService: AccessibilityService) {
        windowManager = accessibilityService.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mDisplayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(mDisplayMetrics)
    }

    /**
     * 获取系统窗口管理器实例
     * @return WindowManager实例，如果未初始化则返回null
     */
    fun getWindowManager(): WindowManager? {
        AssistsService.instance?.getSystemService(Context.WINDOW_SERVICE)?.let { return (it as WindowManager) }
        return null
    }

    /**
     * 创建默认的浮窗布局参数
     * @return 配置好的WindowManager.LayoutParams实例
     */
    fun createLayoutParams(): WindowManager.LayoutParams {
        val layoutParams = WindowManager.LayoutParams()
        layoutParams.flags = (WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)

        layoutParams.height = ScreenUtils.getScreenHeight()
        layoutParams.width = ScreenUtils.getScreenWidth()
        layoutParams.gravity = Gravity.START or Gravity.TOP
        layoutParams.format = PixelFormat.RGBA_8888
        //此处layoutParams.type不建议使用TYPE_TOAST，因为在一些版本较低的系统中会出现拖动异常的问题，虽然它不需要权限
        layoutParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        //背景明暗度0~1，数值越大背景越暗，只有在flags设置了WindowManager.LayoutParams.FLAG_DIM_BEHIND 这个属性才会生效
        layoutParams.dimAmount = 0.0f
        //透明度0~1，数值越大越不透明
        layoutParams.alpha = 1f
        return layoutParams
    }

    /**
     * 隐藏所有浮窗
     * @param isTouchable 隐藏后是否可触摸，默认为true
     */
    suspend fun hideAll(isTouchable: Boolean = true, filterViews: List<View> = arrayListOf()) {
        withContext(Dispatchers.Main) {
            viewList.values.forEach {
                if (filterViews.contains(it.view)) {
                    return@forEach
                }
                it.view.isInvisible = true
                if (isTouchable) {
                    it.touchableByWrapper()
                } else {
                    it.nonTouchableByWrapper()
                }
            }
        }
    }

    /**
     * 隐藏最顶层浮窗
     * @param isTouchable 隐藏后是否可触摸，默认为true
     */
    suspend fun hideTop(isTouchable: Boolean = true) {
        withContext(Dispatchers.Main) {
            viewList.values.lastOrNull()?.let {
                it.view.isInvisible = true
                if (isTouchable) {
                    it.touchableByWrapper()
                } else {
                    it.nonTouchableByWrapper()
                }
            }
        }
    }

    suspend fun hide(view: View?, isTouchable: Boolean = true) {
        view ?: return
        withContext(Dispatchers.Main) {
            viewList.values.forEach {
                if (view == it.view) {
                    it.view.isInvisible = true
                    if (isTouchable) {
                        it.touchableByWrapper()
                    } else {
                        it.nonTouchableByWrapper()
                    }
                }
            }
        }
    }

    /**
     * 显示最顶层浮窗
     * @param isTouchable 显示后是否可触摸，默认为true
     */
    suspend fun showTop(isTouchable: Boolean = true) {
        withContext(Dispatchers.Main) {
            viewList.values.lastOrNull()?.let {
                it.view.isVisible = true
                if (isTouchable) {
                    it.touchableByWrapper()
                } else {
                    it.nonTouchableByWrapper()
                }
            }
        }
    }

    /**
     * 显示所有浮窗
     * @param isTouchable 显示后是否可触摸，默认为true
     */
    suspend fun showAll(isTouchable: Boolean = true) {
        withContext(Dispatchers.Main) {
            viewList.values.forEach {
                it.view.isVisible = true
                if (isTouchable) {
                    it.touchableByWrapper()
                } else {
                    it.nonTouchableByWrapper()
                }
            }
        }
    }

    /**
     * 添加浮窗包装器
     * @param windowWrapper 浮窗包装器
     * @param isStack 是否堆叠显示，默认为true
     * @param isTouchable 是否可触摸，默认为true
     */
    fun add(windowWrapper: AssistsWindowWrapper?, isStack: Boolean = true, isTouchable: Boolean = true): ViewWrapper? {
        windowWrapper ?: return null
        return add(view = windowWrapper.getView(), layoutParams = windowWrapper.wmlp, isStack = isStack, isTouchable = isTouchable)
    }

    /**
     * 添加浮窗视图
     * @param view 要添加的视图
     * @param layoutParams 布局参数
     * @param isStack 是否堆叠显示，默认为true
     * @param isTouchable 是否可触摸，默认为true
     */
    fun add(
        view: View?,
        layoutParams: WindowManager.LayoutParams = createLayoutParams(),
        isStack: Boolean = true,
        isTouchable: Boolean = true,
        viewTag: Any? = null
    ): ViewWrapper? {
        view ?: return null
        if (!isStack) {
            viewList.values.lastOrNull()?.let { it.view.isInvisible = true }
        }
        view.tag = viewTag
        windowManager.addView(view, layoutParams)
        if (isTouchable) {
            layoutParams.touchableByLayoutParams()
        } else {
            layoutParams.nonTouchableByLayoutParams()
        }
        val wrapper = ViewWrapper(view, layoutParams)
        viewList[wrapper.uniqueId] = wrapper
        return wrapper
    }

    /**
     * FLAG_NOT_FOCUSABLE
     * 8
     * 0x08
     * 不获取焦点
     * FLAG_NOT_TOUCHABLE
     * 16
     * 0x10
     * 不响应触摸
     * FLAG_NOT_TOUCH_MODAL
     * 32
     * 0x20
     * 不拦截触摸
     * FLAG_WATCH_OUTSIDE_TOUCH
     * 4
     * 0x04
     * 监听窗外点击
     * FLAG_LAYOUT_NO_LIMITS
     * 512
     * 0x200
     * 可绘制超出屏幕
     * FLAG_LAYOUT_IN_SCREEN
     * 256
     * 0x100
     * 屏幕全区域布局
     * FLAG_FULLSCREEN
     * 1024
     * 0x400
     * 全屏显示
     * FLAG_DIM_BEHIND
     * 2
     * 0x02
     * 背景变暗
     * FLAG_SECURE
     * 8192
     * 0x2000
     * 防录屏防截图
     * FLAG_KEEP_SCREEN_ON
     * 128
     * 0x80
     * 保持常亮
     * FLAG_SHOW_WHEN_LOCKED
     * 524288
     * 0x80000
     * 锁屏时可见
     * FLAG_DISMISS_KEYGUARD
     * 4194304
     * 0x400000
     * 解锁屏幕
     * FLAG_TURN_SCREEN_ON
     * 2097152
     * 0x200000
     * 点亮屏幕
     * FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
     * 128
     * 0x80
     * 自动锁屏（不常用）
     * FLAG_SHOW_WALLPAPER
     * 1048576
     * 0x100000
     * 显示墙纸
     * FLAG_HARDWARE_ACCELERATED
     * 16777216
     * 0x1000000
     * 强制硬件加速
     *
     */
    suspend fun setFlags(flag: Int) {
        withContext(Dispatchers.Main) {
            viewList.values.forEach {
                it.layoutParams.flags = flag
                updateViewLayout(it.view, it.layoutParams)
            }
        }
    }

    /**
     * 添加浮窗并隐藏之前的浮窗
     * @param view 要添加的视图
     * @param params 布局参数
     */
    fun push(view: View?, params: WindowManager.LayoutParams = createLayoutParams()): ViewWrapper? {
        return add(view, params, isStack = false)
    }

    /**
     * 移除最顶层浮窗并显示下一个浮窗
     * @param showTop 是否显示下一个浮窗，默认为true
     */
    suspend fun pop(showTop: Boolean = true) {
        viewList.values.lastOrNull()?.let { removeView(it.view) }
        if (showTop) showTop()
    }

    /**
     * 移除指定浮窗
     * @param view 要移除的视图
     */
    @Deprecated("使用removeWindow")
    fun removeView(view: View?) {
        view ?: return
        try {
            windowManager.removeView(view)
            viewList.values.find {
                return@find view == it.view
            }?.let {
                viewList.remove(it.uniqueId)
            }

            if (viewList.size == 1 && viewList.values.first().view == WindowMinimizeManager.viewBinding?.root) {
                WindowMinimizeManager.hide()
            }

        } catch (e: Throwable) {
            LogUtils.e(e)
        }
    }

    /**
     * 移除指定浮窗
     * @param view 要移除的视图
     */
    fun removeWindow(view: View?) {
        view ?: return
        try {
            windowManager.removeView(view)
            viewList.values.find {
                return@find view == it.view
            }?.let {
                viewList.remove(it.uniqueId)
            }

            if (viewList.size == 1 && viewList.values.first().view == WindowMinimizeManager.viewBinding?.root) {
                WindowMinimizeManager.hide()
            }

        } catch (e: Throwable) {
            LogUtils.e(e)
        }
    }

    fun removeWindow(viewTag: Any?) {
        try {
            viewList.values.find {
                return@find it.view.tag == viewTag
            }?.let {
                windowManager.removeView(it.view)
                viewList.remove(it.uniqueId)
            }

            if (viewList.size == 1 && viewList.values.first().view == WindowMinimizeManager.viewBinding?.root) {
                WindowMinimizeManager.hide()
            }

        } catch (e: Throwable) {
            LogUtils.e(e)
        }
    }


    fun removeAllWindow() {
        try {
            viewList.forEach {
                windowManager.removeView(it.value.view)
            }
            viewList.clear()
        } catch (e: Throwable) {
            LogUtils.e(e)
        }
    }

    /**
     * 检查指定视图是否已添加为浮窗
     * @param view 要检查的视图
     * @return 是否存在于浮窗列表中
     */
    fun contains(view: View?): Boolean {
        view ?: return false
        return viewList.values.find {
            return@find view == it.view
        } != null
    }

    fun contains(viewTag: Any?): Boolean {
        return viewList.values.find {
            return@find it.view.tag == viewTag
        } != null
    }

    /**
     * 检查指定浮窗包装器是否已添加
     * @param wrapper 要检查的浮窗包装器
     * @return 是否存在于浮窗列表中
     */
    fun contains(wrapper: AssistsWindowWrapper?): Boolean {
        wrapper ?: return false
        return viewList.values.find {
            return@find wrapper.getView() == it.view
        } != null
    }

    /**
     * 检查指定浮窗是否可见
     * @param view 要检查的视图
     * @return 是否可见
     */
    fun isVisible(view: View): Boolean {
        return viewList.values.find {
            return@find view == it.view
        }?.let {
            return@let it.view.isVisible
        } ?: false
    }

    /**
     * 更新浮窗布局
     * @param view 要更新的视图
     * @param params 新的布局参数
     */
    suspend fun updateViewLayout(view: View?, params: ViewGroup.LayoutParams?) {
        view ?: return
        params ?: return
        runMain { windowManager.updateViewLayout(view, params) }
    }

    /**
     * 设置所有浮窗为可触摸状态
     */
    suspend fun touchableByAll() {
        viewList.values.forEach { it.touchableByWrapper() }
    }

    /**
     * 设置所有浮窗为不可触摸状态
     */
    suspend fun nonTouchableByAll() {
        viewList.values.forEach { it.nonTouchableByWrapper() }
    }

    fun WindowManager.LayoutParams.focusInput() {
        flags = (WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
    }


    /**
     * 设置布局参数为可触摸状态
     */
    fun WindowManager.LayoutParams.touchableByLayoutParams() {
        flags = (WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
    }

    /**
     * 设置布局参数为不可触摸状态
     */
    fun WindowManager.LayoutParams.nonTouchableByLayoutParams() {
        flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
    }

    /**
     * 设置浮窗包装器为可触摸状态
     */
    suspend fun ViewWrapper.touchableByWrapper() {
        layoutParams.touchableByLayoutParams()
        updateViewLayout(view, layoutParams)
    }

    /**
     * 设置浮窗包装器为不可触摸状态
     */
    suspend fun ViewWrapper.nonTouchableByWrapper() {
        layoutParams.nonTouchableByLayoutParams()
        updateViewLayout(view, layoutParams)
    }

    /**
     * 显示一个临时的Toast样式浮窗
     * @param delay 显示时长，默认2000毫秒
     */
    fun String.overlayToast(delay: Long = 2000) {
        AssistsService.instance?.let {
            CoroutineWrapper.launch(isMain = true) {
                val textView = TextView(it).apply {
                    text = this@overlayToast
                    setTextColor(Color.WHITE)
                    setPadding(SizeUtils.dp2px(10f))
                    layoutParams = ViewGroup.LayoutParams(-2, -2)
                }
                val assistsWindowWrapper = AssistsWindowWrapper(textView, wmLayoutParams = createLayoutParams().apply {
                    width = -2
                    height = -2
                }).apply {
                    showOption = false
                    initialCenter = true
                }
                add(assistsWindowWrapper, isTouchable = false)
                runIO { delay(delay) }
                removeWindow(assistsWindowWrapper.getView())
            }
        }
    }

    /**
     * 浮窗视图包装类
     * @param view 浮窗视图
     * @param layoutParams 布局参数
     */
    class ViewWrapper(val view: View, val layoutParams: WindowManager.LayoutParams, val uniqueId: String = UUID.randomUUID().toString())
}