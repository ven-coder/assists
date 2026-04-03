package com.ven.assists.log

/**
 * 带时间戳追加（同 [AssistsLog.appendTimestampedEntry]），便于链式：`"msg".logAppend()`、`123.logAppend()`。
 * - [CharSequence]：原样写入（不经 Gson 包一层 JSON 引号）；
 * - 其它类型：优先 Gson 序列化为 JSON，失败则用 [toString]；
 * - `null`：写入字面量 `null`。
 */
fun Any?.logAppend(): String {
    val message: CharSequence = when (this) {
        null -> "null"
        is CharSequence -> this
        else -> runCatching {
            AssistsLog.gson().toJson(this)
        }.getOrElse {
            toString()
        }
    }
    return AssistsLog.appendTimestampedEntry(message)
}

/**
 * 追加一条日志：
 * - [String]：原样写入（不经 Gson 包一层 JSON 引号，换行符也是真实换行）；
 * - 其它类型：优先 Gson 序列化为 JSON，失败则写入完整类名；null 写入 `null`。
 * 不自动换行；需要换行时在字符串中自行带 `\n`。
 */
fun Any?.log() {
    val line = when (this) {
        null -> "null"
        is String -> this
        else -> runCatching {
            AssistsLog.gson().toJson(this)
        }.getOrElse {
            this.javaClass.name
        }
    }
    AssistsLog.appendLine(line)
}
