package com.ven.assists.screenshot

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.os.Build
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.getBoundsInScreen
import com.ven.assists.AssistsCore.takeScreenshot
import java.io.ByteArrayOutputStream

/**
 * 截图专用模块：全屏/节点区域截图及 Bitmap 转 Base64
 */
object AssistsScreenshot {

    /**
     * 将 Bitmap 编码为 Base64 字符串
     * @param withDataUrlPrefix 为 true 时返回 data:image/...;base64,... 格式
     */
    fun bitmapToBase64(
        bitmap: Bitmap,
        format: CompressFormat = CompressFormat.PNG,
        quality: Int = 100,
        withDataUrlPrefix: Boolean = true,
    ): String {
        val mimeType = compressFormatToMimeType(format)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(format, quality, outputStream)
        val encoded = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        return if (withDataUrlPrefix) {
            "data:$mimeType;base64,$encoded"
        } else {
            encoded
        }
    }

    fun compressFormatToMimeType(format: CompressFormat): String {
        return when (format) {
            CompressFormat.PNG -> "image/png"
            CompressFormat.JPEG -> "image/jpeg"
            CompressFormat.WEBP -> "image/webp"
            else -> "image/png"
        }
    }

    fun parseCompressFormat(formatStr: String?): CompressFormat {
        return when (formatStr?.uppercase()) {
            "JPEG", "JPG" -> CompressFormat.JPEG
            "WEBP" -> CompressFormat.WEBP
            else -> CompressFormat.PNG
        }
    }

    /**
     * 从全屏截图中裁剪节点区域
     */
    fun cropNodeRegion(fullScreenshot: Bitmap, node: AccessibilityNodeInfo): Bitmap? {
        return runCatching {
            val bounds = node.getBoundsInScreen()
            Bitmap.createBitmap(
                fullScreenshot,
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height(),
            )
        }.getOrNull()
    }

    /**
     * 截取全屏 Bitmap（API 30+）
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun takeFullScreenBitmap(): Bitmap? {
        return AssistsCore.takeScreenshot()
    }

    /**
     * 截取全屏并返回 Base64（API 30+）
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun takeFullScreenBase64(
        format: CompressFormat = CompressFormat.PNG,
        quality: Int = 100,
        withDataUrlPrefix: Boolean = true,
    ): String? {
        val bitmap = takeFullScreenBitmap() ?: return null
        return try {
            bitmapToBase64(bitmap, format, quality, withDataUrlPrefix)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 截取节点区域并返回 Base64（API 30+）
     * @param fullScreenshot 可复用已有全屏截图，为 null 时自动截取
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun takeNodeScreenshotBase64(
        node: AccessibilityNodeInfo,
        fullScreenshot: Bitmap? = null,
        format: CompressFormat = CompressFormat.PNG,
        quality: Int = 100,
        withDataUrlPrefix: Boolean = true,
    ): String? {
        val screenshot = fullScreenshot ?: takeFullScreenBitmap() ?: return null
        val shouldRecycleFull = fullScreenshot == null
        return try {
            val nodeBitmap = node.takeScreenshot(screenshot = screenshot) ?: return null
            try {
                bitmapToBase64(nodeBitmap, format, quality, withDataUrlPrefix)
            } finally {
                nodeBitmap.recycle()
            }
        } finally {
            if (shouldRecycleFull) {
                screenshot.recycle()
            }
        }
    }
}
