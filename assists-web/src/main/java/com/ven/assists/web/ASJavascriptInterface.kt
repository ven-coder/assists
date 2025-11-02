package com.ven.assists.web

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.net.wifi.WifiManager
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
import com.ven.assists.mp.MPManager
import com.ven.assists.mp.MPManager.getBitmap
import com.ven.assists.service.AssistsService
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.utils.runIO
import com.ven.assists.utils.runMain
import com.ven.assists.web.databinding.WebFloatingWindowBinding
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowManager.overlayToast
import com.ven.assists.window.AssistsWindowWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri
import com.blankj.utilcode.util.ActivityUtils

class ASJavascriptInterface(val webView: WebView) {
    var callIntercept: ((json: String) -> CallInterceptResult)? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    fun <T> callback(result: CallResponse<T>) {
        coroutineScope.launch {
            runCatching {
                val json = GsonUtils.toJson(result)
                val encoded = Base64.encodeToString(json.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                val js = String.format("javascript:assistsxCallback('%s')", encoded)
                webView.evaluateJavascript(js, null)
            }.onFailure {
                LogUtils.e(it)
            }
        }
    }

    @JavascriptInterface
    fun call(originJson: String): String {
        var requestJson = originJson

        runCatching {
            callIntercept?.invoke(originJson)?.let {
                if (it.intercept) {
                    return it.result
                } else {
                    requestJson = it.result
                }
            }
        }.onFailure { LogUtils.e(it) }

        var result = GsonUtils.toJson(CallResponse<Any>(code = -1))
        runCatching {
            val request = GsonUtils.fromJson<CallRequest<JsonObject>>(requestJson, object : TypeToken<CallRequest<JsonObject>>() {}.type)
            when (request.method) {
                CallMethod.keepScreenOn -> {
                    val tip = request.arguments?.get("tip")?.asString ?: ""
                    AssistsCore.keepScreenOn(tip)
                }

                CallMethod.clearKeepScreenOn -> {
                    AssistsCore.clearKeepScreenOn()
                }

                CallMethod.getClipboardLatestText -> {
                    val value = (AssistsService.instance?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).let { clipboardManager ->
                        if (!clipboardManager.hasPrimaryClip()) {
                            return@let ""
                        }
                        return@let clipboardManager.primaryClip?.let {
                            if (it.itemCount <= 0) return@let ""
                            val item = it.getItemAt(0)
                            // 可能是 text、uri 或 intent
                            return@let item.text?.toString()
                                ?: item.uri?.toString()
                                ?: item.intent?.toUri(Intent.URI_INTENT_SCHEME)
                        } ?: ""
                    }
                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("text", value)
                    }))
                }

                CallMethod.isAppInstalled -> {
                    val packageName = request.arguments?.get("packageName")?.asString ?: ""
                    val appInstalled = AppUtils.isAppInstalled(packageName)
                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("appInstalled", appInstalled)
                    }))
                }

                CallMethod.getNetworkType -> {
                    CoroutineWrapper.launch {
                        runCatching {
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
                            callback(CallResponse(code = 0, data = data, callbackId = request.callbackId))
                        }.onFailure {
                            LogUtils.e(it)
                            callback(CallResponse(code = 0, data = JsonObject(), callbackId = request.callbackId))
                        }
                    }
                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject()))
                }

                CallMethod.getDeviceInfo -> {

                    CoroutineWrapper.launch {
                        runCatching {
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
                            callback(CallResponse(code = 0, data = data, callbackId = request.callbackId))
                        }.onFailure {
                            LogUtils.e(it)
                            callback(CallResponse(code = 0, data = JsonObject(), callbackId = request.callbackId))
                        }
                    }


                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject()))

                }

                CallMethod.getUniqueDeviceId -> {
                    val uniqueDeviceId = DeviceUtils.getUniqueDeviceId()
                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("uniqueDeviceId", uniqueDeviceId)
                    }))
                }

                CallMethod.getAndroidID -> {
                    val androidID = DeviceUtils.getAndroidID()
                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("androidID", androidID)
                    }))
                }

                CallMethod.getMacAddress -> {
                    CoroutineWrapper.launch {
                        runCatching {
                            val macAddress = DeviceUtils.getMacAddress()
                            callback(CallResponse(code = 0, data = JsonObject().apply {
                                addProperty("macAddress", macAddress)
                            }, callbackId = request.callbackId))
                        }.onFailure {
                            callback(CallResponse(code = -1, data = JsonObject(), callbackId = request.callbackId, message = it.message))
                        }
                    }
                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("resultType", "callback")
                    }))
                }

                CallMethod.longPressGestureAutoPaste -> {
                    CoroutineWrapper.launch {
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
                        callback(CallResponse(code = if (result) 0 else -1, data = result, callbackId = request.callbackId))

                    }
                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("resultType", "callback")
                    }))
                }

                CallMethod.getAppInfo -> {
                    val packageName = request.arguments?.get("packageName")?.asString ?: ""
                    CoroutineWrapper.launch {
                        runCatching {
                            val appInfo = AppUtils.getAppInfo(packageName)
                            callback(CallResponse(code = 0, data = appInfo, callbackId = request.callbackId))
                        }.onFailure {
                            callback(CallResponse(code = 0, data = JsonObject(), callbackId = request.callbackId))
                        }
                    }
                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("resultType", "callback")
                    }))
                }

                CallMethod.loadWebViewOverlay -> {
                    CoroutineWrapper.launch(isMain = true) {
                        runCatching {
                            val url = request.arguments?.get("url")?.asString ?: ""
                            val initialWidth = request.arguments?.get("initialWidth")?.asInt ?: (ScreenUtils.getScreenWidth() * 0.8).toInt()
                            val initialHeight = request.arguments?.get("initialHeight")?.asInt ?: (ScreenUtils.getScreenHeight() * 0.5).toInt()
                            val minWidth = request.arguments?.get("minWidth")?.asInt ?: (ScreenUtils.getScreenWidth() * 0.5).toInt()
                            val minHeight = request.arguments?.get("minHeight")?.asInt ?: (ScreenUtils.getScreenHeight() * 0.5).toInt()
                            val initialCenter = request.arguments?.get("initialCenter")?.asBoolean ?: true
                            val keepScreenOn = request.arguments?.get("keepScreenOn")?.asBoolean ?: false
                            val webWindowBinding = WebFloatingWindowBinding.inflate(LayoutInflater.from(AssistsService.instance)).apply {
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
                                        AssistsCore.clearKeepScreenOn()
                                    }
                                ).apply {
                                    viewBinding.ivWebBack.isVisible = true
                                    viewBinding.ivWebBack.setOnClickListener { webWindowBinding.webView.goBack() }
                                    viewBinding.ivWebForward.isVisible = true
                                    viewBinding.ivWebForward.setOnClickListener { webWindowBinding.webView.goBack() }

                                    viewBinding.ivWebRefresh.isVisible = true
                                    viewBinding.ivWebRefresh.setOnClickListener { webWindowBinding.webView.reload() }
                                    webWindowBinding.webView.onReceivedTitle = { viewBinding.tvTitle.text = it }
                                    this.minWidth = minWidth
                                    this.minHeight = minHeight
                                    this.initialCenter = initialCenter
                                    if (keepScreenOn) {
                                        AssistsCore.keepScreenOn()
                                    }
                                }
                            )
                        }.onSuccess {
                            callback(CallResponse<Boolean>(code = 0, data = true, callbackId = request.callbackId))
                        }.onFailure {
                            callback(CallResponse<Boolean>(code = -1, data = false, callbackId = request.callbackId))
                        }

                    }

                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("resultType", "callback")
                    }))
                }

                CallMethod.scanQR -> {
                    CoroutineWrapper.launch {
                        AssistsWindowManager.hideAll()
                        val scanIntentResult = CustomFileProvider.requestLaunchersScan(ScanOptions())
                        AssistsWindowManager.showTop()
                        callback(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                            addProperty("value", scanIntentResult?.contents ?: "")
                        }, callbackId = request.callbackId))
                    }

                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("resultType", "callback")
                    }))
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
                }

                CallMethod.takeScreenshot -> {
                    CoroutineWrapper.launch {
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

                            takeScreenshot2Bitmap ?: let {
                                callback(CallResponse<JsonObject>(code = -1, message = "截屏失败", callbackId = request.callbackId))
                                return@launch
                            }
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
                        callback(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                            add("images", JsonArray().apply {
                                list.forEach {
                                    add(it)
                                }
                            })
                        }, callbackId = request.callbackId))

                    }

                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("resultType", "callback")
                    }))
                }

                CallMethod.performLinearGesture -> {
                    CoroutineWrapper.launch {
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
                        if (result) {
                            callback(CallResponse<Boolean>(code = 0, data = true, callbackId = request.callbackId))
                        } else {
                            callback(CallResponse<Boolean>(code = -1, data = false, callbackId = request.callbackId))
                        }
                    }
                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("resultType", "callback")
                    }))
                }

                CallMethod.getAppScreenSize -> {
                    val bounds = AssistsCore.getAppBoundsInScreen()?.toBounds()
                    result = GsonUtils.toJson(CallResponse<Node.Bounds>(code = 0, data = bounds))
                }

                CallMethod.getScreenSize -> {
                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("screenWidth", ScreenUtils.getScreenWidth())
                        addProperty("screenHeight", ScreenUtils.getScreenHeight())
                    }))
                }

                CallMethod.clickByGesture -> {
                    CoroutineWrapper.launch {
                        val switchWindowIntervalDelay = request.arguments?.get("switchWindowIntervalDelay")?.asLong ?: 250
                        val duration = request.arguments?.get("duration")?.asLong ?: 25
                        AssistsWindowManager.nonTouchableByAll()
                        delay(switchWindowIntervalDelay)
                        val result =
                            AssistsCore.gestureClick(
                                x = request.arguments?.get("x")?.asFloat ?: 0f,
                                y = request.arguments?.get("y")?.asFloat ?: 0f,
                                duration = duration
                            )
                        AssistsWindowManager.touchableByAll()
                        if (result) {
                            callback(CallResponse<Boolean>(code = 0, data = true, callbackId = request.callbackId))
                        } else {
                            callback(CallResponse<Boolean>(code = -1, data = false, callbackId = request.callbackId))
                        }
                    }
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = true))
                }

                CallMethod.clickNodeByGesture -> {
                    CoroutineWrapper.launch {
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
                        if (result) {
                            callback(CallResponse<Boolean>(code = 0, data = true, callbackId = request.callbackId))
                        } else {
                            callback(CallResponse<Boolean>(code = -1, data = false, callbackId = request.callbackId))
                        }
                    }
                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("resultType", "callback")
                    }))
                }

                CallMethod.doubleClickNodeByGesture -> {
                    CoroutineWrapper.launch {
                        runCatching {
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

                            callback(CallResponse<Boolean>(code = 0, data = true, callbackId = request.callbackId))
                        }.onFailure {
                            callback(CallResponse<Boolean>(code = -1, data = false, callbackId = request.callbackId))
                        }

                    }
                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("resultType", "callback")
                    }))
                }


                CallMethod.getBoundsInParent -> {

                    val bounds = NodeCacheManager.get(request.node?.nodeId ?: "")?.getBoundsInParent()?.toBounds()

                    result = GsonUtils.toJson(CallResponse<Node.Bounds>(code = 0, data = bounds))
                }

                CallMethod.getBoundsInScreen -> {

                    val bounds = NodeCacheManager.get(request.node?.nodeId ?: "")?.getBoundsInScreen()?.toBounds()

                    result = GsonUtils.toJson(CallResponse<Node.Bounds>(code = 0, data = bounds))
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
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = value))

                }

                CallMethod.getAllText -> {

                    val texts = NodeCacheManager.get(request.node?.nodeId ?: "")?.getAllText()

                    result = GsonUtils.toJson(CallResponse<List<String>>(code = 0, data = texts))
                }

                CallMethod.getChildren -> {

                    val nodes = NodeCacheManager.get(request.node?.nodeId ?: "")?.getChildren()?.toNodes()

                    result = GsonUtils.toJson(CallResponse<List<Node>>(code = 0, data = nodes))
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
                    result = GsonUtils.toJson(CallResponse<List<Node>>(code = 0, data = nodes))
                }

                CallMethod.findById -> {
                    val id = request.arguments?.get("id")?.asString ?: ""
                    val filterText = request.arguments?.get("filterText")?.asString ?: ""
                    val filterDes = request.arguments?.get("filterDes")?.asString ?: ""
                    val filterClass = request.arguments?.get("filterClass")?.asString ?: ""
                    request.node?.get()?.let {
                        val nodes = it.findById(id, filterText = filterText, filterClass = filterClass, filterDes = filterDes).toNodes()
                        result = GsonUtils.toJson(CallResponse<List<Node>>(code = 0, data = nodes))
                    } ?: let {
                        val nodes = AssistsCore.findById(id, filterText = filterText, filterClass = filterClass, filterDes = filterDes).toNodes()
                        result = GsonUtils.toJson(CallResponse<List<Node>>(code = 0, data = nodes))
                    }
                }


                CallMethod.findByText -> {
                    val text = request.arguments?.get("text")?.asString ?: ""
                    val filterViewId = request.arguments?.get("filterViewId")?.asString ?: ""
                    val filterDes = request.arguments?.get("filterDes")?.asString ?: ""
                    val filterClass = request.arguments?.get("filterClass")?.asString ?: ""
                    request.node?.get()?.let {
                        val nodes = it.findByText(text, filterViewId = filterViewId, filterDes = filterDes, filterClass = filterClass).toNodes()
                        result = GsonUtils.toJson(CallResponse<List<Node>>(code = 0, data = nodes))
                    } ?: let {
                        val nodes =
                            AssistsCore.findByText(text, filterViewId = filterViewId, filterDes = filterDes, filterClass = filterClass).toNodes()
                        result = GsonUtils.toJson(CallResponse<List<Node>>(code = 0, data = nodes))
                    }
                }

                CallMethod.findByTags -> {
                    val className = request.arguments?.get("className")?.asString ?: ""
                    val text = request.arguments?.get("filterText")?.asString ?: ""
                    val viewId = request.arguments?.get("filterViewId")?.asString ?: ""
                    val des = request.arguments?.get("filterDes")?.asString ?: ""
                    request.node?.get()?.let {
                        val nodes = it.findByTags(className = className, viewId = viewId, des = des, text = text).toNodes()
                        result = GsonUtils.toJson(CallResponse<List<Node>>(code = 0, data = nodes))
                    } ?: let {
                        val nodes = AssistsCore.findByTags(className = className, text = text, viewId = viewId, des = des).toNodes()
                        result = GsonUtils.toJson(CallResponse<List<Node>>(code = 0, data = nodes))
                    }
                }

                CallMethod.selectionText -> {
                    val selectionStart = request.arguments?.get("selectionStart")?.asInt ?: 0
                    val selectionEnd = request.arguments?.get("selectionEnd")?.asInt ?: 0
                    val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.selectionText(selectionStart, selectionEnd) == true
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = isSuccess))
                }

                CallMethod.scrollForward -> {
                    val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.scrollForward() == true
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = isSuccess))
                }

                CallMethod.scrollBackward -> {
                    val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.scrollBackward() == true
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = isSuccess))
                }

                CallMethod.findByTextAllMatch -> {
                    val nodes = AssistsCore.findByTextAllMatch(request.arguments?.get("text")?.asString ?: "").toNodes()
                    result = GsonUtils.toJson(CallResponse<List<Node>>(code = 0, data = nodes))
                }

                CallMethod.containsText -> {
                    val isSuccess =
                        NodeCacheManager.get(request.node?.nodeId ?: "")?.containsText(request.arguments?.get("text")?.asString ?: "") == true
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = isSuccess))
                }

                CallMethod.findFirstParentByTags -> {
                    val node =
                        NodeCacheManager.get(request.node?.nodeId ?: "")?.findFirstParentByTags(request.arguments?.get("className")?.asString ?: "")
                            ?.toNode()
                    result = GsonUtils.toJson(CallResponse<Node?>(code = 0, data = node))
                }

                CallMethod.getNodes -> {
                    val nodes =
                        NodeCacheManager.get(request.node?.nodeId ?: "")?.getNodes()
                            ?.toNodes()
                    result = GsonUtils.toJson(CallResponse<List<Node>>(code = 0, data = nodes))
                }

                CallMethod.findFirstParentClickable -> {
                    val node =
                        NodeCacheManager.get(request.node?.nodeId ?: "")?.findFirstParentClickable()
                            ?.toNode()
                    result = GsonUtils.toJson(CallResponse<Node?>(code = 0, data = node))
                }


                CallMethod.setNodeText -> {
                    val isSuccess =
                        NodeCacheManager.get(request.node?.nodeId ?: "")?.setNodeText(request.arguments?.get("text")?.asString ?: "") == true
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = isSuccess))
                }

                CallMethod.click -> {
                    val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.click() == true
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = isSuccess))
                }

                CallMethod.longClick -> {
                    val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.longClick() == true
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = isSuccess))
                }

                CallMethod.paste -> {
                    val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.paste(request.arguments?.get("text")?.asString ?: "") == true
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = isSuccess))
                }

                CallMethod.focus -> {
                    val isSuccess = NodeCacheManager.get(request.node?.nodeId ?: "")?.focus() == true
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = isSuccess))
                }

                //其他方法
                CallMethod.launchApp -> {
                    val packageName = request.arguments?.get("packageName")?.asString ?: ""
                    CoroutineWrapper.launch { AssistsCore.launchApp(packageName) }
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = true))
                }

                CallMethod.getPackageName -> {
                    val packageName = AssistsCore.getPackageName()
                    result = GsonUtils.toJson(CallResponse<String>(code = 0, data = packageName))
                }

                CallMethod.overlayToast -> {
                    val text = request.arguments?.get("text")?.asString ?: ""
                    val delay = request.arguments?.get("delay")?.asLong ?: 2000L
                    text.overlayToast(delay)
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = true))
                }

                CallMethod.back -> {
                    val resultBack = AssistsCore.back()
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = resultBack))
                }

                CallMethod.home -> {
                    val resultBack = AssistsCore.home()
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = resultBack))
                }

                CallMethod.notifications -> {
                    val resultBack = AssistsCore.notifications()
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = resultBack))
                }

                CallMethod.recentApps -> {
                    val resultBack = AssistsCore.recentApps()
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, data = resultBack))
                }

                CallMethod.httpRequest -> {
                    CoroutineWrapper.launch {
                        runCatching {
                            val url = request.arguments?.get("url")?.asString ?: ""
                            val method = request.arguments?.get("method")?.asString?.uppercase() ?: "GET"
                            val headers = request.arguments?.get("headers")?.asJsonObject
                            val body = request.arguments?.get("body")?.asString ?: ""
                            val timeoutSeconds = request.arguments?.get("timeout")?.asLong ?: 30L

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

                                else -> {
                                    callback(
                                        CallResponse<JsonObject>(
                                            code = -1,
                                            message = "不支持的请求方法: $method",
                                            callbackId = request.callbackId
                                        )
                                    )
                                    return@runCatching
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

                            callback(CallResponse(code = 0, data = responseData, callbackId = request.callbackId))
                        }.onFailure {
                            LogUtils.e(it)
                            callback(
                                CallResponse<JsonObject>(
                                    code = -1,
                                    message = "请求失败: ${it.message}",
                                    data = JsonObject(),
                                    callbackId = request.callbackId
                                )
                            )
                        }
                    }
                    result = GsonUtils.toJson(CallResponse<JsonObject>(code = 0, data = JsonObject().apply {
                        addProperty("resultType", "callback")
                    }))
                }

                CallMethod.openUrlInBrowser -> {
                    val url = request.arguments?.get("url")?.asString ?: ""
                    CoroutineWrapper.launch(isMain = true) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            if (AppUtils.isAppForeground()) {
                                ActivityUtils.getTopActivity()?.startActivity(intent)
                            } else {
                                AssistsService.instance?.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            LogUtils.e(e)
                            "打开外部浏览器失败：${e.message}".overlayToast()
                        }
                    }
                    result = GsonUtils.toJson(CallResponse<Boolean>(code = 0, message = "", data = false))
                }


                else -> {

                }
            }
        }.onFailure {
            LogUtils.e(it)
        }
        return result
    }


    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)  // 可选 JPEG/PNG
        val byteArray = outputStream.toByteArray()
        return "data:image/png;base64,${Base64.encodeToString(byteArray, Base64.NO_WRAP)}"
    }
} 