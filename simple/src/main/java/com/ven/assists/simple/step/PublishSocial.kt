package com.ven.assists.simple.step

import android.content.ComponentName
import android.content.Intent
import android.text.TextUtils
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.TimeUtils
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.findByTags
import com.ven.assists.AssistsCore.findFirstParentClickable
import com.ven.assists.AssistsCore.getBoundsInScreen
import com.ven.assists.AssistsCore.logNode
import com.ven.assists.AssistsCore.paste
import com.ven.assists.service.AssistsService
import com.ven.assists.simple.App
import com.ven.assists.log.AssistsLog
import com.ven.assists.stepper.Step
import com.ven.assists.stepper.StepCollector
import com.ven.assists.stepper.StepImpl
import kotlinx.coroutines.delay

class PublishSocial : StepImpl() {
    override fun onImpl(collector: StepCollector) {
        collector.next(StepTag.STEP_1) { it ->
            AssistsLog.appendTimestampedEntry("启动微信")
            Intent().apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                component = ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                AssistsService.getOrNull()?.startActivity(this)
            }
            it.data?.let {
                AssistsLog.appendTimestampedEntry("PublishSocial STEP_1 收到数据：$it")
            }
            return@next Step.get(StepTag.STEP_2, data = "字符串数据：2")
        }.next(StepTag.STEP_2) {

            it.data?.let {
                AssistsLog.appendTimestampedEntry("收到数据：$it")
            }
            AssistsCore.findByText("发现").forEach {
                val screen = it.getBoundsInScreen()
                if (screen.left > 630 && screen.top > 1850) {
                    AssistsLog.appendTimestampedEntry("已打开微信主页，点击【发现】")
                    it.parent.parent.click()
                    return@next Step.get(StepTag.STEP_3, data = "字符串数据：3333")

                }
            }

            if (AssistsCore.getPackageName() == App.TARGET_PACKAGE_NAME) {
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_2, data = "字符串数据：22222")
            }

            if (it.repeatCount == 5) {
                return@next Step.get(StepTag.STEP_1, data = "字符串数据：1111111")
            }

            return@next Step.repeat
        }.next(StepTag.STEP_3) {

            it.data?.let {
                AssistsLog.appendTimestampedEntry("收到数据：$it")
            }
            AssistsCore.findByText("朋友圈").forEach {
                it.logNode()
                val screen = it.getBoundsInScreen()
                if (screen.left > 140 && screen.top > 240) {
                    AssistsLog.appendTimestampedEntry("点击朋友圈")
                    it.findFirstParentClickable()?.let {
                        it.logNode()
                        it.click()
                    }
                }
                return@next Step.get(StepTag.STEP_4)
            }
            return@next Step.none
        }.next(StepTag.STEP_4) {
            AssistsCore.findByText("朋友圈封面，再点一次可以改封面").forEach {
                AssistsLog.appendTimestampedEntry("已进入朋友圈")
                return@next Step.get(StepTag.STEP_5)
            }
            AssistsCore.findByText("朋友圈封面，点按两次修改封面").forEach {
                AssistsLog.appendTimestampedEntry("已进入朋友圈")
                return@next Step.get(StepTag.STEP_5)

            }
            if (it.repeatCount == 5) {
                AssistsLog.appendTimestampedEntry("未进入朋友圈")
            }
            return@next Step.repeat
        }.next(StepTag.STEP_5) {
            runIO { delay(2000) }
            AssistsCore.findByText("拍照，记录生活").firstOrNull()?.let {
                AssistsCore.findByText("我知道了").firstOrNull()?.click()
                runIO {
                    delay(1000)
                }
            }

            AssistsLog.appendTimestampedEntry("点击拍照分享按钮")
            AssistsCore.findByText("拍照分享").forEach {
                it.click()
                return@next Step.get(StepTag.STEP_6)
            }
            return@next Step.none
        }.next(StepTag.STEP_6) {
            AssistsLog.appendTimestampedEntry("从相册选择")
            AssistsCore.findByText("从相册选择").forEach {
                it.findFirstParentClickable()?.let {
                    it.click()
                    return@next Step.get(StepTag.STEP_7)
                }
            }
            return@next Step.none
        }.next(StepTag.STEP_7) {
            AssistsCore.findByText("我知道了").forEach {
                it.click()
                return@next Step.get(StepTag.STEP_7)
            }
            AssistsCore.findByText("权限申请").forEach {
                AssistsCore.findByText("确定").forEach {
                    it.click()
                    return@next Step.get(StepTag.STEP_7)
                }
            }
            AssistsCore.findByText("允许").forEach {
                it.click()
                return@next Step.get(StepTag.STEP_8)
            }
            AssistsLog.appendTimestampedEntry("选择第一张相片")
            return@next Step.get(StepTag.STEP_8)
        }.next(StepTag.STEP_8) {
            AssistsCore.findByTags("android.support.v7.widget.RecyclerView").forEach {
                for (index in 0 until it.childCount) {
                    if (TextUtils.equals("android.widget.RelativeLayout", it.getChild(index).className)) {
                        it.getChild(index).let { child ->
                            child.findByTags("android.widget.TextView").firstOrNull() ?: let {
                                child.findByTags("android.widget.CheckBox").forEach { it.click() }
                                return@next Step.get(StepTag.STEP_9)
                            }
                        }
                    }
                }
            }
            AssistsCore.findByTags("androidx.recyclerview.widget.RecyclerView").forEach {
                for (index in 0 until it.childCount) {
                    if (TextUtils.equals("android.widget.RelativeLayout", it.getChild(index).className)) {
                        it.getChild(index).let { child ->
                            child.findByTags("android.widget.TextView").firstOrNull() ?: let {
                                child.findByTags("android.widget.CheckBox").forEach { it.click() }
                                return@next Step.get(StepTag.STEP_9)
                            }
                        }

                    }
                }
            }
            return@next Step.none
        }.next(StepTag.STEP_9) {
            AssistsLog.appendTimestampedEntry("点击完成")
            AssistsCore.findByText("完成").forEach {
                it.click()
                return@next Step.get(StepTag.STEP_10)

            }
            return@next Step.none

        }.next(StepTag.STEP_10) {
            AssistsLog.appendTimestampedEntry("输入发表内容")
            AssistsCore.findByTags("android.widget.EditText").forEach {
                it.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                it.paste("${TimeUtils.getNowString()}: Assists发的一条私密朋友圈")
                return@next Step.get(StepTag.STEP_11)

            }
            return@next Step.none

        }.next(StepTag.STEP_11) {
            AssistsLog.appendTimestampedEntry("点击谁可以看")
            AssistsCore.findByText("谁可以看").forEach {
                it.findFirstParentClickable()?.let { it.click() }
                return@next Step.get(StepTag.STEP_12)
            }
            return@next Step.none

        }.next(StepTag.STEP_12) {
            AssistsLog.appendTimestampedEntry("点击仅自己可见")
            AssistsCore.findByText("仅自己可见").forEach {
                it.findFirstParentClickable()?.let { it.click() }
                return@next Step.get(StepTag.STEP_13)
            }
            return@next Step.none

        }.next(StepTag.STEP_13) {
            AssistsLog.appendTimestampedEntry("点击完成")
            AssistsCore.findByText("完成").forEach {
                it.click()
                return@next Step.get(StepTag.STEP_14)
            }
            return@next Step.none

        }.next(StepTag.STEP_14) {
            AssistsLog.appendTimestampedEntry("点击发表")
            AssistsCore.findByText("发表").forEach {
                it.click()
            }
            return@next Step.none
        }
    }

}