package com.ven.assists.simple.step

import com.ven.assists.stepper2.Step

object WxUnfollow {


    val next1: (suspend (Step) -> Step?) = aaa@{step->

        return@aaa step.next(next2)
    }

    val next2: (suspend (Step) -> Step?) = {

        null
    }
}