package com.ven.assists.web

import android.content.Context
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.Utils
import com.ven.assists.service.AssistsService

/**
 * JavascriptInterface 统一的 Context 获取工具类
 * 优先级：AssistsService.instance > ActivityUtils.getTopActivity() > Utils.getApp()
 */
object JavascriptInterfaceContext {
    /**
     * 获取 Context
     * @return Context，如果都为空则返回 null
     */
    fun getContext(): Context? {
        return AssistsService.instance
            ?: ActivityUtils.getTopActivity()
            ?: Utils.getApp()
    }

    /**
     * 获取 Context，如果为空则抛出异常
     * @return Context
     * @throws IllegalStateException 如果所有方式都无法获取 Context
     */
    fun requireContext(): Context {
        return getContext() ?: throw IllegalStateException("无法获取 Context")
    }
}

