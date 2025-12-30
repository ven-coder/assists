package com.ven.assists.web.network

import com.blankj.utilcode.util.LogUtils
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * OkHttpClient 单例管理器
 * 提供统一的 OkHttpClient 实例，支持动态配置和重置
 */
object OkHttpClientManager {
    @Volatile
    private var client: OkHttpClient? = null

    // 默认配置
    private var defaultConnectTimeout: Long = 30L
    private var defaultReadTimeout: Long = 30L
    private var defaultWriteTimeout: Long = 30L

    /**
     * 获取 OkHttpClient 实例（单例）
     * 如果未配置则使用默认配置创建
     */
    fun getClient(): OkHttpClient {
        return client ?: synchronized(this) {
            client ?: createDefaultClient().also { client = it }
        }
    }

    /**
     * 配置 OkHttpClient
     * @param connectTimeout 连接超时时间（秒），null 表示使用默认值
     * @param readTimeout 读取超时时间（秒），null 表示使用默认值
     * @param writeTimeout 写入超时时间（秒），null 表示使用默认值
     */
    fun configure(
        connectTimeout: Long? = null,
        readTimeout: Long? = null,
        writeTimeout: Long? = null
    ) {
        synchronized(this) {
            val builder = OkHttpClient.Builder()

            val finalConnectTimeout = connectTimeout ?: defaultConnectTimeout
            val finalReadTimeout = readTimeout ?: defaultReadTimeout
            val finalWriteTimeout = writeTimeout ?: defaultWriteTimeout

            builder.connectTimeout(finalConnectTimeout, TimeUnit.SECONDS)
            builder.readTimeout(finalReadTimeout, TimeUnit.SECONDS)
            builder.writeTimeout(finalWriteTimeout, TimeUnit.SECONDS)

            client = builder.build()

            LogUtils.d("OkHttpClient configured: connectTimeout=$finalConnectTimeout, readTimeout=$finalReadTimeout, writeTimeout=$finalWriteTimeout")
        }
    }

    /**
     * 重置 OkHttpClient 为默认配置
     */
    fun reset() {
        synchronized(this) {
            client = createDefaultClient()
            LogUtils.d("OkHttpClient reset to default")
        }
    }

    /**
     * 设置默认超时时间（用于后续创建新实例）
     */
    fun setDefaultTimeouts(
        connectTimeout: Long = 30L,
        readTimeout: Long = 30L,
        writeTimeout: Long = 30L
    ) {
        synchronized(this) {
            defaultConnectTimeout = connectTimeout
            defaultReadTimeout = readTimeout
            defaultWriteTimeout = writeTimeout
        }
    }

    /**
     * 创建默认的 OkHttpClient
     */
    private fun createDefaultClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(defaultConnectTimeout, TimeUnit.SECONDS)
            .readTimeout(defaultReadTimeout, TimeUnit.SECONDS)
            .writeTimeout(defaultWriteTimeout, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 获取当前配置信息
     */
    fun getConfig(): JsonObject {
        val currentClient = client ?: createDefaultClient()
        return JsonObject().apply {
            addProperty("connectTimeout", defaultConnectTimeout)
            addProperty("readTimeout", defaultReadTimeout)
            addProperty("writeTimeout", defaultWriteTimeout)
        }
    }
}

