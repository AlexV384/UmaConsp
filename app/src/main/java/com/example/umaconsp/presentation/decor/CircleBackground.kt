package com.example.umaconsp.presentation.decor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

private data class Blob(
    val xFraction: Float,
    val yFraction: Float,
    val radius: Float,
    val color: Color
)

@Composable
fun CircleBackground() {
    val blobs = remember {
        val random = Random(42)
        listOf(
            Color(0xFF5B5BE6).copy(alpha = 0.08f),
            Color(0xFF0F766E).copy(alpha = 0.06f),
            Color(0xFF7C3AED).copy(alpha = 0.05f),
            Color(0xFF94A3B8).copy(alpha = 0.04f)
        ).flatMap { color ->
            List(6) {
                Blob(
                    xFraction = random.nextFloat(),
                    yFraction = random.nextFloat(),
                    radius = random.nextFloat() * 52f + 22f,
                    color = color
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawBlobs(blobs)
        }
    }
}

private fun DrawScope.drawBlobs(blobs: List<Blob>) {
    blobs.forEach { blob ->
        drawCircle(
            color = blob.color,
            radius = blob.radius,
            center = Offset(size.width * blob.xFraction, size.height * blob.yFraction)
        )
    }
}
