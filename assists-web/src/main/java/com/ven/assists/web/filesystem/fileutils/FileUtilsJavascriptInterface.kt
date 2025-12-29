package com.ven.assists.web.filesystem.fileutils

import android.content.Context
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.blankj.utilcode.util.FileUtils
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
import java.io.File
import java.io.FileFilter
import java.nio.charset.StandardCharsets

/**
 * 文件工具相关的 JavascriptInterface
 * 提供文件操作相关的功能
 */
class FileUtilsJavascriptInterface(val webView: WebView) {
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
        val js = String.format("javascript:assistsxFileUtilsCallback('%s')", encoded)
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
                FileUtilsCallMethod.getFileByPath -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = null)
                    } else {
                        val file = FileUtils.getFileByPath(filePath)
                        val data = JsonObject().apply {
                            addProperty("path", file?.absolutePath ?: "")
                            addProperty("exists", file?.exists() ?: false)
                        }
                        request.createResponse(0, data = data)
                    }
                }

                FileUtilsCallMethod.isFileExists -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = false)
                    } else {
                        val result = FileUtils.isFileExists(filePath)
                        request.createResponse(0, data = result)
                    }
                }

                FileUtilsCallMethod.rename -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    val newName = request.arguments?.get("newName")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = false)
                    } else if (newName.isEmpty()) {
                        request.createResponse(-1, message = "newName参数不能为空", data = false)
                    } else {
                        val file = File(filePath)
                        val result = FileUtils.rename(file, newName)
                        request.createResponse(if (result) 0 else -1, data = result)
                    }
                }

                FileUtilsCallMethod.isDir -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = false)
                    } else {
                        val result = FileUtils.isDir(filePath)
                        request.createResponse(0, data = result)
                    }
                }

                FileUtilsCallMethod.isFile -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = false)
                    } else {
                        val result = FileUtils.isFile(filePath)
                        request.createResponse(0, data = result)
                    }
                }

                FileUtilsCallMethod.createOrExistsDir -> {
                    val dirPath = request.arguments?.get("dirPath")?.asString ?: ""
                    if (dirPath.isEmpty()) {
                        request.createResponse(-1, message = "dirPath参数不能为空", data = false)
                    } else {
                        val result = FileUtils.createOrExistsDir(dirPath)
                        request.createResponse(if (result) 0 else -1, data = result)
                    }
                }

                FileUtilsCallMethod.createOrExistsFile -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = false)
                    } else {
                        val result = FileUtils.createOrExistsFile(filePath)
                        request.createResponse(if (result) 0 else -1, data = result)
                    }
                }

                FileUtilsCallMethod.createFileByDeleteOldFile -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = false)
                    } else {
                        val result = FileUtils.createFileByDeleteOldFile(filePath)
                        request.createResponse(if (result) 0 else -1, data = result)
                    }
                }

                FileUtilsCallMethod.copy -> {
                    val srcFilePath = request.arguments?.get("srcFilePath")?.asString ?: ""
                    val destFilePath = request.arguments?.get("destFilePath")?.asString ?: ""
                    if (srcFilePath.isEmpty()) {
                        request.createResponse(-1, message = "srcFilePath参数不能为空", data = false)
                    } else if (destFilePath.isEmpty()) {
                        request.createResponse(-1, message = "destFilePath参数不能为空", data = false)
                    } else {
                        val result = FileUtils.copy(srcFilePath, destFilePath)
                        request.createResponse(if (result) 0 else -1, data = result)
                    }
                }

                FileUtilsCallMethod.move -> {
                    val srcFilePath = request.arguments?.get("srcFilePath")?.asString ?: ""
                    val destFilePath = request.arguments?.get("destFilePath")?.asString ?: ""
                    if (srcFilePath.isEmpty()) {
                        request.createResponse(-1, message = "srcFilePath参数不能为空", data = false)
                    } else if (destFilePath.isEmpty()) {
                        request.createResponse(-1, message = "destFilePath参数不能为空", data = false)
                    } else {
                        val result = FileUtils.move(srcFilePath, destFilePath)
                        request.createResponse(if (result) 0 else -1, data = result)
                    }
                }

                FileUtilsCallMethod.delete -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = false)
                    } else {
                        val result = FileUtils.delete(filePath)
                        request.createResponse(if (result) 0 else -1, data = result)
                    }
                }

                FileUtilsCallMethod.deleteAllInDir -> {
                    val dirPath = request.arguments?.get("dirPath")?.asString ?: ""
                    if (dirPath.isEmpty()) {
                        request.createResponse(-1, message = "dirPath参数不能为空", data = false)
                    } else {
                        val result = FileUtils.deleteAllInDir(dirPath)
                        request.createResponse(if (result) 0 else -1, data = result)
                    }
                }

                FileUtilsCallMethod.deleteFilesInDir -> {
                    val dirPath = request.arguments?.get("dirPath")?.asString ?: ""
                    if (dirPath.isEmpty()) {
                        request.createResponse(-1, message = "dirPath参数不能为空", data = false)
                    } else {
                        val result = FileUtils.deleteFilesInDir(dirPath)
                        request.createResponse(if (result) 0 else -1, data = result)
                    }
                }

                FileUtilsCallMethod.deleteFilesInDirWithFilter -> {
                    val dirPath = request.arguments?.get("dirPath")?.asString ?: ""
                    val filterPattern = request.arguments?.get("filterPattern")?.asString
                    if (dirPath.isEmpty()) {
                        request.createResponse(-1, message = "dirPath参数不能为空", data = false)
                    } else {
                        try {
                            val filter = if (filterPattern != null) {
                                FileFilter { file -> file.name.matches(Regex(filterPattern)) }
                            } else {
                                null
                            }
                            val result = FileUtils.deleteFilesInDirWithFilter(dirPath, filter)
                            request.createResponse(if (result) 0 else -1, data = result)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "删除文件失败: ${e.message}", data = false)
                        }
                    }
                }

                FileUtilsCallMethod.listFilesInDir -> {
                    val dirPath = request.arguments?.get("dirPath")?.asString ?: ""
                    if (dirPath.isEmpty()) {
                        request.createResponse(-1, message = "dirPath参数不能为空", data = JsonArray())
                    } else {
                        try {
                            val files = FileUtils.listFilesInDir(dirPath)
                            val jsonArray = JsonArray().apply {
                                files.forEach { file ->
                                    add(JsonObject().apply {
                                        addProperty("path", file.absolutePath)
                                        addProperty("name", file.name)
                                        addProperty("isDirectory", file.isDirectory)
                                        addProperty("length", file.length())
                                    })
                                }
                            }
                            request.createResponse(0, data = jsonArray)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "列出文件失败: ${e.message}", data = JsonArray())
                        }
                    }
                }

                FileUtilsCallMethod.listFilesInDirWithFilter -> {
                    val dirPath = request.arguments?.get("dirPath")?.asString ?: ""
                    val filterPattern = request.arguments?.get("filterPattern")?.asString
                    if (dirPath.isEmpty()) {
                        request.createResponse(-1, message = "dirPath参数不能为空", data = JsonArray())
                    } else {
                        try {
                            val filter = if (filterPattern != null) {
                                FileFilter { file -> file.name.matches(Regex(filterPattern)) }
                            } else {
                                null
                            }
                            val files = FileUtils.listFilesInDirWithFilter(dirPath, filter)
                            val jsonArray = JsonArray().apply {
                                files.forEach { file ->
                                    add(JsonObject().apply {
                                        addProperty("path", file.absolutePath)
                                        addProperty("name", file.name)
                                        addProperty("isDirectory", file.isDirectory)
                                        addProperty("length", file.length())
                                    })
                                }
                            }
                            request.createResponse(0, data = jsonArray)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "列出文件失败: ${e.message}", data = JsonArray())
                        }
                    }
                }

                FileUtilsCallMethod.getFileLastModified -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = 0L)
                    } else {
                        try {
                            val time = FileUtils.getFileLastModified(filePath)
                            request.createResponse(0, data = time)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "获取文件修改时间失败: ${e.message}", data = 0L)
                        }
                    }
                }

                FileUtilsCallMethod.getFileCharsetSimple -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = "")
                    } else {
                        try {
                            val charset = FileUtils.getFileCharsetSimple(filePath)
                            request.createResponse(0, data = charset)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "获取文件编码失败: ${e.message}", data = "")
                        }
                    }
                }

                FileUtilsCallMethod.getFileLines -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = 0)
                    } else {
                        try {
                            val lines = FileUtils.getFileLines(filePath)
                            request.createResponse(0, data = lines)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "获取文件行数失败: ${e.message}", data = 0)
                        }
                    }
                }

                FileUtilsCallMethod.getSize -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = 0L)
                    } else {
                        try {
                            val size = FileUtils.getSize(filePath)
                            request.createResponse(0, data = size)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "获取文件大小失败: ${e.message}", data = 0L)
                        }
                    }
                }

                FileUtilsCallMethod.getLength -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = 0L)
                    } else {
                        try {
                            val length = FileUtils.getLength(filePath)
                            request.createResponse(0, data = length)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "获取文件长度失败: ${e.message}", data = 0L)
                        }
                    }
                }

                FileUtilsCallMethod.getFileMD5 -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = "")
                    } else {
                        try {
                            val md5 = FileUtils.getFileMD5(filePath)
                            val base64 = Base64.encodeToString(md5, Base64.NO_WRAP)
                            request.createResponse(0, data = base64)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "获取文件MD5失败: ${e.message}", data = "")
                        }
                    }
                }

                FileUtilsCallMethod.getFileMD5ToString -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = "")
                    } else {
                        try {
                            val md5 = FileUtils.getFileMD5ToString(filePath)
                            request.createResponse(0, data = md5)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "获取文件MD5失败: ${e.message}", data = "")
                        }
                    }
                }

                FileUtilsCallMethod.getDirName -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = "")
                    } else {
                        val dirName = FileUtils.getDirName(filePath)
                        request.createResponse(0, data = dirName)
                    }
                }

                FileUtilsCallMethod.getFileName -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = "")
                    } else {
                        val fileName = FileUtils.getFileName(filePath)
                        request.createResponse(0, data = fileName)
                    }
                }

                FileUtilsCallMethod.getFileNameNoExtension -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = "")
                    } else {
                        val fileName = FileUtils.getFileNameNoExtension(filePath)
                        request.createResponse(0, data = fileName)
                    }
                }

                FileUtilsCallMethod.getFileExtension -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = "")
                    } else {
                        val extension = FileUtils.getFileExtension(filePath)
                        request.createResponse(0, data = extension)
                    }
                }

                FileUtilsCallMethod.notifySystemToScan -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = false)
                    } else {
                        try {
                            val context = JavascriptInterfaceContext.getContext()
                            if (context == null) {
                                request.createResponse(-1, message = "上下文无效", data = false)
                            } else {
                                FileUtils.notifySystemToScan(File(filePath))
                                request.createResponse(0, data = true)
                            }
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "通知系统扫描文件失败: ${e.message}", data = false)
                        }
                    }
                }

                FileUtilsCallMethod.getFsTotalSize -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = 0L)
                    } else {
                        try {
                            val size = FileUtils.getFsTotalSize(filePath)
                            request.createResponse(0, data = size)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "获取文件系统总大小失败: ${e.message}", data = 0L)
                        }
                    }
                }

                FileUtilsCallMethod.getFsAvailableSize -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    if (filePath.isEmpty()) {
                        request.createResponse(-1, message = "filePath参数不能为空", data = 0L)
                    } else {
                        try {
                            val size = FileUtils.getFsAvailableSize(filePath)
                            request.createResponse(0, data = size)
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            request.createResponse(-1, message = "获取文件系统可用大小失败: ${e.message}", data = 0L)
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

