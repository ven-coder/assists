package com.ven.assists.log

import com.blankj.utilcode.util.FileIOUtils
import com.blankj.utilcode.util.TimeUtils
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 日志读写：支持自定义目录/文件名；最大长度截断 + 按文件热流通知。
 * - [latestLine] / [entireLogText]：默认目标（[AssistsLogTarget.DEFAULT]）的 Flow；
 * - 自定义目标请使用 [latestLineFlow] / [entireLogTextFlow]。
 */
object AssistsLog {

    const val DEFAULT_MAX_FILE_LENGTH = 5000

    enum class LogStream {
        LATEST_LINE,
        ENTIRE_LOG_TEXT,
    }

    private val gson = Gson()

    private class LogFileState {
        val lock = Any()
        val latestLine = MutableStateFlow("")
        val entireLogText = MutableStateFlow("")
    }

    private val fileStates = ConcurrentHashMap<String, LogFileState>()
    private val defaultState = LogFileState()

    init {
        fileStates[AssistsLogPaths.resolveLogFilePath(AssistsLogTarget.DEFAULT)] = defaultState
    }

    /** 每次写入只推送本条内容（默认目标） */
    val latestLine: SharedFlow<String> = defaultState.latestLine.asStateFlow()

    /** 日志文件每次更新后的完整内容（默认目标） */
    val entireLogText: StateFlow<String> = defaultState.entireLogText.asStateFlow()

    fun latestLineFlow(target: AssistsLogTarget = AssistsLogTarget.DEFAULT): StateFlow<String> =
        stateFor(target).latestLine

    fun entireLogTextFlow(target: AssistsLogTarget = AssistsLogTarget.DEFAULT): StateFlow<String> =
        stateFor(target).entireLogText

    fun flowFor(target: AssistsLogTarget, stream: LogStream): StateFlow<String> = when (stream) {
        LogStream.LATEST_LINE -> latestLineFlow(target)
        LogStream.ENTIRE_LOG_TEXT -> entireLogTextFlow(target)
    }

    fun appendTimestampedEntry(
        message: CharSequence,
        target: AssistsLogTarget = AssistsLogTarget.DEFAULT,
    ): String {
        val existing = readAllText(target)
        val piece = buildString {
            if (existing.isNotEmpty()) append('\n')
            append(TimeUtils.getNowString())
            append('\n')
            append(message)
        }
        appendLine(piece, target = target)
        return message.toString()
    }

    fun appendLine(
        line: String,
        maxLength: Int = DEFAULT_MAX_FILE_LENGTH,
        target: AssistsLogTarget = AssistsLogTarget.DEFAULT,
    ) {
        val state = stateFor(target)
        synchronized(state.lock) {
            val file = AssistsLogPaths.resolveLogFile(target, ensureWritable = true)
            val existing = if (file.exists()) FileIOUtils.readFile2String(file) else ""
            var combined = existing + line
            if (combined.length > maxLength) {
                combined = combined.takeLast(maxLength)
            }
            FileIOUtils.writeFileFromString(file, combined, false)
            state.latestLine.tryEmit(line)
            state.entireLogText.value = combined
        }
    }

    fun readAllText(target: AssistsLogTarget = AssistsLogTarget.DEFAULT): String {
        val state = stateFor(target)
        synchronized(state.lock) {
            val file = AssistsLogPaths.resolveLogFile(target)
            if (!file.exists()) return ""
            return FileIOUtils.readFile2String(file) ?: ""
        }
    }

    fun refreshFromFile(target: AssistsLogTarget = AssistsLogTarget.DEFAULT) {
        val state = stateFor(target)
        synchronized(state.lock) {
            val file = AssistsLogPaths.resolveLogFile(target)
            val content = if (!file.exists()) "" else FileIOUtils.readFile2String(file) ?: ""
            state.entireLogText.value = content
        }
    }

    fun clear(target: AssistsLogTarget = AssistsLogTarget.DEFAULT) {
        val state = stateFor(target)
        synchronized(state.lock) {
            val file = AssistsLogPaths.resolveLogFile(target, ensureWritable = true)
            FileIOUtils.writeFileFromString(file, "", false)
            state.latestLine.tryEmit("")
            state.entireLogText.value = ""
        }
    }

    fun replaceAll(
        content: String,
        target: AssistsLogTarget = AssistsLogTarget.DEFAULT,
    ) {
        if (content.isEmpty()) {
            clear(target)
            return
        }
        val state = stateFor(target)
        synchronized(state.lock) {
            val file = AssistsLogPaths.resolveLogFile(target, ensureWritable = true)
            FileIOUtils.writeFileFromString(file, content, false)
            state.latestLine.tryEmit(content)
            state.entireLogText.value = content
        }
    }

    internal fun gson(): Gson = gson

    private fun stateFor(target: AssistsLogTarget): LogFileState {
        val key = AssistsLogPaths.resolveLogFilePath(target)
        return fileStates.getOrPut(key) { LogFileState() }
    }
}
