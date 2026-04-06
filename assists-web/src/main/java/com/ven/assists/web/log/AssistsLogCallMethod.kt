package com.ven.assists.web.log

/**
 * AssistsLog 桥接方法名（与 H5 约定一致）
 */
object AssistsLogCallMethod {
    const val readAllText = "readAllText"
    const val clear = "clear"
    const val refreshFromFile = "refreshFromFile"
    const val appendLine = "appendLine"
    const val appendTimestampedEntry = "appendTimestampedEntry"
    const val replaceAll = "replaceAll"
    const val subscribe = "subscribe"
    const val unsubscribe = "unsubscribe"

    /** 截图 + 节点树 + 日志 multipart 上传（需 API 30+） */
    const val uploadLogs = "uploadLogs"

    /** 获取日志服务当前域名（origin，无路径；与上传、管理后台同源） */
    const val getLogServiceBaseUrl = "getLogServiceBaseUrl"
}
