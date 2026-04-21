package com.ven.assists.window

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import androidx.core.view.isInvisible
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.SizeUtils
import com.blankj.utilcode.util.TimeUtils
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.longClick
import com.ven.assists.AssistsCore.nodeGestureClick
import com.ven.assists.AssistsCore.scrollBackward
import com.ven.assists.AssistsCore.scrollForward
import com.ven.assists.AssistsCore.selectionText
import com.ven.assists.AssistsCore.setNodeText
import com.ven.assists.base.databinding.MinimizeFloatingBinding
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.window.AssistsWindowManager.overlayToast
import kotlinx.coroutines.delay

object WindowMinimizeManager : AssistsServiceListener {
    var wmlp: WindowManager.LayoutParams? = null

    /** 当前布局高度 */
    private var layoutHeight: Int = 0

    /** 当前布局宽度 */
    private var layoutWidth: Int = 0

    /** 触摸事件按下时的原始X坐标 */
    private var eventDownRawX = 0

    /** 触摸事件按下时的原始Y坐标 */
    private var eventDownRawY = 0

    /** 最小高度限制，-1表示无限制 */
    var minHeight = -1

    /** 最小宽度限制，-1表示无限制 */
    var minWidth = -1

    /** 最大高度限制，-1表示无限制 */
    var maxHeight = -1

    /** 最大宽度限制，-1表示无限制 */
    var maxWidth = -1
    private val onTouchScaleListener = object : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                layoutHeight = viewBinding?.root?.measuredHeight ?: 0
                layoutWidth = viewBinding?.root?.measuredWidth ?: 0
                eventDownRawX = event.rawX.toInt()
                eventDownRawY = event.rawY.toInt()
                return true
            }
            if (event.action == MotionEvent.ACTION_MOVE) {
                val width = layoutWidth + (eventDownRawX - event.rawX.toInt())
                if (width > 0) {
                    if ((minWidth == -1 || width >= minWidth) && (maxWidth == -1 || width <= maxWidth)) {
                        wmlp?.width = width
                        wmlp?.x = event.rawX.toInt()
                    }
                }

                val height = layoutHeight - (eventDownRawY - event.rawY.toInt())

                if (height > 0) {
                    if ((minHeight == -1 || height >= minHeight) && (maxHeight == -1 || height <= maxHeight)) {
                        wmlp?.height = height
                    }
                }
                CoroutineWrapper.launch { AssistsWindowManager.updateViewLayout(viewBinding?.root, wmlp) }
                return true
            }

            return false
        }
    }

    private val onTouchMoveListener = object : View.OnTouchListener {

        var downTime = 0L

        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                downTime = TimeUtils.getNowMills()
                return true
            }
            if (event.action == MotionEvent.ACTION_MOVE) {
                wmlp?.x = event.rawX.toInt()

                wmlp?.y = event.rawY.toInt() - BarUtils.getStatusBarHeight()
                wmlp?.let {
                    if (it.x < -((viewBinding?.root?.measuredWidth ?: 0) / 2)) {
                        it.x = -((viewBinding?.root?.measuredWidth ?: 0) / 2)
                    }
                    if (it.x >= ScreenUtils.getScreenWidth() - (viewBinding?.root?.measuredWidth ?: 0) / 2) {
                        it.x = ScreenUtils.getScreenWidth() - (viewBinding?.root?.measuredWidth ?: 0) / 2
                    }
                    if (it.y < -((viewBinding?.root?.measuredHeight ?: 0) / 2)) {
                        it.y = -((viewBinding?.root?.measuredHeight ?: 0) / 2)
                    }
                    if (it.y >= ScreenUtils.getScreenHeight() - (viewBinding?.root?.measuredHeight ?: 0) / 2) {
                        it.y = ScreenUtils.getScreenHeight() - (viewBinding?.root?.measuredHeight ?: 0) / 2
                    }
                }
                CoroutineWrapper.launch { AssistsWindowManager.updateViewLayout(viewBinding?.root, wmlp) }
                return true
            }
            if (event.action == MotionEvent.ACTION_UP) {
                if (TimeUtils.getNowMills() - downTime < 250) {

                    if (AssistsWindowManager.viewList.size <= 1) {
                        CoroutineWrapper.launch { "当前未添加浮窗".overlayToast() }
                    } else {
                        CoroutineWrapper.launch {
                            val invisibleViews = AssistsWindowManager.viewList.filter { it.value.view.isInvisible }
                            if (invisibleViews.isEmpty()) {
                                AssistsWindowManager.hideAll(isTouchable = false, filterViews = viewBinding?.root?.let { listOf(it) } ?: listOf())
                            } else {
                                AssistsWindowManager.showAll(isTouchable = true)
                            }
                        }
                    }

                }
            }

            return false
        }
    }

    @SuppressLint("StaticFieldLeak")
    var viewBinding: MinimizeFloatingBinding? = null
        private set
        @SuppressLint("ClickableViewAccessibility")
        get() {
            if (field == null) {
                field = MinimizeFloatingBinding.inflate(LayoutInflater.from(AssistsService.getOrNull())).apply {
                    wmlp = AssistsWindowManager.createLayoutParams().apply {
                        width = -2
                        height = -2
                    }
                    root.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            if (root.measuredWidth > 0) {
                                root.isInvisible = false
                                val measuredHeight = root.measuredHeight
                                val initialY = ScreenUtils.getScreenHeight() / 2 - measuredHeight / 2
                                wmlp?.y = initialY
                                CoroutineWrapper.launch { wmlp?.let { AssistsWindowManager.updateViewLayout(root, it) } }
                                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            }
                        }
                    })
                    root.setOnTouchListener(onTouchMoveListener)
                }
            }
            return field
        }

    fun show() {
        if (!AssistsService.listeners.contains(this)) {
            AssistsService.listeners.add(this)
        }
        if (viewBinding?.root?.isInvisible == true) {
            viewBinding?.root?.isInvisible = false
        }

        if (!AssistsWindowManager.contains(viewBinding?.root)) {
            wmlp?.let { AssistsWindowManager.add(view = viewBinding?.root, layoutParams = it) }
        }
    }

    fun hide() {
        viewBinding?.root?.isInvisible = true
    }

    fun close() {
        AssistsWindowManager.removeView(viewBinding?.root)
        viewBinding = null
    }

    override fun onUnbind() {
    }

}