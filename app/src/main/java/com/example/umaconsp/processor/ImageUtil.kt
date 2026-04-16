package com.textimage.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.Reader

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
    fun encode(image: Bitmap): ByteArray {
        val output = ByteArrayOutputStream(1024 * 1024 * 100)
        image.compress(Bitmap.CompressFormat.JPEG, 80, output)
        return output.toByteArray()
    }
    fun decode(context: Context, uri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    }
}