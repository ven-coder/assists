package com.ven.assists.log

import com.blankj.utilcode.util.FileIOUtils
import com.blankj.utilcode.util.TimeUtils
import com.google.gson.Gson
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 单文件日志：最大长度截断 + 热流通知。
 * - [latestLine]：每次只发射本次追加/覆盖的片段；
 * - [entireLogText]：每次文件内容变化后发射**当前整份**正文（截断后仍为当前文件全文）。
 */
object AssistsLog {

    const val DEFAULT_MAX_FILE_LENGTH = 5000

    private val gson = Gson()
    private val lock = Any()

    private val _latestLine = MutableStateFlow("")

    /** 每次写入只推送本条内容；replay=1 时新订阅者先收到上一次一条（若有） */
    val latestLine: SharedFlow<String> = _latestLine.asStateFlow()

    private val _entireLogText = MutableStateFlow("")
    /** 日志文件每次更新后的完整内容（与 [readAllText] 一致）；新订阅者先收到当前快照 */
    val entireLogText: StateFlow<String> = _entireLogText.asStateFlow()

    /**
     * 追加一条带当前时间戳的条目（非空时在条目前加换行，与条目内为「时间\\n正文」），返回 [message] 字符串形式
     */
    fun appendTimestampedEntry(message: CharSequence): String {
        val existing = readAllText()
        val piece = buildString {
            if (existing.isNotEmpty()) append('\n')
            append(TimeUtils.getNowString())
            append('\n')
            append(message)
        }
        appendLine(piece)
        return message.toString()
    }

    /**
     * 将内容原样追加到日志文件（不自动添加换行，需换行时由调用方在 [line] 中自行包含）；
     * 超过 [maxLength] 时从头部丢弃最旧字符
     */
    fun appendLine(line: String, maxLength: Int = DEFAULT_MAX_FILE_LENGTH) {
        synchronized(lock) {
            val file = AssistsLogPaths.logFile()
            file.parentFile?.mkdirs()
            val existing = if (file.exists()) FileIOUtils.readFile2String(file) else ""
            var combined = existing + line
            if (combined.length > maxLength) {
                combined = combined.takeLast(maxLength)
            }
            FileIOUtils.writeFileFromString(file, combined, false)
            _latestLine.tryEmit(line)
            _entireLogText.value = combined
        }
    }

    /**
     * 读取当前日志文件中的全部文本；文件不存在时返回空字符串
     */
    fun readAllText(): String {
        synchronized(lock) {
            val file = AssistsLogPaths.logFile()
            if (!file.exists()) return ""
            return FileIOUtils.readFile2String(file) ?: ""
        }
    }

    /**
     * 从日志文件重新读入并更新 [entireLogText]（与磁盘不一致时用于刷新展示）
     */
    fun refreshFromFile() {
        synchronized(lock) {
            val file = AssistsLogPaths.logFile()
            val content = if (!file.exists()) "" else FileIOUtils.readFile2String(file) ?: ""
            _entireLogText.value = content
        }
    }

    /**
     * 清空日志文件，并推送空串到 [latestLine]、[entireLogText]
     */
    fun clear() {
        synchronized(lock) {
            val file = AssistsLogPaths.logFile()
            file.parentFile?.mkdirs()
            FileIOUtils.writeFileFromString(file, "", false)
            _latestLine.tryEmit("")
            _entireLogText.value = ""
        }
    }

    /**
     * 用 [content] 完全覆盖日志文件（不做长度截断；需要限制长度时请自行处理 [content]）
     * [content] 为空时等价于 [clear]
     */
    fun replaceAll(content: String) {
        if (content.isEmpty()) {
            clear()
            return
        }
        synchronized(lock) {
            val file = AssistsLogPaths.logFile()
            file.parentFile?.mkdirs()
            FileIOUtils.writeFileFromString(file, content, false)
            _latestLine.tryEmit(content)
            _entireLogText.value = content
        }
    }

    internal fun gson(): Gson = gson
}
