package com.ven.assists.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import androidx.core.graphics.createBitmap

object BitmapUtils {
    fun cropCenterWithCornerRatio(
        src: Bitmap,
        scale: Float = 1f,          // 0 < scale <= 1
        cornerRatio: Float = 0f     // 0.0 ~ 1.0
    ): Bitmap {

        // 1. HardwareBitmap 转软件位图（API 24+ 安全）
        val bitmap = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            src.config == Bitmap.Config.HARDWARE
        ) {
            src.copy(Bitmap.Config.ARGB_8888, false)
        } else src

        val width = bitmap.width
        val height = bitmap.height

        // —— 2. 按你的定义计算裁剪区域（scale 是裁剪比例）——
        // 使用确定性的计算方式，确保每次计算结果一致
        val s = scale.coerceIn(0.0001f, 1f)  // 防止 0 并保持 0~1 范围

        // 使用 Math.round 确保舍入的一致性
        val targetWidth = Math.round(width * s).toInt()
        val targetHeight = Math.round(height * s).toInt()

        // 确保裁剪区域不超出边界
        val actualWidth = targetWidth.coerceIn(1, width)
        val actualHeight = targetHeight.coerceIn(1, height)
        
        val left = (width - actualWidth) / 2
        val top = (height - actualHeight) / 2

        val cropped = Bitmap.createBitmap(bitmap, left, top, actualWidth, actualHeight)

        // —— 3. 圆角比例（0~1, 1 = 完全圆形）——
        val cornerRatioClamped = cornerRatio.coerceIn(0f, 1f)
        
        // 如果没有圆角，直接返回裁剪后的bitmap，避免Canvas绘制带来的不确定性
        if (cornerRatioClamped <= 0f) {
            // 清理临时bitmap（如果bitmap是从src复制的）
            if (bitmap != src) {
                bitmap.recycle()
            }
            return cropped
        }

        val minSide = minOf(cropped.width, cropped.height)
        // 使用 Math.round 确保圆角半径计算的一致性
        val cornerRadius = Math.round((minSide / 2f) * cornerRatioClamped).toFloat()

        // —— 4. 绘制圆角输出 ——
        val output = createBitmap(cropped.width, cropped.height)

        val canvas = Canvas(output)
        // 使用抗锯齿，但通过确定性的计算来减少差异
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.isFilterBitmap = false  // 禁用bitmap过滤，减少不确定性
        val rectF = RectF(0f, 0f, cropped.width.toFloat(), cropped.height.toFloat())

        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        
        // 清理临时bitmap
        cropped.recycle()
        if (bitmap != src) {
            bitmap.recycle()
        }

        return output
    }

}