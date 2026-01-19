package com.ven.assists.web.network

import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ven.assists.web.CallRequest
import com.ven.assists.web.CallResponse
import com.ven.assists.web.JavascriptInterfaceContext
import com.ven.assists.web.createResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 网络请求相关的 JavascriptInterface
 * 提供 HTTP 请求相关的功能，包括 GET、POST、文件上传和下载
 */
class HttpJavascriptInterface(val webView: WebView) {
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
        val js = String.format("javascript:assistsxHttpCallback('%s')", encoded)
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
        val request = GsonUtils.fromJson<CallRequest<JsonObject>>(
            originJson,
            object : TypeToken<CallRequest<JsonObject>>() {}.type
        )
        runCatching {
            val response = when (request.method) {
                HttpCallMethod.httpGet -> {
                    handleGetRequest(request)
                }

                HttpCallMethod.httpPost -> {
                    handlePostRequest(request)
                }

                HttpCallMethod.httpPostFile -> {
                    handlePostFileRequest(request)
                }

                HttpCallMethod.httpDownload -> {
                    handleDownloadRequest(request)
                }

                HttpCallMethod.httpConfigure -> {
                    handleConfigureRequest(request)
                }

                HttpCallMethod.httpReset -> {
                    handleResetRequest(request)
                }

                HttpCallMethod.httpGetConfig -> {
                    handleGetConfigRequest(request)
                }

                else -> {
                    request.createResponse(-1, message = "方法未支持")
                }
            }
            callbackResponse(response)
        }.onFailure {
            LogUtils.e(it)
            callbackResponse(request.createResponse(-1, message = it.message, data = JsonObject()))
        }
    }

    /**
     * 处理 GET 请求
     */
    private fun handleGetRequest(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val url = request.arguments?.get("url")?.asString ?: ""
        val headers = request.arguments?.get("headers")?.asJsonObject

        if (url.isEmpty()) {
            return request.createResponse(-1, message = "url参数不能为空", data = JsonObject())
        }

        return try {
            val client = OkHttpClientManager.getClient()

            val requestBuilder = Request.Builder().url(url).get()

            // 添加请求头
            headers?.entrySet()?.forEach { entry ->
                requestBuilder.addHeader(entry.key, entry.value.asString)
            }

            val httpRequest = requestBuilder.build()
            val httpResponse = client.newCall(httpRequest).execute()
            val responseBody = httpResponse.body?.string() ?: ""

            val responseData = JsonObject().apply {
                addProperty("statusCode", httpResponse.code)
                addProperty("statusMessage", httpResponse.message)
                addProperty("body", responseBody)
                add("headers", JsonObject().apply {
                    httpResponse.headers.forEach { pair ->
                        addProperty(pair.first, pair.second)
                    }
                })
            }

            request.createResponse(0, data = responseData)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "GET请求失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 处理 POST 请求
     */
    private fun handlePostRequest(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val url = request.arguments?.get("url")?.asString ?: ""
        val headers = request.arguments?.get("headers")?.asJsonObject
        val body = request.arguments?.get("body")?.asString ?: ""

        if (url.isEmpty()) {
            return request.createResponse(-1, message = "url参数不能为空", data = JsonObject())
        }

        return try {
            val client = OkHttpClientManager.getClient()

            val requestBuilder = Request.Builder().url(url)

            // 添加请求头
            headers?.entrySet()?.forEach { entry ->
                requestBuilder.addHeader(entry.key, entry.value.asString)
            }

            // 构建请求体
            val contentType = headers?.get("Content-Type")?.asString ?: "application/json; charset=utf-8"
            val requestBody = body.toRequestBody(contentType.toMediaType())
            requestBuilder.post(requestBody)

            val httpRequest = requestBuilder.build()
            val httpResponse = client.newCall(httpRequest).execute()
            val responseBody = httpResponse.body?.string() ?: ""

            val responseData = JsonObject().apply {
                addProperty("statusCode", httpResponse.code)
                addProperty("statusMessage", httpResponse.message)
                addProperty("body", responseBody)
                add("headers", JsonObject().apply {
                    httpResponse.headers.forEach { pair ->
                        addProperty(pair.first, pair.second)
                    }
                })
            }

            request.createResponse(0, data = responseData)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "POST请求失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 处理 POST 文件上传请求
     * 支持单个文件和多文件上传，同时支持多个表单字段
     * 
     * 参数说明：
     * - url: 请求地址（必需）
     * - headers: 请求头（可选）
     * - formData: 表单字段（可选，JsonObject，键值对形式）
     * - files: 文件数组（必需，JsonArray，每个元素为 JsonObject）
     *   每个文件对象包含：
     *   - filePath: 文件路径（必需）
     *   - fieldName: 字段名（可选，默认 "file"）
     *   - fileName: 文件名（可选，默认使用文件原名）
     *   - contentType: 文件类型（可选，默认 "application/octet-stream"）
     * 
     * 注意：单文件上传时，files 数组只需包含一个文件对象
     */
    private fun handlePostFileRequest(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val url = request.arguments?.get("url")?.asString ?: ""
        val headers = request.arguments?.get("headers")?.asJsonObject
        val formData = request.arguments?.get("formData")?.asJsonObject
        val filesArray = request.arguments?.get("files")?.asJsonArray

        if (url.isEmpty()) {
            return request.createResponse(-1, message = "url参数不能为空", data = JsonObject())
        }

        if (filesArray == null || filesArray.size() == 0) {
            return request.createResponse(-1, message = "files参数不能为空，至少需要上传一个文件", data = JsonObject())
        }

        // 收集要上传的文件列表
        val fileList = mutableListOf<FileInfo>()

        filesArray.forEach { element ->
            val fileObj = element.asJsonObject
            val filePath = fileObj.get("filePath")?.asString ?: ""
            val fieldName = fileObj.get("fieldName")?.asString ?: "file"
            val fileName = fileObj.get("fileName")?.asString
            val contentType = fileObj.get("contentType")?.asString ?: "application/octet-stream"

            if (filePath.isEmpty()) {
                return request.createResponse(-1, message = "files数组中的filePath参数不能为空", data = JsonObject())
            }

            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                return request.createResponse(-1, message = "文件不存在或不是有效文件: $filePath", data = JsonObject())
            }

            fileList.add(FileInfo(file, fieldName, fileName ?: file.name, contentType))
        }

        return try {
            val client = OkHttpClientManager.getClient()

            // 构建 MultipartBody
            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)

            // 先添加表单字段
            formData?.entrySet()?.forEach { entry ->
                val value = entry.value
                when {
                    value.isJsonPrimitive -> {
                        // 字符串值
                        multipartBuilder.addFormDataPart(entry.key, value.asString)
                    }
                    value.isJsonArray -> {
                        // 数组值，支持同名字段多个值
                        value.asJsonArray.forEach { arrayElement ->
                            multipartBuilder.addFormDataPart(entry.key, arrayElement.asString)
                        }
                    }
                    else -> {
                        // 其他类型转为字符串
                        multipartBuilder.addFormDataPart(entry.key, value.toString())
                    }
                }
            }

            // 添加所有文件
            fileList.forEach { fileInfo ->
                val mediaType = fileInfo.contentType.toMediaType()
                val fileBody = fileInfo.file.asRequestBody(mediaType)
                multipartBuilder.addFormDataPart(fileInfo.fieldName, fileInfo.fileName, fileBody)
            }

            val requestBody = multipartBuilder.build()

            val requestBuilder = Request.Builder().url(url).post(requestBody)

            // 添加请求头（注意：multipart/form-data 的 Content-Type 会自动设置，不要手动设置）
            headers?.entrySet()?.forEach { entry ->
                val key = entry.key
                // 跳过 Content-Type，让 OkHttp 自动设置
                if (key.lowercase() != "content-type") {
                    requestBuilder.addHeader(key, entry.value.asString)
                }
            }

            val httpRequest = requestBuilder.build()
            val httpResponse = client.newCall(httpRequest).execute()
            val responseBody = httpResponse.body?.string() ?: ""

            val responseData = JsonObject().apply {
                addProperty("statusCode", httpResponse.code)
                addProperty("statusMessage", httpResponse.message)
                addProperty("body", responseBody)
                add("headers", JsonObject().apply {
                    httpResponse.headers.forEach { pair ->
                        addProperty(pair.first, pair.second)
                    }
                })
            }

            request.createResponse(0, data = responseData)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "文件上传失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 文件信息数据类
     */
    private data class FileInfo(
        val file: File,
        val fieldName: String,
        val fileName: String,
        val contentType: String
    )

    /**
     * 处理文件下载请求
     */
    private fun handleDownloadRequest(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val url = request.arguments?.get("url")?.asString ?: ""
        val savePath = request.arguments?.get("savePath")?.asString ?: ""
        val headers = request.arguments?.get("headers")?.asJsonObject

        if (url.isEmpty()) {
            return request.createResponse(-1, message = "url参数不能为空", data = JsonObject())
        }

        if (savePath.isEmpty()) {
            return request.createResponse(-1, message = "savePath参数不能为空", data = JsonObject())
        }

        return try {
            val client = OkHttpClientManager.getClient()

            val requestBuilder = Request.Builder().url(url).get()

            // 添加请求头
            headers?.entrySet()?.forEach { entry ->
                requestBuilder.addHeader(entry.key, entry.value.asString)
            }

            val httpRequest = requestBuilder.build()
            val httpResponse = client.newCall(httpRequest).execute()

            if (!httpResponse.isSuccessful) {
                return request.createResponse(
                    -1,
                    message = "下载失败: ${httpResponse.code} ${httpResponse.message}",
                    data = JsonObject()
                )
            }

            val responseBody = httpResponse.body ?: throw Exception("响应体为空")
            val saveFile = File(savePath)

            // 确保父目录存在
            saveFile.parentFile?.mkdirs()

            // 写入文件
            saveFile.outputStream().use { output ->
                responseBody.byteStream().use { input ->
                    input.copyTo(output)
                }
            }

            val responseData = JsonObject().apply {
                addProperty("statusCode", httpResponse.code)
                addProperty("statusMessage", httpResponse.message)
                addProperty("savePath", saveFile.absolutePath)
                addProperty("fileSize", saveFile.length())
                add("headers", JsonObject().apply {
                    httpResponse.headers.forEach { pair ->
                        addProperty(pair.first, pair.second)
                    }
                })
            }

            request.createResponse(0, data = responseData)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "文件下载失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 处理配置 OkHttpClient 请求
     */
    private fun handleConfigureRequest(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val connectTimeout = request.arguments?.get("connectTimeout")?.asLong
        val readTimeout = request.arguments?.get("readTimeout")?.asLong
        val writeTimeout = request.arguments?.get("writeTimeout")?.asLong

        return try {
            OkHttpClientManager.configure(
                connectTimeout = connectTimeout,
                readTimeout = readTimeout,
                writeTimeout = writeTimeout
            )
            val config = OkHttpClientManager.getConfig()
            request.createResponse(0, message = "配置成功", data = config)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "配置失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 处理重置 OkHttpClient 请求
     */
    private fun handleResetRequest(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        return try {
            OkHttpClientManager.reset()
            val config = OkHttpClientManager.getConfig()
            request.createResponse(0, message = "重置成功", data = config)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "重置失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 处理获取配置信息请求
     */
    private fun handleGetConfigRequest(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        return try {
            val config = OkHttpClientManager.getConfig()
            request.createResponse(0, data = config)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "获取配置失败: ${e.message}", data = JsonObject())
        }
    }
}

