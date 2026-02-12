package com.ven.assists.web

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.view.isVisible
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.DeviceUtils
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.NetworkUtils
import com.blankj.utilcode.util.PathUtils
import com.blankj.utilcode.util.ScreenUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.journeyapps.barcodescanner.ScanOptions
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.containsText
import com.ven.assists.AssistsCore.findById
import com.ven.assists.AssistsCore.findByTags
import com.ven.assists.AssistsCore.findByText
import com.ven.assists.AssistsCore.findFirstParentByTags
import com.ven.assists.AssistsCore.findFirstParentClickable
import com.ven.assists.AssistsCore.focus
import com.ven.assists.AssistsCore.getAllText
import com.ven.assists.AssistsCore.getBoundsInParent
import com.ven.assists.AssistsCore.getBoundsInScreen
import com.ven.assists.AssistsCore.getChildren
import com.ven.assists.AssistsCore.getNodes
import com.ven.assists.AssistsCore.longClick
import com.ven.assists.AssistsCore.longPressGestureAutoPaste
import com.ven.assists.AssistsCore.nodeGestureClick
import com.ven.assists.AssistsCore.paste
import com.ven.assists.AssistsCore.scrollBackward
import com.ven.assists.AssistsCore.scrollForward
import com.ven.assists.AssistsCore.selectionText
import com.ven.assists.AssistsCore.setNodeText
import com.ven.assists.AssistsCore.takeScreenshot
import com.ven.assists.AssistsCore.takeScreenshotSave
import com.ven.assists.mp.MPManager
import com.ven.assists.mp.MPManager.getBitmap
import com.ven.assists.service.AssistsService
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.web.JavascriptInterfaceContext
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowManager.overlayToast
import com.ven.assists.web.utils.TextRecognitionChineseLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.ImageUtils
import com.ven.assists.utils.AudioPlayerUtil
import com.ven.assists.utils.ContactsUtil
import com.ven.assists.utils.FileDownloadUtil
import com.ven.assists.utils.runMain
import com.ven.assists.web.utils.AudioPlayManager
import kotlinx.coroutines.CompletableDeferred
import androidx.core.graphics.toColorInt

class ASJavascriptInterfaceAsync(val webView: WebView) {
    var callIntercept: ((json: String) -> CallInterceptResult)? = null
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
        val js = String.format("javascript:assistsxCallback('%s')", encoded)
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
        var requestJson = originJson
        val callIntercept = runCatching {
            callIntercept?.invoke(originJson)?.let {
                if (it.intercept) {
                    callback(it.result)
                    return@runCatching true
                } else {
                    requestJson = it.result
                    return@runCatching false
                }
            }
        }.onFailure { LogUtils.e(it) }
        if (callIntercept.getOrNull() == true) return

