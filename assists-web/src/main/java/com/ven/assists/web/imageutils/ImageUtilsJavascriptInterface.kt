package com.ven.assists.web.imageutils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.ImageUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.PathUtils
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ven.assists.web.CallRequest
import com.ven.assists.web.CallResponse
import com.ven.assists.web.JavascriptInterfaceContext
import com.ven.assists.web.createResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 图片工具相关的 JavascriptInterface
 * 提供图片处理相关的功能，包括转换、处理、压缩、保存等
 * 所有方法只接受图片路径参数，返回处理后的图片路径
 */
class ImageUtilsJavascriptInterface(val webView: WebView) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    fun <T> callbackResponse(result: CallResponse<T>) {
        coroutineScope.launch {
            runCatching {
                val json = GsonUtils.toJson(result)
                callback(json)
            }.onFailure {
                LogUtils.e(it)
            }
        }
    }

    fun callback(result: String) {
        val encoded = Base64.encodeToString(result.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val js = String.format("javascript:assistsxImageUtilsCallback('%s')", encoded)
        webView.evaluateJavascript(js, null)
    }

    @JavascriptInterface
    fun call(originJson: String): String {
        val result = GsonUtils.toJson(CallResponse<Any>(code = 0))
        coroutineScope.launch(Dispatchers.IO) {
            processCall(originJson)
        }
        return result
    }

    private suspend fun CoroutineScope.processCall(originJson: String) {
        val request = GsonUtils.fromJson<CallRequest<JsonObject>>(
            originJson,
            object : TypeToken<CallRequest<JsonObject>>() {}.type
        )
        runCatching {
            val response = when (request.method) {
                // 获取相关
                ImageUtilsCallMethod.getSize -> handleGetSize(request)
                ImageUtilsCallMethod.getImageType -> handleGetImageType(request)
                ImageUtilsCallMethod.isImage -> handleIsImage(request)
                ImageUtilsCallMethod.getRotateDegree -> handleGetRotateDegree(request)
                
                // 图片处理相关
                ImageUtilsCallMethod.scale -> handleScale(request)
                ImageUtilsCallMethod.clip -> handleClip(request)
                ImageUtilsCallMethod.skew -> handleSkew(request)
                ImageUtilsCallMethod.rotate -> handleRotate(request)
                ImageUtilsCallMethod.toRound -> handleToRound(request)
                ImageUtilsCallMethod.toRoundCorner -> handleToRoundCorner(request)
                ImageUtilsCallMethod.addCornerBorder -> handleAddCornerBorder(request)
                ImageUtilsCallMethod.addCircleBorder -> handleAddCircleBorder(request)
                ImageUtilsCallMethod.addReflection -> handleAddReflection(request)
                ImageUtilsCallMethod.addTextWatermark -> handleAddTextWatermark(request)
                ImageUtilsCallMethod.addImageWatermark -> handleAddImageWatermark(request)
                ImageUtilsCallMethod.toAlpha -> handleToAlpha(request)
                ImageUtilsCallMethod.toGray -> handleToGray(request)
                ImageUtilsCallMethod.fastBlur -> handleFastBlur(request)
                ImageUtilsCallMethod.renderScriptBlur -> handleRenderScriptBlur(request)
                ImageUtilsCallMethod.stackBlur -> handleStackBlur(request)
                
                // 压缩相关
                ImageUtilsCallMethod.compressByScale -> handleCompressByScale(request)
                ImageUtilsCallMethod.compressByQuality -> handleCompressByQuality(request)
                ImageUtilsCallMethod.compressBySampleSize -> handleCompressBySampleSize(request)
                
                // 保存相关
                ImageUtilsCallMethod.save -> handleSave(request)
                ImageUtilsCallMethod.save2Album -> handleSave2Album(request)
                
                else -> {
                    request.createResponse(-1, message = "方法未支持")
                }
            }
            callbackResponse(response)
        }.onFailure {
            LogUtils.e(it)
            callbackResponse(request.createResponse(-1, message = it.message, data = JsonObject()))
        }
    }

    // ==================== 获取相关 ====================

    /**
     * 获取图片尺寸
     */
    private fun handleGetSize(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val size = ImageUtils.getSize(imagePath)
            
            val data = JsonObject().apply {
                addProperty("filePath", imagePath)
                addProperty("width", size[0])
                addProperty("height", size[1])
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "获取失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 获取图片类型
     */
    private fun handleGetImageType(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val imageType = ImageUtils.getImageType(imagePath)
            val data = JsonObject().apply {
                addProperty("filePath", imagePath)
                addProperty("imageType", imageType.toString())
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "获取失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 判断是否为图片
     */
    private fun handleIsImage(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val fileName = request.arguments?.get("fileName")?.asString
        
        if (fileName.isNullOrEmpty()) {
            return request.createResponse(-1, message = "fileName参数不能为空", data = JsonObject())
        }
        
        return try {
            val isImage = ImageUtils.isImage(fileName)
            val data = JsonObject().apply {
                addProperty("isImage", isImage)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "判断失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 获取图片旋转角度
     */
    private fun handleGetRotateDegree(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val degree = ImageUtils.getRotateDegree(imagePath)
            val data = JsonObject().apply {
                addProperty("filePath", imagePath)
                addProperty("degree", degree)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "获取失败: ${e.message}", data = JsonObject())
        }
    }

    // ==================== 图片处理相关 ====================

    /**
     * 缩放图片
     */
    private fun handleScale(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val scaleWidth = request.arguments?.get("scaleWidth")?.asFloat
        val scaleHeight = request.arguments?.get("scaleHeight")?.asFloat
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val scaledBitmap = when {
                scaleWidth != null && scaleHeight != null -> {
                    ImageUtils.scale(bitmap, scaleWidth, scaleHeight)
                }
                scaleWidth != null -> {
                    ImageUtils.scale(bitmap, scaleWidth, scaleWidth)
                }
                scaleHeight != null -> {
                    ImageUtils.scale(bitmap, scaleHeight, scaleHeight)
                }
                else -> {
                    bitmap.recycle()
                    return request.createResponse(-1, message = "scaleWidth或scaleHeight参数不能为空", data = JsonObject())
                }
            }
            
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(scaledBitmap, savePath, imagePath, format)
            scaledBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 裁剪图片
     */
    private fun handleClip(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val x = request.arguments?.get("x")?.asInt ?: 0
        val y = request.arguments?.get("y")?.asInt ?: 0
        val width = request.arguments?.get("width")?.asInt
        val height = request.arguments?.get("height")?.asInt
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val clipWidth = width ?: (bitmap.width - x)
            val clipHeight = height ?: (bitmap.height - y)
            
            val clippedBitmap = ImageUtils.clip(bitmap, x, y, clipWidth, clipHeight)
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(clippedBitmap, savePath, imagePath, format)
            clippedBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 倾斜图片
     */
    private fun handleSkew(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val kx = request.arguments?.get("kx")?.asFloat ?: 0f
        val ky = request.arguments?.get("ky")?.asFloat ?: 0f
        val px = request.arguments?.get("px")?.asFloat
        val py = request.arguments?.get("py")?.asFloat
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val skewedBitmap = if (px != null && py != null) {
                ImageUtils.skew(bitmap, kx, ky, px, py)
            } else {
                ImageUtils.skew(bitmap, kx, ky)
            }
            
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(skewedBitmap, savePath, imagePath, format)
            skewedBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 旋转图片
     */
    private fun handleRotate(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val degree = request.arguments?.get("degree")?.asInt ?: 0
        val px = request.arguments?.get("px")?.asFloat
        val py = request.arguments?.get("py")?.asFloat
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val rotatedBitmap = if (px != null && py != null) {
                ImageUtils.rotate(bitmap, degree, px, py)
            } else {
                ImageUtils.rotate(bitmap, degree, 0f, 0f)
            }
            
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(rotatedBitmap, savePath, imagePath, format)
            rotatedBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 转为圆形图片
     */
    private fun handleToRound(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val roundBitmap = ImageUtils.toRound(bitmap)
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(roundBitmap, savePath, imagePath, format)
            roundBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 转为圆角图片
     */
    private fun handleToRoundCorner(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val radius = request.arguments?.get("radius")?.asFloat ?: 0f
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val roundCornerBitmap = ImageUtils.toRoundCorner(bitmap, radius)
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(roundCornerBitmap, savePath, imagePath, format)
            roundCornerBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 添加圆角边框
     */
    private fun handleAddCornerBorder(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val borderSize = request.arguments?.get("borderSize")?.asInt ?: 0
        val color = request.arguments?.get("color")?.asString ?: "#000000"
        val cornerRadius = request.arguments?.get("cornerRadius")?.asFloat ?: 0f
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val colorInt = Color.parseColor(color)
            val borderedBitmap = ImageUtils.addCornerBorder(bitmap, borderSize.toFloat(), colorInt, cornerRadius)
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(borderedBitmap, savePath, imagePath, format)
            borderedBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 添加圆形边框
     */
    private fun handleAddCircleBorder(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val borderSize = request.arguments?.get("borderSize")?.asInt ?: 0
        val color = request.arguments?.get("color")?.asString ?: "#000000"
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val colorInt = Color.parseColor(color)
            val borderedBitmap = ImageUtils.addCircleBorder(bitmap, borderSize.toFloat(), colorInt)
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(borderedBitmap, savePath, imagePath, format)
            borderedBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 添加倒影
     */
    private fun handleAddReflection(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val reflectionHeight = request.arguments?.get("reflectionHeight")?.asInt ?: 0
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val reflectionBitmap = ImageUtils.addReflection(bitmap, reflectionHeight)
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(reflectionBitmap, savePath, imagePath, format)
            reflectionBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 添加文字水印
     */
    private fun handleAddTextWatermark(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val text = request.arguments?.get("text")?.asString ?: ""
        val x = request.arguments?.get("x")?.asInt ?: 0
        val y = request.arguments?.get("y")?.asInt ?: 0
        val color = request.arguments?.get("color")?.asString ?: "#000000"
        val size = request.arguments?.get("size")?.asFloat ?: 16f
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val colorInt = Color.parseColor(color)
            val watermarkedBitmap = ImageUtils.addTextWatermark(bitmap, text, x, y, colorInt.toFloat(), size)
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(watermarkedBitmap, savePath, imagePath, format)
            watermarkedBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 添加图片水印
     */
    private fun handleAddImageWatermark(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val watermarkPath = request.arguments?.get("watermarkPath")?.asString
        val x = request.arguments?.get("x")?.asInt ?: 0
        val y = request.arguments?.get("y")?.asInt ?: 0
        val alpha = request.arguments?.get("alpha")?.asInt ?: 255
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        if (watermarkPath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "watermarkPath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val watermarkBitmap = loadBitmap(watermarkPath)
                ?: return request.createResponse(-1, message = "无法加载水印图片", data = JsonObject()).also {
                    bitmap.recycle()
                }
            
            val watermarkedBitmap = ImageUtils.addImageWatermark(bitmap, watermarkBitmap, x, y, alpha)
            bitmap.recycle()
            watermarkBitmap.recycle()
            
            val resultPath = saveBitmapToFile(watermarkedBitmap, savePath, imagePath, format)
            watermarkedBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 转为 alpha 位图
     */
    private fun handleToAlpha(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val alphaBitmap = ImageUtils.toAlpha(bitmap)
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(alphaBitmap, savePath, imagePath, format)
            alphaBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 转为灰度图片
     */
    private fun handleToGray(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val grayBitmap = ImageUtils.toGray(bitmap)
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(grayBitmap, savePath, imagePath, format)
            grayBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 快速模糊
     */
    private fun handleFastBlur(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val radius = request.arguments?.get("radius")?.asFloat ?: 0f
        val scale = request.arguments?.get("scale")?.asFloat ?: 1f
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val blurredBitmap = ImageUtils.fastBlur(bitmap, radius, scale)
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(blurredBitmap, savePath, imagePath, format)
            blurredBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * RenderScript 模糊
     */
    private fun handleRenderScriptBlur(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val radius = request.arguments?.get("radius")?.asFloat ?: 0f
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val context = JavascriptInterfaceContext.getContext()
                ?: return request.createResponse(-1, message = "上下文无效", data = JsonObject())
            
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val blurredBitmap = ImageUtils.renderScriptBlur(bitmap, radius, false)
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(blurredBitmap, savePath, imagePath, format)
            blurredBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * Stack 模糊
     */
    private fun handleStackBlur(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val radius = request.arguments?.get("radius")?.asInt ?: 0
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val blurredBitmap = ImageUtils.stackBlur(bitmap, radius)
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(blurredBitmap, savePath, imagePath, format)
            blurredBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "处理失败: ${e.message}", data = JsonObject())
        }
    }

    // ==================== 压缩相关 ====================

    /**
     * 按缩放压缩
     */
    private fun handleCompressByScale(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val scaleWidth = request.arguments?.get("scaleWidth")?.asFloat
        val scaleHeight = request.arguments?.get("scaleHeight")?.asFloat
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val compressedBitmap = when {
                scaleWidth != null && scaleHeight != null -> {
                    ImageUtils.compressByScale(bitmap, scaleWidth, scaleHeight)
                }
                scaleWidth != null -> {
                    ImageUtils.compressByScale(bitmap, scaleWidth, scaleWidth)
                }
                scaleHeight != null -> {
                    ImageUtils.compressByScale(bitmap, scaleHeight, scaleHeight)
                }
                else -> {
                    bitmap.recycle()
                    return request.createResponse(-1, message = "scaleWidth或scaleHeight参数不能为空", data = JsonObject())
                }
            }
            
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(compressedBitmap, savePath, imagePath, format)
            compressedBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "压缩失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 按质量压缩
     */
    private fun handleCompressByQuality(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val quality = request.arguments?.get("quality")?.asInt ?: 100
        val format = request.arguments?.get("format")?.asString ?: "JPEG"
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val compressedBytes = ImageUtils.compressByQuality(bitmap, quality)
            bitmap.recycle()
            
            val compressFormat = when (format.uppercase()) {
                "PNG" -> Bitmap.CompressFormat.PNG
                "WEBP" -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.JPEG
            }
            
            val saveFile = if (!savePath.isNullOrEmpty()) {
                File(savePath)
            } else {
                val originalFile = File(imagePath)
                val originalName = originalFile.nameWithoutExtension
                val extension = when (compressFormat) {
                    Bitmap.CompressFormat.JPEG -> "jpg"
                    Bitmap.CompressFormat.WEBP -> "webp"
                    else -> "png"
                }
                File(originalFile.parent ?: PathUtils.getInternalAppFilesPath(), "${originalName}_compressed_${System.currentTimeMillis()}.$extension")
            }
            
            saveFile.parentFile?.mkdirs()
            saveFile.writeBytes(compressedBytes)
            
            val resultPath = saveFile.absolutePath
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "压缩失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 按采样大小压缩
     */
    private fun handleCompressBySampleSize(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val sampleSize = request.arguments?.get("sampleSize")?.asInt ?: 1
        val format = parseCompressFormat(request.arguments?.get("format")?.asString)
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val compressedBitmap = ImageUtils.compressBySampleSize(bitmap, sampleSize)
            bitmap.recycle()
            
            val resultPath = saveBitmapToFile(compressedBitmap, savePath, imagePath, format)
            compressedBitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("filePath", resultPath)
            }
            request.createResponse(0, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "压缩失败: ${e.message}", data = JsonObject())
        }
    }

    // ==================== 保存相关 ====================

    /**
     * 保存图片
     */
    private fun handleSave(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val savePath = request.arguments?.get("savePath")?.asString
        val format = request.arguments?.get("format")?.asString ?: "PNG"
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val saveFile = if (!savePath.isNullOrEmpty()) {
                File(savePath)
            } else {
                File(PathUtils.getInternalAppFilesPath(), "image_${System.currentTimeMillis()}.${format.lowercase()}")
            }
            
            val compressFormat = when (format.uppercase()) {
                "JPEG", "JPG" -> Bitmap.CompressFormat.JPEG
                "WEBP" -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.PNG
            }
            
            saveFile.parentFile?.mkdirs()
            val success = ImageUtils.save(bitmap, saveFile, compressFormat)
            bitmap.recycle()
            
            val data = JsonObject().apply {
                addProperty("success", success)
                addProperty("filePath", saveFile.absolutePath)
            }
            request.createResponse(if (success) 0 else -1, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "保存失败: ${e.message}", data = JsonObject())
        }
    }

    /**
     * 保存图片到相册
     */
    private fun handleSave2Album(request: CallRequest<JsonObject>): CallResponse<JsonObject> {
        val imagePath = request.arguments?.get("imagePath")?.asString
        val fileName = request.arguments?.get("fileName")?.asString
        val format = request.arguments?.get("format")?.asString ?: "PNG"
        
        if (imagePath.isNullOrEmpty()) {
            return request.createResponse(-1, message = "imagePath参数不能为空", data = JsonObject())
        }
        
        return try {
            val context = JavascriptInterfaceContext.getContext()
                ?: return request.createResponse(-1, message = "上下文无效", data = JsonObject())
            
            val bitmap = loadBitmap(imagePath)
                ?: return request.createResponse(-1, message = "无法加载图片", data = JsonObject())
            
            val compressFormat = when (format.uppercase()) {
                "JPEG", "JPG" -> Bitmap.CompressFormat.JPEG
                "WEBP" -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.PNG
            }
            
            val finalFileName = fileName ?: "image_${System.currentTimeMillis()}.${format.lowercase()}"
            val file = ImageUtils.save2Album(bitmap, finalFileName, compressFormat)
            bitmap.recycle()
            
            val filePath = file?.absolutePath ?: ""
            val success = filePath.isNotEmpty()
            
            val data = JsonObject().apply {
                addProperty("success", success)
                addProperty("filePath", filePath)
            }
            request.createResponse(if (!filePath.isNullOrEmpty()) 0 else -1, data = data)
        } catch (e: Exception) {
            LogUtils.e(e)
            request.createResponse(-1, message = "保存失败: ${e.message}", data = JsonObject())
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析图片格式字符串为 Bitmap.CompressFormat
     * @param formatStr 格式字符串，支持 PNG、JPEG、JPG、WEBP
     * @param defaultFormat 默认格式，如果 formatStr 为空或无效则使用此格式
     * @return Bitmap.CompressFormat
     */
    private fun parseCompressFormat(formatStr: String?, defaultFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): Bitmap.CompressFormat {
        return when (formatStr?.uppercase()) {
            "JPEG", "JPG" -> Bitmap.CompressFormat.JPEG
            "WEBP" -> Bitmap.CompressFormat.WEBP
            "PNG" -> Bitmap.CompressFormat.PNG
            else -> defaultFormat
        }
    }

    /**
     * 加载 Bitmap（从文件路径）
     */
    private fun loadBitmap(imagePath: String?): Bitmap? {
        if (imagePath.isNullOrEmpty()) {
            return null
        }
        return try {
            ImageUtils.getBitmap(imagePath)
        } catch (e: Exception) {
            LogUtils.e(e)
            null
        }
    }

    /**
     * 保存 Bitmap 到文件
     * @param bitmap 要保存的 Bitmap
     * @param savePath 保存路径，如果为空则自动生成
     * @param originalPath 原始图片路径，用于生成默认文件名
     * @param format 图片格式，默认为 PNG
     * @return 保存后的文件路径
     */
    private fun saveBitmapToFile(
        bitmap: Bitmap,
        savePath: String?,
        originalPath: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG
    ): String {
        val saveFile = if (!savePath.isNullOrEmpty()) {
            File(savePath)
        } else {
            // 根据原始文件路径生成新文件名
            val originalFile = File(originalPath)
            val originalName = originalFile.nameWithoutExtension
            val extension = when (format) {
                Bitmap.CompressFormat.JPEG -> "jpg"
                Bitmap.CompressFormat.WEBP -> "webp"
                else -> "png"
            }
            File(originalFile.parent ?: PathUtils.getInternalAppFilesPath(), "${originalName}_processed_${System.currentTimeMillis()}.$extension")
        }
        
        saveFile.parentFile?.mkdirs()
        val success = ImageUtils.save(bitmap, saveFile, format)
        if (!success) {
            throw Exception("保存图片失败")
        }
        return saveFile.absolutePath
    }
}
