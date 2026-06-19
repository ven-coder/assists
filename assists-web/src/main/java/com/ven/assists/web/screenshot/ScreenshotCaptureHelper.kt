package com.ven.assists.web.screenshot

import android.graphics.Bitmap.CompressFormat
import android.os.Build
import com.ven.assists.screenshot.AssistsScreenshot.bitmapToBase64
import com.ven.assists.screenshot.AssistsScreenshot.takeFullScreenBase64
import com.ven.assists.screenshot.AssistsScreenshot.takeFullScreenBitmap
import com.ven.assists.screenshot.AssistsScreenshot.takeNodeScreenshotBase64
import com.ven.assists.mp.MPManager
import com.ven.assists.mp.MPManager.getBitmap
import com.ven.assists.web.NodeCacheManager
import com.ven.assists.window.AssistsWindowManager
import kotlinx.coroutines.delay

/**
 * 截图采集辅助：隐藏浮窗、兼容低版本录屏截图
 */
object ScreenshotCaptureHelper {

    suspend fun <T> withHiddenOverlay(
        overlayHiddenScreenshotDelayMillis: Long = 250L,
        block: suspend () -> T,
    ): T {
        AssistsWindowManager.hideAll()
        delay(overlayHiddenScreenshotDelayMillis)
        return try {
            block()
        } finally {
            AssistsWindowManager.showTop()
        }
    }

    suspend fun captureFullScreenBase64(
        overlayHiddenScreenshotDelayMillis: Long = 250L,
        format: CompressFormat = CompressFormat.PNG,
        withDataUrlPrefix: Boolean = true,
    ): String? {
        return withHiddenOverlay(overlayHiddenScreenshotDelayMillis) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                takeFullScreenBase64(format = format, withDataUrlPrefix = withDataUrlPrefix)
            } else {
                captureFullScreenBase64Legacy(format, withDataUrlPrefix)
            }
        }
    }

    suspend fun captureNodeScreenshotBase64(
        nodeId: String,
        overlayHiddenScreenshotDelayMillis: Long = 250L,
        format: CompressFormat = CompressFormat.PNG,
        withDataUrlPrefix: Boolean = true,
    ): String? {
        val node = NodeCacheManager.get(nodeId) ?: return null
        return withHiddenOverlay(overlayHiddenScreenshotDelayMillis) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                takeNodeScreenshotBase64(
                    node = node,
                    format = format,
                    withDataUrlPrefix = withDataUrlPrefix,
                )
            } else {
                captureNodeScreenshotBase64Legacy(node, format, withDataUrlPrefix)
            }
        }
    }

    suspend fun captureNodesScreenshotBase64(
        nodeIds: List<String>,
        overlayHiddenScreenshotDelayMillis: Long = 250L,
        format: CompressFormat = CompressFormat.PNG,
        withDataUrlPrefix: Boolean = true,
    ): List<String> {
        if (nodeIds.isEmpty()) {
            return captureFullScreenBase64(
                overlayHiddenScreenshotDelayMillis = overlayHiddenScreenshotDelayMillis,
                format = format,
                withDataUrlPrefix = withDataUrlPrefix,
            )?.let { listOf(it) } ?: emptyList()
        }

        return withHiddenOverlay(overlayHiddenScreenshotDelayMillis) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                captureNodesScreenshotBase64Api30(nodeIds, format, withDataUrlPrefix)
            } else {
                captureNodesScreenshotBase64Legacy(nodeIds, format, withDataUrlPrefix)
            }
        }
    }

    private suspend fun captureNodesScreenshotBase64Api30(
        nodeIds: List<String>,
        format: CompressFormat,
        withDataUrlPrefix: Boolean,
    ): List<String> {
        val fullScreenshot = takeFullScreenBitmap() ?: return emptyList()
        return try {
            nodeIds.mapNotNull { nodeId ->
                val node = NodeCacheManager.get(nodeId) ?: return@mapNotNull null
                takeNodeScreenshotBase64(
                    node = node,
                    fullScreenshot = fullScreenshot,
                    format = format,
                    withDataUrlPrefix = withDataUrlPrefix,
                )
            }
        } finally {
            fullScreenshot.recycle()
        }
    }

    private fun captureNodesScreenshotBase64Legacy(
        nodeIds: List<String>,
        format: CompressFormat,
        withDataUrlPrefix: Boolean,
    ): List<String> {
        val fullScreenshot = MPManager.takeScreenshot2Bitmap() ?: return emptyList()
        return try {
            nodeIds.mapNotNull { nodeId ->
                val node = NodeCacheManager.get(nodeId) ?: return@mapNotNull null
                val nodeBitmap = node.getBitmap(screenshot = fullScreenshot) ?: return@mapNotNull null
                try {
                    bitmapToBase64(nodeBitmap, format, withDataUrlPrefix = withDataUrlPrefix)
                } finally {
                    nodeBitmap.recycle()
                }
            }
        } finally {
            fullScreenshot.recycle()
        }
    }

    private fun captureFullScreenBase64Legacy(
        format: CompressFormat,
        withDataUrlPrefix: Boolean,
    ): String? {
        val bitmap = MPManager.takeScreenshot2Bitmap() ?: return null
        return try {
            bitmapToBase64(bitmap, format, withDataUrlPrefix = withDataUrlPrefix)
        } finally {
            bitmap.recycle()
        }
    }

    private fun captureNodeScreenshotBase64Legacy(
        node: android.view.accessibility.AccessibilityNodeInfo,
        format: CompressFormat,
        withDataUrlPrefix: Boolean,
    ): String? {
        val fullScreenshot = MPManager.takeScreenshot2Bitmap() ?: return null
        return try {
            val nodeBitmap = node.getBitmap(screenshot = fullScreenshot) ?: return null
            try {
                bitmapToBase64(nodeBitmap, format, withDataUrlPrefix = withDataUrlPrefix)
            } finally {
                nodeBitmap.recycle()
            }
        } finally {
            fullScreenshot.recycle()
        }
    }
}
