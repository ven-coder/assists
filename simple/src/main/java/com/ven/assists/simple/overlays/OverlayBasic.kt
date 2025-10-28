package com.ven.assists.simple.overlays

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.TimeUtils
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.longClick
import com.ven.assists.AssistsCore.longPressGestureAutoPaste
import com.ven.assists.AssistsCore.nodeGestureClick
import com.ven.assists.AssistsCore.scrollBackward
import com.ven.assists.AssistsCore.scrollForward
import com.ven.assists.AssistsCore.selectionText
import com.ven.assists.AssistsCore.setNodeText
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowManager.overlayToast
import com.ven.assists.window.AssistsWindowWrapper
import com.ven.assists.simple.MultiTouchDrawingActivity
import com.ven.assists.simple.ScreenshotReviewActivity
import com.ven.assists.simple.TestActivity
import com.ven.assists.simple.databinding.BasicOverlayBinding
import com.ven.assists.utils.CoroutineWrapper
import kotlinx.coroutines.delay

object OverlayBasic : AssistsServiceListener {

    @SuppressLint("StaticFieldLeak")
    var viewBinding: BasicOverlayBinding? = null
        private set
        get() {
            if (field == null) {
                field = BasicOverlayBinding.inflate(LayoutInflater.from(AssistsService.instance)).apply {
                    //点击
                    btnClick.setOnClickListener {
                        CoroutineWrapper.launch {
                            AssistsService.instance?.startActivity(Intent(AssistsService.instance, TestActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                            delay(1000)
                            AssistsCore.findById("com.ven.assists.demo:id/btn_test").firstOrNull()?.click()
                        }

                    }
                    //手势点击
                    btnGestureClick.setOnClickListener {
                        CoroutineWrapper.launch {
                            ActivityUtils.getTopActivity()?.startActivity(Intent(AssistsService.instance, TestActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                            delay(1000)
                            AssistsCore.findById("com.ven.assists.demo:id/btn_test").firstOrNull()?.nodeGestureClick()
                        }
                    }
                    //长按
                    btnLongClick.setOnClickListener {
                        CoroutineWrapper.launch {
                            ActivityUtils.getTopActivity()?.startActivity(Intent(AssistsService.instance, TestActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                            delay(1000)
                            AssistsCore.findById("com.ven.assists.demo:id/btn_test").firstOrNull()?.longClick()
                        }
                    }
                    //手势长按
                    btnGestureLongClick.setOnClickListener {
                        CoroutineWrapper.launch {
                            ActivityUtils.getTopActivity()?.startActivity(Intent(AssistsService.instance, TestActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                            delay(1000)
                            AssistsCore.findById("com.ven.assists.demo:id/btn_test").firstOrNull()?.nodeGestureClick(duration = 1000)
                        }
                    }
                    //单指手势（画圆）
                    btnGestureSingleDraw.setOnClickListener {
                        CoroutineWrapper.launch(isMain = true) {
                            ActivityUtils.getTopActivity()
                                ?.startActivity(Intent(AssistsService.instance, MultiTouchDrawingActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            delay(1000)

                            performCircularGestureSingle()
                        }
                    }
                    //双指手势（画圆）
                    btnGestureDoubleDraw.setOnClickListener {
                        CoroutineWrapper.launch(isMain = true) {
                            ActivityUtils.getTopActivity()
                                ?.startActivity(Intent(AssistsService.instance, MultiTouchDrawingActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            delay(1000)

                            performCircularGestureDouble()
                        }
                    }
                    //单指手势（不规则）
                    btnGestureThreeDraw.setOnClickListener {
                        CoroutineWrapper.launch(isMain = true) {
                            ActivityUtils.getTopActivity()
                                ?.startActivity(Intent(AssistsService.instance, MultiTouchDrawingActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            delay(1000)

                            performSnakeGesture()
                        }
                    }
                    //选择文本
                    btnSelectText.setOnClickListener {
                        CoroutineWrapper.launch(isMain = true) {
                            ActivityUtils.getTopActivity()?.startActivity(Intent(AssistsService.instance, TestActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                            delay(1000)

                            TestActivity.scrollUp?.invoke()
                            delay(500)

                            AssistsCore.findById("com.ven.assists.demo:id/et_input").firstOrNull()?.let {
                                it.selectionText(it.text.length - 3, it.text.length)
                            }
                        }

                    }
                    //修改文本
                    btnChangeText.setOnClickListener {
                        CoroutineWrapper.launch(isMain = true) {
                            ActivityUtils.getTopActivity()?.startActivity(Intent(AssistsService.instance, TestActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                            delay(1000)

                            TestActivity.scrollUp?.invoke()
                            delay(500)
                            AssistsCore.findById("com.ven.assists.demo:id/et_input").firstOrNull()?.let {
                                it.setNodeText("测试修改文本: ${TimeUtils.getNowString()}")
                            }
                        }
                    }
                    //向前滚动
                    btnListScroll.setOnClickListener {
                        CoroutineWrapper.launch(isMain = true) {
                            ActivityUtils.getTopActivity()?.startActivity(Intent(AssistsService.instance, TestActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                            delay(1000)

                            TestActivity.scrollUp?.invoke()
                            delay(500)
                            var next = true
                            while (next) {
                                AssistsCore.findById("com.ven.assists.demo:id/scrollView").firstOrNull()?.let {
                                    next = it.scrollForward()
                                    delay(1000)
                                }
                            }
                            "已滚动到底部".overlayToast()
                        }

                    }
                    //向后滚动
                    btnListScrollBack.setOnClickListener {
                        CoroutineWrapper.launch(isMain = true) {
                            ActivityUtils.getTopActivity()?.startActivity(Intent(AssistsService.instance, TestActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                            delay(1000)

                            TestActivity.scrollDown?.invoke()
                            delay(500)
                            var next = true
                            while (next) {
                                AssistsCore.findById("com.ven.assists.demo:id/scrollView").firstOrNull()?.let {
                                    next = it.scrollBackward()
                                    delay(1000)
                                }
                            }
                            "已滚动到顶部".overlayToast()
                        }

                    }

                    //返回
                    btnBack.setOnClickListener {
                        AssistsCore.back()
                    }
                    //桌面
                    btnHome.setOnClickListener { AssistsCore.home() }
                    //通知
                    btnTask.setOnClickListener { AssistsCore.recentApps() }
                    //最新任务
                    btnNotification.setOnClickListener { AssistsCore.notifications() }

                    btnPowerDialog.setOnClickListener {
                        AssistsService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)

                    }
                    btnToggleSplitScreen.setOnClickListener {
                        AssistsService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)

                    }
                    btnLockScreen.setOnClickListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            AssistsService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                        } else {
                            "仅支持Android9及以上版本".overlayToast()
                        }
                    }
                    btnTakeScreenshot.setOnClickListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            AssistsService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                        } else {
                            "仅支持Android9及以上版本".overlayToast()
                        }
                    }
                    btn1.setOnClickListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            AssistsService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_KEYCODE_HEADSETHOOK)
                        } else {
                            "仅支持Android12及以上版本".overlayToast()
                        }
                    }
                    btn2.setOnClickListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            AssistsService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS)
                        } else {
                            "仅支持Android12及以上版本".overlayToast()
                        }
                    }
                    btnToast.setOnClickListener {
                        "这是浮窗级别Toast：${TimeUtils.getNowString()}".overlayToast(delay = 3000)
                    }
                }
            }
            return field
        }

    var onClose: ((parent: View) -> Unit)? = null

    var showed = false
        private set
        get() {
            assistWindowWrapper?.let {
                return AssistsWindowManager.isVisible(it.getView())
            } ?: return false
        }

    var assistWindowWrapper: AssistsWindowWrapper? = null
        private set
        get() {
            viewBinding?.let {
                if (field == null) {
                    field = AssistsWindowWrapper(
                        it.root,
                        wmLayoutParams = AssistsWindowManager.createLayoutParams().apply {
                            width = (ScreenUtils.getScreenWidth() * 0.8).toInt()
                            height = (ScreenUtils.getScreenHeight() * 0.5).toInt()
                            it.root.keepScreenOn=true
                        },
                        onClose = this.onClose
                    ).apply {
                        minWidth = (ScreenUtils.getScreenWidth() * 0.6).toInt()
                        minHeight = (ScreenUtils.getScreenHeight() * 0.4).toInt()
                        initialCenter = true
                        viewBinding.tvTitle.text = "基础示例"

                    }
                }
            }
            return field
        }

    fun show() {
        if (!AssistsService.listeners.contains(this)) {
            AssistsService.listeners.add(this)
        }
        AssistsWindowManager.add(assistWindowWrapper)
    }

    fun hide() {
        AssistsWindowManager.removeView(assistWindowWrapper?.getView())
    }

    override fun onUnbind() {
        viewBinding = null
        assistWindowWrapper = null
    }

    suspend fun performCircularGestureSingle() {
        val screenWidth = ScreenUtils.getScreenWidth()
        val screenHeight = ScreenUtils.getScreenHeight()
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val radius1 = 200f // 第一个圆的半径

        // 创建第一个圆的路径
        val path1 = Path()
        path1.addCircle(centerX, centerY, radius1, Path.Direction.CW)

        // 创建两个手势描述
        val stroke1 = GestureDescription.StrokeDescription(path1, 0, 2000)

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(stroke1)

        // 分发手势
        AssistsCore.dispatchGesture(gestureBuilder.build())
    }

    suspend fun performCircularGestureDouble() {
        val screenWidth = ScreenUtils.getScreenWidth()
        val screenHeight = ScreenUtils.getScreenHeight()
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val radius1 = 200f // 第一个圆的半径
        val radius2 = 300f // 第二个圆的半径

        // 创建第一个圆的路径
        val path1 = Path()
        path1.addCircle(centerX, centerY, radius1, Path.Direction.CW)

        // 创建第二个圆的路径
        val path2 = Path()
        path2.addCircle(centerX, centerY, radius2, Path.Direction.CW)

        // 创建两个手势描述
        val stroke1 = GestureDescription.StrokeDescription(path1, 0, 2000)
        val stroke2 = GestureDescription.StrokeDescription(path2, 0, 2000)

        // 创建手势构建器并添加两个手势
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(stroke1)
        gestureBuilder.addStroke(stroke2)

        // 分发手势
        AssistsCore.dispatchGesture(gestureBuilder.build())
    }

    suspend fun performSnakeGesture() {
        val screenWidth = ScreenUtils.getScreenWidth()
        val screenHeight = ScreenUtils.getScreenHeight()
        val segmentHeight = 200f // 每段路径的垂直高度
        val maxHorizontalOffset = 300f // 水平方向的最大偏移量

        val path = Path()
        var currentY = BarUtils.getStatusBarHeight() + BarUtils.getActionBarHeight().toFloat() + 100f
        var currentX = screenWidth / 2f // 从屏幕中间开始

        // 移动到起点
        path.moveTo(currentX, currentY)

        // 生成蛇形路径
        while (currentY < screenHeight) {
            // 随机生成水平偏移量
            val offsetX = (Math.random() * maxHorizontalOffset * 2 - maxHorizontalOffset).toFloat()
            currentX += offsetX
            currentY += segmentHeight

            // 确保 X 坐标在屏幕范围内
            currentX = currentX.coerceIn(0f, screenWidth.toFloat())

            // 添加路径点
            path.lineTo(currentX, currentY)
        }

        // 创建手势描述
        val stroke = GestureDescription.StrokeDescription(path, 0, 5000) // 5秒完成手势
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(stroke)

        // 分发手势
        AssistsCore.dispatchGesture(gestureBuilder.build())
    }
}