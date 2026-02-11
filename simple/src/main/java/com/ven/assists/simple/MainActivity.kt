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
import com.lxj.xpopup.XPopup
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.logNode
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.simple.databinding.ActivityMainBinding
import com.ven.assists.simple.overlays.OverlayAdvanced
import com.ven.assists.simple.overlays.OverlayBasic
import com.ven.assists.simple.overlays.OverlayPro
import com.ven.assists.simple.overlays.OverlayStatusCard
import com.ven.assists.simple.overlays.OverlayWeb
import com.ven.assists.utils.ContactsUtil
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.utils.NodeClassValue
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
        }
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
        if (AssistsCore.isAccessibilityServiceEnabled()) {
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
            CoroutineWrapper.launch { AssistsCore.launchApp(AppUtils.getAppPackageName()) }
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
        val areNotificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        if (!areNotificationsEnabled) {
            // 通知权限未开启，提示用户去设置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionUtils.permission(Manifest.permission.POST_NOTIFICATIONS).callback(object : SimpleCallback {
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
        XPopup.Builder(this).asConfirm("提示", "未开启通知权限，开启通知权限以获得完整测试相关通知提示") {
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