package com.ven.assists.window

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.blankj.utilcode.util.ScreenUtils
import com.ven.assists.base.databinding.AssistsWindowLayoutWrapperBinding
import com.ven.assists.utils.CoroutineWrapper

/**
 * 浮窗包装类
 * 为浮窗提供统一的外观和交互行为，包括：
 * 1. 可拖动移动位置
 * 2. 可缩放大小
 * 3. 可关闭
 * 4. 支持自定义初始位置和大小限制
 */
@SuppressLint("ClickableViewAccessibility")
class AssistsWindowWrapper(
    view: View,
    wmLayoutParams: WindowManager.LayoutParams? = null,
    onClose: ((parent: View) -> Unit)? = null,
) {
    /** 当前布局高度 */
    private var layoutHeight: Int = 0

    /** 当前布局宽度 */
    private var layoutWidth: Int = 0

    /** 触摸事件按下时的屏幕原始X坐标 */
    private var eventDownRawX = 0

    /** 触摸事件按下时的屏幕原始Y坐标 */
    private var eventDownRawY = 0

    /** 触摸按下时窗口的X坐标，用于按增量更新避免跳变 */
    private var downWindowX = 0

    /** 触摸按下时窗口的Y坐标，用于按增量更新避免跳变 */
    private var downWindowY = 0

    /** 最小高度限制，-1表示无限制 */
    var minHeight = -1

    /** 最小宽度限制，-1表示无限制 */
    var minWidth = -1

    /** 最大高度限制，-1表示无限制 */
    var maxHeight = -1

    /** 最大宽度限制，-1表示无限制 */
    var maxWidth = -1

    /** 初始X坐标 */
    var initialX = 0

    /** 初始Y坐标 */
    var initialY = 0

    /** X轴偏移量 */
    var initialXOffset = 0

    /** Y轴偏移量 */
    var initialYOffset = 0

    /** 是否初始居中显示 */
    var initialCenter = false

    /** 是否显示操作按钮（移动、缩放、关闭） */
    var showOption: Boolean = true

    /** 是否显示背景 */
    var showBackground = true

    /** 窗口布局参数 */
    var wmlp: WindowManager.LayoutParams = wmLayoutParams ?: let { AssistsWindowManager.createLayoutParams() }

    /**
     * 缩放触摸事件监听器
     * 处理浮窗的缩放操作：以按下时窗口尺寸与位置为基准，按手指位移增量更新，避免跳变
     */
    private val onTouchScaleListener = object : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    layoutHeight = viewBinding.root.measuredHeight
                    layoutWidth = viewBinding.root.measuredWidth
                    eventDownRawX = event.rawX.toInt()
                    eventDownRawY = event.rawY.toInt()
                    downWindowX = wmlp.x
                    downWindowY = wmlp.y
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - eventDownRawX
                    val dy = event.rawY.toInt() - eventDownRawY
                    // 左下角缩放：向左拖增大宽度并左移，向下拖增大高度
                    val width = layoutWidth - dx
                    if (width > 0) {
                        if ((minWidth == -1 || width >= minWidth) && (maxWidth == -1 || width <= maxWidth)) {
                            wmlp.width = width
                            wmlp.x = downWindowX + dx
                        }
                    }

                    val height = layoutHeight + dy
                    if (height > 0) {
                        if ((minHeight == -1 || height >= minHeight) && (maxHeight == -1 || height <= maxHeight)) {
                            wmlp.height = height
                        }
                    }
                    CoroutineWrapper.launch { AssistsWindowManager.updateViewLayout(viewBinding.root, wmlp) }
                    return true
                }
            }
            return false
        }
    }

    /**
     * 移动触摸事件监听器
     * 处理浮窗的拖动移动：以按下时窗口位置为基准，按手指位移增量更新，避免跳变
     */
    private val onTouchMoveListener = object : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    eventDownRawX = event.rawX.toInt()
                    eventDownRawY = event.rawY.toInt()
                    downWindowX = wmlp.x
                    downWindowY = wmlp.y
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    wmlp.x = downWindowX + (event.rawX.toInt() - eventDownRawX)
                    wmlp.y = downWindowY + (event.rawY.toInt() - eventDownRawY)
                    CoroutineWrapper.launch { AssistsWindowManager.updateViewLayout(viewBinding.root, wmlp) }
                    return true
                }
            }
            return false
        }
    }

    /**
     * 视图绑定对象
     * 负责初始化浮窗的布局和行为
     */
    val viewBinding: AssistsWindowLayoutWrapperBinding by lazy {
        AssistsWindowLayoutWrapperBinding.inflate(LayoutInflater.from(view.context)).apply {
            root.isInvisible = true
            // 添加全局布局监听，处理初始位置和显示
            root.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (root.measuredWidth > 0) {
                        root.isInvisible = false
                        if (initialCenter) {
                            val measuredWidth = root.measuredWidth
                            val measuredHeight = root.measuredHeight
                            val initialX = ScreenUtils.getScreenWidth() / 2 - measuredWidth / 2
                            val initialY = ScreenUtils.getScreenHeight() / 2 - measuredHeight / 2
                            wmlp.x = initialX
                            wmlp.y = initialY
                        } else {
                            wmlp.x = initialX
                            wmlp.y = initialY
                        }
                        CoroutineWrapper.launch { AssistsWindowManager.updateViewLayout(root, wmlp) }
                        root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            })
            // 设置移动、缩放和关闭按钮的事件监听
            ivMove.setOnTouchListener(onTouchMoveListener)
            ivScale.setOnTouchListener(onTouchScaleListener)
            ivClose.setOnClickListener { onClose?.invoke(root) ?: AssistsWindowManager.removeView(root) }
            // 根据配置显示或隐藏操作按钮和背景
            flHeader.isVisible = showOption
            ivScale.isVisible = showOption
            llBottomBar.isVisible = showOption
            if (!showBackground) {
                root.background = null
            }
            // 添加内容视图
            flContainer.addView(view)
            ivMaximize.setOnClickListener {
                wmlp.x = 0
                wmlp.y = 0
                wmlp.width = ScreenUtils.getScreenWidth()
                wmlp.height = ScreenUtils.getScreenHeight()
                CoroutineWrapper.launch { AssistsWindowManager.updateViewLayout(root, wmlp) }
            }
            ivMinimize.setOnClickListener {
                CoroutineWrapper.launch { AssistsWindowManager.hide(root) }
                WindowMinimizeManager.show()
            }
            wmlp.x = initialX
            wmlp.y = initialY
        }
    }

    /**
     * 设置浮窗为不可触摸状态
     * 此状态下浮窗将忽略所有触摸事件
     */
    fun ignoreTouch() {
        wmlp.flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        AssistsWindowManager.getWindowManager()?.updateViewLayout(viewBinding.root, wmlp)
    }

    /**
     * 设置浮窗为可触摸状态
     * 此状态下浮窗可以响应触摸事件
     */
    fun consumeTouch() {
        wmlp.flags = (WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        AssistsWindowManager.getWindowManager()?.updateViewLayout(viewBinding.root, wmlp)
    }

    /**
     * 获取浮窗的根视图
     * @return 浮窗的根View对象
     */
    fun getView(): View {
        return viewBinding.root
    }
}