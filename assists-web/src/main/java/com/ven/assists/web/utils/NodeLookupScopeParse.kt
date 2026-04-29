package com.ven.assists.web.utils

import com.blankj.utilcode.util.LogUtils
import com.google.gson.JsonObject
import com.ven.assists.AssistsCore.NodeLookupScope

/**
 * 与 [com.ven.assistsx.AisNodeViewServer] 中 query 参数一致的 scope 字符串解析。
 */
internal object NodeLookupScopeParse {

    private const val SCOPE_ALL_WINDOWS = "all_windows"
    private const val SCOPE_ACTIVE_WINDOW = "active_window"

    /**
     * 从 JS bridge arguments 中解析 [NodeLookupScope]；缺省或非法值视为 [NodeLookupScope.ActiveWindow]。
     */
    fun fromArguments(arguments: JsonObject?): NodeLookupScope {
        val raw = arguments?.get("scope")?.asString?.trim()?.lowercase() ?: return NodeLookupScope.ActiveWindow
        return when {
            raw.isEmpty() || raw == SCOPE_ACTIVE_WINDOW -> NodeLookupScope.ActiveWindow
            raw == SCOPE_ALL_WINDOWS -> NodeLookupScope.AllWindows
            else -> {
                LogUtils.w("Unknown JS scope: $raw, fallback to active_window")
                NodeLookupScope.ActiveWindow
            }
        }
    }
}
