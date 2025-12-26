package com.ven.assists.web.utils

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.provider.Settings
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.LogUtils
import kotlin.let

/**
 * 音频播放管理工具类
 * 负责管理音频播放、音量控制和恢复
 */
object AudioPlayManager {
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var originalVolume: Int = -1 // 保存原始音量

    /**
     * 执行音频播放（循环播放电话铃声，音量调至80%）
     * @param context Context上下文，如果为null则使用ActivityUtils.getTopActivity()
     */
    fun startAudioPlay(context: Context? = null) {
        try {
            val activity = context ?: ActivityUtils.getTopActivity()
            if (activity == null) {
                LogUtils.w("AudioPlayManager: 无法获取当前 Activity，跳过音频播放")
                return
            }

            // 获取 AudioManager
            audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // 保存当前音量
            originalVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
            LogUtils.d("AudioPlayManager: 保存原始音量 -> $originalVolume")

            // 获取最大音量
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

            // 计算80%的音量（调试模式下使用10%）
            val targetVolume = if (AppUtils.isAppDebug()) {
                (maxVolume * 0.8).toInt()
            } else {
                (maxVolume * 0.8).toInt()
            }

            // 设置音量为80%
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
            LogUtils.i("AudioPlayManager: 音量已设置为80% -> $targetVolume/$maxVolume")

            // 释放之前的 MediaPlayer
            mediaPlayer?.release()

            // 使用系统电话铃声
            mediaPlayer = MediaPlayer.create(activity, Settings.System.DEFAULT_RINGTONE_URI)

            // 设置循环播放
            mediaPlayer?.isLooping = true

            // 设置音频流类型
            @Suppress("DEPRECATION")
            mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)

            mediaPlayer?.setOnErrorListener { mp, what, extra ->
                LogUtils.e("AudioPlayManager: 音频播放错误 -> what: $what, extra: $extra")
                restoreVolume() // 发生错误时也恢复音量
                mp.release()
                mediaPlayer = null
                true
            }

            mediaPlayer?.start()
            LogUtils.i("AudioPlayManager: 电话铃声循环播放已开始")
        } catch (e: Exception) {
            LogUtils.e("AudioPlayManager: 音频播放失败", e)
            restoreVolume() // 发生异常时恢复音量
        }
    }

    /**
     * 停止音频播放并恢复音量
     */
    fun stopAudioPlay() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
                mediaPlayer = null
                LogUtils.i("AudioPlayManager: 音频播放已停止")
            }

            // 恢复原始音量
            restoreVolume()
        } catch (e: Exception) {
            LogUtils.e("AudioPlayManager: 停止音频播放失败", e)
            // 即使发生异常也尝试恢复音量
            restoreVolume()
        }
    }

    /**
     * 恢复原始音量
     */
    private fun restoreVolume() {
        try {
            if (originalVolume >= 0 && audioManager != null) {
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                LogUtils.i("AudioPlayManager: 音量已恢复 -> $originalVolume")
                originalVolume = -1
                audioManager = null
            }
        } catch (e: Exception) {
            LogUtils.e("AudioPlayManager: 恢复音量失败", e)
        }
    }

    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
}

