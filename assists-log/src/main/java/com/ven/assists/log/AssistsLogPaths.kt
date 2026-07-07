package com.ven.assists.log

import com.blankj.utilcode.util.PathUtils
import java.io.File
import java.io.IOException

/**
 * 日志路径解析：诊断固定文件名 + 可自定义日志目录/文件名（后缀固定 .txt）
 */
object AssistsLogPaths {
    const val LOG_FILE_NAME = "assists_log.txt"
    const val DEFAULT_LOG_BASE_NAME = "assists_log"
    const val LOG_FILE_EXTENSION = ".txt"
    const val SCREENSHOT_FILE_NAME = "assists_screenshot.png"
    const val NODE_TREE_FILE_NAME = "assists_node_tree.json"

    /** 默认单文件路径（向后兼容） */
    fun logFile(): File = resolveLogFile(AssistsLogTarget.DEFAULT)

    /** 默认 PNG；其它格式传入对应扩展名，如 jpg、webp */
    fun screenshotFile(extension: String = "png"): File =
        File(PathUtils.getInternalAppFilesPath(), "assists_screenshot.$extension")

    fun screenshotFile(): File = screenshotFile("png")

    fun nodeTreeFile(): File = File(PathUtils.getInternalAppFilesPath(), NODE_TREE_FILE_NAME)

    /**
     * 解析日志文件绝对路径（不创建文件）。
     * [ensureWritable] 为 true 时校验目录可写（写入前使用）。
     */
    fun resolveLogFilePath(
        target: AssistsLogTarget = AssistsLogTarget.DEFAULT,
        ensureWritable: Boolean = false,
    ): String {
        val dir = resolveLogDir(target)
        if (ensureWritable) {
            ensureWritableDir(dir)
        }
        return File(dir, normalizeLogFileName(target.fileName)).absolutePath
    }

    fun resolveLogFile(
        target: AssistsLogTarget = AssistsLogTarget.DEFAULT,
        ensureWritable: Boolean = false,
    ): File = File(resolveLogFilePath(target, ensureWritable))

    fun resolveLogDir(target: AssistsLogTarget): File {
        val rawDirPath = target.dirPath?.trim()?.trimEnd('/')
        val dir = when {
            rawDirPath.isNullOrBlank() -> File(PathUtils.getInternalAppFilesPath())
            else -> {
                if (!File(rawDirPath).isAbsolute) {
                    throw IllegalArgumentException("dirPath must be absolute")
                }
                File(validateAllowedDirPath(rawDirPath))
            }
        }
        return dir
    }

    fun normalizeLogFileName(fileName: String?): String {
        val raw = fileName?.trim().orEmpty().ifBlank { DEFAULT_LOG_BASE_NAME }
        val withoutExt = if (raw.endsWith(LOG_FILE_EXTENSION, ignoreCase = true)) {
            raw.dropLast(LOG_FILE_EXTENSION.length)
        } else {
            raw
        }.trim().ifBlank { DEFAULT_LOG_BASE_NAME }
        return "$withoutExt$LOG_FILE_EXTENSION"
    }

    private fun validateAllowedDirPath(path: String): String {
        val file = File(path)
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
            throw IllegalArgumentException("log dir path not allowed")
        }
        return normalized
    }

    private fun ensureWritableDir(dir: File) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("log directory not writable: ${dir.absolutePath}")
        }
        if (!dir.isDirectory || !dir.canWrite()) {
            throw IOException("log directory not writable: ${dir.absolutePath}")
        }
    }
}