        val request = GsonUtils.fromJson<CallRequest<JsonObject>>(requestJson, object : TypeToken<CallRequest<JsonObject>>() {}.type)
        runCatching {
            val response = when (request.method) {
                CallMethod.audioStop -> handleAudioStop(request)

                CallMethod.audioPlayRingtone -> handleAudioPlayRingtone(request)

                CallMethod.audioStopRingtone -> handleAudioStopRingtone(request)

                CallMethod.audioPlayFromFile -> handleAudioPlayFromFile(request)

                CallMethod.download -> handleDownload(request)

                CallMethod.getNetworkType -> handleGetNetworkType(request)

                CallMethod.recognizeTextInScreenshot -> handleRecognizeTextInScreenshot(request)

                CallMethod.getDeviceInfo -> handleGetDeviceInfo(request)

                CallMethod.getUniqueDeviceId -> handleGetUniqueDeviceId(request)

                CallMethod.getAndroidID -> handleGetAndroidID(request)

                CallMethod.getMacAddress -> handleGetMacAddress(request)

                CallMethod.longPressGestureAutoPaste -> handleLongPressGestureAutoPaste(request)

                CallMethod.getAppInfo -> handleGetAppInfo(request)

                CallMethod.scanQR -> handleScanQR(request)


                CallMethod.takeScreenshot -> handleTakeScreenshot(request)

                CallMethod.takeScreenshotSave -> handleTakeScreenshotSave(request)

                CallMethod.takeScreenshotToFile -> handleTakeScreenshotToFile(request)

                CallMethod.performLinearGesture -> handlePerformLinearGesture(request)

                CallMethod.getAppScreenSize -> handleGetAppScreenSize(request)

                CallMethod.getScreenSize -> handleGetScreenSize(request)

                CallMethod.clickByGesture -> handleClickByGesture(request)

                CallMethod.clickNodeByGesture -> handleClickNodeByGesture(request)

                CallMethod.doubleClickNodeByGesture -> handleDoubleClickNodeByGesture(request)

                CallMethod.getBoundsInParent -> handleGetBoundsInParent(request)

                CallMethod.getBoundsInScreen -> handleGetBoundsInScreen(request)

                CallMethod.isVisible -> handleIsVisible(request)

                CallMethod.getAllText -> handleGetAllText(request)

                CallMethod.getChildren -> handleGetChildren(request)

                CallMethod.getAllNodes -> handleGetAllNodes(request)

                CallMethod.findById -> handleFindById(request)

                CallMethod.findByText -> handleFindByText(request)

                CallMethod.findByTags -> handleFindByTags(request)

                CallMethod.selectionText -> handleSelectionText(request)

                CallMethod.scrollForward -> handleScrollForward(request)

                CallMethod.scrollBackward -> handleScrollBackward(request)

                CallMethod.findByTextAllMatch -> handleFindByTextAllMatch(request)

                CallMethod.containsText -> handleContainsText(request)

                CallMethod.findFirstParentByTags -> handleFindFirstParentByTags(request)

                CallMethod.getNodes -> handleGetNodes(request)

                CallMethod.findFirstParentClickable -> handleFindFirstParentClickable(request)

                CallMethod.setNodeText -> handleSetNodeText(request)

                CallMethod.click -> handleClick(request)

                CallMethod.longClick -> handleLongClick(request)

                CallMethod.paste -> handlePaste(request)

                CallMethod.focus -> handleFocus(request)

                CallMethod.launchApp -> handleLaunchApp(request)

                CallMethod.getPackageName -> handleGetPackageName(request)


                CallMethod.back -> handleBack(request)

                CallMethod.home -> handleHome(request)

                CallMethod.notifications -> handleNotifications(request)

                CallMethod.recentApps -> handleRecentApps(request)

                CallMethod.httpRequest -> handleHttpRequest(request)

                CallMethod.openUrlInBrowser -> handleOpenUrlInBrowser(request)

                CallMethod.addContact -> handleAddContact(request)

                CallMethod.getAllContacts -> handleGetAllContacts(request)

                CallMethod.saveRootNodeTreeJson -> handleSaveRootNodeTreeJson(request)

                CallMethod.getClipboardText -> handleGetClipboardText(request)

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

    private suspend fun handleAudioStop(request: CallRequest<JsonObject>): CallResponse<Any?> {
        AudioPlayerUtil.stop()
        return request.createResponse(0, data = null)
    }

    private fun handleAudioPlayRingtone(request: CallRequest<JsonObject>): CallResponse<Any?> {
        return JavascriptInterfaceContext.getContext()?.let {
            AudioPlayManager.startAudioPlay(it)
            request.createResponse(0, data = "开始播放系统电话铃声")
        } ?: request.createResponse(-1, data = "上下文无效")
    }

    private fun handleAudioStopRingtone(request: CallRequest<JsonObject>): CallResponse<Any?> {
        AudioPlayManager.stopAudioPlay()
        return request.createResponse(0, data = "已停止播放")
    }

    private suspend fun handleAudioPlayFromFile(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val filePath = request.arguments?.get("filePath")?.asString ?: ""
        val volume = request.arguments?.get("volume")?.asFloat
        val useAbsoluteVolume = request.arguments?.get("useAbsoluteVolume")?.asBoolean ?: false
        return JavascriptInterfaceContext.getContext()?.let {
            val completableDeferred = CompletableDeferred<Exception?>()
            AudioPlayerUtil.playFromFile(
                it, filePath, volume = volume,
                useAbsoluteVolume = useAbsoluteVolume,
                listener = object : AudioPlayerUtil.PlayListener {
                    override fun onError(error: String) {
                        completableDeferred.complete(RuntimeException(error))
                    }

                    override fun onCompletion() {
                        completableDeferred.complete(null)
                    }
                })
            val result = completableDeferred.await()
            request.createResponse(
                if (result == null) 0 else -1,
                data = if (result == null) "播放完成" else "播放失败: ${result.message}"
            )
        } ?: request.createResponse(-1, data = "无障碍服务无效")
    }

    private suspend fun handleDownload(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val url = request.arguments?.get("url")?.asString ?: ""
        return JavascriptInterfaceContext.getContext()?.let {
            val result = FileDownloadUtil.downloadFile(it, url)
            when (result) {
                is FileDownloadUtil.DownloadResult.Error -> request.createResponse(-1, data = result.exception.message)
                is FileDownloadUtil.DownloadResult.Success -> request.createResponse(0, data = result.file.path)
                else -> request.createResponse(-1, data = null)
            }
        } ?: request.createResponse(-1, data = null)
    }

    private fun handleGetNetworkType(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val networkType = NetworkUtils.getNetworkType()
        val networkTypeValue = when (networkType) {
            NetworkUtils.NetworkType.NETWORK_ETHERNET -> "NETWORK_ETHERNET"
            NetworkUtils.NetworkType.NETWORK_WIFI -> "NETWORK_WIFI"
            NetworkUtils.NetworkType.NETWORK_5G -> "NETWORK_5G"
            NetworkUtils.NetworkType.NETWORK_4G -> "NETWORK_4G"
            NetworkUtils.NetworkType.NETWORK_3G -> "NETWORK_3G"
            NetworkUtils.NetworkType.NETWORK_2G -> "NETWORK_2G"
            NetworkUtils.NetworkType.NETWORK_UNKNOWN -> "NETWORK_UNKNOWN"
            NetworkUtils.NetworkType.NETWORK_NO -> "NETWORK_NO"
        }
        val data = JsonObject().apply {
            addProperty("networkType", networkTypeValue)
        }
        return request.createResponse(0, data = data)
    }

    private suspend fun handleRecognizeTextInScreenshot(request: CallRequest<JsonObject>): CallResponse<Any?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return request.createResponse(-1, message = "Screenshot recognition requires Android R or above", data = false)
        }
        val targetText = request.arguments?.get("targetText")?.asString ?: ""
        val rotationDegrees = request.arguments?.get("rotationDegrees")?.asInt ?: 0
        val overlayHiddenDelay = request.arguments?.get("overlayHiddenScreenshotDelayMillis")?.asLong ?: 250L
        val restoreOverlay = request.arguments?.get("restoreOverlay")?.asBoolean ?: true

        val regionJson = request.arguments?.get("region")?.asJsonObject
        val regionRect = regionJson?.let {
            val left = it.get("left")?.asInt
            val top = it.get("top")?.asInt
            val right = it.get("right")?.asInt
            val bottom = it.get("bottom")?.asInt
            val width = it.get("width")?.asInt
            val height = it.get("height")?.asInt

            val resolvedLeft = left ?: 0
            val resolvedTop = top ?: 0
            val resolvedRight = when {
                right != null -> right
                width != null -> resolvedLeft + width
                else -> null
            }
            val resolvedBottom = when {
                bottom != null -> bottom
                height != null -> resolvedTop + height
                else -> null
            }

            if (resolvedRight == null || resolvedBottom == null) {
                null
            } else if (resolvedRight <= resolvedLeft || resolvedBottom <= resolvedTop) {
                null
            } else {
                Rect(resolvedLeft, resolvedTop, resolvedRight, resolvedBottom)
            }
        }

        if (restoreOverlay) {
            AssistsWindowManager.hideAll()
        }
        delay(overlayHiddenDelay)
        val recognitionResult = runCatching {
            TextRecognitionChineseLocator.findWordPositionsInScreenshotRegion(
                region = regionRect,
                targetText = targetText,
                rotationDegrees = rotationDegrees
            )
        }.onFailure {
            LogUtils.e(it)
        }
        if (restoreOverlay) {
            AssistsWindowManager.showTop()
        }

        return recognitionResult.fold(
            onSuccess = { result ->
                val positionsArray = JsonArray().apply {
                    result.targetPositions.forEach { position ->
                        add(JsonObject().apply {
                            addProperty("text", position.text)
                            addProperty("left", position.left)
                            addProperty("top", position.top)
                            addProperty("right", position.right)
                            addProperty("bottom", position.bottom)
                            addProperty("width", position.width)
                            addProperty("height", position.height)
                        })
                    }
                }
                val data = JsonObject().apply {
                    addProperty("fullText", result.fullText)
                    addProperty("processingTimeMillis", result.processingTimeMillis)
                    add("positions", positionsArray)
                }
                request.createResponse(0, data = data)
            },
            onFailure = {
                request.createResponse(
                    -1,
                    message = it.message ?: "Recognition failed",
                    data = ""
                )
            }
        )
    }

