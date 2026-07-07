package com.ven.assists.log

/**
 * 日志文件目标：可选绝对目录 [dirPath] 与不含 .txt 后缀的 [fileName]。
 * 不传 [dirPath] 时使用应用内部 files 目录；不传 [fileName] 时默认 log-default.txt。
 */
data class AssistsLogTarget(
    val dirPath: String? = null,
    val fileName: String? = null,
) {
    companion object {
        val DEFAULT = AssistsLogTarget()
    }
}
