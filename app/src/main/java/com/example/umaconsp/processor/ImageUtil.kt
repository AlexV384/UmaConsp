package com.textimage.processor

import android.graphics.Bitmap

object ImageUtil {
    fun isBlack(image: Bitmap, x: Int, y: Int, luminanceCutOff: Int = 140): Boolean {
        if (x < 0 || y < 0 || x >= image.width || y >= image.height) return false
        return try {
            val pixel = image.getPixel(x, y)
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            val luminance = r * 0.299 + g * 0.587 + b * 0.114
            luminance < luminanceCutOff
        } catch (e: Exception) {
            false
        }
    }
}