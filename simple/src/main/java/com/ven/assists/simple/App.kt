package com.ven.assists.simple

import android.app.Application
import com.blankj.utilcode.util.Utils
import com.ven.assists.stepper.StepManager

class App : Application() {

    companion object {
        const val TARGET_PACKAGE_NAME = "com.tencent.mm"
    }

    override fun onCreate() {
        super.onCreate()
        Utils.init(this)
        //设置全局步骤默认间隔时长
        StepManager.DEFAULT_STEP_DELAY = 1000L
    }
}