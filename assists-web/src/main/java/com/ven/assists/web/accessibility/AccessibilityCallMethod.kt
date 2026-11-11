package com.ven.assists.web.accessibility

/**
 * 无障碍相关的方法常量定义
 */
object AccessibilityCallMethod {
    /**
     * 检查无障碍服务是否已开启
     */
    const val isA11yEnabled = "isA11yEnabled"

    /** 获取随选朗读服务及快捷方式状态 */
    const val getSelectToSpeakStatus = "getSelectToSpeakStatus"

    /** 打开指定的系统设置页 */
    const val openSystemSettings = "openSystemSettings"

    /**
     * 打开无障碍设置页面
     */
    const val openAccessibilitySetting = "openAccessibilitySetting"
}
