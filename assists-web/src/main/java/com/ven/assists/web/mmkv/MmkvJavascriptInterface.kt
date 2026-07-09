package com.ven.assists.web.mmkv

import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
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
 * MMKV 键值存储 JavascriptInterface
 */
class MmkvJavascriptInterface(val webView: WebView) {
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
        val js = String.format("javascript:assistsxMmkvCallback('%s')", encoded)
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
            ASWebView.globalMmkvCallIntercepts.forEach { intercept ->
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
                MmkvCallMethod.putString -> handlePutString(request)
                MmkvCallMethod.getString -> handleGetString(request)
                MmkvCallMethod.putBoolean -> handlePutBoolean(request)
                MmkvCallMethod.getBoolean -> handleGetBoolean(request)
                MmkvCallMethod.putInt -> handlePutInt(request)
                MmkvCallMethod.getInt -> handleGetInt(request)
                MmkvCallMethod.putLong -> handlePutLong(request)
                MmkvCallMethod.getLong -> handleGetLong(request)
                MmkvCallMethod.putFloat -> handlePutFloat(request)
                MmkvCallMethod.getFloat -> handleGetFloat(request)
                MmkvCallMethod.putDouble -> handlePutDouble(request)
                MmkvCallMethod.getDouble -> handleGetDouble(request)
                MmkvCallMethod.putBytes -> handlePutBytes(request)
                MmkvCallMethod.getBytes -> handleGetBytes(request)
                MmkvCallMethod.remove -> handleRemove(request)
                MmkvCallMethod.contains -> handleContains(request)
                MmkvCallMethod.clearAll -> handleClearAll(request)
                MmkvCallMethod.allKeys -> handleAllKeys(request)
                MmkvCallMethod.close -> handleClose(request)
                else -> request.createResponse(-1, message = "Unsupported method: ${request.method}", data = null)
            }
            callbackResponse(response)
        }.onFailure {
            LogUtils.e(it)
            callbackResponse(request.createResponse(-1, message = it.message ?: "Execution failed", data = null))
        }
    }

    private fun handlePutString(request: CallRequest<JsonObject>): CallResponse<Any> {
        val args = request.arguments
        val key = args?.get("key")?.asString ?: ""
        val value = args?.get("value")?.takeIf { !it.isJsonNull }?.asString
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.putString(mmkvId, rootPath, key, value)
        }
    }

    private fun handleGetString(request: CallRequest<JsonObject>): CallResponse<Any> {
        val key = request.arguments?.get("key")?.asString ?: ""
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.getString(mmkvId, rootPath, key)
        }
    }

    private fun handlePutBoolean(request: CallRequest<JsonObject>): CallResponse<Any> {
        val args = request.arguments
        val key = args?.get("key")?.asString ?: ""
        val value = args?.get("value")?.asBoolean ?: false
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.putBoolean(mmkvId, rootPath, key, value)
        }
    }

    private fun handleGetBoolean(request: CallRequest<JsonObject>): CallResponse<Any> {
        val key = request.arguments?.get("key")?.asString ?: ""
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.getBoolean(mmkvId, rootPath, key)
        }
    }

    private fun handlePutInt(request: CallRequest<JsonObject>): CallResponse<Any> {
        val args = request.arguments
        val key = args?.get("key")?.asString ?: ""
        val value = args?.get("value")?.asInt ?: 0
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.putInt(mmkvId, rootPath, key, value)
        }
    }

    private fun handleGetInt(request: CallRequest<JsonObject>): CallResponse<Any> {
        val key = request.arguments?.get("key")?.asString ?: ""
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.getInt(mmkvId, rootPath, key)
        }
    }

    private fun handlePutLong(request: CallRequest<JsonObject>): CallResponse<Any> {
        val args = request.arguments
        val key = args?.get("key")?.asString ?: ""
        val value = args?.get("value")?.asLong ?: 0L
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.putLong(mmkvId, rootPath, key, value)
        }
    }

    private fun handleGetLong(request: CallRequest<JsonObject>): CallResponse<Any> {
        val key = request.arguments?.get("key")?.asString ?: ""
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.getLong(mmkvId, rootPath, key)
        }
    }

    private fun handlePutFloat(request: CallRequest<JsonObject>): CallResponse<Any> {
        val args = request.arguments
        val key = args?.get("key")?.asString ?: ""
        val value = args?.get("value")?.asFloat ?: 0f
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.putFloat(mmkvId, rootPath, key, value)
        }
    }

    private fun handleGetFloat(request: CallRequest<JsonObject>): CallResponse<Any> {
        val key = request.arguments?.get("key")?.asString ?: ""
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.getFloat(mmkvId, rootPath, key)
        }
    }

    private fun handlePutDouble(request: CallRequest<JsonObject>): CallResponse<Any> {
        val args = request.arguments
        val key = args?.get("key")?.asString ?: ""
        val value = args?.get("value")?.asDouble ?: 0.0
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.putDouble(mmkvId, rootPath, key, value)
        }
    }

    private fun handleGetDouble(request: CallRequest<JsonObject>): CallResponse<Any> {
        val key = request.arguments?.get("key")?.asString ?: ""
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.getDouble(mmkvId, rootPath, key)
        }
    }

    private fun handlePutBytes(request: CallRequest<JsonObject>): CallResponse<Any> {
        val args = request.arguments
        val key = args?.get("key")?.asString ?: ""
        val value = args?.get("value")?.takeIf { !it.isJsonNull }?.asString
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.putBytes(mmkvId, rootPath, key, value)
        }
    }

    private fun handleGetBytes(request: CallRequest<JsonObject>): CallResponse<Any> {
        val key = request.arguments?.get("key")?.asString ?: ""
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.getBytes(mmkvId, rootPath, key)
        }
    }

    private fun handleRemove(request: CallRequest<JsonObject>): CallResponse<Any> {
        val key = request.arguments?.get("key")?.asString ?: ""
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.remove(mmkvId, rootPath, key)
        }
    }

    private fun handleContains(request: CallRequest<JsonObject>): CallResponse<Any> {
        val key = request.arguments?.get("key")?.asString ?: ""
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.contains(mmkvId, rootPath, key)
        }
    }

    private fun handleClearAll(request: CallRequest<JsonObject>): CallResponse<Any> {
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.clearAll(mmkvId, rootPath)
        }
    }

    private fun handleAllKeys(request: CallRequest<JsonObject>): CallResponse<Any> {
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.allKeys(mmkvId, rootPath)
        }
    }

    private fun handleClose(request: CallRequest<JsonObject>): CallResponse<Any> {
        return runMmkvCall(request) { mmkvId, rootPath ->
            MmkvManager.close(mmkvId, rootPath)
            null
        }
    }

    private fun runMmkvCall(
        request: CallRequest<JsonObject>,
        block: (String?, String?) -> Any?,
    ): CallResponse<Any> {
        val args = request.arguments
        val mmkvId = args?.get("mmkvId")?.takeIf { !it.isJsonNull }?.asString
        val rootPath = args?.get("rootPath")?.takeIf { !it.isJsonNull }?.asString
        return try {
            val data = block(mmkvId, rootPath)
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            request.createResponse(-1, message = e.message ?: "Execution failed", data = null)
        }
    }
}
