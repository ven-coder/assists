package com.ven.assists.mp

import android.app.Activity
import android.app.Application
import android.app.Service.MEDIA_PROJECTION_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.ImageUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.PathUtils
import com.blankj.utilcode.util.ScreenUtils
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.getBoundsInScreen
import com.ven.assists.AssistsCore.nodeGestureClick
import com.ven.assists.service.AssistsService
import com.ven.assists.utils.CoroutineWrapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.File

/**
 * 屏幕录制管理器
 * 负责处理屏幕录制相关的功能，包括权限请求、截图和图像处理
 */
object MPManager {
    /** 媒体投影请求码 */
    const val REQUEST_CODE = "request_code_media_projection"
    /** 媒体投影请求数据 */
    const val REQUEST_DATA = "request_data_media_projection"

    /** 存储Activity和其对应的结果启动器映射 */
    private var requestLaunchers = hashMapOf<Activity, ActivityResultLauncher<Intent>>()

    /** 服务启用回调 */
    var onEnable: ((service: MPService, intent: Intent, flags: Int, startId: Int) -> Unit)? = null
    /** 异步等待服务启用的Deferred对象 */
    private var completableDeferredEnable: CompletableDeferred<Boolean>? = null
    /** 自动允许权限的协程Job */
    private var autoAllowJob: Job? = null

    /** 图像读取器实例 */
    private var imageReader: ImageReader? = null

    var mediaProjectionCallback:MediaProjection.Callback?=null

    /** 屏幕录制是否已启用 */
    var isEnable = false
        private set
        get() {
            field = imageReader?.let { return@let true } ?: false
            return field
        }

