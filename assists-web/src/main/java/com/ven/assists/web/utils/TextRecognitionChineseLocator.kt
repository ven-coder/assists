package com.ven.assists.web.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.net.Uri
import androidx.annotation.RequiresApi
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.ven.assists.AssistsCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 使用 ML Kit 中文文字识别查找词组位置的工具类
 */
object TextRecognitionChineseLocator {

    /**
     * 词组位置结果数据
     */
    data class WordPosition(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /**
     * 识别结果数据
     */
    data class RecognitionResult(
        val fullText: String,
        val targetPositions: List<WordPosition>,
        val processingTimeMillis: Long
    )

    private val recognizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * 通过 Bitmap 查找指定词组位置
     */
    suspend fun findWordPositions(
        bitmap: Bitmap,
        targetText: String,
        rotationDegrees: Int = 0
    ): RecognitionResult {
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        return processImage(image, targetText)
    }

    /**
     * 在指定截图区域内查找词组并返回原截图中的坐标
     */
    suspend fun findWordPositionsInRegion(
        bitmap: Bitmap,
        region: Rect,
        targetText: String,
        rotationDegrees: Int = 0
    ): RecognitionResult {
        require(!region.isEmpty) { "Region must not be empty" }
        val bounds = Rect(0, 0, bitmap.width, bitmap.height)
        require(bounds.contains(region)) { "Region must be inside bitmap bounds" }

        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            region.left,
            region.top,
            region.width(),
            region.height()
        )

        return try {
            val recognition = findWordPositions(croppedBitmap, targetText, rotationDegrees)
            if (recognition.targetPositions.isEmpty()) {
                recognition
            } else {
                val adjusted = recognition.targetPositions.map { position ->
                    position.copy(
                        left = position.left + region.left,
                        top = position.top + region.top,
                        right = position.right + region.left,
                        bottom = position.bottom + region.top
                    )
                }
                recognition.copy(targetPositions = adjusted)
            }
        } finally {
            if (!croppedBitmap.isRecycled) {
                croppedBitmap.recycle()
            }
        }
    }

    /**
     * 通过 Uri 查找指定词组位置
     */
    suspend fun findWordPositions(
        context: Context,
        imageUri: Uri,
        targetText: String
    ): RecognitionResult {
        val image = InputImage.fromFilePath(context, imageUri)
        return processImage(image, targetText)
    }

    /**
     * 直接通过当前截图的指定区域查找词组位置
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun findWordPositionsInScreenshotRegion(
        region: Rect? = null,
        targetText: String,
        rotationDegrees: Int = 0
    ): RecognitionResult {
        val screenshot = AssistsCore.takeScreenshot()
            ?: throw IllegalStateException("Screenshot capture failed")
        return try {
            if (region == null || region.isEmpty) {
                findWordPositions(screenshot, targetText, rotationDegrees)
            } else {
                findWordPositionsInRegion(screenshot, region, targetText, rotationDegrees)
            }
        } finally {
            if (!screenshot.isRecycled) {
                screenshot.recycle()
            }
        }
    }

    /**
     * 识别结果：屏幕中所有文字及其位置
     */
    data class AllTextPositionsResult(
        val fullText: String,
        val positions: List<WordPosition>,
        val processingTimeMillis: Long
    )

    /**
     * 通过 Bitmap 识别所有文字及其位置
     */
    suspend fun getAllTextPositions(
        bitmap: Bitmap,
        rotationDegrees: Int = 0
    ): AllTextPositionsResult {
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        return processImageForAllPositions(image)
    }

    /**
     * 在指定截图区域内识别所有文字并返回原截图中的坐标
     */
    suspend fun getAllTextPositionsInRegion(
        bitmap: Bitmap,
        region: Rect,
        rotationDegrees: Int = 0
    ): AllTextPositionsResult {
        require(!region.isEmpty) { "Region must not be empty" }
        val bounds = Rect(0, 0, bitmap.width, bitmap.height)
        require(bounds.contains(region)) { "Region must be inside bitmap bounds" }

        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            region.left,
            region.top,
            region.width(),
            region.height()
        )

