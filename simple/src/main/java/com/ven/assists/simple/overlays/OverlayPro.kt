package com.ven.assists.simple.overlays

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.gestureClick
import com.ven.assists.AssistsCore.isImageView
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowManager.overlayToast
import com.ven.assists.window.AssistsWindowWrapper
import com.ven.assists.simple.ImageGalleryActivity
import com.ven.assists.simple.ScreenshotReviewActivity
import com.ven.assists.log.AssistsLog
import com.ven.assists.log.logAppend
import com.ven.assists.simple.databinding.ProOverlayBinding
import com.ven.assists.text.TextRecognitionChineseLocator
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.web.mlkit.MlkitScreenTextUtils
import com.ven.assists.mp.MPManager
import com.ven.assists.mp.MPManager.takeScreenshot2File
import kotlinx.coroutines.delay

@SuppressLint("StaticFieldLeak")
object OverlayPro : AssistsServiceListener {

    private var disableNotificationView: View? = null

    var viewBinding: ProOverlayBinding? = null
        private set
        get() {
            if (field == null) {
                field = ProOverlayBinding.inflate(LayoutInflater.from(AssistsService.getOrNull())).apply {
                    btnListenerNotification.setOnClickListener {
                        if (!AssistsService.listeners.contains(notificationListener)) {
                            AssistsService.listeners.add(notificationListener)
                        }
                        CoroutineWrapper.launch(isMain = true) {
                            OverlayLog.show()
                            AssistsLog.appendTimestampedEntry("通知监听中...")
                        }
                    }
                    btnDisablePullNotification.setOnClickListener {
                        disableNotificationView?.let {
                            AssistsWindowManager.removeView(it)
                            disableNotificationView = null
                            btnDisablePullNotification.setText("禁止下拉通知栏")
                            return@setOnClickListener
                        }
                        disableNotificationView = View(AssistsService.getOrNull()).apply {
                            setBackgroundColor(Color.parseColor("#80000000"))
                            layoutParams = ViewGroup.LayoutParams(-1, BarUtils.getStatusBarHeight())
                        }
                        AssistsWindowManager.add(view = disableNotificationView, layoutParams = AssistsWindowManager.createLayoutParams().apply {
                            width = -1
                            height = BarUtils.getStatusBarHeight()
                        })
                        btnDisablePullNotification.setText("允许下拉通知栏")
                    }
                    btnScreenCapture.setOnClickListener {
                        CoroutineWrapper.launch {
                            val result = MPManager.request(autoAllow = false, timeOut = 5000)
                            if (result) {
                                "已获取屏幕录制权限".overlayToast()
                            } else {
                                "获取屏幕录制权限超时".overlayToast()
                            }
                        }
                    }
                    btnScreenCaptureAuto.setOnClickListener {
                        CoroutineWrapper.launch {
                            val result = MPManager.request(autoAllow = true, timeOut = 5000)
                            if (result) {
                                "已获取屏幕录制权限".overlayToast()
                            } else {
                                "获取屏幕录制权限超时".overlayToast()
                            }
                        }
                    }
                    btnTakeScreenshot.setOnClickListener {
                        CoroutineWrapper.launch {
                            runCatching {
                                val file = takeScreenshot2File()
                                AssistsCore.launchApp(Intent(AssistsService.getOrNull(), ScreenshotReviewActivity::class.java).apply {
                                    putExtra("path", file?.path)
                                })
                            }.onFailure {
                                LogUtils.d(it)
                                "截图失败，尝试请求授予屏幕录制后重试".overlayToast()
                            }
                        }

                    }
                    btnTakeScreenshotAllImage.setOnClickListener {
                        takeScreenshotAllImage()
                    }
                    btnScreenTextPositions.setOnClickListener {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            "屏幕文字识别需要 Android 11（API 30）及以上系统。".overlayToast()
                            return@setOnClickListener
                        }
                        OverlayLog.show()
                        CoroutineWrapper.launch {
                            "开始识别当前屏幕文字位置…".logAppend()
                            AssistsWindowManager.hideAll()
                            try {
                                delay(250L)
                                val result = MlkitScreenTextUtils.getScreenTextPositions()
                                result.fold(
                                    onSuccess = { rec ->
                                        val previewCount = 20
                                        "文字块数量=${rec.positions.size} 耗时毫秒=${rec.processingTimeMillis} 全文长度=${rec.fullText.length}".logAppend()
                                        rec.positions.take(previewCount).forEachIndexed { index, pos ->
                                            "[${index + 1}] \"${pos.text}\" 左=${pos.left} 上=${pos.top} 右=${pos.right} 下=${pos.bottom} 宽=${pos.width} 高=${pos.height}".logAppend()
                                        }
                                        if (rec.positions.size > previewCount) {
                                            "… 另有 ${rec.positions.size - previewCount} 条已省略".logAppend()
                                        }
                                        "识别完成。".logAppend()
                                    },
                                    onFailure = { e ->
                                        LogUtils.e(e)
                                        "识别失败：${e.message}".logAppend()
                                    }
                                )
                            } finally {
                                AssistsWindowManager.showAll()
                            }
                        }
                    }
                    btnOcrClickPhraseBasic.setOnClickListener {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            "屏幕文字识别需要 Android 11（API 30）及以上系统。".overlayToast()
                            return@setOnClickListener
                        }
                        OverlayLog.show()
                        CoroutineWrapper.launch {
                            val phrase = "web支持"
                            "词组识别并手势点击（首个匹配）：$phrase".logAppend()
                            AssistsWindowManager.hideAll()
                            try {
                                delay(250L)
                                val clicked = runCatching {
                                    TextRecognitionChineseLocator.gestureClickFirstPhraseMatchOnScreen(
                                        targetText = phrase,
                                    )
                                }.getOrElse { e ->
                                    LogUtils.e(e)
                                    "词组识别并点击失败：${e.message}".logAppend()
                                    false
                                }
                                if (clicked) {
                                    "已在首个匹配区域中心派发点击手势。".logAppend()
                                } else {
                                    "未找到匹配或手势点击失败。".logAppend()
                                }
                            } finally {
                                AssistsWindowManager.showAll()
                            }
                        }
                    }
                    btnMpScreenTextPositions.setOnClickListener {
                        OverlayLog.show()
                        CoroutineWrapper.launch {
                            "开始 MP 录屏截图并识别文字位置…".logAppend()
                            val bitmap = captureScreenWithMp() ?: return@launch
                            try {
                                runCatching {
                                    TextRecognitionChineseLocator.getAllTextPositions(bitmap)
                                }.fold(
                                    onSuccess = { rec ->
                                        val previewCount = 20
                                        "文字块数量=${rec.positions.size} 耗时毫秒=${rec.processingTimeMillis} 全文长度=${rec.fullText.length}".logAppend()
                                        rec.positions.take(previewCount).forEachIndexed { index, pos ->
                                            "[${index + 1}] \"${pos.text}\" 左=${pos.left} 上=${pos.top} 右=${pos.right} 下=${pos.bottom} 宽=${pos.width} 高=${pos.height}".logAppend()
                                        }
                                        if (rec.positions.size > previewCount) {
                                            "… 另有 ${rec.positions.size - previewCount} 条已省略".logAppend()
                                        }
                                        "识别完成。".logAppend()
                                    },
                                    onFailure = { e ->
                                        LogUtils.e(e)
                                        "识别失败：${e.message}".logAppend()
                                    }
                                )
                            } finally {
                                if (!bitmap.isRecycled) {
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                    btnMpOcrClickPhraseBasic.setOnClickListener {
                        OverlayLog.show()
                        CoroutineWrapper.launch {
                            val phrase = "web支持"
                            "MP 录屏截图：词组识别并手势点击（首个匹配）：$phrase".logAppend()
                            // 截图阶段不恢复浮窗，识别并点击结束后再 showAll
                            val bitmap = captureScreenWithMp(restoreOverlaysAfterCapture = false)
                            if (bitmap == null) {
                                AssistsWindowManager.showAll()
                                return@launch
                            }
                            try {
                                runCatching {
                                    TextRecognitionChineseLocator.findWordPositions(bitmap, phrase)
                                }.fold(
                                    onSuccess = { recognition ->
                                        val first = recognition.targetPositions.firstOrNull()
                                        if (first == null) {
                                            "未找到匹配词组。".logAppend()
                                            return@fold
                                        }
                                        val cx = (first.left + first.right) / 2f
                                        val cy = (first.top + first.bottom) / 2f
                                        val ok = gestureClick(cx, cy, 25L)
                                        if (ok) {
                                            "已在首个匹配区域中心派发点击手势。".logAppend()
                                        } else {
                                            "已定位词组但手势点击失败。".logAppend()
                                        }
                                    },
                                    onFailure = { e ->
                                        LogUtils.e(e)
                                        "词组识别并点击失败：${e.message}".logAppend()
                                    }
                                )
                            } finally {
                                if (!bitmap.isRecycled) {
                                    bitmap.recycle()
                                }
                                AssistsWindowManager.showAll()
                            }
                        }
                    }

                }
            }
            return field
        }

    /**
     * 确保已授予录屏权限后截取一帧屏幕；失败时打日志并 toast。
     *
     * @param restoreOverlaysAfterCapture 为 true 时在截图流程结束时恢复浮窗；为 false 时由调用方在后续逻辑结束后再 [AssistsWindowManager.showAll]。
     */
    private suspend fun captureScreenWithMp(restoreOverlaysAfterCapture: Boolean = true): Bitmap? {
        if (!MPManager.isEnable) {
            val ok = MPManager.request(autoAllow = true, timeOut = 5000)
            if (!ok) {
                "获取屏幕录制权限失败或超时".logAppend()
                "获取屏幕录制权限失败或超时".overlayToast()
                return null
            }
        }
        AssistsWindowManager.hideAll()
        return try {
            delay(250L)
            runCatching { MPManager.takeScreenshot2Bitmap() }.getOrElse { e ->
                LogUtils.e(e)
                "MP 截图失败：${e.message}".logAppend()
                "请先授予录屏权限后重试".overlayToast()
                null
            } ?: run {
                "MP 截图返回空".logAppend()
                null
            }
        } finally {
            if (restoreOverlaysAfterCapture) {
                AssistsWindowManager.showAll()
            }
        }
    }

    private fun takeScreenshotAllImage() {
        CoroutineWrapper.launch(isMain = true) {
            runCatching {
                AssistsWindowManager.hideAll()
                delay(250)
                val screenshot = MPManager.takeScreenshot2Bitmap()
                screenshot ?: return@runCatching
                val list: ArrayList<String> = arrayListOf()
                AssistsCore.getAllNodes().forEach {
                    if (it.isImageView()) {
                        val file = it.takeScreenshot2File(screenshot)
                        file?.let { list.add(file.path) }
                    }
                }
                AssistsWindowManager.showAll()

                AssistsCore.launchApp(Intent(AssistsService.getOrNull(), ImageGalleryActivity::class.java).apply {
                    putStringArrayListExtra("extra_image_paths", list)
                })

                // 显示提示
                "已捕获 ${list.size} 张图片".overlayToast()
            }.onFailure {
                LogUtils.d(it)
                "截图失败，尝试请求授予屏幕录制后重试".overlayToast()
                AssistsWindowManager.showAll()

            }
        }

    }

    private val notificationListener = object : AssistsServiceListener {
        override fun onAccessibilityEvent(event: AccessibilityEvent) {
            super.onAccessibilityEvent(event)
            if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                event.text.forEach {
                    CoroutineWrapper.launch {
                        "监听到通知：${it}".logAppend()
                        "监听到通知：${it}".overlayToast()
                    }
                }
            }
        }
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
                    field = AssistsWindowWrapper(it.root, wmLayoutParams = AssistsWindowManager.createLayoutParams().apply {
                        width = (ScreenUtils.getScreenWidth() * 0.8).toInt()
                        height = (ScreenUtils.getScreenHeight() * 0.5).toInt()
                    }, onClose = this.onClose).apply {
                        minWidth = (ScreenUtils.getScreenWidth() * 0.6).toInt()
                        minHeight = (ScreenUtils.getScreenHeight() * 0.4).toInt()
                        initialCenter = true
                        viewBinding.tvTitle.text = "进阶示例"
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
}