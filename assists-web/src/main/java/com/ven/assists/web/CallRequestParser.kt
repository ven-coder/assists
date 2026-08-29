package com.ven.assists.web

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.ven.assists.web.utils.normalizeCallRequestJson
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils

/**
 * Bridge 请求安全解析工具
 *
 * JS 侧传参类型不符（如把 findById 返回的 Node[] 数组传给单节点参数 node）时，
 * Gson 反序列化会抛 JsonSyntaxException；若解析写在 runCatching 之外，
 * 会炸掉无异常处理器的协程导致进程崩溃（曾在线上触发）。
 *
 * 统一入口：
 * 1. 先经 [JsonParser] 解析为 Json 树并归一化（node 数组取首元素、nodes 对象包装为数组）；
 * 2. 再反序列化为 [CallRequest]；
 * 3. 任一步失败均返回 null，由调用方返回错误响应，不再向外抛异常。
 */
object CallRequestParser {

    private val callRequestType = object : TypeToken<CallRequest<JsonObject>>() {}.type

    /**
     * 安全解析 Bridge 请求；失败返回 null。
     * @param requestJson JS 侧 call(originJson) 传入的原始 JSON 字符串
     */
    fun parse(requestJson: String): CallRequest<JsonObject>? {
        return runCatching {
            val root = JsonParser.parseString(requestJson).asJsonObject
            normalizeCallRequestJson(root)
            GsonUtils.fromJson<CallRequest<JsonObject>>(root.toString(), callRequestType)
        }.getOrElse {
            LogUtils.e("CallRequestParser parse failed: ${it.message}")
            null
        }
    }
}
