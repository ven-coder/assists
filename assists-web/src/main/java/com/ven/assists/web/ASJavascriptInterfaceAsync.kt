package com.ven.assists.web

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
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
import com.ven.assists.web.databinding.WebFloatingWindowBinding
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowManager.overlayToast
import com.ven.assists.window.AssistsWindowWrapper
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
import com.blankj.utilcode.util.ImageUtils
import com.ven.assists.utils.AudioPlayerUtil
import com.ven.assists.utils.ContactsUtil
import com.ven.assists.utils.FileDownloadUtil
import com.ven.assists.web.utils.AudioPlayManager
import kotlinx.coroutines.CompletableDeferred

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
                CallMethod.audioStop -> {
                    AudioPlayerUtil.stop()
                    val response = request.createResponse(0, data = null)
                    response
                }

                CallMethod.audioPlayRingtone -> {
                    JavascriptInterfaceContext.getContext()?.let {
                        AudioPlayManager.startAudioPlay(it)
                        val response = request.createResponse(0, data = "开始播放系统电话铃声")
                        response
                    } ?: let {
                        val response = request.createResponse(-1, data = "上下文无效")
                        response
                    }
                }

                CallMethod.audioStopRingtone -> {
                    AudioPlayManager.stopAudioPlay()
                    val response = request.createResponse(0, data = "已停止播放")
                    response
                }

                CallMethod.audioPlayFromFile -> {
                    val filePath = request.arguments?.get("filePath")?.asString ?: ""
                    val volume = request.arguments?.get("volume")?.asFloat
                    val useAbsoluteVolume = request.arguments?.get("useAbsoluteVolume")?.asBoolean ?: false
                    JavascriptInterfaceContext.getContext()?.let {
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
                        val response = request.createResponse(
                            if (result == null) 0 else -1,
                            data = if (result == null) "播放完成" else "播放失败: ${result.message}"
                        )
                        response
                    } ?: let {
                        val response = request.createResponse(-1, data = "无障碍服务无效")
                        response
                    }
                }

                CallMethod.download -> {
                    val url = request.arguments?.get("url")?.asString ?: ""
                    JavascriptInterfaceContext.getContext()?.let {
                        val result = FileDownloadUtil.downloadFile(it, url)
                        when (result) {
                            is FileDownloadUtil.DownloadResult.Error -> {
                                val response = request.createResponse(-1, data = result.exception.message)
                                response
                            }

                            is FileDownloadUtil.DownloadResult.Success -> {
                                val response = request.createResponse(0, data = result.file.path)
                                response
                            }

                            else -> {
                                val response = request.createResponse(-1, data = null)
                                response
                            }
                        }
                    } ?: let {
                        val response = request.createResponse(-1, data = null)
                        response
                    }
                }

                CallMethod.getNetworkType -> {
                    val networkType = NetworkUtils.getNetworkType()
                    var networkTypeValue = ""
                    networkTypeValue = when (networkType) {
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
                    val response = request.createResponse(0, data = data)
                    response
                }

                CallMethod.recognizeTextInScreenshot -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        val response = request.createResponse(-1, message = "Screenshot recognition requires Android R or above", data = false)
                        response
                    } else {
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

                        recognitionResult.fold(
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

                                val response = request.createResponse(0, data = data)
                                response
                            },
                            onFailure = {
                                val response = request.createResponse(
                                    -1,
                                    message = it.message ?: "Recognition failed",
                                    data = ""
                                )
                                response
                            }
                        )
                    }
                }

                CallMethod.getDeviceInfo -> {
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

                    val response = request.createResponse(0, data = data)
                    response
                }

                CallMethod.getUniqueDeviceId -> {
                    val uniqueDeviceId = DeviceUtils.getUniqueDeviceId()
                    val response = request.createResponse(0, data = JsonObject().apply {
                        addProperty("uniqueDeviceId", uniqueDeviceId)
                    })
                    response
                }

                CallMethod.getAndroidID -> {
                    val androidID = DeviceUtils.getAndroidID()
                    val response = request.createResponse(0, data = JsonObject().apply {
                        addProperty("androidID", androidID)
                    })
                    response
                }

                CallMethod.getMacAddress -> {
                    val macAddress = DeviceUtils.getMacAddress()
                    val response = request.createResponse(0, data = JsonObject().apply {
                        addProperty("macAddress", macAddress)
                    })
                    response
                }

                CallMethod.longPressGestureAutoPaste -> {
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
                    var result = false
                    if (request.node?.nodeId.isNullOrEmpty()) {
                        result = AssistsCore.longPressGestureAutoPaste(
                            x = x,
                            y = y,
                            text = text,
                            matchedPackageName = matchedPackageName,
                            matchedText = matchedText,
                            timeoutMillis = timeoutMillis,
                            longPressDuration = longPressDuration
                        )
                    } else {
                        result = NodeCacheManager.get(request?.node?.nodeId ?: "")?.longPressGestureAutoPaste(
                            text = text,
                            matchedPackageName = matchedPackageName,
                            matchedText = matchedText,
                            timeoutMillis = timeoutMillis,
                            longPressDuration = longPressDuration
                        ) ?: false
                    }
                    AssistsWindowManager.touchableByAll()
                    val response = request.createResponse(code = if (result) 0 else -1, data = result, callbackId = request.callbackId)
                    response
                }

                CallMethod.getAppInfo -> {
                    val packageName = request.arguments?.get("packageName")?.asString ?: ""
                    val appInfo = AppUtils.getAppInfo(packageName)
                    val response = request.createResponse(0, data = appInfo)
                    response
                }

                CallMethod.loadWebViewOverlay -> {
                    val url = request.arguments?.get("url")?.asString ?: ""
                    val initialWidth = request.arguments?.get("initialWidth")?.asInt ?: (ScreenUtils.getScreenWidth() * 0.8).toInt()
                    val initialHeight = request.arguments?.get("initialHeight")?.asInt ?: (ScreenUtils.getScreenHeight() * 0.5).toInt()
                    val minWidth = request.arguments?.get("minWidth")?.asInt ?: (ScreenUtils.getScreenWidth() * 0.5).toInt()
                    val minHeight = request.arguments?.get("minHeight")?.asInt ?: (ScreenUtils.getScreenHeight() * 0.5).toInt()
                    val initialCenter = request.arguments?.get("initialCenter")?.asBoolean ?: true
                    val webWindowBinding = WebFloatingWindowBinding.inflate(LayoutInflater.from(JavascriptInterfaceContext.requireContext())).apply {
                        webView.loadUrl(url)
                        webView.setBackgroundColor(0)
                    }
                    AssistsWindowManager.add(
                        windowWrapper = AssistsWindowWrapper(
                            wmLayoutParams = AssistsWindowManager.createLayoutParams().apply {
                                width = initialWidth
                                height = initialHeight
                            },
                            view = webWindowBinding.root,
                            onClose = {
                                webWindowBinding.webView.loadUrl("about:blank")
                                webWindowBinding.webView.stopLoading()
                                webWindowBinding.webView.clearHistory()
                                webWindowBinding.webView.removeAllViews()
                                webWindowBinding.webView.destroy()
                                webWindowBinding.root.removeAllViews()
                                val viewGroup = it as ViewGroup
                                viewGroup.removeAllViews()
                                AssistsWindowManager.removeWindow(it)
                            }
                        ).apply {
                            viewBinding.ivWebBack.isVisible = true
                            viewBinding.ivWebBack.setOnClickListener { webWindowBinding.webView.goBack() }
                            viewBinding.ivWebForward.isVisible = true
                            viewBinding.ivWebForward.setOnClickListener { webWindowBinding.webView.goBack() }

                            viewBinding.ivWebRefresh.isVisible = true
                            viewBinding.ivWebRefresh.setOnClickListener { webWindowBinding.webView.reload() }

                            this.minWidth = minWidth
                            this.minHeight = minHeight
                            this.initialCenter = initialCenter
                        }
                    )

                    val response = request.createResponse(0, data = true)
                    response
                }

                CallMethod.scanQR -> {
                    AssistsWindowManager.hideAll()
                    val scanIntentResult = CustomFileProvider.requestLaunchersScan(ScanOptions())
                    AssistsWindowManager.showTop()
                    val response = request.createResponse(0, data = JsonObject().apply {
                        addProperty("value", scanIntentResult?.contents ?: "")
                    })
                    response
                }

                CallMethod.setOverlayFlags -> {
                    request.arguments?.apply {
                        val flagList = arrayListOf<Int>()
                        get("flags")?.asJsonArray?.forEach {
                            flagList.add(it.asInt)
                        }
                        val flags = flagList.reduce { a, b -> a or b }
                        CoroutineWrapper.launch { AssistsWindowManager.setFlags(flags) }
                    }
                    val response = request.createResponse(0, data = true)
                    response
                }

                CallMethod.takeScreenshot -> {
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
                    val response = request.createResponse(if (list.isEmpty()) -1 else 0, data = JsonObject().apply {
                        add("images", JsonArray().apply {
                            list.forEach {
                                add(it)
                            }
                        })
                    })
                    response
                }

                CallMethod.takeScreenshotSave -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        val response = request.createResponse(-1, message = "takeScreenshotSave requires Android R or above", data = null)
                        response
                    } else {
                        val overlayHiddenScreenshotDelayMillis = request.arguments?.get("overlayHiddenScreenshotDelayMillis")?.asLong ?: 250
                        val filePath = request.arguments?.get("filePath")?.asString
                        val formatStr = request.arguments?.get("format")?.asString ?: "PNG"
                        
                        // 解析格式参数
                        val format = when (formatStr.uppercase()) {
                            "PNG" -> CompressFormat.PNG
                            "JPEG", "JPG" -> CompressFormat.JPEG
                            "WEBP" -> CompressFormat.WEBP
                            else -> CompressFormat.PNG
                        }
                        
                        // 获取文件扩展名
                        val fileExtension = when (format) {
                            CompressFormat.PNG -> "png"
                            CompressFormat.JPEG -> "jpg"
                            CompressFormat.WEBP -> "webp"
                            else -> "png"
                        }
                        
                        AssistsWindowManager.hideAll()
                        delay(overlayHiddenScreenshotDelayMillis)
                        
                        // 构建文件对象
                        val file = filePath?.let { File(it) } ?: File(PathUtils.getInternalAppFilesPath() + "/${System.currentTimeMillis()}.$fileExtension")
                        
                        // 如果提供了节点，则保存节点截图；否则保存全屏截图
                        val savedFile = if (request.node?.nodeId.isNullOrEmpty()) {
                            // 全屏截图保存
                            AssistsCore.takeScreenshotSave(file = file, format = format)
                        } else {
                            // 节点截图保存
                            NodeCacheManager.get(request.node?.nodeId ?: "")?.takeScreenshotSave(file = file, format = format)
                        }
                        
                        AssistsWindowManager.showTop()
                        
                        val response = request.createResponse(
                            if (savedFile == null) -1 else 0,
                            message = if (savedFile == null) "截图保存失败" else "截图保存成功",
                            data = JsonObject().apply {
                                addProperty("file", savedFile?.path ?: "")
                            }
                        )
                        response
                    }
                }

                CallMethod.takeScreenshotToFile -> {
                    val overlayHiddenScreenshotDelayMillis = request.arguments?.get("overlayHiddenScreenshotDelayMillis")?.asLong ?: 250
                    val formatStr = request.arguments?.get("format")?.asString ?: "PNG"
                    val baseFilePath = request.arguments?.get("filePath")?.asString
                    
                    // 解析格式参数
                    val format = when (formatStr.uppercase()) {
                        "PNG" -> CompressFormat.PNG
                        "JPEG", "JPG" -> CompressFormat.JPEG
                        "WEBP" -> CompressFormat.WEBP
                        else -> CompressFormat.PNG
                    }
                    
                    // 获取文件扩展名
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
                        // Android R 及以上版本
                        val screenshot = AssistsCore.takeScreenshot()
                        AssistsWindowManager.showTop()
                        
                        if (request.nodes.isNullOrEmpty()) {
                            // 如果没有指定节点，保存全屏截图
                            val file = baseFilePath?.let { File(it) } ?: File(PathUtils.getInternalAppFilesPath() + "/screenshot_${System.currentTimeMillis()}.$fileExtension")
                            file.parentFile?.mkdirs()
                            screenshot?.let {
                                val success = ImageUtils.save(it, file, format)
                                if (success) {
                                    filePaths.add(file.absolutePath)
                                }
                                it.recycle()
                            }
                        } else {
                            // 保存多个节点截图
                            request.nodes?.forEachIndexed { index, nodeRequest ->
                                val bitmap = NodeCacheManager.get(nodeRequest.nodeId)?.takeScreenshot(screenshot = screenshot)
                                bitmap?.let {
                                    val file = if (baseFilePath != null && request.nodes?.size == 1) {
                                        // 如果只有一个节点且指定了路径，使用指定路径
                                        File(baseFilePath)
                                    } else {
                                        // 多个节点时，使用索引生成文件名
                                        val fileName = if (baseFilePath != null) {
                                            val baseFile = File(baseFilePath)
                                            val nameWithoutExt = baseFile.nameWithoutExtension
                                            val parent = baseFile.parent ?: PathUtils.getInternalAppFilesPath()
                                            File(parent, "${nameWithoutExt}_${index}.$fileExtension")
                                        } else {
                                            File(PathUtils.getInternalAppFilesPath() + "/screenshot_${System.currentTimeMillis()}_${index}.$fileExtension")
                                        }
                                        fileName
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
                        // Android R 以下版本使用 MPManager
                        val takeScreenshot2Bitmap = MPManager.takeScreenshot2Bitmap()
                        AssistsWindowManager.showTop()
                        
                        takeScreenshot2Bitmap?.let {
                            if (request.nodes.isNullOrEmpty()) {
                                // 如果没有指定节点，保存全屏截图
                                val file = baseFilePath?.let { File(it) } ?: File(PathUtils.getInternalAppFilesPath() + "/screenshot_${System.currentTimeMillis()}.$fileExtension")
                                file.parentFile?.mkdirs()
                                val success = ImageUtils.save(it, file, format)
                                if (success) {
                                    filePaths.add(file.absolutePath)
                                }
                            } else {
                                // 保存多个节点截图
                                request.nodes?.forEachIndexed { index, nodeRequest ->
                                    val bitmap = NodeCacheManager.get(nodeRequest.nodeId)?.getBitmap(screenshot = it)
                                    bitmap?.let { nodeBitmap ->
                                        val file = if (baseFilePath != null && request.nodes?.size == 1) {
                                            // 如果只有一个节点且指定了路径，使用指定路径
                                            File(baseFilePath)
                                        } else {
                                            // 多个节点时，使用索引生成文件名
                                            val fileName = if (baseFilePath != null) {
                                                val baseFile = File(baseFilePath)
                                                val nameWithoutExt = baseFile.nameWithoutExtension
                                                val parent = baseFile.parent ?: PathUtils.getInternalAppFilesPath()
                                                File(parent, "${nameWithoutExt}_${index}.$fileExtension")
                                            } else {
                                                File(PathUtils.getInternalAppFilesPath() + "/screenshot_${System.currentTimeMillis()}_${index}.$fileExtension")
                                            }
                                            fileName
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
                    
                    val response = request.createResponse(
                        if (filePaths.isEmpty()) -1 else 0,
                        message = if (filePaths.isEmpty()) "截图保存失败" else "截图保存成功",
                        data = JsonObject().apply {
                            add("files", JsonArray().apply {
                                filePaths.forEach { add(it) }
                            })
                        }
                    )
                    response
                }

                CallMethod.performLinearGesture -> {
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
                    val response = request.createResponse(if (result) 0 else -1, data = result)
                    response
                }

                CallMethod.getAppScreenSize -> {
                    val bounds = AssistsCore.getAppBoundsInScreen()?.toBounds()
                    val response = request.createResponse(0, data = bounds)
                    response
                }

                CallMethod.getScreenSize -> {
                    val response = request.createResponse(0, data = JsonObject().apply {
                        addProperty("screenWidth", ScreenUtils.getScreenWidth())
                        addProperty("screenHeight", ScreenUtils.getScreenHeight())
                    })
                    response
                }

                CallMethod.clickByGesture -> {
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
                    val response = request.createResponse(if (result) 0 else -1, data = result)
                    response
                }

                CallMethod.clickNodeByGesture -> {
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
                    val response = request.createResponse(if (result) 0 else -1, data = result)
                    response
                }

                CallMethod.doubleClickNodeByGesture -> {
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

                    val response = request.createResponse(0, data = true)
                    response
                }


                CallMethod.getBoundsInParent -> {

                    val bounds = NodeCacheManager.get(request.node?.nodeId ?: "")?.getBoundsInParent()?.toBounds()

                    val response = request.createResponse(0, data = bounds)
                    response
                }

                CallMethod.getBoundsInScreen -> {

                    val bounds = NodeCacheManager.get(request.node?.nodeId ?: "")?.getBoundsInScreen()?.toBounds()

                    val response = request.createResponse(0, data = bounds)
                    response
                }

                CallMethod.isVisible -> {

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
                    val response = request.createResponse(0, data = value)
                    response
                }

                CallMethod.getAllText -> {

                    val texts = NodeCacheManager.get(request.node?.nodeId ?: "")?.getAllText()

                    val response = request.createResponse(0, data = texts)
                    response
                }

                CallMethod.getChildren -> {

                    val nodes = NodeCacheManager.get(request.node?.nodeId ?: "")?.getChildren()?.toNodes()

                    val response = request.createResponse(0, data = nodes)
                    response
                }

                CallMethod.getAllNodes -> {
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
                    val response = request.createResponse(0, data = nodes)
                    response
                }

                CallMethod.findById -> {
                    val id = request.arguments?.get("id")?.asString ?: ""
                    val filterText = request.arguments?.get("filterText")?.asString ?: ""
                    val filterDes = request.arguments?.get("filterDes")?.asString ?: ""
                    val filterClass = request.arguments?.get("filterClass")?.asString ?: ""
                    val nodes = request.node?.get()?.let {
                        val nodes = it.findById(id, filterText = filterText, filterClass = filterClass, filterDes = filterDes).toNodes()
                        return@let nodes
                    } ?: let {
                        val nodes = AssistsCore.findById(id, filterText = filterText, filterClass = filterClass, filterDes = filterDes).toNodes()
                        return@let nodes
                    }
                    val response = request.createResponse(0, data = nodes)
                    response
                }


                CallMethod.findByText -> {
                    val text = request.arguments?.get("text")?.asString ?: ""
                    val filterViewId = request.arguments?.get("filterViewId")?.asString ?: ""
                    val filterDes = request.arguments?.get("filterDes")?.asString ?: ""
                    val filterClass = request.arguments?.get("filterClass")?.asString ?: ""
                    val nodes = request.node?.get()?.let {
                        val nodes = it.findByText(text, filterViewId = filterViewId, filterDes = filterDes, filterClass = filterClass).toNodes()
                        return@let nodes
                    } ?: let {
                        val nodes =
                            AssistsCore.findByText(text, filterViewId = filterViewId, filterDes = filterDes, filterClass = filterClass).toNodes()
                        return@let nodes
                    }
                    val response = request.createResponse(0, data = nodes)
                    response
                }

                CallMethod.findByTags -> {
                    val className = request.arguments?.get("className")?.asString ?: ""
                    val text = request.arguments?.get("filterText")?.asString ?: ""
                    val viewId = request.arguments?.get("filterViewId")?.asString ?: ""
                    val des = request.arguments?.get("filterDes")?.asString ?: ""
                    val nodes = request.node?.get()?.let {
                        val nodes = it.findByTags(className = className, viewId = viewId, des = des, text = text).toNodes()
                        return@let nodes
                    } ?: let {
                        val nodes = AssistsCore.findByTags(className = className, text = text, viewId = viewId, des = des).toNodes()
                        return@let nodes
                    }
                    val response = request.createResponse(0, data = nodes)
                    response
                }

                CallMethod.selectionText -> {
                    val selectionStart = request.arguments?.get("selectionStart")?.asInt ?: 0
                    val selectionEnd = request.arguments?.get("selectionEnd")?.asInt ?: 0
                    val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.selectionText(selectionStart, selectionEnd) == true
                    val response = request.createResponse(0, data = isSuccess)
                    response
                }

                CallMethod.scrollForward -> {
                    val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.scrollForward() == true
                    val response = request.createResponse(0, data = isSuccess)
                    response
                }

                CallMethod.scrollBackward -> {
                    val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.scrollBackward() == true
                    val response = request.createResponse(0, data = isSuccess)
                    response
                }

                CallMethod.findByTextAllMatch -> {
                    val nodes = AssistsCore.findByTextAllMatch(request.arguments?.get("text")?.asString ?: "").toNodes()
                    val response = request.createResponse(0, data = nodes)
                    response
                }

                CallMethod.containsText -> {
                    val isSuccess =
                        NodeCacheManager.get(request.node?.nodeId ?: "")?.containsText(request.arguments?.get("text")?.asString ?: "") == true
                    val response = request.createResponse(0, data = isSuccess)
                    response
                }

                CallMethod.findFirstParentByTags -> {
                    val node =
                        NodeCacheManager.get(request.node?.nodeId ?: "")?.findFirstParentByTags(request.arguments?.get("className")?.asString ?: "")
                            ?.toNode()
                    val response = request.createResponse(0, data = node)
                    response
                }

                CallMethod.getNodes -> {
                    val nodes =
                        NodeCacheManager.get(request.node?.nodeId ?: "")?.getNodes()
                            ?.toNodes()
                    val response = request.createResponse(0, data = nodes)
                    response
                }

                CallMethod.findFirstParentClickable -> {
                    val node =
                        NodeCacheManager.get(request.node?.nodeId ?: "")?.findFirstParentClickable()
                            ?.toNode()
                    val response = request.createResponse(0, data = node)
                    response
                }


                CallMethod.setNodeText -> {
                    val isSuccess =
                        NodeCacheManager.get(request.node?.nodeId ?: "")?.setNodeText(request.arguments?.get("text")?.asString ?: "") == true
                    val response = request.createResponse(0, data = isSuccess)
                    response
                }

                CallMethod.click -> {
                    val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.click() == true
                    val response = request.createResponse(0, data = isSuccess)
                    response
                }

                CallMethod.longClick -> {
                    val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.longClick() == true
                    val response = request.createResponse(0, data = isSuccess)
                    response
                }

                CallMethod.paste -> {
                    val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.paste(request.arguments?.get("text")?.asString ?: "") == true
                    val response = request.createResponse(0, data = isSuccess)
                    response
                }

                CallMethod.focus -> {
                    val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.focus() == true
                    val response = request.createResponse(0, data = isSuccess)
                    response
                }

                //其他方法
                CallMethod.launchApp -> {
                    val packageName = request.arguments?.get("packageName")?.asString ?: ""
                    AssistsCore.launchApp(packageName)
                    val response = request.createResponse(0, data = true)
                    response
                }

                CallMethod.getPackageName -> {
                    val packageName = AssistsCore.getPackageName()
                    val response = request.createResponse(0, data = packageName)
                    response
                }

                CallMethod.overlayToast -> {
                    val text = request.arguments?.get("text")?.asString ?: ""
                    val delay = request.arguments?.get("delay")?.asLong ?: 2000L
                    text.overlayToast(delay)
                    val response = request.createResponse(0, data = true)
                    response
                }

                CallMethod.back -> {
                    val resultBack = AssistsCore.back()
                    val response = request.createResponse(0, data = resultBack)
                    response
                }

                CallMethod.home -> {
                    val resultBack = AssistsCore.home()
                    val response = request.createResponse(0, data = resultBack)
                    response
                }

                CallMethod.notifications -> {
                    val resultBack = AssistsCore.notifications()
                    val response = request.createResponse(0, data = resultBack)
                    response
                }

                CallMethod.recentApps -> {
                    val resultBack = AssistsCore.recentApps()
                    val response = request.createResponse(0, data = resultBack)
                    response
                }

                CallMethod.httpRequest -> {
                    val url = request.arguments?.get("url")?.asString ?: ""
                    val method = request.arguments?.get("method")?.asString?.uppercase() ?: "GET"
                    val headers = request.arguments?.get("headers")?.asJsonObject
                    val body = request.arguments?.get("body")?.asString ?: ""
                    val timeoutSeconds = request.arguments?.get("timeout")?.asLong ?: 30L

                    // 验证请求方法
                    if (method != "GET" && method != "POST") {
                        val response = request.createResponse(-1, message = "不支持的请求方法: $method", data = JsonObject())
                        response
                    } else {
                        try {
                            val client = OkHttpClient.Builder()
                                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                                .build()

                            val requestBuilder = Request.Builder().url(url)

                            // 添加请求头
                            headers?.entrySet()?.forEach { entry ->
                                requestBuilder.addHeader(entry.key, entry.value.asString)
                            }

                            // 根据请求方法构建请求
                            when (method) {
                                "GET" -> requestBuilder.get()
                                "POST" -> {
                                    val contentType = headers?.get("Content-Type")?.asString ?: "application/json; charset=utf-8"
                                    val requestBody = body.toRequestBody(contentType.toMediaType())
                                    requestBuilder.post(requestBody)
                                }
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

                            val response = request.createResponse(0, data = responseData)
                            response
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            val response = request.createResponse(-1, message = "请求失败: ${e.message}", data = JsonObject())
                            response
                        }
                    }
                }

                CallMethod.openUrlInBrowser -> {
                    val url = request.arguments?.get("url")?.asString ?: ""
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        JavascriptInterfaceContext.getContext()?.startActivity(intent)
                        val response = request.createResponse(0, data = true)
                        response
                    } catch (e: Exception) {
                        LogUtils.e(e)
                        "打开外部浏览器失败：${e.message}".overlayToast()
                        val response = request.createResponse(-1, message = "打开浏览器失败: ${e.message}", data = false)
                        response
                    }
                }

                CallMethod.addContact -> {
                    val name = request.arguments?.get("name")?.asString ?: ""
                    val phoneNumber = request.arguments?.get("phoneNumber")?.asString ?: ""
                    
                    if (name.isEmpty() || phoneNumber.isEmpty()) {
                        val response = request.createResponse(-1, message = "Name and phone number are required", data = false)
                        response
                    } else {
                        try {
                            val success = ContactsUtil.addContact(name, phoneNumber)
                            val response = request.createResponse(
                                code = if (success) 0 else -1,
                                message = if (success) "Contact added successfully" else "Failed to add contact",
                                data = success
                            )
                            response
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            val response = request.createResponse(-1, message = "Error: ${e.message}", data = false)
                            response
                        }
                    }
                }

                CallMethod.getAllContacts -> {
                    try {
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
                        val response = request.createResponse(0, data = contactsArray)
                        response
                    } catch (e: Exception) {
                        LogUtils.e(e)
                        val response = request.createResponse(-1, message = "Error: ${e.message}", data = JsonArray())
                        response
                    }
                }

                CallMethod.saveRootNodeTreeJson -> {
                    try {
                        val filePath = request.arguments?.get("filePath")?.asString
                        val prettyPrint = request.arguments?.get("prettyPrint")?.asBoolean ?: true
                        
                        // 构建文件对象
                        val file = filePath?.let { File(it) } ?: File(PathUtils.getInternalAppFilesPath() + "/node_tree_${System.currentTimeMillis()}.json")
                        
                        // 保存节点树JSON到文件
                        val savedFile = AssistsCore.saveRootNodeTreeJson(file = file, prettyPrint = prettyPrint)
                        
                        val response = request.createResponse(
                            if (savedFile == null) -1 else 0,
                            message = if (savedFile == null) "保存节点树JSON失败" else "保存成功",
                            data = JsonObject().apply {
                                addProperty("file", savedFile?.path ?: "")
                            }
                        )
                        response
                    } catch (e: Exception) {
                        LogUtils.e(e)
                        val response = request.createResponse(-1, message = "Error: ${e.message}", data = JsonObject())
                        response
                    }
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


    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)  // 可选 JPEG/PNG
        val byteArray = outputStream.toByteArray()
        return "data:image/png;base64,${Base64.encodeToString(byteArray, Base64.NO_WRAP)}"
    }
} 