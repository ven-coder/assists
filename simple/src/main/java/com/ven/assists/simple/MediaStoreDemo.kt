package com.ven.assists.simple

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface

/**
 * 相册媒体读取演示：遍历系统相册的图片与视频，将真实路径输出到 Logcat
 *
 * Logcat 过滤 tag：MediaStoreDemo
 */
object MediaStoreDemo {
    private const val TAG = "MediaStoreDemo"

    /** 待授权后重试置顶的媒体（Android 10+ 修改其他应用创建的媒体需弹系统授权框） */
    private var pendingTopItem: MediaItem? = null

    /** 遍历相册全部图片与视频，按拍摄时间混排输出真实路径（与系统相册顺序一致），并输出总数汇总 */
    fun readAllMedia(context: Context) {
        Log.d(TAG, "Start reading media store...")
        val sorted = queryAllMediaMixed(context)
        sorted.forEach { item ->
            Log.d(TAG, "[${item.type}] id=${item.id} taken=${item.taken} name=${item.name} path=${item.path}")
        }
        val imageCount = sorted.count { it.type == "image" }
        val videoCount = sorted.count { it.type == "video" }
        Log.d(TAG, "Media store total: image=$imageCount, video=$videoCount, all=${sorted.size}")
    }

    /** 查询全部图片与视频，按拍摄时间倒序混排（与系统相册顺序一致） */
    fun queryAllMediaMixed(context: Context): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()
        mediaList += query(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image")
        mediaList += query(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video")
        // 与相册一致：按拍摄时间倒序混排；无拍摄时间的记录回退按入库时间排
        return mediaList.sortedWith(
            compareByDescending<MediaItem> { it.sortTime }
                .thenByDescending { it.dateAdded }
        )
    }

    /** 置顶操作结果 */
    sealed class TopResult {
        /** 修改成功 */
        data class Success(val name: String, val path: String) : TopResult()

        /** 需要用户授权（Android 10+ 修改其他应用创建的媒体），调用方需发起系统的授权确认框 */
        data class NeedGrant(val grantIntentSender: IntentSender) : TopResult()

        /** 相册混排数量不足 5 条 */
        data class TooFew(val size: Int) : TopResult()

        /** 其他失败 */
        data class Error(val reason: String) : TopResult()
    }

    /**
     * 将相册混排顺序中的第 5 条（index=4）的拍摄时间改为当前时间，
     * 使其在相册中排到最前
     *
     * Android 16 起 MediaStore.DATE_TAKEN 为只读列，第三方 update 会被静默忽略，
     * 因此改为：写文件 EXIF 拍摄时间 → MediaStore.scanFile() 重扫，
     * 由扫描器从 EXIF 重新推导 DATE_TAKEN（仅对带 EXIF 的图片有效）
     */
    fun topFifthMedia(context: Context): TopResult {
        val sorted = queryAllMediaMixed(context)
        if (sorted.size < 5) return TopResult.TooFew(sorted.size)
        val target = sorted[4]
        Log.d(
            TAG,
            "Top target(5th): [${target.type}] name=${target.name} path=${target.path} taken=${target.taken}"
        )
        return updateExifDateTaken(context, target)
    }

    /** 授权成功后重试置顶（在 Activity 的授权回调中调用） */
    fun retryTopAfterGrant(context: Context): TopResult {
        val target = pendingTopItem ?: return TopResult.Error("no pending top item")
        return updateExifDateTaken(context, target)
    }

    /** 清除待授权状态（用户拒绝授权时调用） */
    fun clearPending() {
        pendingTopItem = null
    }

    /**
     * 复制置顶：把混排第 5 条复制为新条目插入相册（本 App 为 owner），
     * insert 时直接指定 DATE_TAKEN 为当前时间 → 新条目排在相册最前
     *
     * 这是绕过「Android 16 第三方无法 update DATE_TAKEN」的标准做法：
     * 自己是 owner 的 insert 允许指定 DATE_TAKEN（微信保存视频就是这个链路）
     */
    fun copyTopFifthMedia(context: Context): TopResult {
        val sorted = queryAllMediaMixed(context)
        if (sorted.size < 5) return TopResult.TooFew(sorted.size)
        val source = sorted[4]
        Log.d(
            TAG,
            "Copy-top source(5th): [${source.type}] name=${source.name} path=${source.path} taken=${source.taken}"
        )
        return copyAsNewMedia(context, source)
    }

    /** 复制授权待重试的源媒体 */
    private var pendingCopySource: MediaItem? = null

    /** 授权成功后重试复制置顶 */
    fun retryCopyAfterGrant(context: Context): TopResult {
        val source = pendingCopySource ?: return TopResult.Error("no pending copy source")
        return copyAsNewMedia(context, source)
    }

    /** 把源媒体复制为新条目并指定当前时间为 DATE_TAKEN（改写 MP4 mvhd 时间戳方案） */
    private fun copyAsNewMedia(context: Context, source: MediaItem): TopResult {
        return try {
            // 1. 按媒体类型选对集合：图片进 Images，视频进 Video
            val collection = if (source.type == "image") {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val newName = buildCopyName(source.name)
            val desiredTaken = System.currentTimeMillis()
            val newValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeOf(source.name))
            }
            val newUri = context.contentResolver.insert(collection, newValues)
                ?: return TopResult.Error("insert returned null")
            // 2. 复制内容
            context.contentResolver.openInputStream(source.contentUri)?.use { input ->
                context.contentResolver.openOutputStream(newUri)?.use { output ->
                    input.copyTo(output)
                }
            } ?: return TopResult.Error("openInputStream failed")
            Log.d(TAG, "Copied as new: $newName")

            // 3. 物理级改写：MP4 改 mvhd 创建时间；JPEG 改 EXIF DateTimeOriginal。
            //    MIUI scanner 上报 DATE_TAKEN 的最终来源就是文件内部时间戳，
            //    改掉它之后无论 scanner 怎么重扫，读到的都是我们指定的时间
            val patched = if (source.type == "video") {
                patchMp4MvhdTime(context, newUri, desiredTaken)
            } else {
                patchJpegExifTime(context, newUri, desiredTaken)
            }
            Log.d(TAG, "Patched file time: $patched")

            // 4. 触发重扫，并轮询回查 DATE_TAKEN：一生效立即返回，不固定等待
            val path = queryDataColumn(context, newUri)
            val newId = ContentUris.parseId(newUri)
            android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            var finalTaken: Long? = null
            val pollStart = System.currentTimeMillis()
            val pollDeadline = pollStart + 5000 // 最多轮询 5 秒
            while (System.currentTimeMillis() < pollDeadline) {
                Thread.sleep(200)
                val t = queryDateTaken(context, collection, newId)
                if (t != null && t > source.taken) {
                    finalTaken = t
                    break
                }
            }

            // 5. 返回最终结果
            return if (finalTaken != null) {
                pendingCopySource = null
                Log.d(
                    TAG,
                    "Final DATE_TAKEN=$finalTaken took=${System.currentTimeMillis() - pollStart}ms — 应排最前"
                )
                TopResult.Success(name = newName, path = path)
            } else {
                TopResult.Error("改写文件时间后 DATE_TAKEN 仍未生效")
            }
        } catch (e: SecurityException) {
            val grantSender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (e as? android.app.RecoverableSecurityException)
                    ?.userAction?.actionIntent?.intentSender
            } else {
                null
            }
            if (grantSender != null) {
                pendingCopySource = source
                TopResult.NeedGrant(grantSender)
            } else {
                Log.e(TAG, "Copy failed for ${source.path}", e)
                TopResult.Error(e.message ?: e.javaClass.simpleName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed for ${source.path}", e)
            TopResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * 改写 MP4 的 mvhd box 创建时间为指定时间。
     * mvhd version=0: creation_time 为 4 字节（自 1904-01-01 UTC 的秒数）；
     * version=1: 为 8 字节。scanner 用该值推导 DATE_TAKEN
     *
     * @return true 改写成功
     */
    private fun patchMp4MvhdTime(context: Context, uri: Uri, timeMillis: Long): Boolean {
        var patched = false
        val pfd = context.contentResolver.openFileDescriptor(uri, "rw") ?: return false
        try {
            // ParcelFileDescriptor 的dup 两个独立 FileDescriptor 实现
            val rafos = java.io.FileOutputStream(pfd.fileDescriptor)
            val rafis = java.io.FileInputStream(pfd.fileDescriptor)
            val writable = rafos.channel.apply { position(0) }
            val readable = rafis.channel.apply { position(0) }
            val fileSize = readable.size()
            var pos = 0L
            val header = ByteArray(8)
            val headerBuf = java.nio.ByteBuffer.wrap(header)
            scan@ while (pos + 8 <= fileSize) {
                readable.position(pos)
                headerBuf.clear()
                while (headerBuf.hasRemaining()) {
                    if (readable.read(headerBuf) < 0) break@scan
                }
                // box size（大端 4 字节）+ type（4 字节）
                var boxSize =
                    ((header[0].toLong() and 0xFF) shl 24) or
                        ((header[1].toLong() and 0xFF) shl 16) or
                        ((header[2].toLong() and 0xFF) shl 8) or
                        (header[3].toLong() and 0xFF)
                val boxType = String(header, 4, 4, Charsets.US_ASCII)
                if (boxSize == 0L) boxSize = fileSize - pos // size=0 表示到文件尾
                if (boxSize < 8) break@scan

                if (boxType == "moov") {
                    // 在 moov 内部找 mvhd 子 box
                    var subPos = pos + 8
                    while (subPos + 8 <= pos + boxSize) {
                        readable.position(subPos)
                        headerBuf.clear()
                        while (headerBuf.hasRemaining()) {
                            if (readable.read(headerBuf) < 0) break@scan
                        }
                        var subSize =
                            ((header[0].toLong() and 0xFF) shl 24) or
                                ((header[1].toLong() and 0xFF) shl 16) or
                                ((header[2].toLong() and 0xFF) shl 8) or
                                (header[3].toLong() and 0xFF)
                        val subType = String(header, 4, 4, Charsets.US_ASCII)
                        if (subSize == 0L) subSize = pos + boxSize - subPos
                        if (subSize < 8) break@scan
                        if (subType == "mvhd") {
                            // box header(8) + version/flags(4)，其后是 creation_time
                            val versionBuf = java.nio.ByteBuffer.allocate(1)
                            readable.position(subPos + 12)
                            readable.read(versionBuf)
                            val version = versionBuf.get(0).toInt()
                            // MP4 时间戳纪元 1904-01-01 UTC 与 Unix 纪元差值（秒）
                            val mp4EpochOffset = 2082844800L
                            val timeBuf: java.nio.ByteBuffer
                            if (version == 1) {
                                // 8 字节大端毫秒（自 1904 纪元）
                                timeBuf = java.nio.ByteBuffer.allocate(8)
                                timeBuf.putLong(timeMillis + mp4EpochOffset * 1000)
                            } else {
                                // 4 字节大端秒（自 1904 纪元）
                                timeBuf = java.nio.ByteBuffer.allocate(4)
                                timeBuf.putInt((timeMillis / 1000 + mp4EpochOffset).toInt())
                            }
                            timeBuf.flip()
                            writable.position(subPos + 12)
                            writable.write(timeBuf)
                            Log.d(TAG, "mvhd patched at offset ${subPos + 12} (version=$version)")
                            patched = true
                            break@scan
                        }
                        subPos += subSize
                    }
                    break@scan // moov 处理完即结束
                }
                pos += boxSize
            }
            runCatching { readable.close() }
            runCatching { writable.close() }
        } catch (e: Exception) {
            Log.e(TAG, "patchMp4MvhdTime failed", e)
        } finally {
            runCatching { pfd.close() }
        }
        return patched
    }

    /** 改写 JPEG 的 EXIF 拍摄时间为指定时间（androidx ExifInterface 直接改副本） */
    private fun patchJpegExifTime(context: Context, uri: Uri, timeMillis: Long): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                val newDate = android.text.format.DateFormat.format(
                    "yyyy:MM:dd HH:mm:ss", timeMillis
                ).toString()
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, newDate)
                exif.setAttribute(ExifInterface.TAG_DATETIME, newDate)
                exif.saveAttributes()
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "patchJpegExifTime failed", e)
            false
        }
    }

    /** 生成复制副本的文件名：name.mp4 -> name-top-<时间戳后缀>.mp4，避免重复点击冲突 */
    private fun buildCopyName(original: String): String {
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        val ext = if (dot > 0) original.substring(dot) else ""
        val suffix = System.currentTimeMillis() % 100000
        return "${base}-top-$suffix${ext}"
    }

    /** 根据扩展名推断 MIME 类型 */
    private fun mimeTypeOf(name: String): String = when {
        name.endsWith(".mp4", true) -> "video/mp4"
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".gif", true) -> "image/gif"
        name.endsWith(".webp", true) -> "image/webp"
        else -> if (name.endsWith(".mp4", true)) "video/mp4" else "application/octet-stream"
    }

    /** 修改指定媒体的 EXIF 拍摄时间为当前时间，并触发 MediaStore 重扫 */
    private fun updateExifDateTaken(context: Context, item: MediaItem): TopResult {
        if (item.type != "image") {
            return TopResult.Error("Android 16 无法修改视频的 DATE_TAKEN（无 EXIF 且列为只读）")
        }
        return try {
            // 1. 通过 content URI 打开文件（MediaStore 会处理 FUSE 重定向）
            val newTakenMillis = System.currentTimeMillis()
            context.contentResolver.openFileDescriptor(item.contentUri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                // 格式：yyyy:MM:dd HH:mm:ss（EXIF 标准）
                val newDate = android.text.format.DateFormat.format(
                    "yyyy:MM:dd HH:mm:ss", newTakenMillis
                ).toString()
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, newDate)
                exif.setAttribute(ExifInterface.TAG_DATETIME, newDate)
                exif.saveAttributes()
            } ?: return TopResult.Error("openFileDescriptor returned null")
            Log.d(TAG, "EXIF updated: ${item.path}")

            // 2. 触发媒体扫描器重扫该文件，扫描器从 EXIF 重新推导 DATE_TAKEN（scanFile 异步）
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(item.path), null, null
            )
            // 3. 回查确认（扫描是异步的，稍作等待后校验）
            Thread.sleep(1500)
            val newTaken = queryDateTaken(context, item.uri, item.id)
            return if (newTaken != null && newTaken > item.taken) {
                pendingTopItem = null
                Log.d(TAG, "DATE_TAKEN updated: ${item.taken} -> $newTaken")
                TopResult.Success(name = item.name, path = item.path)
            } else {
                TopResult.Error("DATE_TAKEN not updated after scan (now=$newTaken)")
            }
        } catch (e: SecurityException) {
            // Android 10+：修改其他应用创建的媒体会抛 RecoverableSecurityException（其子类），
            // 保存目标，引导用户系统授权后重试
            val grantSender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (e as? android.app.RecoverableSecurityException)
                    ?.userAction?.actionIntent?.intentSender
            } else {
                null
            }
            if (grantSender != null) {
                pendingTopItem = item
                Log.w(TAG, "Need write grant for ${item.path}")
                TopResult.NeedGrant(grantSender)
            } else {
                Log.e(TAG, "Update EXIF failed for ${item.path}", e)
                TopResult.Error(e.message ?: e.javaClass.simpleName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update EXIF failed for ${item.path}", e)
            TopResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /** 回查指定媒体的当前 DATE_TAKEN */
    private fun queryDateTaken(context: Context, collectionUri: Uri, id: Long): Long? {
        val uri = ContentUris.withAppendedId(collectionUri, id)
        return context.contentResolver.query(
            uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    /** 查询指定媒体的真实文件路径（DATA 列） */
    private fun queryDataColumn(context: Context, uri: Uri): String {
        return context.contentResolver.query(
            uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) ?: "" else ""
        } ?: ""
    }

    /** 单条媒体记录 */
    data class MediaItem(
        val type: String,
        val uri: Uri,
        val id: Long,
        val taken: Long,
        val dateAdded: Long,
        val name: String,
        val path: String
    ) {
        /** 该条媒体的 content URI（集合 URI + id） */
        val contentUri: Uri
            get() = ContentUris.withAppendedId(uri, id)
        /** 排序时间：优先拍摄时间（毫秒），无则回退入库时间（秒转毫秒） */
        val sortTime: Long
            get() = if (taken > 0) taken else dateAdded * 1000
    }

    /** 查询单个媒体集合，返回记录列表（不做排序，由调用方统一混排） */
    private fun query(context: Context, uri: Uri, type: String): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        context.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_ADDED
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            // DATA 列虽被标记废弃，但仍是获取真实路径的可靠方式
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                list += MediaItem(
                    type = type,
                    uri = uri,
                    id = cursor.getLong(idCol),
                    taken = cursor.getLong(takenCol),
                    dateAdded = cursor.getLong(addedCol),
                    name = cursor.getString(nameCol) ?: "",
                    path = cursor.getString(dataCol) ?: ""
                )
            }
        } ?: Log.e(TAG, "[$type] query returned null")
        return list
    }
}
