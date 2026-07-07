package com.ven.assists.web.db

/**
 * SQLite 数据库桥接方法名常量
 */
object DbCallMethod {
    const val exec = "exec"
    const val query = "query"
    const val execBatch = "execBatch"
    const val close = "close"

    /** 需要数据库路径参数的方法（供拦截器判断） */
    val pathAwareMethods = setOf(exec, query, execBatch, close)
}
