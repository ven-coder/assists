package com.ven.assists.log

import com.blankj.utilcode.util.PathUtils
import java.io.File
import java.io.IOException

/**
 * 日志路径解析：日志与伴生文件（截图、节点树）共用目录与基础文件名，扩展名各自独立。
 */
object AssistsLogPaths {
    const val LOG_FILE_NAME = "log-default.txt"
    const val DEFAULT_LOG_BASE_NAME = "log-default"
    const val LOG_FILE_EXTENSION = ".txt"
    const val SCREENSHOT_FILE_NAME = "log-default.png"
    const val NODE_TREE_FILE_NAME = "log-default.json"

    /** 默认单文件路径（向后兼容） */
    fun logFile(): File = resolveLogFile(AssistsLogTarget.DEFAULT)

    /** 默认 PNG；其它格式传入对应扩展名，如 jpg、webp */
    fun screenshotFile(extension: String = "png"): File =
        resolveScreenshotFile(AssistsLogTarget.DEFAULT, extension)

    fun screenshotFile(): File = screenshotFile("png")

    fun nodeTreeFile(): File = resolveNodeTreeFile(AssistsLogTarget.DEFAULT)

    /**
     * 解析与日志同目录、同基础文件名的截图路径（扩展名由 [extension] 指定，如 png、jpg、webp）。
     */
    fun resolveScreenshotFilePath(
        target: AssistsLogTarget = AssistsLogTarget.DEFAULT,
        extension: String = "png",
        ensureWritable: Boolean = false,
    ): String = resolveSiblingFilePath(target, extension, ensureWritable)

    fun resolveScreenshotFile(
        target: AssistsLogTarget = AssistsLogTarget.DEFAULT,
        extension: String = "png",
        ensureWritable: Boolean = false,
    ): File = File(resolveScreenshotFilePath(target, extension, ensureWritable))

    /**
     * 解析与日志同目录、同基础文件名的节点树 JSON 路径。
     */
    fun resolveNodeTreeFilePath(
        target: AssistsLogTarget = AssistsLogTarget.DEFAULT,
        ensureWritable: Boolean = false,
    ): String = resolveSiblingFilePath(target, "json", ensureWritable)

    fun resolveNodeTreeFile(
        target: AssistsLogTarget = AssistsLogTarget.DEFAULT,
        ensureWritable: Boolean = false,
    ): File = File(resolveNodeTreeFilePath(target, ensureWritable))

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
        return "${resolveLogBaseName(fileName)}$LOG_FILE_EXTENSION"
    }

    /** 解析日志基础文件名（不含 .txt 后缀），与截图/节点树共用。 */
    fun resolveLogBaseName(fileName: String?): String {
        val raw = fileName?.trim().orEmpty().ifBlank { DEFAULT_LOG_BASE_NAME }
        val withoutExt = if (raw.endsWith(LOG_FILE_EXTENSION, ignoreCase = true)) {
            raw.dropLast(LOG_FILE_EXTENSION.length)
        } else {
            raw
        }.trim().ifBlank { DEFAULT_LOG_BASE_NAME }
        return withoutExt
    }

    /**
     * 解析与日志同目录、同基础文件名的伴生文件路径（扩展名由 [extension] 指定）。
     */
    fun resolveSiblingFilePath(
        target: AssistsLogTarget = AssistsLogTarget.DEFAULT,
        extension: String,
        ensureWritable: Boolean = false,
    ): String {
        val dir = resolveLogDir(target)
        if (ensureWritable) {
            ensureWritableDir(dir)
        }
        val ext = extension.trim().trimStart('.').ifBlank { "png" }
        return File(dir, "${resolveLogBaseName(target.fileName)}.$ext").absolutePath
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
