package com.ven.assists.web.utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * 归一化 Bridge 请求 Json：
 * - `node` 误传数组（如 JS 侧把 findById 返回的 Node[] 直接传给单节点 API click/longClick 等）时取首元素；
 * - `nodes` 误传单对象时包装为数组。
 *
 * 在 Gson 反序列化前调用，避免类型不匹配抛 JsonSyntaxException 导致进程崩溃。
 */
fun normalizeCallRequestJson(root: JsonObject) {
    root.get("node")?.let { nodeElement ->
        if (nodeElement.isJsonArray) {
            val array = nodeElement.asJsonArray
            if (array.size() > 0) {
                root.add("node", array[0])
            } else {
                root.remove("node")
            }
        }
    }
    root.get("nodes")?.let { nodesElement ->
        if (nodesElement.isJsonObject) {
            root.add("nodes", JsonArray().apply { add(nodesElement) })
        }
    }
}