    private fun handleGetDeviceInfo(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val uniqueDeviceId = DeviceUtils.getUniqueDeviceId()
        val androidID = DeviceUtils.getAndroidID()
        val macAddress = DeviceUtils.getMacAddress()
        val isDeviceRooted = DeviceUtils.isDeviceRooted()
        val manufacturer = DeviceUtils.getManufacturer()
        val model = DeviceUtils.getModel()
        val sdkVersionCode = DeviceUtils.getSDKVersionCode()
        val sdkVersionName = DeviceUtils.getSDKVersionName()
        val abiList = DeviceUtils.getABIs()
        val isAdbEnabled = DeviceUtils.isAdbEnabled()
        val isDevelopmentSettingsEnabled = DeviceUtils.isDevelopmentSettingsEnabled()
        val isEmulator = DeviceUtils.isEmulator()
        val isTablet = DeviceUtils.isTablet()

        val data = JsonObject().apply {
            addProperty("uniqueDeviceId", uniqueDeviceId)
            addProperty("androidID", androidID)
            addProperty("macAddress", macAddress)
            addProperty("isDeviceRooted", isDeviceRooted)
            addProperty("manufacturer", manufacturer)
            addProperty("model", model)
            addProperty("sdkVersionCode", sdkVersionCode)
            addProperty("sdkVersionName", sdkVersionName)
            add("abiList", JsonArray().apply {
                abiList.forEach { add(it) }
            })
            addProperty("isAdbEnabled", isAdbEnabled)
            addProperty("isDevelopmentSettingsEnabled", isDevelopmentSettingsEnabled)
            addProperty("isEmulator", isEmulator)
            addProperty("isTablet", isTablet)
        }
        return request.createResponse(0, data = data)
    }

    private fun handleGetUniqueDeviceId(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val uniqueDeviceId = DeviceUtils.getUniqueDeviceId()
        return request.createResponse(0, data = JsonObject().apply {
            addProperty("uniqueDeviceId", uniqueDeviceId)
        })
    }

    private fun handleGetAndroidID(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val androidID = DeviceUtils.getAndroidID()
        return request.createResponse(0, data = JsonObject().apply {
            addProperty("androidID", androidID)
        })
    }

    private fun handleGetMacAddress(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val macAddress = DeviceUtils.getMacAddress()
        return request.createResponse(0, data = JsonObject().apply {
            addProperty("macAddress", macAddress)
        })
    }

