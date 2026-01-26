package com.ven.assists.web.gallery

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.blankj.utilcode.util.LogUtils
import java.io.File
import java.io.FileInputStream

/**
 * 系统相册操作工具类
 * 提供保存图片/视频到相册和从相册删除的功能
 */
object GalleryUtils {

    /**
     * 保存图片到系统相册
     * @param context Context
     * @param file 图片文件
     * @param displayName 显示名称（可选，默认使用文件名）
     * @return GalleryResult 包含 uri、id、type 和 success
     */
    fun addImageToGallery(context: Context, file: File, displayName: String? = null): GalleryResult {
        val fileName = displayName ?: file.name
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addImageToGalleryQ(context, file, fileName)
        } else {
            addImageToGalleryLegacy(context, file, fileName)
        }
    }

    /**
     * 保存视频到系统相册
     * @param context Context
     * @param file 视频文件
     * @param displayName 显示名称（可选，默认使用文件名）
     * @return GalleryResult 包含 uri、id、type 和 success
     */
    fun addVideoToGallery(context: Context, file: File, displayName: String? = null): GalleryResult {
        val fileName = displayName ?: file.name
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addVideoToGalleryQ(context, file, fileName)
        } else {
            addVideoToGalleryLegacy(context, file, fileName)
        }
    }

    /**
     * 从系统相册删除媒体文件
     * @param context Context
     * @param uri 媒体文件的URI
     * @return 删除的行数，大于0表示成功
     */
    fun deleteFromGallery(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            LogUtils.e(e, "从相册删除失败")
            0
        }
    }

    /**
     * 从系统相册删除媒体文件（通过ID和类型）
     * @param context Context
     * @param id 媒体文件的ID
     * @param type 媒体类型，"image" 或 "video"
     * @return 删除的行数，大于0表示成功
     */
    fun deleteFromGallery(context: Context, id: Long, type: String): Int {
        val uri = when (type.lowercase()) {
            "image" -> Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                id.toString()
            )
            "video" -> Uri.withAppendedPath(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                id.toString()
            )
            else -> return 0
        }
        return deleteFromGallery(context, uri)
    }

    /**
     * Android 10 及以上添加图片到相册
     */
    private fun addImageToGalleryQ(context: Context, file: File, fileName: String): GalleryResult {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(file.extension))
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return GalleryResult(null, null, null, false)

        val id = getMediaId(uri)

        return try {
            val outputStream = context.contentResolver.openOutputStream(uri) ?: return GalleryResult(uri, id, "image", false)
            outputStream.use { os ->
                FileInputStream(file).use { inputStream ->
                    inputStream.copyTo(os)
                }
            }
            GalleryResult(uri, id, "image", true)
        } catch (e: Exception) {
            LogUtils.e(e, "写入图片到相册失败")
            // 如果写入失败，删除已创建的记录
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (deleteException: Exception) {
                LogUtils.e(deleteException, "删除相册记录失败")
            }
            GalleryResult(uri, id, "image", false)
        }
    }

    /**
     * Android 10 以下添加图片到相册
     */
    private fun addImageToGalleryLegacy(context: Context, file: File, fileName: String): GalleryResult {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(file.extension))
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        val id = uri?.let { getMediaId(it) }
        return GalleryResult(uri, id, "image", uri != null)
    }

    /**
     * Android 10 及以上添加视频到相册
     */
    private fun addVideoToGalleryQ(context: Context, file: File, fileName: String): GalleryResult {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(file.extension))
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }

        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return GalleryResult(null, null, null, false)

        val id = getMediaId(uri)

        return try {
            val outputStream = context.contentResolver.openOutputStream(uri) ?: return GalleryResult(uri, id, "video", false)
            outputStream.use { os ->
                FileInputStream(file).use { inputStream ->
                    inputStream.copyTo(os)
                }
            }
            GalleryResult(uri, id, "video", true)
        } catch (e: Exception) {
            LogUtils.e(e, "写入视频到相册失败")
            // 如果写入失败，删除已创建的记录
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (deleteException: Exception) {
                LogUtils.e(deleteException, "删除相册记录失败")
            }
            GalleryResult(uri, id, "video", false)
        }
    }

    /**
     * Android 10 以下添加视频到相册
     */
    private fun addVideoToGalleryLegacy(context: Context, file: File, fileName: String): GalleryResult {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(file.extension))
        }

        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        val id = uri?.let { getMediaId(it) }
        return GalleryResult(uri, id, "video", uri != null)
    }

    /**
     * 从URI中提取媒体ID
     */
    private fun getMediaId(uri: Uri): Long? {
        return try {
            val segments = uri.pathSegments
            if (segments.isNotEmpty()) {
                segments.last().toLongOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            LogUtils.e(e, "获取媒体ID失败")
            null
        }
    }

    /**
     * 判断是否为图片文件
     */
    fun isImageFile(extension: String): Boolean {
        return extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")
    }

    /**
     * 判断是否为视频文件
     */
    fun isVideoFile(extension: String): Boolean {
        return extension.lowercase() in listOf("mp4", "avi", "mov", "wmv", "flv", "mkv", "3gp", "webm", "m4v")
    }

    /**
     * 获取文件的 MIME 类型
     */
    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "mkv" -> "video/x-matroska"
            "3gp" -> "video/3gpp"
            "webm" -> "video/webm"
            "m4v" -> "video/x-m4v"
            else -> "application/octet-stream"
        }
    }
}
