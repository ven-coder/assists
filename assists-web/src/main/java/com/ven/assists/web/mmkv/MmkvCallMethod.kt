package com.ven.assists.web.mmkv

/**
 * MMKV 键值存储桥接方法名常量
 */
object MmkvCallMethod {
    const val putString = "putString"
    const val getString = "getString"
    const val putBoolean = "putBoolean"
    const val getBoolean = "getBoolean"
    const val putInt = "putInt"
    const val getInt = "getInt"
    const val putLong = "putLong"
    const val getLong = "getLong"
    const val putFloat = "putFloat"
    const val getFloat = "getFloat"
    const val putDouble = "putDouble"
    const val getDouble = "getDouble"
    const val putBytes = "putBytes"
    const val getBytes = "getBytes"
    const val remove = "remove"
    const val contains = "contains"
    const val clearAll = "clearAll"
    const val allKeys = "allKeys"
    const val close = "close"

    /** 需要 mmkvId / rootPath 参数的方法（供拦截器判断） */
    val pathAwareMethods = setOf(
        putString,
        getString,
        putBoolean,
        getBoolean,
        putInt,
        getInt,
        putLong,
        getLong,
        putFloat,
        getFloat,
        putDouble,
        getDouble,
        putBytes,
        getBytes,
        remove,
        contains,
        clearAll,
        allKeys,
        close,
    )
}
