package com.ven.assists.mp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ven.assists.base.R

class MPService : Service() {

    companion object {
        var onStartCommand:
                ((service: MPService, intent: Intent, flags: Int, startId: Int) -> Unit)? =
            null
    }

    override fun onCreate() {
        super.onCreate()
        // 先建立通知通道；startForeground 延迟到 onStartCommand（此时才能拿到授权数据判断）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // targetSdk 36（Android 15/16）下启动 mediaProjection 类型前台服务，
        // 系统除要求静态权限 FOREGROUND_SERVICE_MEDIA_PROJECTION 外，还要求已获得
        // android:project_media 动态权限——该权限仅在用户完成 MediaProjection 授权
        // （createScreenCaptureIntent 弹窗）后授予。若未授权时强行 startForeground 会抛
        // SecurityException 导致进程崩溃，且 START_STICKY 会让系统反复重启该服务形成崩溃循环。
        //
        // 因此：仅当本次启动携带有效授权数据（授权结果 code/data，或 MPManager 中已有投影实例）
        // 时才启动 mediaProjection 前台服务；否则直接停止自身，绝不崩溃。
        // MPManager.onResult 在授权成功后启动本服务，携带 REQUEST_CODE=RESULT_OK(-1) 与授权 data；
        // 判断「已授权」用 hasExtra（REQUEST_CODE 的值为 -1，不能按值判）。
        val authorized =
            intent?.let { it.hasExtra(MPManager.REQUEST_CODE) && it.hasExtra(MPManager.REQUEST_DATA) } == true
                || MPManager.getMediaProjection() != null
        if (!authorized) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        initNotificationChannel()
        onStartCommand?.invoke(this, intent!!, flags, startId)
        return START_NOT_STICKY
    }

    private fun initNotificationChannel() {
        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                "mirror.hsl"
            } else {
                ""
            }
        val builder = NotificationCompat.Builder(this, channelId)
        val notification =
            builder.setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        // 与 manifest 中 foregroundServiceType=mediaProjection 一致，且需 FOREGROUND_SERVICE_MEDIA_PROJECTION 权限
        // 已通过 authorized 校验，这里再兜底 try-catch，防御个别 ROM 校验差异
        try {
            ServiceCompat.startForeground(
                this,
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } catch (e: SecurityException) {
            // 仍被拒（如授权 token 已失效）则停止自身，避免进程崩溃
            stopSelf()
        }
    }

    /** 创建通知通道 */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "mirror.hsl"
        val chan =
            NotificationChannel(
                channelId,
                "ForegroundService",
                NotificationManager.IMPORTANCE_NONE
            )
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
