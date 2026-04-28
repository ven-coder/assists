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
        initNotificationChannel()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        onStartCommand?.invoke(this, intent, flags, startId)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun initNotificationChannel() {
        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
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
        ServiceCompat.startForeground(
            this,
            1,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
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
