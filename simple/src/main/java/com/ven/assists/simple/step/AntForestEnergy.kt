package com.ven.assists.simple.step

import com.blankj.utilcode.util.AppUtils
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.containsText
import com.ven.assists.AssistsCore.findById
import com.ven.assists.AssistsCore.findByText
import com.ven.assists.AssistsCore.findFirstParentClickable
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.simple.CaptureLayout
import com.ven.assists.log.AssistsLog
import com.ven.assists.log.logAppend
import com.ven.assists.stepper.Step
import com.ven.assists.stepper.StepCollector
import com.ven.assists.stepper.StepImpl
import com.ven.assists.stepper.StepManager
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.mp.MPManager
import com.ven.assists.opcv.OpencvWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar


class AntForestEnergy : StepImpl(), AssistsServiceListener {

    val targetPkg = "com.eg.android.AlipayGphone"

    override fun onImpl(collector: StepCollector) {
        if (!AssistsService.listeners.contains(this)) {
            AssistsService.listeners.add(this)
        }
        collector.next(StepTag.STEP_1) {
            if (MPManager.isEnable) {
                overLog("屏幕录制已开启")
                return@next Step.get(StepTag.STEP_2)
            }
            overLog("开启屏幕录制")
            val result = MPManager.request(autoAllow = true)
            if (result) {
                return@next Step.get(StepTag.STEP_2)
            }
            "未成功自动开启屏幕录制".logAppend()
            return@next Step.none
        }.next(StepTag.STEP_2) {
            overLog("启动支付宝")
            AppUtils.launchApp(targetPkg)
            return@next Step.get(StepTag.STEP_3)
        }.next(StepTag.STEP_3, isRunCoroutineIO = true) {
            val packageName = AssistsCore.getPackageName()
            if (packageName == targetPkg) {
                //检查是否在首页
                AssistsCore.findById("com.alipay.android.tablauncher:id/tab_bar_container_fl").firstOrNull()?.let { tab_bar_container_fl ->
                    //确定在首页

                    AssistsCore.findByTags("android.support.v7.widget.RecyclerView")
                        .firstOrNull()
                        ?.findByText("蚂蚁森林")
                        ?.firstOrNull()
                        ?.findFirstParentClickable()?.let {
                            overLog("点击蚂蚁森林")
                            it.click()
                            delay(1000)
                            while (true) {
                                AssistsCore.findById("com.alipay.mobile.nebulax.integration:id/relativeLayout_content").firstOrNull()?.let {
                                    if (it.findByText("返回").firstOrNull() != null && it.findByText("蚂蚁森林").firstOrNull() != null) {

                                        var await = false
                                        AssistsCore.getAllNodes().forEach {
                                            if (it.containsText("稍等片刻...")) {
                                                await = true;
                                                overLog("等待加载...")
                                                delay(500)
                                            }
                                        }

                                        if (!await) {
                                            return@next Step.get(StepTag.STEP_4)
                                        }

                                    } else {
                                        overLog("未进入蚂蚁森林，重新进入...")
                                        return@next Step.repeat
                                    }
                                }
                            }
                            return@next Step.none
                        } ?: let {
                        //未找到蚂蚁森林，点击首页
                        tab_bar_container_fl.findById("android:id/tabs").firstOrNull()?.let {
                            overLog("点击首页")
                            it.click()
                        }
                        return@next Step.repeat
                    }
                } ?: let {
                    overLog("返回")
                    AssistsCore.back()
                    return@next Step.repeat
                }
            } else {
                //未打开支付宝，重新执行步骤2
                overLog("未成功启动支付宝，重新启动")
                return@next Step.get(StepTag.STEP_2)
            }
        }.next(StepTag.STEP_4, isRunCoroutineIO = true) {
            overLog("开始识别能量球...")
            delay(500)
            withContext(Dispatchers.Main) {
                AssistsWindowManager.hideAll()
            }
            delay(500)
            val screenMat = OpencvWrapper.getScreenMat()
            if (screenMat == null) {
                overLog("识别失败，无法获取屏幕图像")
                return@next Step.none
            }
            runMain {
                AssistsWindowManager.showAll()
            }
            val capBeginY = (screenMat.height() * 0.2).toInt()
            val capEndY = screenMat.height() * 0.18
            val capMat = Mat(screenMat, Rect(0, capBeginY, screenMat.width(), capEndY.toInt()))

            //注意，模板截取时需要以当前手机设备分辨率一致大小的情况下截取，否则影响匹配准确率
            val temp3 = OpencvWrapper.getTemplateFromAssets("temp3.jpg") ?: Mat()
            val lowerGreen = Scalar(35.0, 100.0, 100.0)
            val upperGreen = Scalar(85.0, 255.0, 255.0)
            val textRect = Rect(temp3.width() / 2 - 85 / 2, temp3.height() / 2 - 85 / 2, 85, 60)
            //生成掩膜，仅匹配指定区域
            val sphereMask = OpencvWrapper.createMask(
                temp3,
                lowerGreen,
                upperGreen,
                arrayListOf(textRect),
                arrayListOf(textRect.clone().apply {
                    width = 40
                    height = 25
                })
            )
            val temp3Result = OpencvWrapper.matchTemplate(capMat, temp3, sphereMask) ?: Mat()


            val points = OpencvWrapper.getResultWithThreshold(temp3Result, 0.98, ignoreX = temp3.width() * 0.5)

            runMain {
                AssistsService.getOrNull()?.let { it ->
                    AssistsWindowManager.add(CaptureLayout(it).apply {
                        points.forEach {
                            it.y += capBeginY
                            addPoint(it, temp3.width(), temp3.height())
                            invalidate()
                        }

                    }, AssistsWindowManager.createLayoutParams(), isStack = true)
                }
            }
            if (points.isEmpty()) {
                overLog("暂无可收集的能量球")
                runMain { AssistsWindowManager.pop() }
                delay(500)
                overLog("即将开始找能量...")
            } else {
                delay(500)
                overLog("开始点击能量球")
                runMain { AssistsWindowManager.nonTouchableByAll() }
                delay(500)
                points.forEach {
                    overLog("点击第 ${points.indexOf(it) + 1} 个能量球（${it.x + temp3.width() / 2},${it.y + temp3.height() / 2}）")
                    runMain { AssistsWindowManager.nonTouchableByAll() }
                    delay(250)
                    AssistsCore.gestureClick((it.x + temp3.width() / 2).toFloat(), (it.y + temp3.height() / 2).toFloat())
                    delay(250)
                    runMain { AssistsWindowManager.touchableByAll() }
                    delay(250)
                }
                overLog("能量球点击完毕")
                delay(250)
                runMain {
                    AssistsWindowManager.pop()
                }
            }
            val templateMat = OpencvWrapper.getTemplateFromAssets("find_more.jpg")
            templateMat?.let {
                val mask = OpencvWrapper.createMask(it, Scalar(10.0, 100.0, 100.0), Scalar(30.0, 255.0, 255.0))
                val result = OpencvWrapper.matchTemplateFromScreenToMinMaxLoc(screenMat, it, mask)
                result?.let {
                    if (it.maxVal > 0.99) {
                        val point = it.maxLoc
                        runMain {
                            AssistsService.getOrNull()?.let {
                                AssistsWindowManager.add(CaptureLayout(it).apply {
                                    addPoint(point, templateMat.width(), templateMat.height())
                                    invalidate()
                                }, AssistsWindowManager.createLayoutParams(), isStack = true)
                            }
                        }
                        runMain { AssistsWindowManager.nonTouchableByAll() }
                        delay(250)
                        overLog("点击找能量")
                        delay(250)
                        runMain {
                            AssistsCore.gestureClick((point.x + templateMat.width() / 2f).toFloat(), (point.y + templateMat.height() / 2f).toFloat())
                        }
                        delay(250)
                        runMain {
                            AssistsWindowManager.pop()
                        }
                        overLog("开始收集能量")
                        delay(1000)
                        return@next Step.get(StepTag.STEP_4)
                    } else {
                        overLog("未匹配到找能量入口，已停止")
                        return@next Step.none
                    }
                }

            }
            return@next Step.repeat
        }
    }

    private fun overLog(value: String) {
        AssistsLog.appendTimestampedEntry(value)
    }

    override fun screenCaptureEnable() {
        CoroutineWrapper.launch {
            overLog("屏幕录制已开启")
            StepManager.execute(AntForestEnergy::class.java, StepTag.STEP_2)
        }
    }
}