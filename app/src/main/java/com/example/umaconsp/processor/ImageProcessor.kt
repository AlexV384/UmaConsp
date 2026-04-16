package com.textimage.processor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.example.umaconsp.processor.ProcessParams

object ImageProcessor {

    fun process(src: Bitmap, params: ProcessParams): Bitmap {
        var image = src

        // 1. Deskew (выравнивание перекоса)
        if (params.deskew) {
            image = deskew(image)
        }

        // 2. Изменение размера
        if (params.targetWidth > 0 && params.targetHeight > 0) {
            image = resize(image, params.targetWidth, params.targetHeight, params.keepAspect)
        }

        // 3. Яркость и контраст
        if (params.brightness != 0f || params.contrast != 1f) {
            image = adjustBrightnessContrast(image, params.brightness, params.contrast)
        }

        // 4. Бинаризация (чёрно-белое по порогу)
        if (params.threshold != 128) { // 128 — нейтральное значение
            image = binarize(image, params.threshold)
        }

        return image
    }

    fun resize(src: Bitmap, targetWidth: Int, targetHeight: Int, keepAspect: Boolean): Bitmap {
        var w = targetWidth
        var h = targetHeight

        if (keepAspect) {
            val aspect = src.width.toDouble() / src.height.toDouble()
            if (w.toDouble() / h > aspect) {
                w = (h * aspect).toInt()
            } else {
                h = (w / aspect).toInt()
            }
        }

        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun adjustBrightnessContrast(src: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, src.config)
        val canvas = Canvas(result)
        val paint = Paint()
        
        val colorMatrix = ColorMatrix()
        val scale = contrast
        val translate = brightness * 255
        
        colorMatrix.set(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        
        return result
    }

    fun binarize(src: Bitmap, threshold: Int): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                val pixel = src.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                val newColor = if (luminance < threshold) Color.BLACK else Color.WHITE
                result.setPixel(x, y, newColor)
            }
        }
        
        return result
    }

    fun deskew(src: Bitmap): Bitmap {
        val deskewer = ImageDeskew(src)
        val angle = deskewer.skewAngle
        if (kotlin.math.abs(angle) < 0.1) return src

        // Поворачиваем изображение на вычисленный угол
        val matrix = Matrix()
        matrix.postRotate(angle.toFloat(), src.width / 2f, src.height / 2f)

        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }
}
