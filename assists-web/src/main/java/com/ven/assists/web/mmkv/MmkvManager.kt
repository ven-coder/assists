package com.ven.assists.web.mmkv

import android.content.Context
import android.util.Base64
import com.blankj.utilcode.util.PathUtils
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.tencent.mmkv.MMKV
import com.ven.assists.web.JavascriptInterfaceContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * MMKV 实例缓存、路径校验与 CRUD 封装
 */
object MmkvManager {

    /** 未传 mmkvId 时使用的默认存储名 */
    const val DEFAULT_MMKV_ID = "default"

    private val instances = ConcurrentHashMap<String, MMKV>()
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    @Volatile
    private var initialized = false

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val context = JavascriptInterfaceContext.requireContext()
            MMKV.initialize(context.applicationContext)
            initialized = true
        }
    }

    fun resolveMmkvId(mmkvId: String?): String {
        return mmkvId?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_MMKV_ID
    }

    fun validateRootPath(rootPath: String?): String? {
        if (rootPath.isNullOrBlank()) return null
        val file = File(rootPath)
        val normalized = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        val allowedBases = listOf(
            PathUtils.getInternalAppDataPath(),
            PathUtils.getExternalAppDataPath(),
        ).map { base ->
            runCatching { File(base).canonicalPath }.getOrDefault(File(base).absolutePath)
        }
        val allowed = allowedBases.any { base ->
            normalized == base || normalized.startsWith("$base${File.separator}")
        }
        if (!allowed) {
            throw IllegalArgumentException("MMKV rootPath is not allowed")
        }
        File(normalized).mkdirs()
        return normalized
    }

    private fun cacheKey(mmkvId: String, rootPath: String?): String {
        return if (rootPath.isNullOrBlank()) mmkvId else "$mmkvId|$rootPath"
    }

    private fun lockFor(key: String): ReentrantLock {
        return locks.computeIfAbsent(key) { ReentrantLock() }
    }

    fun open(mmkvId: String?, rootPath: String?): MMKV {
        ensureInitialized()
        val effectiveId = resolveMmkvId(mmkvId)
        val validatedRoot = validateRootPath(rootPath)
        val key = cacheKey(effectiveId, validatedRoot)
        return instances.computeIfAbsent(key) {
            if (validatedRoot.isNullOrBlank()) {
                MMKV.mmkvWithID(effectiveId)
            } else {
                MMKV.mmkvWithID(effectiveId, validatedRoot)
            } ?: throw IllegalStateException("Failed to open MMKV: $effectiveId")
        }
    }

    fun close(mmkvId: String?, rootPath: String?) {
        val effectiveId = resolveMmkvId(mmkvId)
        val validatedRoot = validateRootPath(rootPath)
        val key = cacheKey(effectiveId, validatedRoot)
        val lock = lockFor(key)
        lock.lock()
        try {
            instances.remove(key)?.close()
            locks.remove(key)
        } finally {
            lock.unlock()
        }
    }

    fun putString(mmkvId: String?, rootPath: String?, key: String, value: String?): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            mmkv.encode(key, value)
            successResult()
        }
    }

    fun getString(mmkvId: String?, rootPath: String?, key: String): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            if (!mmkv.containsKey(key)) {
                return@runWithLock valueResult(null)
            }
            valueResult(mmkv.decodeString(key))
        }
    }

    fun putBoolean(mmkvId: String?, rootPath: String?, key: String, value: Boolean): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            mmkv.encode(key, value)
            successResult()
        }
    }

    fun getBoolean(mmkvId: String?, rootPath: String?, key: String): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            if (!mmkv.containsKey(key)) {
                return@runWithLock valueResult(null)
            }
            valueResult(mmkv.decodeBool(key, false))
        }
    }

    fun putInt(mmkvId: String?, rootPath: String?, key: String, value: Int): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            mmkv.encode(key, value)
            successResult()
        }
    }

    fun getInt(mmkvId: String?, rootPath: String?, key: String): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            if (!mmkv.containsKey(key)) {
                return@runWithLock valueResult(null)
            }
            valueResult(mmkv.decodeInt(key, 0))
        }
    }

    fun putLong(mmkvId: String?, rootPath: String?, key: String, value: Long): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            mmkv.encode(key, value)
            successResult()
        }
    }

    fun getLong(mmkvId: String?, rootPath: String?, key: String): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            if (!mmkv.containsKey(key)) {
                return@runWithLock valueResult(null)
            }
            valueResult(mmkv.decodeLong(key, 0L))
        }
    }

    fun putFloat(mmkvId: String?, rootPath: String?, key: String, value: Float): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            mmkv.encode(key, value)
            successResult()
        }
    }

    fun getFloat(mmkvId: String?, rootPath: String?, key: String): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            if (!mmkv.containsKey(key)) {
                return@runWithLock valueResult(null)
            }
            valueResult(mmkv.decodeFloat(key, 0f))
        }
    }

    fun putDouble(mmkvId: String?, rootPath: String?, key: String, value: Double): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            mmkv.encode(key, value)
            successResult()
        }
    }

    fun getDouble(mmkvId: String?, rootPath: String?, key: String): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            if (!mmkv.containsKey(key)) {
                return@runWithLock valueResult(null)
            }
            valueResult(mmkv.decodeDouble(key, 0.0))
        }
    }

    fun putBytes(mmkvId: String?, rootPath: String?, key: String, valueBase64: String?): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            val bytes = if (valueBase64.isNullOrBlank()) {
                ByteArray(0)
            } else {
                Base64.decode(valueBase64, Base64.NO_WRAP)
            }
            mmkv.encode(key, bytes)
            successResult()
        }
    }

    fun getBytes(mmkvId: String?, rootPath: String?, key: String): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            if (!mmkv.containsKey(key)) {
                return@runWithLock valueResult(null)
            }
            val bytes = mmkv.decodeBytes(key)
            val base64 = if (bytes == null) null else Base64.encodeToString(bytes, Base64.NO_WRAP)
            valueResult(base64)
        }
    }

    fun remove(mmkvId: String?, rootPath: String?, key: String): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            mmkv.removeValueForKey(key)
            successResult()
        }
    }

    fun contains(mmkvId: String?, rootPath: String?, key: String): JsonObject {
        requireKey(key)
        return runWithLock(mmkvId, rootPath) { mmkv ->
            JsonObject().apply {
                addProperty("exists", mmkv.containsKey(key))
            }
        }
    }

    fun clearAll(mmkvId: String?, rootPath: String?): JsonObject {
        return runWithLock(mmkvId, rootPath) { mmkv ->
            mmkv.clearAll()
            successResult()
        }
    }

    fun allKeys(mmkvId: String?, rootPath: String?): JsonObject {
        return runWithLock(mmkvId, rootPath) { mmkv ->
            val keys = mmkv.allKeys()?.toList().orEmpty()
            JsonObject().apply {
                add("keys", JsonArray().apply { keys.forEach { add(it) } })
                addProperty("count", keys.size)
            }
        }
    }

    private fun requireKey(key: String) {
        if (key.isBlank()) {
            throw IllegalArgumentException("key must not be blank")
        }
    }

    private fun successResult(): JsonObject {
        return JsonObject().apply {
            addProperty("success", true)
        }
    }

    private fun valueResult(value: Any?): JsonObject {
        return JsonObject().apply {
            when (value) {
                null -> add("value", JsonNull.INSTANCE)
                is Boolean -> addProperty("value", value)
                is Number -> addProperty("value", value)
                is String -> addProperty("value", value)
                else -> addProperty("value", value.toString())
            }
        }
    }

    private fun runWithLock(
        mmkvId: String?,
        rootPath: String?,
        block: (MMKV) -> JsonObject,
    ): JsonObject {
        val effectiveId = resolveMmkvId(mmkvId)
        val validatedRoot = validateRootPath(rootPath)
        val key = cacheKey(effectiveId, validatedRoot)
        val lock = lockFor(key)
        lock.lock()
        try {
            val mmkv = open(effectiveId, validatedRoot)
            return block(mmkv)
        } finally {
            lock.unlock()
        }
    }
}
