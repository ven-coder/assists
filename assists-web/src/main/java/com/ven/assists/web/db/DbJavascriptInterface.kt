package com.ven.assists.web.db

import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ven.assists.web.ASWebView
import com.ven.assists.web.CallInterceptResult
import com.ven.assists.web.CallRequest
import com.ven.assists.web.CallResponse
import com.ven.assists.web.createResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

/**
 * SQLite 数据库 JavascriptInterface
 */
class DbJavascriptInterface(val webView: WebView) {
    var callIntercept: ((json: String) -> CallInterceptResult)? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    fun <T> callbackResponse(result: CallResponse<T>) {
        coroutineScope.launch {
            runCatching {
                val json = GsonUtils.toJson(result)
                callback(json)
            }.onFailure {
                LogUtils.e(it)
            }
        }
    }

    fun callback(result: String) {
        val encoded = Base64.encodeToString(result.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val js = String.format("javascript:assistsxDbCallback('%s')", encoded)
        webView.evaluateJavascript(js, null)
    }

    @JavascriptInterface
    fun call(originJson: String): String {
        val result = GsonUtils.toJson(CallResponse<Any>(code = 0))
        coroutineScope.launch(Dispatchers.IO) {
            processCall(originJson)
        }
        return result
    }

    private suspend fun CoroutineScope.processCall(originJson: String) {
        var requestJson = originJson
        val intercepted = runCatching {
            ASWebView.globalDbCallIntercepts.forEach { intercept ->
                val result = intercept.invoke(requestJson)
                if (result.intercept) {
                    callback(result.result)
                    return@runCatching true
                } else {
                    requestJson = result.result
                }
            }
            callIntercept?.invoke(requestJson)?.let {
                if (it.intercept) {
                    callback(it.result)
                    true
                } else {
                    requestJson = it.result
                    false
                }
            } ?: false
        }.onFailure { LogUtils.e(it) }
        if (intercepted.getOrNull() == true) return

        val request = GsonUtils.fromJson<CallRequest<JsonObject>>(
            requestJson,
            object : TypeToken<CallRequest<JsonObject>>() {}.type,
        )
        runCatching {
            val response = when (request.method) {
                DbCallMethod.exec -> handleExec(request)
                DbCallMethod.query -> handleQuery(request)
                DbCallMethod.execBatch -> handleExecBatch(request)
                DbCallMethod.close -> handleClose(request)
                else -> request.createResponse(-1, message = "方法未支持: ${request.method}", data = null)
            }
            callbackResponse(response)
        }.onFailure {
            LogUtils.e(it)
            callbackResponse(request.createResponse(-1, message = it.message ?: "执行失败", data = null))
        }
    }

    private fun handleExec(request: CallRequest<JsonObject>): CallResponse<Any> {
        val args = request.arguments
        val sql = args?.get("sql")?.asString ?: ""
        if (sql.isEmpty()) {
            return request.createResponse(-1, message = "sql参数不能为空", data = null)
        }
        return runDbCall(request) { dbPath ->
            val result = DbDatabaseManager.exec(dbPath, sql, parseBindArgs(args))
            JsonObject().apply {
                addProperty("rowsAffected", result.rowsAffected)
                addProperty("lastInsertRowId", result.lastInsertRowId)
            }
        }
    }

    private fun handleQuery(request: CallRequest<JsonObject>): CallResponse<Any> {
        val args = request.arguments
        val sql = args?.get("sql")?.asString ?: ""
        if (sql.isEmpty()) {
            return request.createResponse(-1, message = "sql参数不能为空", data = null)
        }
        return runDbCall(request) { dbPath ->
            DbDatabaseManager.query(dbPath, sql, parseBindArgs(args))
        }
    }

    private fun handleExecBatch(request: CallRequest<JsonObject>): CallResponse<Any> {
        val args = request.arguments
        val statements = parseStatements(args)
        if (statements.isEmpty()) {
            return request.createResponse(-1, message = "statements参数不能为空", data = null)
        }
        return runDbCall(request) { dbPath ->
            DbDatabaseManager.execBatch(dbPath, statements)
        }
    }

    private fun handleClose(request: CallRequest<JsonObject>): CallResponse<Any> {
        return runDbCall(request) { dbPath ->
            DbDatabaseManager.close(dbPath)
            null
        }
    }

    private fun runDbCall(
        request: CallRequest<JsonObject>,
        block: (String) -> Any?,
    ): CallResponse<Any> {
        val args = request.arguments
        val dbPathArg = args?.get("dbPath")?.asString
        val dbName = args?.get("dbName")?.asString
        if (dbPathArg.isNullOrBlank() && dbName.isNullOrBlank()) {
            return request.createResponse(-1, message = "dbPath或dbName必须指定其一", data = null)
        }
        return try {
            val dbPath = DbDatabaseManager.resolveDbPath(dbPathArg, dbName)
            val data = block(dbPath)
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            request.createResponse(-1, message = e.message ?: "执行失败", data = null)
        }
    }

    private fun parseBindArgs(args: JsonObject?): Array<String?>? {
        val array = args?.getAsJsonArray("bindArgs") ?: return null
        if (array.size() == 0) return null
        return Array(array.size()) { index ->
            val element = array[index]
            when {
                element.isJsonNull -> null
                element.isJsonPrimitive -> {
                    val primitive = element.asJsonPrimitive
                    when {
                        primitive.isString -> primitive.asString
                        primitive.isNumber -> primitive.asNumber.toString()
                        primitive.isBoolean -> primitive.asBoolean.toString()
                        else -> primitive.asString
                    }
                }
                else -> element.toString()
            }
        }
    }

    private fun parseStatements(args: JsonObject?): List<String> {
        val array = args?.getAsJsonArray("statements") ?: return emptyList()
        return buildList {
            array.forEach { element ->
                if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                    add(element.asString)
                }
            }
        }
    }
}