    private suspend fun handleLongPressGestureAutoPaste(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val matchedPackageName = request.arguments?.get("matchedPackageName")?.asString
        val text = request.arguments?.get("text")?.asString ?: ""
        val matchedText = request.arguments?.get("matchedText")?.asString ?: "粘贴"
        val timeoutMillis = request.arguments?.get("timeoutMillis")?.asLong ?: 1500
        val longPressDuration = request.arguments?.get("longPressDuration")?.asLong ?: 600
        val point = request.arguments?.get("point")?.asJsonObject ?: JsonObject()

        val switchWindowIntervalDelay = request.arguments?.get("switchWindowIntervalDelay")?.asLong ?: 250
        AssistsWindowManager.nonTouchableByAll()
        delay(switchWindowIntervalDelay)

        val x = point.get("x")?.asFloat ?: 0f
        val y = point.get("y")?.asFloat ?: 0f
        val result = if (request.node?.nodeId.isNullOrEmpty()) {
            AssistsCore.longPressGestureAutoPaste(
                x = x,
                y = y,
                text = text,
                matchedPackageName = matchedPackageName,
                matchedText = matchedText,
                timeoutMillis = timeoutMillis,
                longPressDuration = longPressDuration
            )
        } else {
            NodeCacheManager.get(request.node?.nodeId ?: "")?.longPressGestureAutoPaste(
                text = text,
                matchedPackageName = matchedPackageName,
                matchedText = matchedText,
                timeoutMillis = timeoutMillis,
                longPressDuration = longPressDuration
            ) ?: false
        }
        AssistsWindowManager.touchableByAll()
        return request.createResponse(code = if (result) 0 else -1, data = result, callbackId = request.callbackId)
    }

    private fun handleGetAppInfo(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val packageName = request.arguments?.get("packageName")?.asString ?: ""
        val appInfo = AppUtils.getAppInfo(packageName)
        return request.createResponse(0, data = appInfo)
    }

    private suspend fun handleScanQR(request: CallRequest<JsonObject>): CallResponse<Any?> {
        AssistsWindowManager.hideAll()
        val scanIntentResult = CustomFileProvider.requestLaunchersScan(ScanOptions())
        AssistsWindowManager.showTop()
        return request.createResponse(0, data = JsonObject().apply {
            addProperty("value", scanIntentResult?.contents ?: "")
        })
    }

