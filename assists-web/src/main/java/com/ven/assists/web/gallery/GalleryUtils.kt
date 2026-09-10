package com.ven.assists.web.gallery

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.blankj.utilcode.util.LogUtils
import com.ven.assists.log.logAppend
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * 系统相册操作工具类
 * 提供保存图片/视频到相册和从相册删除的功能
 */
object GalleryUtils {

    private fun galleryTimeLog(message: String) {
        message.logAppend()
    }

    /**
     * 保存图片到系统相册
     * @param context Context
     * @param file 图片文件
     * @param displayName 显示名称（可选，默认使用文件名）
     * @param timestamp 媒体时间戳（可选，Unix epoch 毫秒；未传入则不更新）
     * @return GalleryResult 包含 uri、id、type 和 success
     */
    fun addImageToGallery(
        context: Context,
        file: File,
        displayName: String? = null,
        timestamp: Long? = null
    ): GalleryResult {
        val fileName = displayName ?: file.name
        galleryTimeLog(
            "[GalleryTime] addImage file=${file.absolutePath}, size=${file.length()}, " +
                "displayName=$fileName, timestampMs=$timestamp, api=${Build.VERSION.SDK_INT}"
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addImageToGalleryQ(context, file, fileName, timestamp)
        } else {
            addImageToGalleryLegacy(context, file, fileName, timestamp)
        }
    }

    /**
     * 保存视频到系统相册
     * @param context Context
     * @param file 视频文件
     * @param displayName 显示名称（可选，默认使用文件名）
     * @param timestamp 媒体时间戳（可选，Unix epoch 毫秒；未传入则不更新）
     * @return GalleryResult 包含 uri、id、type 和 success
     */
    fun addVideoToGallery(
        context: Context,
        file: File,
        displayName: String? = null,
        timestamp: Long? = null
    ): GalleryResult {
        val fileName = displayName ?: file.name
        galleryTimeLog(
            "[GalleryTime] addVideo file=${file.absolutePath}, size=${file.length()}, " +
                "displayName=$fileName, timestampMs=$timestamp, api=${Build.VERSION.SDK_INT}"
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addVideoToGalleryQ(context, file, fileName, timestamp)
        } else {
            addVideoToGalleryLegacy(context, file, fileName, timestamp)
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

    /** Android 10 及以上添加图片到相册。 */
    private fun addImageToGalleryQ(
        context: Context,
        file: File,
        fileName: String,
        timestamp: Long?
    ): GalleryResult {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(file.extension))
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            timestamp?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        galleryTimeLog(
            "[GalleryTime] insert image branch=q, collection=$collection, " +
                "displayName=$fileName, mime=${getMimeType(file.extension)}, " +
                "relativePath=${Environment.DIRECTORY_PICTURES}, requestedDateTakenMs=$timestamp"
        )
        val uri = context.contentResolver.insert(collection, values)
            ?: run {
                galleryTimeLog("[GalleryTime] image insert returned null")
                return GalleryResult(null, null, null, false)
            }
        val id = getMediaId(uri)
        galleryTimeLog("[GalleryTime] image inserted uri=$uri, id=$id")
        return try {
            val copiedBytes = copyFileToUri(context, file, uri)
            galleryTimeLog("[GalleryTime] image copied uri=$uri, bytes=$copiedBytes")
            if (timestamp != null && !updateMediaTime(context, uri, "image", timestamp, null)) {
                throw IllegalStateException("更新图片媒体时间失败")
            }
            GalleryResult(uri, id, "image", true)
        } catch (e: Exception) {
            LogUtils.e(e, "写入图片到相册失败")
            deleteInsertedMedia(context, uri)
            GalleryResult(uri, id, "image", false)
        }
    }

    /** Android 10 以下添加图片到相册。 */
    private fun addImageToGalleryLegacy(
        context: Context,
        file: File,
        fileName: String,
        timestamp: Long?
    ): GalleryResult {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(file.extension))
            timestamp?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val id = uri?.let { getMediaId(it) }
        if (uri != null && timestamp != null && !updateMediaTime(context, uri, "image", timestamp, file.absolutePath)) {
            deleteInsertedMedia(context, uri)
            return GalleryResult(uri, id, "image", false)
        }
        return GalleryResult(uri, id, "image", uri != null)
    }

