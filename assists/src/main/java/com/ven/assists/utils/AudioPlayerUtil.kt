package com.ven.assists.utils

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import java.io.File
import java.lang.ref.WeakReference

/**
 * 音频播放工具类
 * 支持 Assets 和本地音频文件的播放
 * 
 * 【重要】音量说明：
 * - MediaPlayer 音量是相对于系统音量的比例
 * - 最终播放音量 = 系统音量 × MediaPlayer音量
 * - 例如：系统音量0.1，设置音量1.0，最终音量 = 0.1 × 1.0 = 0.1
 * - 如果需要绝对音量控制，请使用 setAbsoluteVolume() 方法（会临时修改系统音量）
 */
object AudioPlayerUtil {

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var currentSource: String? = null
    private var currentVolume: Float? = null  // 当前音量设置，null 表示使用系统音量
    private var originalSystemVolume: Int = -1  // 保存原始系统音量
    private var useAbsoluteVolume: Boolean = false  // 是否使用绝对音量
    private var restoreVolumeOnComplete: Boolean = false  // 播放完成后是否恢复系统音量
    private var contextRef: WeakReference<Context>? = null  // 使用弱引用保存上下文，避免内存泄露

    /**
     * 播放状态
     */
    enum class PlayState {
        IDLE,       // 空闲
        PREPARING,  // 准备中
        PLAYING,    // 播放中
        PAUSED,     // 暂停
        STOPPED,    // 停止
        ERROR       // 错误
    }

    /**
     * 播放监听器
     */
    interface PlayListener {
        /** 准备完成 */
        fun onPrepared() {}
        
        /** 播放开始 */
        fun onStart() {}
        
        /** 播放暂停 */
        fun onPause() {}
        
        /** 播放停止 */
        fun onStop() {}
        
        /** 播放完成 */
        fun onCompletion() {}
        
        /** 播放错误 */
        fun onError(error: String) {}
        
        /** 播放进度更新 */
        fun onProgressUpdate(current: Int, duration: Int) {}
    }

    private var playListener: PlayListener? = null
    private var currentState: PlayState = PlayState.IDLE

