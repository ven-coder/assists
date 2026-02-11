package com.ven.assists.simple.overlays

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import com.blankj.utilcode.util.ScreenUtils
import com.ven.assists.mp.MPManager
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.simple.databinding.WebOverlayBinding
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.utils.runMain
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowWrapper
import kotlinx.coroutines.Job
import kotlin.apply
import kotlin.let

/**
 * 专门加载 status-card.html 的 Overlay，用于展示状态卡片界面。
 */
@SuppressLint("StaticFieldLeak")
object OverlayStatusCard : AssistsServiceListener {

    private var viewBinding: WebOverlayBinding? = null
        @SuppressLint("ClickableViewAccessibility")
        get() {
            if (field == null) {
                field = WebOverlayBinding.inflate(LayoutInflater.from(AssistsService.instance)).apply {
                    web.setBackgroundColor(0)
                    web.onReceivedTitle = {
                        assistWindowWrapper?.viewBinding?.tvTitle?.text = it
                    }
                }
            }
            return field
        }

    var onClose: ((parent: View) -> Unit)? = null

    var showed: Boolean
        get() = assistWindowWrapper?.let {
            AssistsWindowManager.isVisible(it.getView())
        } ?: false
        private set(_) {}

    var assistWindowWrapper: AssistsWindowWrapper? = null
        private set
        get() {
            viewBinding?.let {
                if (field == null) {
                    field = AssistsWindowWrapper(it.root, wmLayoutParams = AssistsWindowManager.createLayoutParams().apply {
                        width = (ScreenUtils.getScreenWidth() * 0.8).toInt()
                        height = (ScreenUtils.getScreenHeight() * 0.5).toInt()
                    }, onClose = { hide() }).apply {
                        minWidth = (ScreenUtils.getScreenWidth() * 0.6).toInt()
                        minHeight = (ScreenUtils.getScreenHeight() * 0.4).toInt()
                        initialCenter = true
                        with(this.viewBinding) {
                            tvTitle.text = "Status Card"
                            ivWebBack.isVisible = true
                            ivWebForward.isVisible = true
                            ivWebRefresh.isVisible = true
                            ivWebBack.setOnClickListener {
                                this@OverlayStatusCard.viewBinding?.web?.goBack()
                            }
                            ivWebForward.setOnClickListener {
                                this@OverlayStatusCard.viewBinding?.web?.goForward()
                            }
                            ivWebRefresh.setOnClickListener {
                                this@OverlayStatusCard.viewBinding?.web?.reload()
                            }
                        }
                    }
                }
            }
            return field
        }

    private const val STATUS_CARD_URL = "file:///android_asset/assists-web-simple/status-card.html"

    fun show(url: String = STATUS_CARD_URL) {
        CoroutineWrapper.launch {
            runMain {
                if (!AssistsWindowManager.contains(assistWindowWrapper?.getView())) {
                    AssistsWindowManager.add(assistWindowWrapper)
                }
                viewBinding?.web?.loadUrl(url)
            }
        }
    }

    fun hide() {
        AssistsWindowManager.removeView(assistWindowWrapper?.getView())
    }

    override fun onUnbind() {
        viewBinding = null
        assistWindowWrapper = null
    }
}