    /** Android 10 及以上添加视频到相册。 */
    private fun addVideoToGalleryQ(
        context: Context,
        file: File,
        fileName: String,
        timestamp: Long?
    ): GalleryResult {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(file.extension))
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
            timestamp?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        galleryTimeLog(
            "[GalleryTime] insert video branch=q, collection=$collection, " +
                "displayName=$fileName, mime=${getMimeType(file.extension)}, " +
                "relativePath=${Environment.DIRECTORY_DCIM}, requestedDateTakenMs=$timestamp"
        )
        val uri = context.contentResolver.insert(collection, values)
            ?: run {
                galleryTimeLog("[GalleryTime] video insert returned null")
                return GalleryResult(null, null, null, false)
            }
        val id = getMediaId(uri)
        galleryTimeLog("[GalleryTime] video inserted uri=$uri, id=$id")
        return try {
            val copiedBytes = copyFileToUri(context, file, uri)
            galleryTimeLog("[GalleryTime] video copied uri=$uri, bytes=$copiedBytes")
            if (timestamp != null && !updateMediaTime(context, uri, "video", timestamp, null)) {
                throw IllegalStateException("更新视频媒体时间失败")
            }
            GalleryResult(uri, id, "video", true)
        } catch (e: Exception) {
            LogUtils.e(e, "写入视频到相册失败")
            deleteInsertedMedia(context, uri)
            GalleryResult(uri, id, "video", false)
        }
    }

    /** Android 10 以下添加视频到相册。 */
    private fun addVideoToGalleryLegacy(
        context: Context,
        file: File,
        fileName: String,
        timestamp: Long?
    ): GalleryResult {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(file.extension))
            timestamp?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        val id = uri?.let { getMediaId(it) }
        if (uri != null && timestamp != null && !updateMediaTime(context, uri, "video", timestamp, file.absolutePath)) {
            deleteInsertedMedia(context, uri)
            return GalleryResult(uri, id, "video", false)
        }
        return GalleryResult(uri, id, "video", uri != null)
    }

    private fun copyFileToUri(context: Context, file: File, uri: Uri): Long {
        var copiedBytes = 0L
        context.contentResolver.openOutputStream(uri)?.use { output ->
            FileInputStream(file).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    copiedBytes += count
                }
            }
        } ?: throw IllegalStateException("无法打开相册媒体输出流")
        return copiedBytes
    }

    private fun deleteInsertedMedia(context: Context, uri: Uri) {
        runCatching {
            val deletedRows = context.contentResolver.delete(uri, null, null)
            galleryTimeLog("[GalleryTime] cleanup inserted media, uri=$uri, deletedRows=$deletedRows")
        }.onFailure {
            LogUtils.e(it, "删除相册记录失败")
        }
    }

    /**
     * 修改媒体文件内部时间，再让 MediaStore 扫描文件并回查 DATE_TAKEN。
     * timestamp 使用 Unix epoch 毫秒；MP4 内部 creation_time 使用 1904 纪元秒数。
     */
    private fun updateMediaTime(
        context: Context,
        uri: Uri,
        type: String,
        timestamp: Long,
        pathHint: String?
    ): Boolean {
        galleryTimeLog(
            "[GalleryTime] updateMediaTime start, type=$type, uri=$uri, " +
                "timestampMs=$timestamp, pathHint=$pathHint"
        )
        val patched = if (type == "image") {
            patchImageExifTime(context, uri, timestamp)
        } else {
            patchMp4MvhdTime(context, uri, timestamp)
        }
        galleryTimeLog("[GalleryTime] file metadata patched=$patched, type=$type, uri=$uri")
        if (!patched) return false

        val path = pathHint ?: queryDataPath(context, uri)
        if (path == null) {
            galleryTimeLog("[GalleryTime] scan skipped because DATA path is null, uri=$uri")
            return false
        }
        galleryTimeLog("[GalleryTime] scanFile path=$path, uri=$uri, expectedDateTakenMs=$timestamp")
        MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        val deadline = System.currentTimeMillis() + 5000L
        var attempt = 0
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(200L)
            attempt++
            val actual = queryDateTaken(context, uri)
            val delta = actual?.minus(timestamp)
            galleryTimeLog(
                "[GalleryTime] DATE_TAKEN poll attempt=$attempt, uri=$uri, " +
                    "actualMs=$actual, expectedMs=$timestamp, deltaMs=$delta"
            )
            if (actual != null && kotlin.math.abs(actual - timestamp) <= 2000L) {
                galleryTimeLog("[GalleryTime] DATE_TAKEN verified, uri=$uri, attempts=$attempt")
                return true
            }
        }
        galleryTimeLog("[GalleryTime] DATE_TAKEN verify timeout, uri=$uri, expectedMs=$timestamp")
        return false
    }

    private fun patchImageExifTime(context: Context, uri: Uri, timestamp: Long): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val date = android.text.format.DateFormat.format(
                    "yyyy:MM:dd HH:mm:ss", timestamp
                ).toString()
                ExifInterface(pfd.fileDescriptor).apply {
                    setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, date)
                    setAttribute(ExifInterface.TAG_DATETIME, date)
                    saveAttributes()
                }
                galleryTimeLog("[GalleryTime] EXIF patched, uri=$uri, date=$date, timestampMs=$timestamp")
            } != null
        } catch (e: Exception) {
            LogUtils.e(e, "更新图片媒体时间失败")
            false
        }
    }

    /** 参照 MediaStoreDemo，修改 MP4 顶层 moov/mvhd 的 creation_time。 */
    private fun patchMp4MvhdTime(context: Context, uri: Uri, timestamp: Long): Boolean {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "rw") ?: return false
            pfd.use { descriptor ->
                val input = FileInputStream(descriptor.fileDescriptor)
                val output = FileOutputStream(descriptor.fileDescriptor)
                input.use { inputStream ->
                    output.use { outputStream ->
                        val readable = inputStream.channel
                        val writable = outputStream.channel
                        val fileSize = readable.size()
                        galleryTimeLog("[GalleryTime] MP4 scan start, uri=$uri, fileSize=$fileSize, timestampMs=$timestamp")
                        val header = ByteBuffer.allocate(8)
                        var position = 0L
                        while (position + 8L <= fileSize) {
                            if (!readBoxHeader(readable, position, header)) break
                            val boxSize = readUInt32(header).let {
                                if (it == 0L) fileSize - position else it
                            }
                            val boxType = readBoxType(header)
                            if (boxSize < 8L || position + boxSize > fileSize) break
                            if (boxType == "moov" && patchMvhd(
                                    readable,
                                    writable,
                                    position,
                                    boxSize,
                                    uri,
                                    timestamp
                                )
                            ) {
                                return@use true
                            }
                            position += boxSize
                        }
                        galleryTimeLog("[GalleryTime] MP4 mvhd not found, uri=$uri")
                        false
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.e(e, "更新视频媒体时间失败")
            false
        }
    }

    private fun patchMvhd(
        readable: java.nio.channels.FileChannel,
        writable: java.nio.channels.FileChannel,
        moovPosition: Long,
        moovSize: Long,
        uri: Uri,
        timestamp: Long
    ): Boolean {
        val header = ByteBuffer.allocate(8)
        var position = moovPosition + 8L
        val end = moovPosition + moovSize
        while (position + 8L <= end) {
            if (!readBoxHeader(readable, position, header)) return false
            val boxSize = readUInt32(header).let { if (it == 0L) end - position else it }
            val boxType = readBoxType(header)
            if (boxSize < 8L || position + boxSize > end) return false
            if (boxType == "mvhd") {
                galleryTimeLog("[GalleryTime] MP4 mvhd found, uri=$uri, position=$position, boxSize=$boxSize")
                val versionBuffer = ByteBuffer.allocate(1)
                readable.position(position + 12L)
                if (readable.read(versionBuffer) != 1) return false
                val version = versionBuffer.array()[0].toInt()
                val creationSeconds = timestamp / 1000L + 2082844800L
                galleryTimeLog(
                    "[GalleryTime] MP4 mvhd encode, uri=$uri, version=$version, " +
                        "timestampMs=$timestamp, creationSeconds=$creationSeconds"
                )
                val value = if (version == 1) {
                    ByteBuffer.allocate(8).apply {
                        putLong(creationSeconds)
                        flip()
                    }
                } else {
                    ByteBuffer.allocate(4).apply {
                        putInt(creationSeconds.toInt())
                        flip()
                    }
                }
                writable.position(position + 12L)
                while (value.hasRemaining()) writable.write(value)
                return true
            }
            position += boxSize
        }
        return false
    }

    private fun readBoxHeader(
        channel: java.nio.channels.FileChannel,
        position: Long,
        buffer: ByteBuffer
    ): Boolean {
        buffer.clear()
        channel.position(position)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) return false
        }
        buffer.flip()
        return true
    }

    private fun readUInt32(buffer: ByteBuffer): Long =
        buffer.getInt(0).toLong() and 0xffffffffL

    private fun readBoxType(buffer: ByteBuffer): String {
        val type = ByteArray(4)
        buffer.position(4)
        buffer.get(type)
        return String(type, StandardCharsets.US_ASCII)
    }

    private fun queryDataPath(context: Context, uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }

    private fun queryDateTaken(context: Context, uri: Uri): Long? {
        return context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATE_TAKEN),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }

    /** 从 URI 中提取媒体 ID。 */
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
