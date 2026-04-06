package com.ven.assists.web.floating

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

    /** 隐藏全部浮窗 */
    const val hideAll = "hideAll"
    /** 隐藏最顶层浮窗 */
    const val hideTop = "hideTop"
    /** 显示全部浮窗 */
    const val showAll = "showAll"
    /** 显示最顶层浮窗 */
    const val showTop = "showTop"
    /** 临时隐藏全部浮窗，超时后按快照恢复 */
    const val temporarilyHideAll = "temporarilyHideAll"
    /** 全部浮窗可触摸 */
    const val touchableByAll = "touchableByAll"
    /** 全部浮窗不可触摸 */
    const val nonTouchableByAll = "nonTouchableByAll"
    /** 移除最顶层浮窗并可选择显示下一层 */
    const val pop = "pop"
    /** 移除所有浮窗（需 confirm: true） */
    const val removeAllWindows = "removeAllWindows"
    /** 隐藏当前 Web 所在浮窗 */
    const val hideCurrent = "hideCurrent"
    /** 显示当前 Web 所在浮窗 */
    const val showCurrent = "showCurrent"
    /** 当前 Web 浮窗是否可见 */
    const val isCurrentVisible = "isCurrentVisible"
    /** 当前 Web 浮窗是否已在管理器中 */
    const val containsCurrent = "containsCurrent"
}
