package com.ven.assists.web.filesystem.fileio

import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.FileIOUtils
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.Utils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ven.assists.web.CallRequest
import com.ven.assists.web.CallResponse
import com.ven.assists.web.createResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * 文件IO相关的 JavascriptInterface
 * 提供文件读写相关的功能
 */
class FileIOJavascriptInterface(val webView: WebView) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    // 用于线程安全写入的锁映射表，每个文件路径对应一个锁对象
    private val fileLocks = ConcurrentHashMap<String, Any>()

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
        val js = String.format("javascript:assistsxFileIOCallback('%s')", encoded)
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
                // 文件写入方法
                FileIOCallMethod.writeFileFromIS -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    val inputStreamBase64 = request.arguments?.get("inputStreamBase64")?.asString
                    val append = request.arguments?.get("append")?.asBoolean ?: false
                    
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = false)
                    } else if (inputStreamBase64.isNullOrEmpty()) {
                        request.createResponse(-1, message = "inputStreamBase64参数不能为空", data = false)
                    } else {
                        try {
                            val bytes = Base64.decode(inputStreamBase64, Base64.NO_WRAP)
                            val inputStream = bytes.inputStream()
                            val file = File(filePath)
                            val result = FileIOUtils.writeFileFromIS(file, inputStream, append)
                            inputStream.close()
                            request.createResponse(if (result) 0 else -1, data = result)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "写入文件失败: ${e.message}", data = false)
                        }
                    }
                }

                FileIOCallMethod.writeFileFromBytesByStream -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    val bytesBase64 = request.arguments?.get("bytesBase64")?.asString
                    val append = request.arguments?.get("append")?.asBoolean ?: false
                    
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = false)
                    } else if (bytesBase64.isNullOrEmpty()) {
                        request.createResponse(-1, message = "bytesBase64参数不能为空", data = false)
                    } else {
                        try {
                            val bytes = Base64.decode(bytesBase64, Base64.NO_WRAP)
                            val file = File(filePath)
                            val result = FileIOUtils.writeFileFromBytesByStream(file, bytes, append)
                            request.createResponse(if (result) 0 else -1, data = result)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "写入文件失败: ${e.message}", data = false)
                        }
                    }
                }

                FileIOCallMethod.writeFileFromBytesByChannel -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    val bytesBase64 = request.arguments?.get("bytesBase64")?.asString
                    val append = request.arguments?.get("append")?.asBoolean ?: false
                    
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = false)
                    } else if (bytesBase64.isNullOrEmpty()) {
                        request.createResponse(-1, message = "bytesBase64参数不能为空", data = false)
                    } else {
                        try {
                            val bytes = Base64.decode(bytesBase64, Base64.NO_WRAP)
                            val file = File(filePath)
                            val result = FileIOUtils.writeFileFromBytesByChannel(file, bytes, append)
                            request.createResponse(if (result) 0 else -1, data = result)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "写入文件失败: ${e.message}", data = false)
                        }
                    }
                }

                FileIOCallMethod.writeFileFromBytesByMap -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    val bytesBase64 = request.arguments?.get("bytesBase64")?.asString
                    val append = request.arguments?.get("append")?.asBoolean ?: false
                    
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = false)
                    } else if (bytesBase64.isNullOrEmpty()) {
                        request.createResponse(-1, message = "bytesBase64参数不能为空", data = false)
                    } else {
                        try {
                            val bytes = Base64.decode(bytesBase64, Base64.NO_WRAP)
                            val file = File(filePath)
                            val result = FileIOUtils.writeFileFromBytesByMap(file, bytes, append)
                            request.createResponse(if (result) 0 else -1, data = result)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "写入文件失败: ${e.message}", data = false)
                        }
                    }
                }

                FileIOCallMethod.writeFileFromString -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    val content = request.arguments?.get("content")?.asString
                    val append = request.arguments?.get("append")?.asBoolean ?: false
                    val threadSafe = request.arguments?.get("threadSafe")?.asBoolean ?: false

                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = false)
                    } else if (content == null) {
                        request.createResponse(-1, message = "content参数不能为空", data = false)
                    } else {
                        try {
                            val file = File(filePath)
                            val result = if (threadSafe) {
                                // 线程安全写入：为每个文件路径获取或创建锁对象
                                val lock = fileLocks.computeIfAbsent(filePath) { Any() }
                                synchronized(lock) {
                                    FileIOUtils.writeFileFromString(file, content, append)
                                }
                            } else {
                                // 非线程安全写入：直接调用
                                FileIOUtils.writeFileFromString(file, content, append)
                            }
                            request.createResponse(if (result) 0 else -1, data = result)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "写入文件失败: ${e.message}", data = false)
                        }
                    }
                }

                // 文件读取方法
                FileIOCallMethod.readFile2List -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    val charsetName = request.arguments?.get("charsetName")?.asString ?: "UTF-8"
                    
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = JsonArray())
                    } else {
                        try {
                            val file = File(filePath)
                            if (!file.exists()) {
                                request.createResponse(-1, message = "文件不存在", data = JsonArray())
                            } else {
                                val list = FileIOUtils.readFile2List(file, charsetName)
                                val jsonArray = JsonArray().apply {
                                    list.forEach { add(it) }
                                }
                                request.createResponse(0, data = jsonArray)
                            }
                        } catch (e: Exception) {
                            LogUtils.e(e)

                            request.createResponse(-1, message = "读取文件失败: ${e.message}", data = JsonArray())
                        }
                    }
                }

                FileIOCallMethod.readFile2String -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    val charsetName = request.arguments?.get("charsetName")?.asString ?: "UTF-8"
                    
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = "")
                    } else {
                        try {
                            val file = File(filePath)
                            if (!file.exists()) {
                                request.createResponse(-1, message = "文件不存在", data = "")
                            } else {
                                val content = FileIOUtils.readFile2String(file, charsetName)
                                request.createResponse(0, data = content)
                            }
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "读取文件失败: ${e.message}", data = "")
                        }
                    }
                }

                FileIOCallMethod.readFile2BytesByStream -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = "")
                    } else {
                        try {
                            val file = File(filePath)
                            if (!file.exists()) {
                                request.createResponse(-1, message = "文件不存在", data = "")
                            } else {
                                val bytes = FileIOUtils.readFile2BytesByStream(file)
                                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                request.createResponse(0, data = base64)
                            }
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "读取文件失败: ${e.message}", data = "")
                        }
                    }
                }

                FileIOCallMethod.readFile2BytesByChannel -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = "")
                    } else {
                        try {
                            val file = File(filePath)
                            if (!file.exists()) {
                                request.createResponse(-1, message = "文件不存在", data = "")
                            } else {
                                val bytes = FileIOUtils.readFile2BytesByChannel(file)
                                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                request.createResponse(0, data = base64)
                            }
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "读取文件失败: ${e.message}", data = "")
                        }
                    }
                }

                FileIOCallMethod.readFile2BytesByMap -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = "")
                    } else {
                        try {
                            val file = File(filePath)
                            if (!file.exists()) {
                                request.createResponse(-1, message = "文件不存在", data = "")
                            } else {
                                val bytes = FileIOUtils.readFile2BytesByMap(file)
                                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                request.createResponse(0, data = base64)
                            }
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "读取文件失败: ${e.message}", data = "")
                        }
                    }
                }

                FileIOCallMethod.setBufferSize -> {
                    val bufferSize = request.arguments?.get("bufferSize")?.asInt
                    
                    if (bufferSize == null || bufferSize <= 0) {
                        request.createResponse(-1, message = "bufferSize参数必须大于0", data = false)
                    } else {
                        try {
                            FileIOUtils.setBufferSize(bufferSize)
                            request.createResponse(0, data = true)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "设置缓冲区大小失败: ${e.message}", data = false)
                        }
                    }
                }

                else -> {
                    request.createResponse(-1, message = "方法未支持")
                }
            }
            callbackResponse(response)
        }.onFailure {
            LogUtils.e(it)
            callbackResponse(request.createResponse(-1, message = it.message, data = ""))
        }
    }
}

