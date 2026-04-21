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
import com.ven.assists.simple.model.Plugin
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.utils.runMain
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowWrapper
import kotlinx.coroutines.Job
import kotlin.apply
import kotlin.let

@SuppressLint("StaticFieldLeak")
object OverlayWeb : AssistsServiceListener {

    var runAutoScrollListJob: Job? = null
    private var logCollectJob: Job? = null

    private var viewBinding: WebOverlayBinding? = null
        @SuppressLint("ClickableViewAccessibility")
        get() {
            if (field == null) {
                field = WebOverlayBinding.inflate(LayoutInflater.from(AssistsService.getOrNull())).apply {
                    web.setBackgroundColor(0)
//                    web.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
//                    web.background.alpha = 150
                    web.onReceivedTitle = {
                        assistWindowWrapper?.viewBinding?.tvTitle?.text = it
                    }
                }
            }
            return field
        }


    var onClose: ((parent: View) -> Unit)? = null

    var showed = false
        private set
        get() {
            assistWindowWrapper?.let {
                return AssistsWindowManager.isVisible(it.getView())
            } ?: return false
        }

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
                        with(viewBinding) {
//                            tvTitle.text = plugin?.overlayTitle ?: ""
                            ivWebBack.isVisible = true
                            ivWebForward.isVisible = true
                            ivWebRefresh.isVisible = true
                            ivWebBack.setOnClickListener {
                                this@OverlayWeb.viewBinding?.web?.goBack()
                            }
                            ivWebForward.setOnClickListener {
                                this@OverlayWeb.viewBinding?.web?.goForward()
                            }
                            ivWebRefresh.setOnClickListener {
                                this@OverlayWeb.viewBinding?.web?.reload()
                            }
                        }
                    }
                }
            }
            return field
        }


    fun show() {

        if (!AssistsService.listeners.contains(this)) {
            AssistsService.listeners.add(this)
        }
        CoroutineWrapper.launch {
            if (!MPManager.isEnable) {
                val result = MPManager.request(autoAllow = true)
                if (!result) {
                    return@launch
                }
            }
            runMain {
                if (!AssistsWindowManager.contains(assistWindowWrapper?.getView())) {
                    AssistsWindowManager.add(assistWindowWrapper)
                }
                viewBinding?.web?.loadUrl("file:///android_asset/assists-web-simple/index.html")
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


}