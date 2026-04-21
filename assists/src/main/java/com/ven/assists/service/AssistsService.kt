package com.ven.assists.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.blankj.utilcode.util.LogUtils
import com.ven.assists.AssistsCore
import com.ven.assists.window.AssistsWindowManager
import java.util.Collections

/**
 * 无障碍服务核心类
 * 负责处理无障碍服务的生命周期和事件分发
 * 提供全局服务实例访问和监听器管理功能
 *
 * 系统对无障碍服务本身即为单连接，业务侧仍需持有引用以便在非 Service 代码中使用。
 * 使用伴生对象内私有引用与 [getOrNull] 封装，避免对外暴露可写静态字段。
 */
open class AssistsService : AccessibilityService() {
    companion object {
        @Volatile
        private var serviceRef: AssistsService? = null

        /**
         * 当前已连接的无障碍服务实例（未启动或未连接时为 null）
         */
        @JvmStatic
        fun getOrNull(): AssistsService? = serviceRef

        /**
         * 全局服务实例
         * 用于在应用中获取无障碍服务实例
         * 当服务未启动或被销毁时为null
         */
        @Deprecated(
            message = "请使用 getOrNull()",
            replaceWith = ReplaceWith(
                expression = "AssistsService.getOrNull()",
                imports = ["com.ven.assists.service.AssistsService"],
            ),
        )
        val instance: AssistsService?
            get() = serviceRef

        /**
         * 服务监听器列表
         * 使用线程安全的集合存储所有监听器
         * 用于分发服务生命周期和无障碍事件
         */
        val listeners: MutableList<AssistsServiceListener> = Collections.synchronizedList(arrayListOf<AssistsServiceListener>())

//        val onAccessibilityEventFlow =
//            MutableSharedFlow<AccessibilityEvent>(replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
//        val onServiceConnectedFlow =
//            MutableSharedFlow<AssistsService>(replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    /**
     * 服务创建时调用
     * 初始化全局服务实例
     */
    override fun onCreate() {
        super.onCreate()
        serviceRef = this
    }

    /**
     * 服务连接成功时调用
     * 初始化服务实例和窗口管理器
     * 通知所有监听器服务已连接
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceRef = this
        AssistsWindowManager.init(this)
        runCatching { listeners.forEach { it.onServiceConnected(this) } }
        LogUtils.d(AssistsCore.LOG_TAG, "assists service on service connected")
    }

    /**
     * 接收无障碍事件
     * 更新服务实例并分发事件给所有监听器
     * @param event 无障碍事件对象
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        serviceRef = this
        runCatching { listeners.forEach { it.onAccessibilityEvent(event) } }
    }

    /**
     * 服务解绑时调用
     * 清除服务实例并通知所有监听器
     * @param intent 解绑的Intent
     * @return 是否调用父类的onUnbind方法
     */
    override fun onUnbind(intent: Intent?): Boolean {
        serviceRef = null
        runCatching { listeners.forEach { it.onUnbind() } }
        return super.onUnbind(intent)
    }

    /**
     * 服务中断时调用
     * 通知所有监听器服务已中断
     */
    override fun onInterrupt() {
        runCatching { listeners.forEach { it.onInterrupt() } }
    }
}