package com.ven.assists.simple.step

import android.content.ComponentName
import android.content.Intent
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.getBoundsInScreen
import com.ven.assists.service.AssistsService
import com.ven.assists.simple.App
import com.ven.assists.log.AssistsLog
import com.ven.assists.stepper.Step
import com.ven.assists.stepper.StepCollector
import com.ven.assists.stepper.StepImpl

class OpenWechat : StepImpl() {
    override fun onImpl(collector: StepCollector) {
        collector.next(StepTag.STEP_1) {
            AssistsLog.appendTimestampedEntry("启动微信")
            Intent().apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                component = ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                AssistsService.getOrNull()?.startActivity(this)
            }
            return@next Step.get(StepTag.STEP_2)
        }.next(StepTag.STEP_2) {
            AssistsCore.findByText("通讯录").forEach {
                val screen = it.getBoundsInScreen()
                if (screen.left > AssistsCore.getX(1080, 340) &&
                    screen.top > AssistsCore.getX(1920, 1850)
                ) {
                    AssistsLog.appendTimestampedEntry("已打开微信主页")
                    it.parent.parent.click()
                    return@next Step.get(StepTag.STEP_3)
                }
            }
            if (AssistsCore.getPackageName() == App.TARGET_PACKAGE_NAME) {
                AssistsCore.back()
                return@next Step.get(StepTag.STEP_2)
            }
            if (it.repeatCount == 5) {
                return@next Step.get(StepTag.STEP_1)
            }

            return@next Step.repeat
        }
    }
}