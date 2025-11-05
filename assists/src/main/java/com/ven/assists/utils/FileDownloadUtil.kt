package com.ven.assists.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * 文件下载工具类
 * 使用 OkHttp 实现文件下载到应用内部存储目录
 */
object FileDownloadUtil {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 下载结果
     */
    sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        data class Error(val exception: Exception) : DownloadResult()
        data class Progress(val current: Long, val total: Long, val percent: Int) : DownloadResult()
    }

    /**
     * 下载文件到应用内部存储目录
     *
     * @param context 上下文
     * @param url 下载地址
     * @param fileName 保存的文件名，如果为空则从 URL 中提取
     * @param subDir 子目录名称，如果为空则保存在根目录
     * @param callback 下载进度回调
     * @return 下载结果
     */
    suspend fun downloadFile(
        context: Context,
        url: String,
        fileName: String? = null,
        subDir: String? = null,
        callback: ((DownloadResult) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            // 确定保存目录
            val saveDir = if (subDir.isNullOrEmpty()) {
                context.filesDir
            } else {
                File(context.filesDir, subDir).apply {
                    if (!exists()) mkdirs()
                }
            }

            // 确定文件名
            val finalFileName = fileName ?: url.substringAfterLast('/').ifEmpty { "download_${System.currentTimeMillis()}" }
            val saveFile = File(saveDir, finalFileName)

            // 如果文件已存在，先删除
            if (saveFile.exists()) {
                saveFile.delete()
            }

            // 创建请求
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            // 执行请求
            val response: Response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("Download failed: ${response.code}")
            }

            val body = response.body ?: throw IOException("Response body is null")
            val contentLength = body.contentLength()
            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(saveFile)

            try {
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead: Long = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // 计算进度
                    if (contentLength > 0) {
                        val percent = ((totalBytesRead * 100) / contentLength).toInt()
                        withContext(Dispatchers.Main) {
                            callback?.invoke(DownloadResult.Progress(totalBytesRead, contentLength, percent))
                        }
                    }
                }

                outputStream.flush()
                DownloadResult.Success(saveFile)
            } finally {
                inputStream.close()
                outputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            DownloadResult.Error(e)
        }
    }

    /**
     * 下载文件到应用内部存储目录（支持断点续传）
     *
     * @param context 上下文
     * @param url 下载地址
     * @param fileName 保存的文件名，如果为空则从 URL 中提取
     * @param subDir 子目录名称，如果为空则保存在根目录
     * @param callback 下载进度回调
     * @return 下载结果
     */
    suspend fun downloadFileWithResume(
        context: Context,
        url: String,
        fileName: String? = null,
        subDir: String? = null,
        callback: ((DownloadResult) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            // 确定保存目录
            val saveDir = if (subDir.isNullOrEmpty()) {
                context.filesDir
            } else {
                File(context.filesDir, subDir).apply {
                    if (!exists()) mkdirs()
                }
            }

            // 确定文件名
            val finalFileName = fileName ?: url.substringAfterLast('/').ifEmpty { "download_${System.currentTimeMillis()}" }
            val saveFile = File(saveDir, finalFileName)

            // 获取已下载的文件大小
            val downloadedLength = if (saveFile.exists()) saveFile.length() else 0L

            // 创建请求，支持断点续传
            val requestBuilder = Request.Builder()
                .url(url)
                .get()

            if (downloadedLength > 0) {
                requestBuilder.addHeader("Range", "bytes=$downloadedLength-")
            }

            val request = requestBuilder.build()

            // 执行请求
            val response: Response = client.newCall(request).execute()

            if (!response.isSuccessful && response.code != 206) {
                throw IOException("Download failed: ${response.code}")
            }

            val body = response.body ?: throw IOException("Response body is null")
            val contentLength = body.contentLength()
            val totalLength = if (response.code == 206) {
                contentLength + downloadedLength
            } else {
                contentLength
            }

            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(saveFile, response.code == 206)

            try {
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead: Long = downloadedLength

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // 计算进度
                    if (totalLength > 0) {
                        val percent = ((totalBytesRead * 100) / totalLength).toInt()
                        withContext(Dispatchers.Main) {
                            callback?.invoke(DownloadResult.Progress(totalBytesRead, totalLength, percent))
                        }
                    }
                }

                outputStream.flush()
                DownloadResult.Success(saveFile)
            } finally {
                inputStream.close()
                outputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            DownloadResult.Error(e)
        }
    }

    /**
     * 下载文件到应用缓存目录
     *
     * @param context 上下文
     * @param url 下载地址
     * @param fileName 保存的文件名，如果为空则从 URL 中提取
     * @param callback 下载进度回调
     * @return 下载结果
     */
    suspend fun downloadFileToCache(
        context: Context,
        url: String,
        fileName: String? = null,
        callback: ((DownloadResult) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val cacheDir = context.cacheDir
            val finalFileName = fileName ?: url.substringAfterLast('/').ifEmpty { "cache_${System.currentTimeMillis()}" }
            val cacheFile = File(cacheDir, finalFileName)

            // 如果文件已存在，先删除
            if (cacheFile.exists()) {
                cacheFile.delete()
            }

            // 创建请求
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            // 执行请求
            val response: Response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("Download failed: ${response.code}")
            }

            val body = response.body ?: throw IOException("Response body is null")
            val contentLength = body.contentLength()
            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(cacheFile)

            try {
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead: Long = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // 计算进度
                    if (contentLength > 0) {
                        val percent = ((totalBytesRead * 100) / contentLength).toInt()
                        withContext(Dispatchers.Main) {
                            callback?.invoke(DownloadResult.Progress(totalBytesRead, contentLength, percent))
                        }
                    }
                }

                outputStream.flush()
                DownloadResult.Success(cacheFile)
            } finally {
                inputStream.close()
                outputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            DownloadResult.Error(e)
        }
    }

    /**
     * 取消所有下载任务
     */
    fun cancelAll() {
        client.dispatcher.cancelAll()
    }

    /**
     * 获取文件大小（字节）
     */
    suspend fun getFileSize(url: String): Long = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()

            val response = client.newCall(request).execute()
            response.body?.contentLength() ?: -1L
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }
}