    /** Activity生命周期回调，用于管理结果启动器 */
    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is ComponentActivity && requestLaunchers[activity] == null) {
                requestLaunchers[activity] = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    onResult(result)
                }
            }
        }

        override fun onActivityStarted(activity: Activity) {
        }

        override fun onActivityResumed(activity: Activity) {
        }

        override fun onActivityPaused(activity: Activity) {
        }

        override fun onActivityStopped(activity: Activity) {
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        }

        override fun onActivityDestroyed(activity: Activity) {
            requestLaunchers.remove(activity)
        }
    }

    /**
     * 处理服务启动命令
     * @param service MPService实例
     * @param intent 启动意图
     * @param flags 启动标志
     * @param startId 启动ID
     */
    private fun onStartCommand(service: MPService, intent: Intent, flags: Int, startId: Int) {
        onCreateImageReader(service, intent, flags, startId)
        onEnable?.invoke(service, intent, flags, startId)
        completableDeferredEnable?.complete(true)
        completableDeferredEnable = null
    }

    /**
     * 处理权限请求结果
     * @param result 活动结果
     */
    private fun onResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            MPService.onStartCommand = ::onStartCommand
            val service = Intent(ActivityUtils.getTopActivity(), MPService::class.java)
            service.putExtra(REQUEST_CODE, result.resultCode)
            service.putExtra(REQUEST_DATA, result.data)
            ActivityUtils.getTopActivity().startService(service)
            LogUtils.d("enable screen capture")
        }
    }

    /**
     * 创建并初始化图像读取器
     * @param service MPService实例
     * @param intent 启动意图
     * @param flags 启动标志
     * @param startId 启动ID
     */
    private fun onCreateImageReader(service: MPService, intent: Intent, flags: Int, startId: Int) {
        imageReader?.close()
        val mediaProjectionManager = service.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val screenWidth = ScreenUtils.getScreenWidth()
        val screenHeight = ScreenUtils.getScreenHeight()
        val screenDensityDpi = ScreenUtils.getScreenDensityDpi()
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2).apply {
            val requestCode = intent.getIntExtra(REQUEST_CODE, -1)
            val requestData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(REQUEST_DATA, Intent::class.java)
            } else {
                intent.getParcelableExtra<Intent>(REQUEST_DATA)
            }
            val mediaProjection = mediaProjectionManager.getMediaProjection(requestCode, requestData!!)
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    mediaProjectionCallback?.onStop()
                }

                @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                override fun onCapturedContentResize(width: Int, height: Int) {
                    mediaProjectionCallback?.onCapturedContentResize(width, height)
                }

                @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                    mediaProjectionCallback?.onCapturedContentVisibilityChanged(isVisible)
                }
            }, Handler(Looper.getMainLooper()))
            mediaProjection.createVirtualDisplay(
                "assists_mp",
                screenWidth,
                screenHeight,
                screenDensityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )
        }
    }

    /**
     * 初始化管理器
     * @param application Application实例
     */
    fun init(application: Application) {
        requestLaunchers.clear()
        application.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
        application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }

    /**
     * 请求屏幕录制权限
     * @param autoAllow 是否自动允许权限弹窗，默认为true
     * @param timeOut 超时时间，默认5000毫秒
     * @return 权限是否获取成功
     */
    suspend fun request(autoAllow: Boolean = true, timeOut: Long = 5000): Boolean {
        var projectionManager: MediaProjectionManager? = null
        AssistsService.instance?.let {
            projectionManager = it.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        } ?: let {
            projectionManager = ActivityUtils.getTopActivity()?.getSystemService(MEDIA_PROJECTION_SERVICE)?.let {
                it as MediaProjectionManager
            }
        }
        projectionManager ?: return false
        val intent = projectionManager!!.createScreenCaptureIntent()
        requestLaunchers[ActivityUtils.getTopActivity()]?.launch(intent)
        completableDeferredEnable = CompletableDeferred()
        if (autoAllow) {
            CoroutineWrapper.launch {
                var time = 0L
                while (time <= timeOut) {
                    AssistsCore.findByTags("android.widget.Button", "android:id/button1", "立即开始").firstOrNull()?.click()
                    AssistsCore.findByTags("android.widget.Spinner", "com.android.systemui:id/screen_share_mode_spinner").firstOrNull()?.let {
                        if (it.click()) {
                            delay(250)
                            AssistsCore.findByTags("android.widget.TextView", "android:id/text1", "整个屏幕").firstOrNull()?.nodeGestureClick()
                        }
                    }
                    AssistsCore.findByTags("android.widget.Button", "android:id/button1", "开始").firstOrNull()?.click()
                    val delayMillis = 250L
                    time += delayMillis
                    delay(delayMillis)
                }
                completableDeferredEnable?.complete(false)
            }
        } else {
            autoAllowJob?.cancel()
            autoAllowJob = CoroutineWrapper.launch {
                var time = 0L
                while (time <= timeOut) {
                    val delayMillis = 250L
                    time += delayMillis
                    delay(delayMillis)
                }
                completableDeferredEnable?.complete(false)
            }
        }
        return completableDeferredEnable?.await() ?: false
    }

    /**
     * 获取当前屏幕截图
     * @return 屏幕截图位图，如果失败返回null
     */
    fun takeScreenshot2Bitmap(): Bitmap? {
        imageReader?.let {
            val image: Image? = it.acquireLatestImage()
            try {
                val bitmap = image?.let { i -> imageToBitmap(i) }
                return bitmap
            } catch (e: Throwable) {
                return null
            } finally {
                image?.close()
            }
        } ?: let { throw RuntimeException("Please request permission for screen recording first") }
    }


    /**
     * 获取指定元素的截图
     * @param screenshot 完整的屏幕截图, 默认为null（默认为null将自动截取当前屏幕作为完整截图）
     * @return 元素区域的位图，如果失败返回null
     */
    fun AccessibilityNodeInfo.getBitmap(screenshot: Bitmap? = null): Bitmap? {
        runCatching {
            getBoundsInScreen().let { nodeBounds ->
                screenshot?.let {
                    val bitmap = Bitmap.createBitmap(it, nodeBounds.left, nodeBounds.top, nodeBounds.width(), nodeBounds.height())
                    return@runCatching bitmap
                } ?: let {
                    takeScreenshot2Bitmap()?.let {
                        val bitmap = Bitmap.createBitmap(it, nodeBounds.left, nodeBounds.top, nodeBounds.width(), nodeBounds.height())
                        return@runCatching bitmap
                    }
                }

            }
            return@runCatching null
        }.onSuccess {
            return it
        }
        return null
    }

    /**
     * 将指定元素的截图保存到文件
     * @param screenshot 完整的屏幕截图，默认为null（默认为null将自动截取当前屏幕作为完整截图）
     * @param file 保存的目标文件，默认保存到应用内部文件目录
     * @return 保存成功返回文件对象，失败返回null
     */
    fun AccessibilityNodeInfo.takeScreenshot2File(
        screenshot: Bitmap? = null,
        file: File = File(
            PathUtils.getInternalAppFilesPath(),
            "screenshot_${System.currentTimeMillis()}.png"
        )
    ): File? {

        screenshot?.let {
            val bitmap = getBitmap(it) ?: return null
            val result = ImageUtils.save(bitmap, file, Bitmap.CompressFormat.PNG)
            if (result) {
                return file
            }
        } ?: let {
            takeScreenshot2Bitmap()?.let {
                val bitmap = getBitmap(it) ?: return null
                val result = ImageUtils.save(bitmap, file, Bitmap.CompressFormat.PNG)
                if (result) {
                    return file
                }
            }
        }


        return null
    }

    /**
     * 将当前屏幕截图保存到文件
     * @param file 保存的目标文件，默认保存到应用内部文件目录
     * @return 保存成功返回文件对象，失败返回null
     */
    fun takeScreenshot2File(
        file: File = File(
            PathUtils.getInternalAppFilesPath(),
            "screenshot_${System.currentTimeMillis()}.png"
        )
    ): File? {
        val bitmap = takeScreenshot2Bitmap()
        val result = ImageUtils.save(bitmap, file, Bitmap.CompressFormat.PNG)
        if (result) {
            return file
        }
        return null
    }

    /**
     * 将Image对象转换为Bitmap
     * @param image 要转换的Image对象
     * @return 转换后的Bitmap对象
     */
    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }
}