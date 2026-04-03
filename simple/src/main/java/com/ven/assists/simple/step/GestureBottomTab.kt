package com.ven.assists.simple.step

import android.content.ComponentName
import android.content.Intent
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.getBoundsInScreen
import com.ven.assists.AssistsCore.nodeGestureClick
import com.ven.assists.service.AssistsService
import com.ven.assists.simple.App
import com.ven.assists.log.AssistsLog
import com.ven.assists.stepper.Step
import com.ven.assists.stepper.StepCollector
import com.ven.assists.stepper.StepImpl
import com.ven.assists.stepper.StepManager

class GestureBottomTab : StepImpl() {
    override fun onImpl(collector: StepCollector) {
        collector.next(StepTag.STEP_1) {
            AssistsLog.appendTimestampedEntry("启动微信")
            Intent().apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                component = ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                AssistsService.instance?.startActivity(this)
            }
            return@next Step.get(StepTag.STEP_2)
        }.next(StepTag.STEP_2) {
            AssistsCore.findByText("微信").forEach {
                val screen = it.getBoundsInScreen()
                if (screen.left > AssistsCore.getX(1080, 90) &&
                    screen.top > AssistsCore.getY(1920, 1850)
                ) {
                    AssistsLog.appendTimestampedEntry("点击【微信】")
                    it.nodeGestureClick()
                    return@next Step.get(StepTag.STEP_3)
                }
            }
            if (AssistsCore.getPackageName() == App.TARGET_PACKAGE_NAME) {
                AssistsCore.back()
                return@next Step.repeat

            }
            if (it.repeatCount == 5) {
                StepManager.execute(this::class.java, StepTag.STEP_1)
                return@next Step.get(StepTag.STEP_1)
            }

            return@next Step.repeat
        }.next(StepTag.STEP_3) {
            AssistsLog.appendTimestampedEntry("点击【通讯录】")
            AssistsCore.findByText("通讯录").forEach {
                val screen = it.getBoundsInScreen()
                if (screen.left > AssistsCore.getX(1080, 340) &&
                    screen.top > AssistsCore.getX(1920, 1850)
                ) {

                    it.nodeGestureClick()
                    return@next Step.get(StepTag.STEP_4)
                }
            }
            return@next Step.none
        }.next(StepTag.STEP_4) {
            AssistsLog.appendTimestampedEntry("点击【发现】")

            AssistsCore.findByText("发现").forEach {
                val screen = it.getBoundsInScreen()
                if (screen.left > AssistsCore.getX(1080, 630) &&
                    screen.top > AssistsCore.getX(1920, 1850)
                ) {
                    it.nodeGestureClick()
                    return@next Step.get(StepTag.STEP_5)
                }
            }
            return@next Step.none
        }.next(StepTag.STEP_5, isRunCoroutineIO = true) {
            AssistsLog.appendTimestampedEntry("点击【我】")

            AssistsCore.findByText("我").forEach {
                val screen = it.getBoundsInScreen()
                if (screen.left > AssistsCore.getX(1080, 920) &&
                    screen.top > AssistsCore.getX(1920, 1850)
                ) {
                    it.nodeGestureClick()
                    return@next Step.get(StepTag.STEP_2)
                }
            }
            return@next Step.none
        }
    }
}