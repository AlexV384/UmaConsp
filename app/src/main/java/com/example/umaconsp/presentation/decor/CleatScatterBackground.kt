package com.example.umaconsp.presentation.decor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.example.umaconsp.R
import kotlin.random.Random

val cleatFilled = R.drawable.cleat_filled
val cleatHollow = R.drawable.cleat_hollow

@Composable
public fun CleatScatterBackgroundCanvas() {
    val bitmapPainters = listOf(
        painterResource(cleatFilled),
        painterResource(cleatHollow)
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawScatteredCleats(bitmapPainters)
        }
    }
}
val colors = listOf(
    Color(0xFF2196F3).copy(alpha = 0.15f), // Blue
    Color(0xFFFF9800).copy(alpha = 0.2f), // Orange
    Color(0xFF4CAF50).copy(alpha = 0.1f), // Green
    Color.Gray.copy(alpha = 0.05f)
)
private fun DrawScope.drawScatteredCleats(painters: List<Painter>) {
    repeat(10) { index ->
//        val color = colors[index % colors.size]
        val radius = Random.nextFloat() * 2.0f - 1.0f // Random radius between 0.05 - 0.1
        val x = Random.nextFloat() * size.width
        val y = Random.nextFloat() * size.height

        val painter = painters[index % painters.size]
        val tint = ColorFilter.tint(colors[index % colors.size])
        with(painter) {
            translate(left = x, top = y) {
                rotate(
                    degrees = radius * 120f,
                    pivot = Offset(0.0f, 0.0f)
                ){
                    draw(size = painter.intrinsicSize * 0.4f, alpha = 0.2f, colorFilter = tint)
                }
            }
        }
    }

    repeat(30) { index ->
//        val color = colors[index % colors.size]
        val radius = Random.nextFloat() * 2.0f - 1.0f // Random radius between 0.05 - 0.1
        val x = Random.nextFloat() * size.width
        val y = Random.nextFloat() * size.height

        val painter = painters[index % painters.size]
        val tint = ColorFilter.tint(colors[index % colors.size])
        with(painter) {
            translate(left = x, top = y) {
                rotate(
                    degrees = radius * 30f,
                    pivot = Offset(0.0f, 0.0f)
                ){
                    draw(size = painter.intrinsicSize * 0.06f, alpha = 1.0f, colorFilter = tint)
                }
            }
        }
    }

}
