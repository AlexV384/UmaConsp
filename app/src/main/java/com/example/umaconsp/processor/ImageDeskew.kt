package com.textimage.processor

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ImageDeskew(private val image: Bitmap) {

    data class HoughLine(var count: Int = 0, var index: Int = 0, var alpha: Double = 0.0, var d: Double = 0.0)

    private val alphaStart = -20.0
    private val alphaStep = 0.2
    private val steps = 40 * 5
    private val dStep = 1.0

    private lateinit var sinA: DoubleArray
    private lateinit var cosA: DoubleArray
    private lateinit var hMatrix: IntArray
    private var dMin = 0.0
    private var dCount = 0

    val skewAngle: Double
        get() {
            calc()
            val lines = getTop(20)
            if (lines.size >= 20) {
                val sum = lines.take(19).sumOf { it.alpha }
                return sum / 19
            }
            return 0.0
        }

    private fun getTop(count: Int): List<HoughLine> {
        val result = Array(count) { HoughLine() }
        for (i in hMatrix.indices) {
            val value = hMatrix[i]
            if (value > result.last().count) {
                result[result.lastIndex].count = value
                result[result.lastIndex].index = i
                var j = result.size - 1
                while (j > 0 && result[j].count > result[j - 1].count) {
                    val tmp = result[j]
                    result[j] = result[j - 1]
                    result[j - 1] = tmp
                    j--
                }
            }
        }
        return result.map { line ->
            val dIndex = line.index / steps
            val alphaIndex = line.index - dIndex * steps
            HoughLine(
                count = line.count,
                index = line.index,
                alpha = getAlpha(alphaIndex),
                d = dIndex + dMin
            )
        }
    }

    private fun calc() {
        val hMin = image.height / 4
        val hMax = image.height * 3 / 4
        init()

        for (y in hMin until hMax) {
            for (x in 1 until image.width - 2) {
                if (ImageUtil.isBlack(image, x, y) && !ImageUtil.isBlack(image, x, y + 1)) {
                    calc(x, y)
                }
            }
        }
    }

    private fun calc(x: Int, y: Int) {
        for (alpha in 0 until steps - 1) {
            val d = y * cosA[alpha] - x * sinA[alpha]
            val dIndex = (d - dMin).toInt()
            val index = dIndex * steps + alpha
            if (index in hMatrix.indices) {
                hMatrix[index] += 1
            }
        }
    }

    private fun init() {
        sinA = DoubleArray(steps - 1)
        cosA = DoubleArray(steps - 1)
        for (i in 0 until steps - 1) {
            val angle = getAlpha(i) * PI / 180.0
            sinA[i] = sin(angle)
            cosA[i] = cos(angle)
        }
        dMin = -image.width.toDouble()
        dCount = (2.0 * (image.width + image.height) / dStep).toInt()
        hMatrix = IntArray(dCount * steps)
    }

    private fun getAlpha(index: Int): Double = alphaStart + index * alphaStep
}
