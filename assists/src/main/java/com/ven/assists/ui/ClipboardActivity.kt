package com.ven.assists.ui

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.ven.assists.AssistsCore
import com.ven.assists.utils.CoroutineWrapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/**
 * 透明Activity，专门用于在应用处于后台时获取剪贴板内容
 */
class ClipboardActivity : AppCompatActivity() {

    companion object {
        // 用于接收剪贴板内容的回调
        private var clipboardDeferred: CompletableDeferred<CharSequence?>? = null

        /**
         * 设置剪贴板结果回调
         */
        fun setClipboardResult(deferred: CompletableDeferred<CharSequence?>) {
            clipboardDeferred = deferred
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置状态栏透明，但保持状态栏文字可见
        BarUtils.setStatusBarColor(this, Color.TRANSPARENT)
        // 设置状态栏图标为深色模式，确保文字可见（根据背景可能需要调整）
        BarUtils.setStatusBarLightMode(this, false)
        LogUtils.d(AssistsCore.LOG_TAG, "ClipboardActivity onCreate")
    }

    override fun onResume() {
        super.onResume()
        CoroutineWrapper.launch {
            delay(300) // 等待Activity完全显示
            // 执行剪贴板操作
            val text = ClipboardUtils.getText()
            LogUtils.d(AssistsCore.LOG_TAG, "ClipboardActivity getText: $text")

            // 返回结果
            clipboardDeferred?.complete(text)
            clipboardDeferred = null

            // 延迟后关闭Activity
            delay(200)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 如果Activity被销毁但还没有返回结果，返回null
        clipboardDeferred?.complete(null)
        clipboardDeferred = null
        LogUtils.d(AssistsCore.LOG_TAG, "ClipboardActivity onDestroy")
    }
}