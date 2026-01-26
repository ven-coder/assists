package com.ven.assists.web.gallery

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.net.toUri
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
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
import java.nio.charset.StandardCharsets

/**
 * 系统相册相关的 JavascriptInterface
 * 提供添加图片/视频到相册和从相册删除的功能
 */
class GalleryJavascriptInterface(val webView: WebView) {
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
        val js = String.format("javascript:assistsxGalleryCallback('%s')", encoded)
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
                GalleryCallMethod.addImageToGallery -> {
                    handleAddImageToGallery(request)
                }

                GalleryCallMethod.addVideoToGallery -> {
                    handleAddVideoToGallery(request)
                }

                GalleryCallMethod.deleteFromGallery -> {
                    handleDeleteFromGallery(request)
                }

                else -> {
                    request.createResponse(-1, message = "方法未支持", data = JsonObject())
                }
            }
            callbackResponse(response)
        }.onFailure {
            LogUtils.e(it)
            callbackResponse(request.createResponse(-1, message = it.message, data = JsonObject()))
        }
    }

    /**
     * 处理添加图片到相册的请求
     * 参数：
     * - filePath: 图片文件路径（必需）
     * - displayName: 显示名称（可选，默认使用文件名）
     */
    private fun handleAddImageToGallery(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val filePath = request.arguments?.get("filePath")?.asString ?: ""
        val displayName = request.arguments?.get("displayName")?.asString

        if (filePath.isEmpty()) {
            return request.createResponse(-1, message = "filePath参数不能为空", data = JsonObject())
        }

        val context = JavascriptInterfaceContext.getContext()
        if (context == null) {
            return request.createResponse(-1, message = "无法获取Context", data = JsonObject())
        }

        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return request.createResponse(-1, message = "文件不存在或不是有效文件: $filePath", data = JsonObject())
        }

        val fileName = displayName ?: file.name
        val fileExtension = file.extension.lowercase()

        // 验证是否为图片文件
        if (!GalleryUtils.isImageFile(fileExtension)) {
            return request.createResponse(-1, message = "文件不是有效的图片格式", data = JsonObject())
        }

        return try {
            val result = GalleryUtils.addImageToGallery(context, file, fileName)

            val responseData = JsonObject().apply {
                addProperty("success", result.success)
                addProperty("uri", result.uri?.toString())
                addProperty("id", result.id)
                addProperty("type", result.type)
                if (result.success) {
                    addProperty("message", "图片已成功添加到相册")
                } else {
                    addProperty("message", "添加图片到相册失败")
                }
            }

            request.createResponse(if (result.success) 0 else -1, data = responseData)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "添加图片到相册失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 处理添加视频到相册的请求
     * 参数：
     * - filePath: 视频文件路径（必需）
     * - displayName: 显示名称（可选，默认使用文件名）
     */
    private fun handleAddVideoToGallery(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val filePath = request.arguments?.get("filePath")?.asString ?: ""
        val displayName = request.arguments?.get("displayName")?.asString

        if (filePath.isEmpty()) {
            return request.createResponse(-1, message = "filePath参数不能为空", data = JsonObject())
        }

        val context = JavascriptInterfaceContext.getContext()
        if (context == null) {
            return request.createResponse(-1, message = "无法获取Context", data = JsonObject())
        }

        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return request.createResponse(-1, message = "文件不存在或不是有效文件: $filePath", data = JsonObject())
        }

        val fileName = displayName ?: file.name
        val fileExtension = file.extension.lowercase()

        // 验证是否为视频文件
        if (!GalleryUtils.isVideoFile(fileExtension)) {
            return request.createResponse(-1, message = "文件不是有效的视频格式", data = JsonObject())
        }

        return try {
            val result = GalleryUtils.addVideoToGallery(context, file, fileName)

            val responseData = JsonObject().apply {
                addProperty("success", result.success)
                addProperty("uri", result.uri?.toString())
                addProperty("id", result.id)
                addProperty("type", result.type)
                if (result.success) {
                    addProperty("message", "视频已成功添加到相册")
                } else {
                    addProperty("message", "添加视频到相册失败")
                }
            }

            request.createResponse(if (result.success) 0 else -1, data = responseData)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "添加视频到相册失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 处理从相册删除的请求
     * 参数：
     * - uri: 媒体文件的URI（必需，格式如：content://media/external/images/media/123）
     * 或者
     * - id: 媒体文件的ID（必需，需要配合type使用）
     * - type: 媒体类型，"image" 或 "video"（当使用id时必需）
     */
    private fun handleDeleteFromGallery(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val uriString = request.arguments?.get("uri")?.asString
        val id = request.arguments?.get("id")?.asLong
        val type = request.arguments?.get("type")?.asString

        val context = JavascriptInterfaceContext.getContext()
        if (context == null) {
            return request.createResponse(-1, message = "无法获取Context", data = JsonObject())
        }

        return try {
            val deletedRows = if (!uriString.isNullOrEmpty()) {
                try {
                    val uri = uriString.toUri()
                    GalleryUtils.deleteFromGallery(context, uri)
                } catch (e: Exception) {
                    LogUtils.e(e, "解析URI失败")
                    return request.createResponse(
                        -1,
                        message = "解析URI失败: ${e.message}",
                        data = JsonObject()
                    )
                }
            } else if (id != null && !type.isNullOrEmpty()) {
                GalleryUtils.deleteFromGallery(context, id, type)
            } else {
                return request.createResponse(
                    -1,
                    message = "参数错误：需要提供uri或(id和type)",
                    data = JsonObject()
                )
            }

            val success = deletedRows > 0

            val responseData = JsonObject().apply {
                addProperty("success", success)
                addProperty("deletedRows", deletedRows)
                if (success) {
                    addProperty("message", "已成功从相册删除")
                } else {
                    addProperty("message", "删除失败：未找到对应的媒体文件")
                }
            }

            request.createResponse(if (success) 0 else -1, data = responseData)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "从相册删除失败: ${e.message}", data = JsonObject())
        }
    }

}
