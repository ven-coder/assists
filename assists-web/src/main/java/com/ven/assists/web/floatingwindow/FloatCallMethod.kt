package com.ven.assists.web.floatingwindow

/**
 * 浮窗相关的方法常量定义
 */
object FloatCallMethod {
    /** 加载浮窗 */
    const val open = "open"
    /** 关闭浮窗 */
    const val close = "close"
    /** 设置浮窗标志位 */
    const val setFlags = "setFlags"
    /** 浮窗层 Toast */
    const val toast = "toast"
    /** 移动浮窗（传入 x,y 为移动距离） */
    const val move = "move"
    /** 刷新浮窗 view 配置 */
    const val refresh = "refresh"
}