    /**
     * 播放 Assets 目录下的音频文件
     *
     * @param context 上下文
     * @param assetPath assets 目录下的文件路径，例如 "sounds/music.mp3"
     * @param isLooping 是否循环播放
     * @param volume 音量 (0.0 - 1.0)，null 表示使用系统音量
     * @param useAbsoluteVolume 是否使用绝对音量（会临时修改系统音量）
     * @param restoreVolumeOnComplete 播放完成后是否恢复系统音量（仅在 useAbsoluteVolume=true 时有效）
     * @param listener 播放监听器
     */
    fun playFromAssets(
        context: Context,
        assetPath: String,
        isLooping: Boolean = false,
        volume: Float? = null,
        useAbsoluteVolume: Boolean = false,
        restoreVolumeOnComplete: Boolean = true,
        listener: PlayListener? = null
    ) {
        try {
            // 停止当前播放并重置
            stopAndReset()

            playListener = listener
            currentSource = assetPath
            currentState = PlayState.PREPARING
            currentVolume = volume
            this.useAbsoluteVolume = useAbsoluteVolume
            this.restoreVolumeOnComplete = restoreVolumeOnComplete
            // 使用 ApplicationContext 避免内存泄露
            this.contextRef = WeakReference(context.applicationContext)

            // 创建 MediaPlayer
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
            }
            
            setupMediaPlayer(context, isLooping)

            // 从 Assets 加载音频
            val afd: AssetFileDescriptor = context.assets.openFd(assetPath)
            mediaPlayer?.apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepareAsync()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            currentState = PlayState.ERROR
            playListener?.onError("Failed to play from assets: ${e.message}")
        }
    }

    /**
     * 播放本地音频文件
     *
     * @param context 上下文
     * @param filePath 本地文件路径
     * @param isLooping 是否循环播放
     * @param volume 音量 (0.0 - 1.0)，null 表示使用系统音量
     * @param useAbsoluteVolume 是否使用绝对音量（会临时修改系统音量）
     * @param restoreVolumeOnComplete 播放完成后是否恢复系统音量（仅在 useAbsoluteVolume=true 时有效）
     * @param listener 播放监听器
     */
    fun playFromFile(
        context: Context,
        filePath: String,
        isLooping: Boolean = false,
        volume: Float? = null,
        useAbsoluteVolume: Boolean = false,
        restoreVolumeOnComplete: Boolean = true,
        listener: PlayListener? = null
    ) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                listener?.onError("File not exists: $filePath")
                return
            }

            // 停止当前播放并重置
            stopAndReset()

            playListener = listener
            currentSource = filePath
            currentState = PlayState.PREPARING
            currentVolume = volume
            this.useAbsoluteVolume = useAbsoluteVolume
            this.restoreVolumeOnComplete = restoreVolumeOnComplete
            // 使用 ApplicationContext 避免内存泄露
            this.contextRef = WeakReference(context.applicationContext)

            // 创建 MediaPlayer
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
            }
            
            setupMediaPlayer(context, isLooping)

            // 从文件加载音频
            mediaPlayer?.apply {
                setDataSource(context, Uri.fromFile(file))
                prepareAsync()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            currentState = PlayState.ERROR
            playListener?.onError("Failed to play from file: ${e.message}")
        }
    }

    /**
     * 播放网络音频
     *
     * @param context 上下文
     * @param url 网络音频 URL
     * @param isLooping 是否循环播放
     * @param volume 音量 (0.0 - 1.0)，null 表示使用系统音量
     * @param useAbsoluteVolume 是否使用绝对音量（会临时修改系统音量）
     * @param restoreVolumeOnComplete 播放完成后是否恢复系统音量（仅在 useAbsoluteVolume=true 时有效）
     * @param listener 播放监听器
     */
    fun playFromUrl(
        context: Context,
        url: String,
        isLooping: Boolean = false,
        volume: Float? = null,
        useAbsoluteVolume: Boolean = false,
        restoreVolumeOnComplete: Boolean = true,
        listener: PlayListener? = null
    ) {
        try {
            // 停止当前播放并重置
            stopAndReset()

            playListener = listener
            currentSource = url
            currentState = PlayState.PREPARING
            currentVolume = volume
            this.useAbsoluteVolume = useAbsoluteVolume
            this.restoreVolumeOnComplete = restoreVolumeOnComplete
            // 使用 ApplicationContext 避免内存泄露
            this.contextRef = WeakReference(context.applicationContext)

            // 创建 MediaPlayer
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
            }
            
            setupMediaPlayer(context, isLooping)

            // 从 URL 加载音频
            mediaPlayer?.apply {
                setDataSource(url)
                prepareAsync()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            currentState = PlayState.ERROR
            playListener?.onError("Failed to play from url: ${e.message}")
        }
    }

    /**
     * 配置 MediaPlayer
     */
    private fun setupMediaPlayer(context: Context, isLooping: Boolean) {
        mediaPlayer?.apply {
            // 设置音频属性
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )

            // 设置循环播放
            setLooping(isLooping)

            // 准备完成监听
            setOnPreparedListener { mp ->
                isPrepared = true
                currentState = PlayState.PLAYING
                
                playListener?.onPrepared()
                mp.start()
                
                // 在开始播放后设置音量，确保生效
                if (useAbsoluteVolume && currentVolume != null) {
                    // 使用绝对音量
                    setAbsoluteVolume(context, currentVolume!!)
                } else {
                    // 使用相对音量
                    applyVolume(context)
                }
                
                playListener?.onStart()
            }

            // 播放完成监听
            setOnCompletionListener {
                currentState = PlayState.STOPPED
                
                // 如果使用了绝对音量且需要恢复，则恢复系统音量
                if (useAbsoluteVolume && restoreVolumeOnComplete && !isLooping) {
                    contextRef?.get()?.let { ctx ->
                        restoreSystemVolume(ctx)
                        android.util.Log.d("AudioPlayerUtil", "播放完成，已恢复系统音量")
                    }
                }
                
                playListener?.onCompletion()
                
                if (!isLooping) {
                    isPrepared = false
                }
            }

            // 错误监听
            setOnErrorListener { _, what, extra ->
                currentState = PlayState.ERROR
                isPrepared = false
                playListener?.onError("MediaPlayer error: what=$what, extra=$extra")
                true
            }
        }
    }

    /**
     * 应用音量设置
     */
    private fun applyVolume(context: Context) {
        try {
            // 如果设置了自定义音量，使用自定义音量；否则使用系统音量
            val volume = if (currentVolume != null) {
                currentVolume!!
            } else {
                getSystemVolume(context)
            }
            
            // 确保音量在有效范围内
            val finalVolume = volume.coerceIn(0f, 1f)
            
            // 设置 MediaPlayer 音量
            mediaPlayer?.setVolume(finalVolume, finalVolume)
            
            // 添加日志以便调试
            android.util.Log.d("AudioPlayerUtil", "Applied volume: $finalVolume (custom: $currentVolume, system: ${getSystemVolume(context)})")
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("AudioPlayerUtil", "Failed to apply volume", e)
        }
    }

    /**
     * 获取系统音量（返回 0.0 - 1.0 的比例值）
     */
    private fun getSystemVolume(context: Context): Float {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVolume > 0) {
                currentVolume.toFloat() / maxVolume.toFloat()
            } else {
                1.0f
            }
        } catch (e: Exception) {
            e.printStackTrace()
            1.0f // 默认最大音量
        }
    }

    /**
     * 开始播放
     */
    fun start() {
        try {
            if (isPrepared && mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
                currentState = PlayState.PLAYING
                playListener?.onStart()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playListener?.onError("Failed to start: ${e.message}")
        }
    }

    /**
     * 暂停播放
     */
    fun pause() {
        try {
            if (isPrepared && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                currentState = PlayState.PAUSED
                playListener?.onPause()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playListener?.onError("Failed to pause: ${e.message}")
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        try {
            if (isPrepared) {
                mediaPlayer?.stop()
                isPrepared = false
                currentState = PlayState.STOPPED
                playListener?.onStop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playListener?.onError("Failed to stop: ${e.message}")
        }
    }

    /**
     * 停止并重置 MediaPlayer（内部使用）
     */
    private fun stopAndReset() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
            }
            isPrepared = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 释放资源
     * 
     * @param context 上下文，如果提供则会自动恢复系统音量（使用了绝对音量时）
     * @param forceRestore 是否强制恢复系统音量，默认根据 restoreVolumeOnComplete 配置
     */
    fun release(context: Context? = null, forceRestore: Boolean = false) {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
            mediaPlayer = null
            isPrepared = false
            currentSource = null
            currentState = PlayState.IDLE
            currentVolume = null
            playListener = null
            
            // 恢复系统音量
            val shouldRestore = forceRestore || (useAbsoluteVolume && restoreVolumeOnComplete)
            if (shouldRestore) {
                // 优先使用传入的 context，否则尝试从弱引用获取
                val ctx = context ?: contextRef?.get()
                ctx?.let { restoreSystemVolume(it) }
            }
            
            // 重置配置
            useAbsoluteVolume = false
            restoreVolumeOnComplete = false
            contextRef = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取当前播放状态
     */
    fun getState(): PlayState {
        return currentState
    }

    /**
     * 获取播放时长（毫秒）
     */
    fun getDuration(): Int {
        return try {
            if (isPrepared) mediaPlayer?.duration ?: 0 else 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 获取当前播放位置（毫秒）
     */
    fun getCurrentPosition(): Int {
        return try {
            if (isPrepared) mediaPlayer?.currentPosition ?: 0 else 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 跳转到指定位置（毫秒）
     */
    fun seekTo(position: Int) {
        try {
            if (isPrepared) {
                mediaPlayer?.seekTo(position)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playListener?.onError("Failed to seek: ${e.message}")
        }
    }

    /**
     * 设置音量
     *
     * @param leftVolume 左声道音量 (0.0 - 1.0)
     * @param rightVolume 右声道音量 (0.0 - 1.0)
     */
    fun setVolume(leftVolume: Float, rightVolume: Float) {
        try {
            // 确保音量在有效范围内
            val left = leftVolume.coerceIn(0f, 1f)
            val right = rightVolume.coerceIn(0f, 1f)
            
            // 保存音量设置
            currentVolume = (left + right) / 2
            
            // 如果 MediaPlayer 已准备好，立即应用音量
            if (isPrepared && mediaPlayer != null) {
                mediaPlayer?.setVolume(left, right)
                android.util.Log.d("AudioPlayerUtil", "Set volume immediately: L=$left, R=$right")
            } else {
                android.util.Log.d("AudioPlayerUtil", "Volume saved, will apply when prepared: L=$left, R=$right")
            }
            // 否则音量会在 onPrepared 时自动应用
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("AudioPlayerUtil", "Failed to set volume", e)
            playListener?.onError("Failed to set volume: ${e.message}")
        }
    }

    /**
     * 设置音量（统一设置左右声道）
     *
     * @param volume 音量 (0.0 - 1.0)
     */
    fun setVolume(volume: Float) {
        setVolume(volume, volume)
    }

    /**
     * 获取当前音量设置
     *
     * @return 当前音量 (0.0 - 1.0)，如果为 null 表示使用系统音量
     */
    fun getCurrentVolume(): Float? {
        return currentVolume
    }

    /**
     * 获取最终实际播放音量（考虑系统音量）
     * 
     * @param context 上下文
     * @return 最终播放音量 = 系统音量 × MediaPlayer音量
     */
    fun getActualVolume(context: Context): Float {
        val systemVol = getSystemVolume(context)
        val playerVol = currentVolume ?: systemVol
        return systemVol * playerVol
    }

    /**
     * 设置绝对音量（会临时调整系统音量）
     * 
     * 注意：这个方法会修改系统音量以达到绝对音量控制的效果
     * 调用 release() 或 restoreSystemVolume() 会恢复原始系统音量
     * 
     * @param context 上下文
     * @param volume 目标绝对音量 (0.0 - 1.0)
     */
    fun setAbsoluteVolume(context: Context, volume: Float) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // 保存原始系统音量
            if (originalSystemVolume == -1) {
                originalSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
            
            // 计算需要设置的系统音量
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetSystemVolume = (volume * maxVolume).toInt().coerceIn(0, maxVolume)
            
            // 设置系统音量
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetSystemVolume, 0)
            
            // 设置 MediaPlayer 音量为最大（因为系统音量已经设置好了）
            currentVolume = 1.0f
            if (isPrepared) {
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
            
            android.util.Log.d("AudioPlayerUtil", "Set absolute volume: $volume (system volume: $targetSystemVolume/$maxVolume)")
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("AudioPlayerUtil", "Failed to set absolute volume", e)
        }
    }

    /**
     * 恢复原始系统音量
     * 
     * @param context 上下文
     */
    fun restoreSystemVolume(context: Context) {
        try {
            if (originalSystemVolume != -1) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalSystemVolume, 0)
                android.util.Log.d("AudioPlayerUtil", "Restored system volume to: $originalSystemVolume")
                originalSystemVolume = -1
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 设置是否循环播放
     */
    fun setLooping(looping: Boolean) {
        try {
            mediaPlayer?.isLooping = looping
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 是否循环播放
     */
    fun isLooping(): Boolean {
        return try {
            mediaPlayer?.isLooping ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取音频时长的格式化字符串 (mm:ss)
     */
    fun getFormattedDuration(): String {
        return formatTime(getDuration())
    }

    /**
     * 获取当前播放位置的格式化字符串 (mm:ss)
     */
    fun getFormattedCurrentPosition(): String {
        return formatTime(getCurrentPosition())
    }

    /**
     * 格式化时间
     */
    private fun formatTime(milliseconds: Int): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    /**
     * 获取当前音频信息（用于调试）
     */
    fun getDebugInfo(context: Context? = null): String {
        return buildString {
            appendLine("=== AudioPlayerUtil Debug Info ===")
            appendLine("State: $currentState")
            appendLine("Is Prepared: $isPrepared")
            appendLine("Is Playing: ${isPlaying()}")
            appendLine("Current Source: $currentSource")
            appendLine("MediaPlayer Volume: $currentVolume")
            appendLine("Use Absolute Volume: $useAbsoluteVolume")
            appendLine("Restore Volume On Complete: $restoreVolumeOnComplete")
            if (context != null) {
                val systemVol = getSystemVolume(context)
                val actualVol = getActualVolume(context)
                appendLine("System Volume: $systemVol")
                appendLine("Actual Playing Volume: $actualVol (= $systemVol × ${currentVolume ?: systemVol})")
            }
            appendLine("Duration: ${getDuration()}ms (${getFormattedDuration()})")
            appendLine("Position: ${getCurrentPosition()}ms (${getFormattedCurrentPosition()})")
            appendLine("Is Looping: ${isLooping()}")
            appendLine("MediaPlayer exists: ${mediaPlayer != null}")
            appendLine("Original System Volume: $originalSystemVolume")
        }
    }
}

