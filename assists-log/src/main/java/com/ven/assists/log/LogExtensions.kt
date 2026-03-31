package com.ven.assists.log

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
