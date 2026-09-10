package com.ven.assists.web.gallery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.PermissionUtils
import com.ven.assists.log.logAppend
import com.google.gson.JsonObject
import com.ven.assists.web.CallRequest
import com.ven.assists.web.CallRequestParser
import com.ven.assists.web.CallResponse
import com.ven.assists.web.JavascriptInterfaceContext
import com.ven.assists.web.createResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume

/**
 * 系统相册相关的 JavascriptInterface
 * 提供添加图片/视频到相册和从相册删除的功能
 */
class GalleryJavascriptInterface(val webView: WebView) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private fun galleryTimeLog(message: String) {
        message.logAppend()
    }

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
        val request = CallRequestParser.parse(originJson) ?: run {
            callbackResponse(CallResponse<Any>(code = -1, message = "请求解析失败", data = null))
            return
        }
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

                GalleryCallMethod.checkMediaPermissions -> {
                    handleCheckMediaPermissions(request)
                }

                GalleryCallMethod.requestMediaPermissions -> {
                    handleRequestMediaPermissions(request)
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
     * - timestamp: 媒体时间戳（可选，Unix epoch 毫秒；未传入则不更新）
     */
    private fun handleAddImageToGallery(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val filePath = request.arguments?.get("filePath")?.asString ?: ""
        val displayName = request.arguments?.get("displayName")?.asString
        val timestamp = request.arguments?.get("timestamp")?.takeUnless { it.isJsonNull }?.asLong
        galleryTimeLog(
            "[GalleryTime] JS addImage request, filePath=$filePath, displayName=$displayName, " +
                "timestampMs=$timestamp, callbackId=${request.callbackId}"
        )

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
            val result = GalleryUtils.addImageToGallery(context, file, fileName, timestamp)

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
     * - timestamp: 媒体时间戳（可选，Unix epoch 毫秒；未传入则不更新）
     */
    private fun handleAddVideoToGallery(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val filePath = request.arguments?.get("filePath")?.asString ?: ""
        val displayName = request.arguments?.get("displayName")?.asString
        val timestamp = request.arguments?.get("timestamp")?.takeUnless { it.isJsonNull }?.asLong
        galleryTimeLog(
            "[GalleryTime] JS addVideo request, filePath=$filePath, displayName=$displayName, " +
                "timestampMs=$timestamp, callbackId=${request.callbackId}"
        )

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
            val result = GalleryUtils.addVideoToGallery(context, file, fileName, timestamp)

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

    private data class MediaPermissionRequest(
        val readImages: Boolean,
        val readVideos: Boolean,
        val write: Boolean
    )

    private fun parseMediaPermissionRequest(request: CallRequest<JsonObject>): MediaPermissionRequest {
        val arguments = request.arguments
        return MediaPermissionRequest(
            readImages = arguments?.get("readImages")?.asBoolean ?: true,
            readVideos = arguments?.get("readVideos")?.asBoolean ?: true,
            write = arguments?.get("write")?.asBoolean ?: true
        )
    }

    private fun mediaPermissionNames(
        request: MediaPermissionRequest
    ): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()
        return buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (request.readImages) add(Manifest.permission.READ_MEDIA_IMAGES)
                if (request.readVideos) add(Manifest.permission.READ_MEDIA_VIDEO)
                if ((request.readImages || request.readVideos)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ) {
                    add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
            } else if (request.readImages || request.readVideos) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (request.write && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.distinct()
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun mediaPermissionStatus(
        context: Context,
        request: MediaPermissionRequest
    ): JsonObject {
        val selectedVisualPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            && hasPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        val readImages = when {
            !request.readImages -> true
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES) || selectedVisualPermission
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> true
        }
        val readVideos = when {
            !request.readVideos -> true
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                hasPermission(context, Manifest.permission.READ_MEDIA_VIDEO) || selectedVisualPermission
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> true
        }
        // Android 10+ 的 MediaStore 插入由应用自行创建的媒体不需要 WRITE_EXTERNAL_STORAGE。
        val write = !request.write || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        return JsonObject().apply {
            addProperty("readImages", readImages)
            addProperty("readVideos", readVideos)
            addProperty("write", write)
            addProperty("allGranted", readImages && readVideos && write)
            addProperty("apiLevel", Build.VERSION.SDK_INT)
        }
    }

    private fun handleCheckMediaPermissions(
        request: CallRequest<JsonObject>
    ): CallResponse<JsonObject> {
        val context = JavascriptInterfaceContext.getContext()
            ?: return request.createResponse(-1, message = "无法获取Context", data = JsonObject())
        return request.createResponse(
            0,
            data = mediaPermissionStatus(context, parseMediaPermissionRequest(request))
        )
    }

    private suspend fun handleRequestMediaPermissions(
        request: CallRequest<JsonObject>
    ): CallResponse<JsonObject> {
        val context = JavascriptInterfaceContext.getContext()
            ?: return request.createResponse(-1, message = "无法获取Context", data = JsonObject())
        val permissionRequest = parseMediaPermissionRequest(request)
        val permissions = mediaPermissionNames(permissionRequest)
        if (permissions.isEmpty()) {
            return request.createResponse(0, data = mediaPermissionStatus(context, permissionRequest))
        }
        val activity = JavascriptInterfaceContext.getActivity()
            ?: return request.createResponse(-1, message = "无法获取Activity，请在前台页面中请求权限", data = JsonObject())

        return suspendCancellableCoroutine { continuation ->
            activity.runOnUiThread {
                PermissionUtils.permission(*permissions.toTypedArray())
                    .callback(object : PermissionUtils.SimpleCallback {
                        override fun onGranted() {
                            if (continuation.isActive) {
                                continuation.resume(
                                    request.createResponse(
                                        0,
                                        data = mediaPermissionStatus(context, permissionRequest)
                                    )
                                )
                            }
                        }

                        override fun onDenied() {
                            if (continuation.isActive) {
                                continuation.resume(
                                    request.createResponse(
                                        -1,
                                        message = "媒体权限未完全授予",
                                        data = mediaPermissionStatus(context, permissionRequest)
                                    )
                                )
                            }
                        }
                    }).request()
            }
        }
    }

    /**
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
