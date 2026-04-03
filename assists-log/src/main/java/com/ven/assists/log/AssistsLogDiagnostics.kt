package com.ven.assists.log

import android.graphics.Bitmap.CompressFormat
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.blankj.utilcode.util.DeviceUtils
import com.blankj.utilcode.util.LogUtils
import com.google.gson.Gson
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.takeScreenshotSave
import com.ven.assists.window.AssistsWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 诊断：固定路径截图、节点树 JSON、multipart 上传（逻辑集中在 assists-log）
 */
object AssistsLogDiagnostics {

    private const val UPLOAD_KEY = "ulk_lQ1sVFKCcLQMI5RBpz5lo8bchWssCwoshUDOsynC-CM"

    /** Debug 包：局域网调试；Release 包：线上日志服务 */
    private fun defaultUploadLogsUrl(): String = if (BuildConfig.DEBUG) {
//        "http://192.168.0.2:3001/api/logs/upload"
        "http://47.242.231.216:3002/api/logs/upload"

    } else {
        "http://47.242.231.216:3002/api/logs/upload"
    }

    /**
     * 与 [defaultUploadLogsUrl] 同环境的管理后台根地址（协议、IP、端口与上传接口一致）
     */
    fun adminWebBaseUrl(): String = if (BuildConfig.DEBUG) {
//        "http://192.168.0.2:3001"
        "http://47.242.231.216:3002"

    } else {
        "http://47.242.231.216:3002"
    }

    private val uploadResponseGson = Gson()

    private fun extensionForFormat(format: CompressFormat): String = when (format) {
        CompressFormat.PNG -> "png"
        CompressFormat.JPEG -> "jpg"
        CompressFormat.WEBP -> "webp"
        else -> "png"
    }

    private fun mediaTypeForFormat(format: CompressFormat): String = when (format) {
        CompressFormat.PNG -> "image/png"
        CompressFormat.JPEG -> "image/jpeg"
        CompressFormat.WEBP -> "image/webp"
        else -> "application/octet-stream"
    }

    /**
     * 保存屏幕截图到固定路径（或自定义 [file]）；[targetNode] 非空则截取该节点区域
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun takeScreenshotSaveToDefault(
        file: File? = null,
        format: CompressFormat = CompressFormat.PNG,
        overlayHiddenDelayMillis: Long = 250L,
        targetNode: AccessibilityNodeInfo? = null
    ): File? {
        val ext = extensionForFormat(format)
        val outFile = file ?: AssistsLogPaths.screenshotFile(ext)
        AssistsWindowManager.hideAll()
        delay(overlayHiddenDelayMillis)
        val saved = if (targetNode == null) {
            AssistsCore.takeScreenshotSave(file = outFile, format = format)
        } else {
            targetNode.takeScreenshotSave(file = outFile, format = format)
        }
        AssistsWindowManager.showTop()
        return saved
    }

    /**
     * 将当前根节点树保存为 JSON，默认使用 [AssistsLogPaths.nodeTreeFile]
     */
    fun saveRootNodeTreeJsonToDefault(
        prettyPrint: Boolean = true,
        file: File? = null
    ): File? {
        val outFile = file ?: AssistsLogPaths.nodeTreeFile()
        return AssistsCore.saveRootNodeTreeJson(file = outFile, prettyPrint = prettyPrint)
    }