        return try {
            val result = getAllTextPositions(croppedBitmap, rotationDegrees)
            if (result.positions.isEmpty()) {
                result
            } else {
                val adjusted = result.positions.map { position ->
                    position.copy(
                        left = position.left + region.left,
                        top = position.top + region.top,
                        right = position.right + region.left,
                        bottom = position.bottom + region.top
                    )
                }
                result.copy(positions = adjusted)
            }
        } finally {
            if (!croppedBitmap.isRecycled) {
                croppedBitmap.recycle()
            }
        }
    }

    /**
     * 直接通过当前截图的指定区域识别所有文字位置
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun getAllTextPositionsInScreenshotRegion(
        region: Rect? = null,
        rotationDegrees: Int = 0
    ): AllTextPositionsResult {
        val screenshot = AssistsCore.takeScreenshot()
            ?: throw IllegalStateException("Screenshot capture failed")
        return try {
            if (region == null || region.isEmpty) {
                getAllTextPositions(screenshot, rotationDegrees)
            } else {
                getAllTextPositionsInRegion(screenshot, region, rotationDegrees)
            }
        } finally {
            if (!screenshot.isRecycled) {
                screenshot.recycle()
            }
        }
    }

    /**
     * 释放识别器资源
     */
    fun close() {
        recognizer.close()
    }

    private suspend fun processImage(
        image: InputImage,
        targetText: String
    ): RecognitionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val recognizedText = recognizer.process(image).await()
        val positions = if (targetText.isBlank()) {
            emptyList()
        } else {
            findTargetPositions(recognizedText, targetText)
        }
        val duration = System.currentTimeMillis() - startTime
        RecognitionResult(
            fullText = recognizedText.text,
            targetPositions = positions,
            processingTimeMillis = duration
        )
    }

    private suspend fun processImageForAllPositions(image: InputImage): AllTextPositionsResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val recognizedText = recognizer.process(image).await()
            val positions = collectAllPositions(recognizedText)
            val duration = System.currentTimeMillis() - startTime
            AllTextPositionsResult(
                fullText = recognizedText.text,
                positions = positions,
                processingTimeMillis = duration
            )
        }

    /** 收集识别结果中所有文字元素及其边界框 */
    private fun collectAllPositions(recognizedText: Text): List<WordPosition> {
        val results = mutableListOf<WordPosition>()
        recognizedText.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                line.elements.forEach { element ->
                    val boundingBox = element.boundingBox ?: return@forEach
                    val text = element.text
                    if (text.isNotBlank()) {
                        results.add(
                            WordPosition(
                                text = text,
                                left = boundingBox.left,
                                top = boundingBox.top,
                                right = boundingBox.right,
                                bottom = boundingBox.bottom
                            )
                        )
                    }
                }
            }
        }
        return results
    }

    private fun findTargetPositions(
        recognizedText: Text,
        targetText: String
    ): List<WordPosition> {
        val normalizedTarget = targetText.replace("\\s+".toRegex(), "")
        if (normalizedTarget.isEmpty()) return emptyList()

        val results = mutableListOf<WordPosition>()
        recognizedText.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                val elements = line.elements
                if (elements.isEmpty()) return@forEach

                val contentBuilder = StringBuilder()
                val indexRanges = mutableListOf<Pair<IntRange, Rect>>()

                var currentIndex = 0
                elements.forEach { element ->
                    val boundingBox = element.boundingBox ?: return@forEach
                    val sanitized = element.text.replace("\\s+".toRegex(), "")
                    if (sanitized.isEmpty()) return@forEach
                    val start = currentIndex
                    val end = currentIndex + sanitized.length
                    contentBuilder.append(sanitized)
                    indexRanges.add(start until end to Rect(boundingBox))
                    currentIndex = end
                }

                if (indexRanges.isEmpty()) return@forEach

                val joinedLine = contentBuilder.toString()
                var searchStart = 0
                while (true) {
                    val matchIndex = joinedLine.indexOf(normalizedTarget, startIndex = searchStart)
                    if (matchIndex == -1) break
                    val matchEnd = matchIndex + normalizedTarget.length
                    val includedRects = indexRanges.filter { range ->
                        range.first.first < matchEnd && range.first.last + 1 > matchIndex
                    }.map { it.second }

                    if (includedRects.isNotEmpty()) {
                        val left = includedRects.minOf { it.left }
                        val top = includedRects.minOf { it.top }
                        val right = includedRects.maxOf { it.right }
                        val bottom = includedRects.maxOf { it.bottom }

                        val matchedText = joinedLine.substring(matchIndex, matchEnd)
                        results.add(
                            WordPosition(
                                text = matchedText,
                                left = left,
                                top = top,
                                right = right,
                                bottom = bottom
                            )
                        )
                    }
                    searchStart = matchIndex + 1
                }
            }
        }
        return results
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }.addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }.addOnCanceledListener {
            continuation.cancel()
        }
    }
}

