package com.ven.assists.simple

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.PermissionUtils
import com.blankj.utilcode.util.PermissionUtils.SimpleCallback
import com.blankj.utilcode.util.TimeUtils
import com.lxj.xpopup.XPopup
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.logNode
import com.ven.assists.log.AssistsLog
import com.ven.assists.log.AssistsLogDiagnostics
import com.ven.assists.log.log
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.simple.databinding.ActivityMainBinding
import com.ven.assists.simple.overlays.OverlayAdvanced
import com.ven.assists.simple.overlays.OverlayBasic
import com.ven.assists.simple.overlays.OverlayLog
import com.ven.assists.simple.overlays.OverlayPro
import com.ven.assists.simple.overlays.OverlayStatusCard
import com.ven.assists.simple.overlays.OverlayWeb
import com.ven.assists.utils.ContactsUtil
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.window.AssistsWindowManager.overlayToast
import kotlinx.coroutines.delay


class MainActivity : AppCompatActivity(), AssistsServiceListener {
    private var isActivityResumed = false
    val viewBind: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater).apply {
            btnEnable.setOnClickListener {
                AssistsCore.openAccessibilitySetting()
                startActivity(Intent(this@MainActivity, SettingGuideActivity::class.java))
            }
            btnBasic.setOnClickListener {
                OverlayBasic.onClose = {
                    OverlayBasic.hide()
                }
                if (OverlayBasic.showed) {
                    OverlayBasic.hide()
                } else {
                    OverlayBasic.show()
                }
            }
            btnPro.setOnClickListener {
                OverlayPro.onClose = {
                    OverlayPro.hide()
                }
                if (OverlayPro.showed) {
                    OverlayPro.hide()
                } else {
                    OverlayPro.show()
                }
            }
            btnAdvanced.setOnClickListener {
                OverlayAdvanced.onClose = {
                    OverlayAdvanced.hide()
                }
                if (OverlayAdvanced.showed) {
                    OverlayAdvanced.hide()
                } else {
                    OverlayAdvanced.show()
                }
            }
            btnWeb.setOnClickListener {
                OverlayWeb.onClose = {
                    OverlayWeb.hide()
                }
                if (OverlayWeb.showed) {
                    OverlayWeb.hide()
                } else {

                    OverlayWeb.show()
                }
            }
            btnLog.setOnClickListener {
                OverlayLog.onClose = {
                    OverlayLog.hide()
                }
                if (OverlayLog.showed) {
                    OverlayLog.hide()
                } else {
                    OverlayLog.show(clearLog = false, mainPageLogViewer = true)
                }
            }
            btnTest.isVisible = AppUtils.isAppDebug()
            btnTest.setOnClickListener {
                OverlayStatusCard.onClose = {
                    OverlayStatusCard.hide()
                }
                if (OverlayStatusCard.showed) {
                    OverlayStatusCard.hide()
                } else {

                    OverlayStatusCard.show("")
                }

            }
            btnMedia.setOnClickListener {
                // 按系统版本申请对应的媒体读取权限，授权后遍历相册输出真实路径
                val permissions = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )

                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )

                    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                PermissionUtils.permission(*permissions)
                    .callback(object : SimpleCallback {
                        override fun onGranted() {
                            "开始读取相册媒体".overlayToast()
                            MediaStoreDemo.readAllMedia(this@MainActivity)
                        }

                        override fun onDenied() {
                            "未授予媒体读取权限，无法读取相册".overlayToast()
                        }
                    }).request()
            }
            btnMediaSortTest.setOnClickListener {
                MediaStoreDemo.topFifthMedia(this@MainActivity).let(::handleTopResult)
            }
            btnMediaCopyTop.setOnClickListener {
                // 复制置顶：先提示处理中，全流程在后台协程执行，完成后提示结果
                "正在复制置顶...".overlayToast()
                CoroutineWrapper.launch {
                    val result = MediaStoreDemo.copyTopFifthMedia(this@MainActivity)
                    runOnUiThread { handleCopyTopResult(result) }
                }
            }
        }
    }

    /** 处理置顶操作结果（含 Android 10+ 其他应用媒体的授权流程） */
    private fun handleTopResult(result: MediaStoreDemo.TopResult) {
        when (result) {
            is MediaStoreDemo.TopResult.Success -> {
                "已置顶: ${result.name}".overlayToast()
                // 置顶后重新输出验证顺序
                MediaStoreDemo.readAllMedia(this)
            }

            is MediaStoreDemo.TopResult.NeedGrant -> {
                // 弹系统授权框，用户确认后重试
                startIntentSenderForResult(
                    result.grantIntentSender,
                    REQUEST_MEDIA_WRITE_GRANT,
                    null, 0, 0, 0
                )
            }

            is MediaStoreDemo.TopResult.TooFew -> {
                "相册混排数量不足 5 条（当前 ${result.size} 条）".overlayToast()
            }

            is MediaStoreDemo.TopResult.Error -> {
                "修改失败: ${result.reason}".overlayToast()
            }
        }
    }

    /** 处理复制置顶结果（含读取源媒体的授权流程） */
    private fun handleCopyTopResult(result: MediaStoreDemo.TopResult) {
        when (result) {
            is MediaStoreDemo.TopResult.Success -> {
                "已复制置顶: ${result.name}".overlayToast()
                // 输出新的混排顺序验证新条目排最前
                MediaStoreDemo.readAllMedia(this)
            }

            is MediaStoreDemo.TopResult.NeedGrant -> {
                // 读取源媒体需要授权，弹系统框，确认后重试复制
                startIntentSenderForResult(
                    result.grantIntentSender,
                    REQUEST_MEDIA_COPY_GRANT,
                    null, 0, 0, 0
                )
            }

            is MediaStoreDemo.TopResult.TooFew -> {
                "相册混排数量不足 5 条（当前 ${result.size} 条）".overlayToast()
            }

            is MediaStoreDemo.TopResult.Error -> {
                "复制失败: ${result.reason}".overlayToast()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_MEDIA_WRITE_GRANT -> {
                if (resultCode == RESULT_OK) {
                    // 授权成功，重试置顶
                    handleTopResult(MediaStoreDemo.retryTopAfterGrant(this))
                } else {
                    MediaStoreDemo.clearPending()
                    "用户拒绝授权，无法修改".overlayToast()
                }
            }

            REQUEST_MEDIA_COPY_GRANT -> {
                if (resultCode == RESULT_OK) {
                    CoroutineWrapper.launch {
                        val result = MediaStoreDemo.retryCopyAfterGrant(this@MainActivity)
                        runOnUiThread { handleCopyTopResult(result) }
                    }
                } else {
                    MediaStoreDemo.clearPending()
                    "用户拒绝授权，无法复制".overlayToast()
                }
            }
        }
    }

    companion object {
        private const val REQUEST_MEDIA_WRITE_GRANT = 10001
        private const val REQUEST_MEDIA_COPY_GRANT = 10002
    }
    private val foregroundServiceIntent: Intent by lazy {
        Intent(this, ForegroundService::class.java)
    }
    private var disableNotificationView: View? = null


    private lateinit var drawingView: MultiTouchDrawingView

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        checkServiceEnable()
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
    }

    private fun checkServiceEnable() {
        if (!isActivityResumed) return
        if (AssistsCore.isA11yEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(foregroundServiceIntent)
            }
            viewBind.btnEnable.isVisible = false
            viewBind.llOption.isVisible = true
        } else {
            stopService(foregroundServiceIntent)
            viewBind.btnEnable.isVisible = true
            viewBind.llOption.isVisible = false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        super.onAccessibilityEvent(event)
//        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
//            LogUtils.d(event.text)
//        }
    }

    override fun onServiceConnected(service: AssistsService) {
        checkServiceEnable()
        if (AssistsCore.getPackageName() != AppUtils.getAppPackageName()) {
            CoroutineWrapper.launch {
                runCatching {
                    val launchApp = AssistsCore .launchApp(AppUtils.getAppPackageName())
                    LogUtils.d(launchApp)
                }.onFailure {
                    LogUtils.e(it)
                }
            }
        }
    }

    private fun onBackApp() {
        CoroutineWrapper.launch {
            while (AssistsCore.getPackageName() != packageName) {
                AssistsCore.back()
                delay(500)
            }
        }
    }

    override fun onUnbind() {
        checkServiceEnable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BarUtils.setStatusBarLightMode(this, true)
        setContentView(viewBind.root)
        AssistsService.listeners.add(this)
        checkPermission()
    }

    private fun checkPermission() {
        val areNotificationsEnabled =
            NotificationManagerCompat.from(this).areNotificationsEnabled();
        if (!areNotificationsEnabled) {
            // 通知权限未开启，提示用户去设置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionUtils.permission(Manifest.permission.POST_NOTIFICATIONS)
                    .callback(object : SimpleCallback {
                        override fun onGranted() {

                        }

                        override fun onDenied() {
                            showNotificationPermissionOpenDialog()
                        }
                    }).request()
            } else {
                showNotificationPermissionOpenDialog()
            }
        }
    }

    private fun showNotificationPermissionOpenDialog() {
        XPopup.Builder(this)
            .asConfirm("提示", "未开启通知权限，开启通知权限以获得完整测试相关通知提示") {
                val intent = Intent()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Android 8.0及以上版本，跳转到应用的通知设置页面
                    intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                } else {
                    // Android 8.0以下版本，跳转到应用详情页面
                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.setData(Uri.parse("package:" + getPackageName()))
                }
                startActivity(intent)
            }.show()

    }

    override fun onDestroy() {
        super.onDestroy()
        AssistsService.listeners.remove(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        moveTaskToBack(true)
    }
}