    /**
     * 顺序：截图 → 保存节点树 → multipart 上传
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun uploadLogs(
        baseUrl: String = defaultUploadLogsUrl(),
        format: CompressFormat = CompressFormat.PNG,
        prettyPrint: Boolean = true,
        overlayHiddenDelayMillis: Long = 250L,
    ): AssistsLogUploadResult {
        val screenshotFile = AssistsLogPaths.screenshotFile(extensionForFormat(format))
        val logFile = AssistsLogPaths.logFile()
        val nodeTreeFile = AssistsLogPaths.nodeTreeFile()

        if (!logFile.exists()) {
            logFile.parentFile?.mkdirs()
            logFile.createNewFile()
        }

        val savedScreenshot = takeScreenshotSaveToDefault(
            file = screenshotFile,
            format = format,
            overlayHiddenDelayMillis = overlayHiddenDelayMillis,
        )
        if (savedScreenshot == null) {
            return AssistsLogUploadResult(false, "截图保存失败")
        }

        val savedNode =
            saveRootNodeTreeJsonToDefault(prettyPrint = prettyPrint, file = nodeTreeFile) ?: return AssistsLogUploadResult(false, "保存节点树失败")

        return withContext(Dispatchers.IO) {
            runCatching {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "log_file",
                        logFile.name,
                        logFile.asRequestBody("text/plain".toMediaType())
                    )
                    .addFormDataPart(
                        "screenshot_file",
                        savedScreenshot.name,
                        savedScreenshot.asRequestBody(mediaTypeForFormat(format).toMediaType())
                    )
                    .addFormDataPart(
                        "node_info_file",
                        savedNode.name,
                        savedNode.asRequestBody("application/json".toMediaType())
                    )
                    .addFormDataPart("device_model", DeviceUtils.getModel())
                    .addFormDataPart("device_unique_id", DeviceUtils.getUniqueDeviceId())
                    .build()

                val httpRequest = Request.Builder()
                    .url(baseUrl)
                    .header("X-Upload-Key", UPLOAD_KEY)
                    .post(multipart)
                    .build()

                client.newCall(httpRequest).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val localLog = logFile.absolutePath
                    val localShot = savedScreenshot.absolutePath
                    val localNode = savedNode.absolutePath
                    if (!resp.isSuccessful) {
                        return@use AssistsLogUploadResult(
                            success = false,
                            message = "上传失败: HTTP ${resp.code}",
                            httpCode = resp.code,
                            responseBody = bodyStr,
                            localLogFilePath = localLog,
                            localScreenshotFilePath = localShot,
                            localNodeTreeFilePath = localNode
                        )
                    }
                    val parsed = runCatching {
                        uploadResponseGson.fromJson(bodyStr, LogUploadApiResponse::class.java)
                    }.getOrNull()
                    if (parsed == null) {
                        return@use AssistsLogUploadResult(
                            success = false,
                            message = "响应解析失败",
                            httpCode = resp.code,
                            responseBody = bodyStr,
                            localLogFilePath = localLog,
                            localScreenshotFilePath = localShot,
                            localNodeTreeFilePath = localNode
                        )
                    }
                    val ok = parsed.success && parsed.data != null
                    AssistsLogUploadResult(
                        success = ok,
                        message = parsed.message.orEmpty(),
                        httpCode = resp.code,
                        responseBody = bodyStr,
                        data = parsed.data,
                        localLogFilePath = localLog,
                        localScreenshotFilePath = localShot,
                        localNodeTreeFilePath = localNode
                    )
                }
            }.getOrElse { e ->
                LogUtils.e(e)
                AssistsLogUploadResult(false, "上传失败: ${e.message}", cause = e)
            }
        }
    }
}

/**
 * 与上传接口 HTTP 200 时 body 结构一致（成功时 [data] 非空）
 */
data class LogUploadApiResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: LogUploadData? = null
)

/**
 * 服务端返回的 data（相对路径等）
 */
data class LogUploadData(
    val id: Long = 0,
    val user_id: String = "",
    val log_file_path: String = "",
    val screenshot_file_path: String = "",
    val node_info_file_path: String = "",
    val created_at: String = ""
)

data class AssistsLogUploadResult(
    val success: Boolean,
    val message: String,
    val httpCode: Int? = null,
    val responseBody: String? = null,
    /** 解析成功时的服务端 data */
    val data: LogUploadData? = null,
    /** 本机参与上传的文件绝对路径 */
    val localLogFilePath: String? = null,
    val localScreenshotFilePath: String? = null,
    val localNodeTreeFilePath: String? = null,
    val cause: Throwable? = null
)