    private suspend fun handleTakeScreenshot(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val overlayHiddenScreenshotDelayMillis = request.arguments?.get("overlayHiddenScreenshotDelayMillis")?.asLong ?: 250
        AssistsWindowManager.hideAll()
        delay(overlayHiddenScreenshotDelayMillis)
        val list = arrayListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val screenshot = AssistsCore.takeScreenshot()
            AssistsWindowManager.showTop()

            request.nodes?.forEach {
                val bitmap = NodeCacheManager.get(it.nodeId)?.takeScreenshot(screenshot = screenshot)
                bitmap?.let {
                    val base64 = bitmapToBase64(it)
                    list.add(base64)
                }
                bitmap?.recycle()
            }
        } else {
            val takeScreenshot2Bitmap = MPManager.takeScreenshot2Bitmap()
            AssistsWindowManager.showTop()

            takeScreenshot2Bitmap?.let {
                request.nodes?.forEach {
                    val bitmap = NodeCacheManager.get(it.nodeId)?.getBitmap(screenshot = takeScreenshot2Bitmap)
                    bitmap?.let {
                        val base64 = bitmapToBase64(it)
                        list.add(base64)
                    }
                    bitmap?.recycle()
                }
                takeScreenshot2Bitmap.recycle()
            }
        }
        return request.createResponse(if (list.isEmpty()) -1 else 0, data = JsonObject().apply {
            add("images", JsonArray().apply {
                list.forEach { add(it) }
            })
        })
    }

    private suspend fun handleTakeScreenshotSave(request: CallRequest<JsonObject>): CallResponse<Any?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return request.createResponse(-1, message = "takeScreenshotSave requires Android R or above", data = null)
        }
        val overlayHiddenScreenshotDelayMillis = request.arguments?.get("overlayHiddenScreenshotDelayMillis")?.asLong ?: 250
        val filePath = request.arguments?.get("filePath")?.asString
        val formatStr = request.arguments?.get("format")?.asString ?: "PNG"

        val format = when (formatStr.uppercase()) {
            "PNG" -> CompressFormat.PNG
            "JPEG", "JPG" -> CompressFormat.JPEG
            "WEBP" -> CompressFormat.WEBP
            else -> CompressFormat.PNG
        }

        val fileExtension = when (format) {
            CompressFormat.PNG -> "png"
            CompressFormat.JPEG -> "jpg"
            CompressFormat.WEBP -> "webp"
            else -> "png"
        }

        AssistsWindowManager.hideAll()
        delay(overlayHiddenScreenshotDelayMillis)

        val file =
            filePath?.let { File(it) } ?: File(PathUtils.getInternalAppFilesPath() + "/${System.currentTimeMillis()}.$fileExtension")

        val savedFile = if (request.node?.nodeId.isNullOrEmpty()) {
            AssistsCore.takeScreenshotSave(file = file, format = format)
        } else {
            NodeCacheManager.get(request.node?.nodeId ?: "")?.takeScreenshotSave(file = file, format = format)
        }

        AssistsWindowManager.showTop()

        return request.createResponse(
            if (savedFile == null) -1 else 0,
            message = if (savedFile == null) "截图保存失败" else "截图保存成功",
            data = JsonObject().apply {
                addProperty("file", savedFile?.path ?: "")
            }
        )
    }

    private suspend fun handleTakeScreenshotToFile(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val overlayHiddenScreenshotDelayMillis = request.arguments?.get("overlayHiddenScreenshotDelayMillis")?.asLong ?: 250
        val formatStr = request.arguments?.get("format")?.asString ?: "PNG"
        val baseFilePath = request.arguments?.get("filePath")?.asString

        val format = when (formatStr.uppercase()) {
            "PNG" -> CompressFormat.PNG
            "JPEG", "JPG" -> CompressFormat.JPEG
            "WEBP" -> CompressFormat.WEBP
            else -> CompressFormat.PNG
        }

        val fileExtension = when (format) {
            CompressFormat.PNG -> "png"
            CompressFormat.JPEG -> "jpg"
            CompressFormat.WEBP -> "webp"
            else -> "png"
        }

        AssistsWindowManager.hideAll()
        delay(overlayHiddenScreenshotDelayMillis)

        val filePaths = arrayListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val screenshot = AssistsCore.takeScreenshot()
            AssistsWindowManager.showTop()

            if (request.nodes.isNullOrEmpty()) {
                val file = baseFilePath?.let { File(it) }
                    ?: File(PathUtils.getInternalAppFilesPath() + "/screenshot_${System.currentTimeMillis()}.$fileExtension")
                file.parentFile?.mkdirs()
                screenshot?.let {
                    val success = ImageUtils.save(it, file, format)
                    if (success) {
                        filePaths.add(file.absolutePath)
                    }
                    it.recycle()
                }
            } else {
                request.nodes?.forEachIndexed { index, nodeRequest ->
                    val bitmap = NodeCacheManager.get(nodeRequest.nodeId)?.takeScreenshot(screenshot = screenshot)
                    bitmap?.let {
                        val file = if (baseFilePath != null && request.nodes?.size == 1) {
                            File(baseFilePath)
                        } else {
                            if (baseFilePath != null) {
                                val baseFile = File(baseFilePath)
                                val nameWithoutExt = baseFile.nameWithoutExtension
                                val parent = baseFile.parent ?: PathUtils.getInternalAppFilesPath()
                                File(parent, "${nameWithoutExt}_${index}.$fileExtension")
                            } else {
                                File(PathUtils.getInternalAppFilesPath() + "/screenshot_${System.currentTimeMillis()}_${index}.$fileExtension")
                            }
                        }
                        file.parentFile?.mkdirs()
                        val success = ImageUtils.save(it, file, format)
                        if (success) {
                            filePaths.add(file.absolutePath)
                        }
                        it.recycle()
                    }
                }
                screenshot?.recycle()
            }
        } else {
            val takeScreenshot2Bitmap = MPManager.takeScreenshot2Bitmap()
            AssistsWindowManager.showTop()

            takeScreenshot2Bitmap?.let {
                if (request.nodes.isNullOrEmpty()) {
                    val file = baseFilePath?.let { File(it) }
                        ?: File(PathUtils.getInternalAppFilesPath() + "/screenshot_${System.currentTimeMillis()}.$fileExtension")
                    file.parentFile?.mkdirs()
                    val success = ImageUtils.save(it, file, format)
                    if (success) {
                        filePaths.add(file.absolutePath)
                    }
                } else {
                    request.nodes?.forEachIndexed { index, nodeRequest ->
                        val bitmap = NodeCacheManager.get(nodeRequest.nodeId)?.getBitmap(screenshot = it)
                        bitmap?.let { nodeBitmap ->
                            val file = if (baseFilePath != null && request.nodes?.size == 1) {
                                File(baseFilePath)
                            } else {
                                if (baseFilePath != null) {
                                    val baseFile = File(baseFilePath)
                                    val nameWithoutExt = baseFile.nameWithoutExtension
                                    val parent = baseFile.parent ?: PathUtils.getInternalAppFilesPath()
                                    File(parent, "${nameWithoutExt}_${index}.$fileExtension")
                                } else {
                                    File(PathUtils.getInternalAppFilesPath() + "/screenshot_${System.currentTimeMillis()}_${index}.$fileExtension")
                                }
                            }
                            file.parentFile?.mkdirs()
                            val success = ImageUtils.save(nodeBitmap, file, format)
                            if (success) {
                                filePaths.add(file.absolutePath)
                            }
                            nodeBitmap.recycle()
                        }
                    }
                }
                it.recycle()
            }
        }

        return request.createResponse(
            if (filePaths.isEmpty()) -1 else 0,
            message = if (filePaths.isEmpty()) "截图保存失败" else "截图保存成功",
            data = JsonObject().apply {
                add("files", JsonArray().apply {
                    filePaths.forEach { add(it) }
                })
            }
        )
    }

    private suspend fun handlePerformLinearGesture(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val startPoint = request.arguments?.get("startPoint")?.asJsonObject ?: JsonObject()
        val endPoint = request.arguments?.get("endPoint")?.asJsonObject ?: JsonObject()
        val path = Path()
        path.moveTo(startPoint.get("x").asFloat, startPoint.get("y").asFloat)
        path.lineTo(endPoint.get("x").asFloat, endPoint.get("y").asFloat)
        val switchWindowIntervalDelay = request.arguments?.get("switchWindowIntervalDelay")?.asLong ?: 250
        AssistsWindowManager.nonTouchableByAll()
        delay(switchWindowIntervalDelay)
        val result =
            AssistsCore.gesture(path = path, startTime = 0, duration = request.arguments?.get("duration")?.asLong ?: 1000)
        AssistsWindowManager.touchableByAll()
        return request.createResponse(if (result) 0 else -1, data = result)
    }

    private fun handleGetAppScreenSize(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val bounds = AssistsCore.getAppBoundsInScreen()?.toBounds()
        return request.createResponse(0, data = bounds)
    }

    private fun handleGetScreenSize(request: CallRequest<JsonObject>): CallResponse<Any?> {
        return request.createResponse(0, data = JsonObject().apply {
            addProperty("screenWidth", ScreenUtils.getScreenWidth())
            addProperty("screenHeight", ScreenUtils.getScreenHeight())
        })
    }

    private suspend fun handleClickByGesture(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val switchWindowIntervalDelay = request.arguments?.get("switchWindowIntervalDelay")?.asLong ?: 250
        val clickDuration = request.arguments?.get("clickDuration")?.asLong ?: 25
        AssistsWindowManager.nonTouchableByAll()
        delay(switchWindowIntervalDelay)
        val result =
            AssistsCore.gestureClick(
                x = request.arguments?.get("x")?.asFloat ?: 0f,
                y = request.arguments?.get("y")?.asFloat ?: 0f,
                duration = clickDuration
            )
        AssistsWindowManager.touchableByAll()
        return request.createResponse(if (result) 0 else -1, data = result)
    }

    private suspend fun handleClickNodeByGesture(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val offsetX = request.arguments?.get("offsetX")?.asFloat ?: (ScreenUtils.getScreenWidth() * 0.01953f)
        val offsetY = request.arguments?.get("offsetY")?.asFloat ?: (ScreenUtils.getScreenWidth() * 0.01953f)
        val switchWindowIntervalDelay = request.arguments?.get("switchWindowIntervalDelay")?.asLong ?: 250
        val clickDuration = request.arguments?.get("clickDuration")?.asLong ?: 25
        val result = NodeCacheManager.get(request.node?.nodeId ?: "")?.nodeGestureClick(
            offsetX = offsetX,
            offsetY = offsetY,
            switchWindowIntervalDelay = switchWindowIntervalDelay,
            duration = clickDuration
        ) ?: false
        return request.createResponse(if (result) 0 else -1, data = result)
    }

    private suspend fun handleDoubleClickNodeByGesture(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val offsetX = request.arguments?.get("offsetX")?.asFloat ?: (ScreenUtils.getScreenWidth() * 0.01953f)
        val offsetY = request.arguments?.get("offsetY")?.asFloat ?: (ScreenUtils.getScreenWidth() * 0.01953f)
        val switchWindowIntervalDelay = request.arguments?.get("switchWindowIntervalDelay")?.asLong ?: 250
        val clickDuration = request.arguments?.get("clickDuration")?.asLong ?: 25
        val clickInterval = request.arguments?.get("clickInterval")?.asLong ?: 100
        val bounds = NodeCacheManager.get(request.node?.nodeId ?: "")?.getBoundsInScreen()

        AssistsWindowManager.nonTouchableByAll()
        delay(switchWindowIntervalDelay)

        val x = (bounds?.centerX()?.toFloat() ?: 0f) + offsetX
        val y = (bounds?.centerY()?.toFloat() ?: 0f) + offsetY

        AssistsCore.gestureClick(x, y, clickDuration)
        delay(clickInterval)
        AssistsCore.gestureClick(x, y, clickDuration)
        AssistsWindowManager.touchableByAll()

        return request.createResponse(0, data = true)
    }

    private fun handleGetBoundsInParent(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val bounds = NodeCacheManager.get(request.node?.nodeId ?: "")?.getBoundsInParent()?.toBounds()
        return request.createResponse(0, data = bounds)
    }

    private fun handleGetBoundsInScreen(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val bounds = NodeCacheManager.get(request.node?.nodeId ?: "")?.getBoundsInScreen()?.toBounds()
        return request.createResponse(0, data = bounds)
    }

    private fun handleIsVisible(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val value = NodeCacheManager.get(request.node?.nodeId ?: "")?.let letRoot@{ node ->
            val compareNodeId = request.arguments?.get("compareNode")?.asJsonObject?.get("nodeId")?.asString ?: ""
            val isFullyByCompareNode = request.arguments?.get("isFullyByCompareNode")?.asBoolean == true

            val compareNode = if (compareNodeId.isNotEmpty()) {
                NodeCacheManager.get(compareNodeId)
            } else {
                null
            }

            compareNode?.let {
                if (!node.isVisibleToUser) return@let false

                val compareNodeBounds = it.getBoundsInScreen()
                val nodeBounds = node.getBoundsInScreen()
                if (isFullyByCompareNode) {
                    if (compareNodeBounds.contains(nodeBounds)) {
                        return@letRoot false
                    }
                    if (Rect.intersects(compareNodeBounds, nodeBounds)) {
                        return@letRoot false
                    }
                    return@letRoot true
                } else {
                    if (compareNodeBounds.contains(nodeBounds)) {
                        return@letRoot false
                    }
                    return@letRoot true
                }
            }

            return@letRoot node.isVisibleToUser
        }
        return request.createResponse(0, data = value)
    }

    private fun handleGetAllText(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val texts = NodeCacheManager.get(request.node?.nodeId ?: "")?.getAllText()
        return request.createResponse(0, data = texts)
    }

    private fun handleGetChildren(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val nodes = NodeCacheManager.get(request.node?.nodeId ?: "")?.getChildren()?.toNodes()
        return request.createResponse(0, data = nodes)
    }

    private fun handleGetAllNodes(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val filterText = request.arguments?.get("filterText")?.asString ?: ""
        val filterDes = request.arguments?.get("filterDes")?.asString ?: ""
        val filterClass = request.arguments?.get("filterClass")?.asString ?: ""
        val filterViewId = request.arguments?.get("filterViewId")?.asString ?: ""
        val nodes = AssistsCore.getAllNodes(
            filterClass = filterClass,
            filterDes = filterDes,
            filterViewId = filterViewId,
            filterText = filterText
        ).toNodes()
        return request.createResponse(0, data = nodes)
    }

    private fun handleFindById(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val id = request.arguments?.get("id")?.asString ?: ""
        val filterText = request.arguments?.get("filterText")?.asString ?: ""
        val filterDes = request.arguments?.get("filterDes")?.asString ?: ""
        val filterClass = request.arguments?.get("filterClass")?.asString ?: ""
        val nodes = request.node?.get()?.let {
            it.findById(id, filterText = filterText, filterClass = filterClass, filterDes = filterDes).toNodes()
        } ?: AssistsCore.findById(id, filterText = filterText, filterClass = filterClass, filterDes = filterDes).toNodes()
        return request.createResponse(0, data = nodes)
    }

    private fun handleFindByText(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val text = request.arguments?.get("text")?.asString ?: ""
        val filterViewId = request.arguments?.get("filterViewId")?.asString ?: ""
        val filterDes = request.arguments?.get("filterDes")?.asString ?: ""
        val filterClass = request.arguments?.get("filterClass")?.asString ?: ""
        val nodes = request.node?.get()?.let {
            it.findByText(text, filterViewId = filterViewId, filterDes = filterDes, filterClass = filterClass).toNodes()
        } ?: AssistsCore.findByText(text, filterViewId = filterViewId, filterDes = filterDes, filterClass = filterClass).toNodes()
        return request.createResponse(0, data = nodes)
    }

    private fun handleFindByTags(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val className = request.arguments?.get("className")?.asString ?: ""
        val text = request.arguments?.get("filterText")?.asString ?: ""
        val viewId = request.arguments?.get("filterViewId")?.asString ?: ""
        val des = request.arguments?.get("filterDes")?.asString ?: ""
        val nodes = request.node?.get()?.let {
            it.findByTags(className = className, viewId = viewId, des = des, text = text).toNodes()
        } ?: AssistsCore.findByTags(className = className, text = text, viewId = viewId, des = des).toNodes()
        return request.createResponse(0, data = nodes)
    }

    private fun handleSelectionText(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val selectionStart = request.arguments?.get("selectionStart")?.asInt ?: 0
        val selectionEnd = request.arguments?.get("selectionEnd")?.asInt ?: 0
        val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.selectionText(selectionStart, selectionEnd) == true
        return request.createResponse(0, data = isSuccess)
    }

    private fun handleScrollForward(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.scrollForward() == true
        return request.createResponse(0, data = isSuccess)
    }

    private fun handleScrollBackward(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.scrollBackward() == true
        return request.createResponse(0, data = isSuccess)
    }

    private fun handleFindByTextAllMatch(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val nodes = AssistsCore.findByTextAllMatch(request.arguments?.get("text")?.asString ?: "").toNodes()
        return request.createResponse(0, data = nodes)
    }

    private fun handleContainsText(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val isSuccess =
            NodeCacheManager.get(request.node?.nodeId ?: "")?.containsText(request.arguments?.get("text")?.asString ?: "") == true
        return request.createResponse(0, data = isSuccess)
    }

    private fun handleFindFirstParentByTags(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val node =
            NodeCacheManager.get(request.node?.nodeId ?: "")?.findFirstParentByTags(request.arguments?.get("className")?.asString ?: "")
                ?.toNode()
        return request.createResponse(0, data = node)
    }

    private fun handleGetNodes(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val nodes = NodeCacheManager.get(request.node?.nodeId ?: "")?.getNodes()?.toNodes()
        return request.createResponse(0, data = nodes)
    }

    private fun handleFindFirstParentClickable(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val node = NodeCacheManager.get(request.node?.nodeId ?: "")?.findFirstParentClickable()?.toNode()
        return request.createResponse(0, data = node)
    }

    private fun handleSetNodeText(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val isSuccess =
            NodeCacheManager.get(request.node?.nodeId ?: "")?.setNodeText(request.arguments?.get("text")?.asString ?: "") == true
        return request.createResponse(0, data = isSuccess)
    }

    private fun handleClick(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.click() == true
        return request.createResponse(0, data = isSuccess)
    }

    private fun handleLongClick(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.longClick() == true
        return request.createResponse(0, data = isSuccess)
    }

    private fun handlePaste(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.paste(request.arguments?.get("text")?.asString ?: "") == true
        return request.createResponse(0, data = isSuccess)
    }

    private fun handleFocus(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.focus() == true
        return request.createResponse(0, data = isSuccess)
    }

    private suspend fun handleLaunchApp(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val packageName = request.arguments?.get("packageName")?.asString ?: ""
        AssistsCore.launchApp(packageName)
        return request.createResponse(0, data = true)
    }

    private fun handleGetPackageName(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val packageName = AssistsCore.getPackageName()
        return request.createResponse(0, data = packageName)
    }

    private fun handleBack(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val resultBack = AssistsCore.back()
        return request.createResponse(0, data = resultBack)
    }

    private fun handleHome(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val resultBack = AssistsCore.home()
        return request.createResponse(0, data = resultBack)
    }

    private fun handleNotifications(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val resultBack = AssistsCore.notifications()
        return request.createResponse(0, data = resultBack)
    }

    private fun handleRecentApps(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val resultBack = AssistsCore.recentApps()
        return request.createResponse(0, data = resultBack)
    }

    private fun handleHttpRequest(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val url = request.arguments?.get("url")?.asString ?: ""
        val method = request.arguments?.get("method")?.asString?.uppercase() ?: "GET"
        val headers = request.arguments?.get("headers")?.asJsonObject
        val body = request.arguments?.get("body")?.asString ?: ""
        val timeoutSeconds = request.arguments?.get("timeout")?.asLong ?: 30L

        if (method != "GET" && method != "POST") {
            return request.createResponse(-1, message = "不支持的请求方法: $method", data = JsonObject())
        }
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build()

            val requestBuilder = Request.Builder().url(url)

            headers?.entrySet()?.forEach { entry ->
                requestBuilder.addHeader(entry.key, entry.value.asString)
            }

            when (method) {
                "GET" -> requestBuilder.get()
                "POST" -> {
                    val contentType = headers?.get("Content-Type")?.asString ?: "application/json; charset=utf-8"
                    val requestBody = body.toRequestBody(contentType.toMediaType())
                    requestBuilder.post(requestBody)
                }

                else -> requestBuilder.get()
            }

            val httpRequest = requestBuilder.build()
            val httpResponse = client.newCall(httpRequest).execute()
            val responseBody = httpResponse.body?.string() ?: ""

            val responseData = JsonObject().apply {
                addProperty("statusCode", httpResponse.code)
                addProperty("statusMessage", httpResponse.message)
                addProperty("body", responseBody)
                add("headers", JsonObject().apply {
                    httpResponse.headers.forEach { pair ->
                        addProperty(pair.first, pair.second)
                    }
                })
            }

            request.createResponse(0, data = responseData)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "请求失败: ${e.message}", data = JsonObject())
        }
    }

    private fun handleOpenUrlInBrowser(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val url = request.arguments?.get("url")?.asString ?: ""
        return try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            JavascriptInterfaceContext.getContext()?.startActivity(intent)
            request.createResponse(0, data = true)
        } catch (e: Exception) {
            LogUtils.e(e)
            "打开外部浏览器失败：${e.message}".overlayToast()
            request.createResponse(-1, message = "打开浏览器失败: ${e.message}", data = false)
        }
    }

    private suspend fun handleAddContact(request: CallRequest<JsonObject>): CallResponse<Any?> {
        val name = request.arguments?.get("name")?.asString ?: ""
        val phoneNumber = request.arguments?.get("phoneNumber")?.asString ?: ""

        if (name.isEmpty() || phoneNumber.isEmpty()) {
            return request.createResponse(-1, message = "Name and phone number are required", data = false)
        }
        return try {
            val success = ContactsUtil.addContact(name, phoneNumber)
            request.createResponse(
                code = if (success) 0 else -1,
                message = if (success) "Contact added successfully" else "Failed to add contact",
                data = success
            )
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "Error: ${e.message}", data = false)
        }
    }

    private suspend fun handleGetAllContacts(request: CallRequest<JsonObject>): CallResponse<Any?> {
        return try {
            val contacts = ContactsUtil.getAllContacts()
            val contactsArray = JsonArray().apply {
                contacts.forEach { contact ->
                    add(JsonObject().apply {
                        addProperty("id", contact.id)
                        addProperty("name", contact.name)
                        add("phoneNumbers", JsonArray().apply {
                            contact.phoneNumbers.forEach { add(it) }
                        })
                        add("emails", JsonArray().apply {
                            contact.emails.forEach { add(it) }
                        })
                        addProperty("address", contact.address)
                    })
                }
            }
            request.createResponse(0, data = contactsArray)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "Error: ${e.message}", data = JsonArray())
        }
    }

    private fun handleSaveRootNodeTreeJson(request: CallRequest<JsonObject>): CallResponse<Any?> {
        return try {
            val filePath = request.arguments?.get("filePath")?.asString
            val prettyPrint = request.arguments?.get("prettyPrint")?.asBoolean ?: true

            val file =
                filePath?.let { File(it) } ?: File(PathUtils.getInternalAppFilesPath() + "/node_tree_${System.currentTimeMillis()}.json")

            val savedFile = AssistsCore.saveRootNodeTreeJson(file = file, prettyPrint = prettyPrint)

            request.createResponse(
                if (savedFile == null) -1 else 0,
                message = if (savedFile == null) "保存节点树JSON失败" else "保存成功",
                data = JsonObject().apply {
                    addProperty("file", savedFile?.path ?: "")
                }
            )
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "Error: ${e.message}", data = JsonObject())
        }
    }

    private suspend fun handleGetClipboardText(request: CallRequest<JsonObject>): CallResponse<Any?> {
        return try {
            val clipboardText = AssistsCore.getClipboardText()
            request.createResponse(
                code = 0,
                data = JsonObject().apply {
                    addProperty("text", clipboardText?.toString() ?: "")
                }
            )
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "获取剪贴板内容失败: ${e.message}", data = JsonObject().apply {
                addProperty("text", "")
            })
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)  // 可选 JPEG/PNG
        val byteArray = outputStream.toByteArray()
        return "data:image/png;base64,${Base64.encodeToString(byteArray, Base64.NO_WRAP)}"
    }
} 