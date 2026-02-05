package com.ven.assists.web.mlkit

import android.graphics.Rect
import android.os.Build
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.ven.assists.web.utils.TextRecognitionChineseLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ML Kit 屏幕文字识别工具方法
 * 封装基于当前截图的词组位置识别与全屏文字位置识别，供业务层或 JavascriptInterface 直接调用
 */
object MlkitScreenTextUtils {

    private const val MIN_SDK_FOR_SCREENSHOT = Build.VERSION_CODES.R

    /**
     * 识别当前屏幕中指定词组的位置
     * @param targetText 要查找的词组，不能为空
     * @param region 可选，只在该矩形区域内识别；为 null 时识别整屏
     * @param rotationDegrees 图片旋转角度，默认 0
     * @return 成功时包含 [TextRecognitionChineseLocator.RecognitionResult]，失败时包含异常信息。API 30 以下返回 failure
     */
    suspend fun findPhrasePositionsOnScreen(
        targetText: String,
        region: Rect? = null,
        rotationDegrees: Int = 0
    ): Result<TextRecognitionChineseLocator.RecognitionResult> {
        if (Build.VERSION.SDK_INT < MIN_SDK_FOR_SCREENSHOT) {
            return Result.failure(
                IllegalStateException("findPhrasePositionsOnScreen requires Android 11 (API 30) or above")
            )
        }
        if (targetText.isBlank()) {
            return Result.failure(IllegalArgumentException("targetText must not be blank"))
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                TextRecognitionChineseLocator.findWordPositionsInScreenshotRegion(
                    region = region,
                    targetText = targetText,
                    rotationDegrees = rotationDegrees
                )
            }
        }
    }

    /**
     * 识别当前屏幕中所有文字内容及其位置
     * @param region 可选，只在该矩形区域内识别；为 null 时识别整屏
     * @param rotationDegrees 图片旋转角度，默认 0
     * @return 成功时包含 [TextRecognitionChineseLocator.AllTextPositionsResult]，失败时包含异常信息。API 30 以下返回 failure
     */
    suspend fun getScreenTextPositions(
        region: Rect? = null,
        rotationDegrees: Int = 0
    ): Result<TextRecognitionChineseLocator.AllTextPositionsResult> {
        if (Build.VERSION.SDK_INT < MIN_SDK_FOR_SCREENSHOT) {
            return Result.failure(
                IllegalStateException("getScreenTextPositions requires Android 11 (API 30) or above")
            )
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                TextRecognitionChineseLocator.getAllTextPositionsInScreenshotRegion(
                    region = region,
                    rotationDegrees = rotationDegrees
                )
            }
        }
    }

    /** 当前运行环境是否支持基于截图的识别（API 30+） */
    fun isScreenTextRecognitionSupported(): Boolean = Build.VERSION.SDK_INT >= MIN_SDK_FOR_SCREENSHOT

    /**
     * 识别当前屏幕中指定词组的位置，并将结果输出为 JSON 字符串
     * @param targetText 要查找的词组，不能为空
     * @param region 可选，只在该矩形区域内识别；为 null 时识别整屏
     * @param rotationDegrees 图片旋转角度，默认 0
     * @return 成功时返回 JSON 字符串，结构为 { "fullText", "positions": [ { "text", "left", "top", "right", "bottom" } ], "processingTimeMillis" }；失败时返回 Result.failure
     */
    suspend fun findPhrasePositionsOnScreenAsJson(
        targetText: String,
        region: Rect? = null,
        rotationDegrees: Int = 0
    ): Result<String> {
        return findPhrasePositionsOnScreen(targetText, region, rotationDegrees)
            .map { recognition -> recognitionToJson(recognition.fullText, recognition.targetPositions, recognition.processingTimeMillis) }
    }

    /**
     * 识别当前屏幕中所有文字及其位置，并将结果输出为 JSON 字符串
     * @param region 可选，只在该矩形区域内识别；为 null 时识别整屏
     * @param rotationDegrees 图片旋转角度，默认 0
     * @return 成功时返回 JSON 字符串，结构为 { "fullText", "positions": [ { "text", "left", "top", "right", "bottom" } ], "processingTimeMillis" }；失败时返回 Result.failure
     */
    suspend fun getScreenTextPositionsAsJson(
        region: Rect? = null,
        rotationDegrees: Int = 0
    ): Result<String> {
        return getScreenTextPositions(region, rotationDegrees)
            .map { result -> recognitionToJson(result.fullText, result.positions, result.processingTimeMillis) }
    }

    /** 将识别结果序列化为 JSON 字符串 */
    private fun recognitionToJson(
        fullText: String,
        positions: List<TextRecognitionChineseLocator.WordPosition>,
        processingTimeMillis: Long
    ): String {
        val positionsArray = JsonArray().apply {
            positions.forEach { pos ->
                add(JsonObject().apply {
                    addProperty("text", pos.text)
                    addProperty("left", pos.left)
                    addProperty("top", pos.top)
                    addProperty("right", pos.right)
                    addProperty("bottom", pos.bottom)
                })
            }
        }
        return JsonObject().apply {
            addProperty("fullText", fullText)
            add("positions", positionsArray)
            addProperty("processingTimeMillis", processingTimeMillis)
        }.toString()
    }
}
