package com.ven.assists.simple.overlays

import android.annotation.SuppressLint
import android.os.Build
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import com.blankj.utilcode.util.ScreenUtils
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowWrapper
import com.ven.assists.log.AssistsLog
import com.ven.assists.log.AssistsLogDiagnostics
import com.ven.assists.log.logAppend
import com.ven.assists.simple.databinding.LogOverlayBinding
import com.ven.assists.stepper.StepManager
import com.ven.assists.utils.CoroutineWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@SuppressLint("StaticFieldLeak")
object OverlayLog : AssistsServiceListener {

    var runAutoScrollListJob: Job? = null
    private var logCollectJob: Job? = null

    private val onScrollTouchListener = object : OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    runAutoScrollListJob?.cancel()
                    runAutoScrollListJob = null
                }

                MotionEvent.ACTION_UP -> {
                    runAutoScrollList()
                }
            }
            return false
        }
    }
    private var viewBinding: LogOverlayBinding? = null
        @SuppressLint("ClickableViewAccessibility")
        get() {
            if (field == null) {
                field = LogOverlayBinding.inflate(LayoutInflater.from(AssistsService.instance)).apply {
                    scrollView.setOnTouchListener(onScrollTouchListener)
                    btnClean.setOnClickListener {
                        CoroutineWrapper.launch { AssistsLog.clear() }
                    }
                    btnReportLog.setOnClickListener {
                        reportLog()
                    }
                }
            }
            return field
        }


    var onClose: ((parent: View) -> Unit)? = null

    var showed = false
        private set
        get() {
            field = assistWindowWrapper?.let {
                return AssistsWindowManager.isVisible(it.getView())
            } ?: return false
            return field
        }

    var assistWindowWrapper: AssistsWindowWrapper? = null
        private set
        get() {
            viewBinding?.let {
                if (field == null) {
                    field = AssistsWindowWrapper(it.root, wmLayoutParams = AssistsWindowManager.createLayoutParams().apply {
                        width = (ScreenUtils.getScreenWidth() * 0.8).toInt()
                        height = (ScreenUtils.getScreenHeight() * 0.5).toInt()
                    }, onClose = {
                        hide()
                        onClose?.invoke(it)
                    }).apply {
                        minWidth = (ScreenUtils.getScreenWidth() * 0.6).toInt()
                        minHeight = (ScreenUtils.getScreenHeight() * 0.4).toInt()
                        initialCenter = true
                        viewBinding.tvTitle.text = "日志"
                    }
                }
            }
            return field
        }

    /**
     * @param clearLog 为 true（默认）时在展示前清空 [AssistsLog] 中的日志；为 false 时保留现有日志仅展示浮窗。
     * @param mainPageLogViewer 为 true 时（如从主页打开）将「停止」改为「添加测试日志」，点击仅追加测试日志；为 false 时保持停止步骤逻辑。
     */
    fun show(clearLog: Boolean = true, mainPageLogViewer: Boolean = false) {
        if (clearLog) {
            AssistsLog.clear()
        } else {
            // 不清空时从磁盘同步到内存，便于主页等入口打开时看到最新本地日志
            AssistsLog.refreshFromFile()
        }
        if (!AssistsService.listeners.contains(this)) {
            AssistsService.listeners.add(this)
        }
        viewBinding.let { }
        if (!AssistsWindowManager.contains(assistWindowWrapper?.getView())) {
            AssistsWindowManager.add(assistWindowWrapper)
            initLogCollect()
            runAutoScrollList(delay = 0)
        }
        applyStopButtonMode(mainPageLogViewer)
        if (!clearLog) {
            CoroutineWrapper.launch {
                withContext(Dispatchers.Main) {
                    viewBinding?.apply {
                        val full = AssistsLog.entireLogText.value
                        tvLog.text = full
                        tvLength.text = "${full.length}"
                    }
                }
            }
        }
    }

    /** 主页模式：添加测试日志；否则：停止步骤 */
    private fun applyStopButtonMode(mainPageLogViewer: Boolean) {
        viewBinding?.btnStop?.apply {
            val density = resources.displayMetrics.density
            val widthDp = if (mainPageLogViewer) 108 else 60
            layoutParams.width = (widthDp * density + 0.5f).toInt()
            requestLayout()
            if (mainPageLogViewer) {
                text = "添加测试日志"
                setOnClickListener {
                    "这是一条测试的日志".logAppend()
                }
            } else {
                text = "停止"
                setOnClickListener {
                    StepManager.isStop = true
                    "停止".logAppend()
                }
            }
        }
    }

    fun hide() {
        AssistsWindowManager.removeView(assistWindowWrapper?.getView())
        logCollectJob?.cancel()
        logCollectJob = null
        runAutoScrollListJob?.cancel()
        runAutoScrollListJob = null
    }

    override fun onUnbind() {
        viewBinding = null
        assistWindowWrapper = null
        logCollectJob?.cancel()
        logCollectJob = null
        runAutoScrollListJob?.cancel()
        runAutoScrollListJob = null
    }


    private fun runAutoScrollList(delay: Long = 5000) {
        runAutoScrollListJob?.cancel()
        runAutoScrollListJob = CoroutineWrapper.launch {
            delay(delay)
            while (true) {
                withContext(Dispatchers.Main) {
                    viewBinding?.scrollView?.smoothScrollBy(0, viewBinding?.scrollView?.getChildAt(0)?.height ?: 0)
                }
                delay(250)
            }
        }
    }

    private fun initLogCollect() {
        logCollectJob?.cancel()
        logCollectJob = CoroutineWrapper.launch {
            AssistsLog.entireLogText.collect { full ->
                withContext(Dispatchers.Main) {
                    viewBinding?.apply {
                        tvLog.text = full
                        tvLength.text = "${full.length}"
                    }
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun reportLog() {
        CoroutineWrapper.launch {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                "需要 Android 11（API 30）及以上才能上报日志。".logAppend()
                return@launch
            }
            "开始上报日志...".logAppend()
            withContext(Dispatchers.Main) {
                viewBinding?.btnReportLog?.apply {
                    isEnabled = false
                    text = "请求中"
                }
            }
            val result = try {
                AssistsLogDiagnostics.uploadLogs()
            } finally {
                withContext(Dispatchers.Main) {
                    viewBinding?.btnReportLog?.apply {
                        isEnabled = true
                        text = "上报"
                    }
                }
            }
            val summary = if (result.success) {
                buildString {
                    append("上报成功")
                    result.httpCode?.let { append("（HTTP ").append(it).append("）") }
                }
            } else {
                buildString {
                    append("上报失败")
                    append("：")
                    append(result.message)
                    result.httpCode?.let { append("（HTTP ").append(it).append("）") }
                    result.data?.let { d ->
                        append("\n")
                        append("编号=").append(d.id)
                        append("，日志路径=").append(d.log_file_path)
                        append("，截图路径=").append(d.screenshot_file_path)
                        append("，节点树路径=").append(d.node_info_file_path)
                    }
                    if (!result.responseBody.isNullOrBlank()) {
                        append("\n")
                        append("响应正文：")
                        append(result.responseBody)
                    }
                    result.cause?.message?.takeIf { it.isNotBlank() }?.let {
                        append("\n")
                        append("异常：")
                        append(it)
                    }
                }
            }
            summary.logAppend()
            if (result.success) {
                "请访问 ${AssistsLogDiagnostics.adminWebBaseUrl()} 管理后台查看日志信息和页面节点信息。".logAppend()
            }
        }
    }
}