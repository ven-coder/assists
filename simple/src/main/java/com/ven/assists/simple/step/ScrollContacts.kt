package com.ven.assists.simple.step

import android.content.ComponentName
import android.content.Intent
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.findFirstParentClickable
import com.ven.assists.AssistsCore.getBoundsInScreen
import com.ven.assists.AssistsCore.scrollForward
import com.ven.assists.service.AssistsService
import com.ven.assists.simple.App
import com.ven.assists.log.AssistsLog
import com.ven.assists.log.logAppend
import com.ven.assists.stepper.Step
import com.ven.assists.stepper.StepCollector
import com.ven.assists.stepper.StepImpl

/**
 * ScrollContacts为该业务场景的分类，作用是可在执行过程按按业务分类来执行
 */
class ScrollContacts : StepImpl() {
    override fun onImpl(collector: StepCollector) {
        //1. 打开微信
        collector.next(StepTag.STEP_1, isRunCoroutineIO = true) {//Step.STEP_1为自己定义的常量
            AssistsLog.appendTimestampedEntry("启动微信")
            Intent().apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                component = ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                AssistsService.getOrNull()?.startActivity(this)
            }
            //执行下一步Step.STEP_2，this::class.java为当前StepImpl实现类的步骤逻辑，如果传其他的StepImpl就会执行指定的StepImpl逻辑
            return@next Step.get(StepTag.STEP_2)

        }.next(StepTag.STEP_2, isRunCoroutineIO = true) {//2. 定义Step.STEP_2逻辑，nextLoop该方法会在指定时间内按指定间隔循环执行
            //按文本查找元素
            AssistsCore.findByText("通讯录").forEach {
                val screen = it.getBoundsInScreen()
                if (screen.left > AssistsCore.getX(1080, 340) &&
                    screen.top > AssistsCore.getX(1920, 1850)
                ) {
                    AssistsLog.appendTimestampedEntry("已打开微信主页，点击【通讯录】")
                    it.findFirstParentClickable()?.click()
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
        }.next(StepTag.STEP_3, isRunCoroutineIO = true) { step ->//3. 定义Step.STEP_3逻辑
            AssistsCore.findByTags("android.widget.ListView").forEach {
                val screen = it.getBoundsInScreen()
                if (screen.left >= 0 && screen.left < AssistsCore.getX(1080, 1080) &&
                    screen.right >= AssistsCore.getX(1080, 1080)
                ) {
                    val result = it.scrollForward()
                    if (!result) {
                        "已滚动到底部".logAppend()
                        return@next Step.none
                    }
                    "继续滚动".logAppend()

                    return@next Step.get(StepTag.STEP_3, delay = 500)
                }

            }
            AssistsCore.findByTags("androidx.recyclerview.widget.RecyclerView").forEach {
                val screen = it.getBoundsInScreen()
                if (screen.left >= 0 && screen.left < AssistsCore.getX(1080, 1080) &&
                    screen.right >= AssistsCore.getX(1080, 1080)
                ) {
                    val result = it.scrollForward()
                    if (!result) {
                        "已滚动到底部".logAppend()
                        return@next Step.none
                    }
                    "继续滚动".logAppend()

                    return@next Step.get(StepTag.STEP_3, delay = 500)
                }
            }
            return@next Step.none
        }
    }
}