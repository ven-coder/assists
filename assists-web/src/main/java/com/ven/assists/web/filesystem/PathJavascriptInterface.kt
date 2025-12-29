package com.ven.assists.web.filesystem

import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.PathUtils
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ven.assists.web.CallRequest
import com.ven.assists.web.CallResponse
import com.ven.assists.web.createResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

/**
 * 路径相关的 JavascriptInterface
 * 提供文件系统路径相关的功能
 */
class PathJavascriptInterface(val webView: WebView) {
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
        val js = String.format("javascript:assistsxPathCallback('%s')", encoded)
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
                // 路径相关方法
//                PathCallMethod.pathJoin -> {
//                    val paths = request.arguments?.get("paths")?.asJsonArray
//                    if (paths == null || paths.size() == 0) {
//                        request.createResponse(-1, message = "paths参数不能为空", data = "")
//                    } else {
//                        val pathArray = mutableListOf<String>()
//                        paths.forEach { pathArray.add(it.asString) }
//                        val result = PathUtils.join(*pathArray.toTypedArray())
//                        request.createResponse(0, data = result)
//                    }
//                }

                PathCallMethod.getRootPath -> {
                    val result = PathUtils.getRootPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getDataPath -> {
                    val result = PathUtils.getDataPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getDownloadCachePath -> {
                    val result = PathUtils.getDownloadCachePath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getInternalAppDataPath -> {
                    val result = PathUtils.getInternalAppDataPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getInternalAppCodeCacheDir -> {
                    val result = PathUtils.getInternalAppCodeCacheDir()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getInternalAppCachePath -> {
                    val result = PathUtils.getInternalAppCachePath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getInternalAppDbsPath -> {
                    val result = PathUtils.getInternalAppDbsPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getInternalAppDbPath -> {
                    val dbName = request.arguments?.get("dbName")?.asString ?: ""
                    if (dbName.isEmpty()) {
                        request.createResponse(-1, message = "dbName参数不能为空", data = "")
                    } else {
                        val result = PathUtils.getInternalAppDbPath(dbName)
                        request.createResponse(0, data = result)
                    }
                }

                PathCallMethod.getInternalAppFilesPath -> {
                    val result = PathUtils.getInternalAppFilesPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getInternalAppSpPath -> {
                    val result = PathUtils.getInternalAppSpPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getInternalAppNoBackupFilesPath -> {
                    val result = PathUtils.getInternalAppNoBackupFilesPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalStoragePath -> {
                    val result = PathUtils.getExternalStoragePath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalMusicPath -> {
                    val result = PathUtils.getExternalMusicPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalPodcastsPath -> {
                    val result = PathUtils.getExternalPodcastsPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalRingtonesPath -> {
                    val result = PathUtils.getExternalRingtonesPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAlarmsPath -> {
                    val result = PathUtils.getExternalAlarmsPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalNotificationsPath -> {
                    val result = PathUtils.getExternalNotificationsPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalPicturesPath -> {
                    val result = PathUtils.getExternalPicturesPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalMoviesPath -> {
                    val result = PathUtils.getExternalMoviesPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalDownloadsPath -> {
                    val result = PathUtils.getExternalDownloadsPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalDcimPath -> {
                    val result = PathUtils.getExternalDcimPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalDocumentsPath -> {
                    val result = PathUtils.getExternalDocumentsPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAppDataPath -> {
                    val result = PathUtils.getExternalAppDataPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAppCachePath -> {
                    val result = PathUtils.getExternalAppCachePath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAppFilesPath -> {
                    val result = PathUtils.getExternalAppFilesPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAppMusicPath -> {
                    val result = PathUtils.getExternalAppMusicPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAppPodcastsPath -> {
                    val result = PathUtils.getExternalAppPodcastsPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAppRingtonesPath -> {
                    val result = PathUtils.getExternalAppRingtonesPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAppAlarmsPath -> {
                    val result = PathUtils.getExternalAppAlarmsPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAppNotificationsPath -> {
                    val result = PathUtils.getExternalAppNotificationsPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAppPicturesPath -> {
                    val result = PathUtils.getExternalAppPicturesPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAppMoviesPath -> {
                    val result = PathUtils.getExternalAppMoviesPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAppDownloadPath -> {
                    val result = PathUtils.getExternalAppDownloadPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAppDcimPath -> {
                    val result = PathUtils.getExternalAppDcimPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAppDocumentsPath -> {
                    val result = PathUtils.getExternalAppDocumentsPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getExternalAppObbPath -> {
                    val result = PathUtils.getExternalAppObbPath()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getRootPathExternalFirst -> {
                    val result = PathUtils.getRootPathExternalFirst()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getAppDataPathExternalFirst -> {
                    val result = PathUtils.getAppDataPathExternalFirst()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getFilesPathExternalFirst -> {
                    val result = PathUtils.getFilesPathExternalFirst()
                    request.createResponse(0, data = result)
                }

                PathCallMethod.getCachePathExternalFirst -> {
                    val result = PathUtils.getCachePathExternalFirst()
                    request.createResponse(0, data = result)
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

