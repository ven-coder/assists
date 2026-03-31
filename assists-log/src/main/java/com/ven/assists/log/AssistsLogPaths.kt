package com.ven.assists.log

import com.blankj.utilcode.util.PathUtils
import java.io.File

/**
 * 诊断与上传共用的固定文件名（应用内部 files 目录）
 */
object AssistsLogPaths {
    const val LOG_FILE_NAME = "assists_log.txt"
    const val SCREENSHOT_FILE_NAME = "assists_screenshot.png"
    const val NODE_TREE_FILE_NAME = "assists_node_tree.json"

    fun logFile(): File = File(PathUtils.getInternalAppFilesPath(), LOG_FILE_NAME)

    /** 默认 PNG；其它格式传入对应扩展名，如 jpg、webp */
    fun screenshotFile(extension: String = "png"): File =
        File(PathUtils.getInternalAppFilesPath(), "assists_screenshot.$extension")

    fun screenshotFile(): File = screenshotFile("png")

    fun nodeTreeFile(): File = File(PathUtils.getInternalAppFilesPath(), NODE_TREE_FILE_NAME)
}
