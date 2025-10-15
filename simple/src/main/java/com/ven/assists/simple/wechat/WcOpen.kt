package com.ven.assists.simple.wechat

import com.ven.assists.AssistsCore
import com.ven.assists.stepperx.Step

suspend fun launchWechat(step: Step): Step? {
    AssistsCore.launchApp("com.tencent.mm")
    return step.next(::wcCheckHomePage)
}

suspend fun wcCheckHomePage(step: Step): Step? {

    return step.repeat()
}