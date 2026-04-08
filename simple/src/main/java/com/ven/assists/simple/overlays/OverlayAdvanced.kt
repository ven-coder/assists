package com.ven.assists.simple.overlays

import android.annotation.SuppressLint
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.isVisible
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.containsText
import com.ven.assists.AssistsCore.getBoundsInScreen
import com.ven.assists.AssistsCore.getNodes
import com.ven.assists.AssistsCore.nodeGestureClick
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowManager.overlayToast
import com.ven.assists.window.AssistsWindowWrapper
import com.ven.assists.log.logAppend
import com.ven.assists.simple.databinding.AdvancedOverlayBinding
import com.ven.assists.simple.step.AntForestEnergy
import com.ven.assists.simple.step.GestureBottomTab
import com.ven.assists.simple.step.GestureScrollSocial
import com.ven.assists.simple.step.OpenWechatSocial
import com.ven.assists.simple.step.PublishSocial
import com.ven.assists.simple.step.ScrollContacts
import com.ven.assists.simple.step.StepTag
import com.ven.assists.simple.wechat.launchWechat
import com.ven.assists.stepper.StepManager
import com.ven.assists.stepper2.Step
import com.ven.assists.text.TextRecognitionChineseLocator
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.web.mlkit.MlkitScreenTextUtils
import kotlinx.coroutines.delay

@SuppressLint("StaticFieldLeak")
object OverlayAdvanced : AssistsServiceListener {


    var viewBinding: AdvancedOverlayBinding? = null
        private set
        get() {
            if (field == null) {
                field = AdvancedOverlayBinding.inflate(LayoutInflater.from(AssistsService.instance)).apply {
                    btnAnswerWechatCall.setOnClickListener {
                        if (!AssistsService.listeners.contains(answerWechatCallListener)) {
                            AssistsService.listeners.add(answerWechatCallListener)
                        }
                        OverlayLog.onClose = {
                            AssistsService.listeners.remove(answerWechatCallListener)
                        }
                        CoroutineWrapper.launch(isMain = true) {
                            OverlayLog.show()
                            "微信电话自动接听监听中...".logAppend()
                        }
                    }
                    btnOpenSocial.setOnClickListener {
                        OverlayLog.show()
                        StepManager.execute(OpenWechatSocial::class.java, StepTag.STEP_1, begin = true)

                    }
                    btnScrollSocial.setOnClickListener {
                        OverlayLog.show()

                        StepManager.execute(GestureScrollSocial::class.java, StepTag.STEP_1, begin = true)

                    }
                    btnPublishSocial.setOnClickListener {
                        OverlayLog.show()
                        StepManager.execute(PublishSocial::class.java, StepTag.STEP_1, begin = true)
                    }
                    btnClickBottomTab.setOnClickListener {
                        OverlayLog.show()

                        StepManager.execute(GestureBottomTab::class.java, StepTag.STEP_1, begin = true)

                    }
                    btnAntForestEnergy.setOnClickListener {
                        OverlayLog.show()

                        StepManager.execute(AntForestEnergy::class.java, StepTag.STEP_1, begin = true)

                    }
                    btnScrollContacts.setOnClickListener {
                        OverlayLog.show()
                        StepManager.execute(ScrollContacts::class.java, StepTag.STEP_1, begin = true)
                    }
                    if (!AppUtils.isAppDebug()) {
                        btnCheckWcUnread.isVisible = false
                    }
                    btnCheckWcUnread.setOnClickListener {
                        runCatching {
                            Step.run(impl = ::launchWechat)
                        }.onFailure {
                            LogUtils.d(it)
                        }
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
                            val phrase = "基础"
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
                }
            }
            return field
        }

    private val answerWechatCallListener: AssistsServiceListener = object : AssistsServiceListener {
        override fun onAccessibilityEvent(event: AccessibilityEvent) {
            super.onAccessibilityEvent(event)
            if (event.packageName == "com.tencent.mm") {
                var isInCall = false
                event.source?.getNodes()?.forEach {
                    if (it.containsText("邀请你语音通话") || it.containsText("邀请你视频通话")) {
                        it.getBoundsInScreen().let {
                            if (it.bottom < ScreenUtils.getScreenHeight() * 0.2) {
                                isInCall = true
                                StepManager.isStop = true
                            }
                            if (it.top > ScreenUtils.getScreenHeight() * 0.50 && it.bottom < ScreenUtils.getScreenHeight() * 0.8) {
                                isInCall = true
                                StepManager.isStop = true
                            }
                        }
                    }
                    if (isInCall && it.containsText("接听") && it.className == "android.widget.ImageButton") {
                        "收到微信电话，接听".overlayToast()
                        "收到微信电话，接听".logAppend()
                        it.click()
                    }
                    if (isInCall && it.containsText("接听") && it.className == "android.widget.Button") {
                        "收到微信电话，接听".overlayToast()
                        "收到微信电话，接听".logAppend()
                        CoroutineWrapper.launch { it.nodeGestureClick() }
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
                        viewBinding.tvTitle.text = "高级示例"
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