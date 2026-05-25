package com.example.umaconsp.presentation.decor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

private data class ScatterItem(
    val xFraction: Float,
    val yFraction: Float,
    val sizeFactor: Float,
    val rotation: Float,
    val alpha: Float,
    val color: Color
)

@Composable
fun CleatScatterBackgroundCanvas() {
    val bitmapPainters = listOf(
        painterResource(R.drawable.cleat_filled),
        painterResource(R.drawable.cleat_hollow)
    )

    val items = remember {
        val random = Random(17)
        buildList {
            repeat(18) {
                add(
                    ScatterItem(
                        xFraction = random.nextFloat(),
                        yFraction = random.nextFloat(),
                        sizeFactor = random.nextFloat() * 0.09f + 0.04f,
                        rotation = random.nextFloat() * 160f - 80f,
                        alpha = random.nextFloat() * 0.14f + 0.04f,
                        color = when (it % 4) {
                            0 -> Color(0xFF5B5BE6)
                            1 -> Color(0xFF0F766E)
                            2 -> Color(0xFF7C3AED)
                            else -> Color(0xFF94A3B8)
                        }
                    )
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawScatteredCleats(bitmapPainters, items)
        }
    }
}

private fun DrawScope.drawScatteredCleats(
    painters: List<Painter>,
    items: List<ScatterItem>
) {
    items.forEachIndexed { index, item ->
        val painter = painters[index % painters.size]
        val tint = ColorFilter.tint(item.color.copy(alpha = item.alpha))
        with(painter) {
            translate(
                left = size.width * item.xFraction,
                top = size.height * item.yFraction
            ) {
                rotate(degrees = item.rotation, pivot = Offset.Zero) {
                    draw(
                        size = painter.intrinsicSize * item.sizeFactor,
                        alpha = 1f,
                        colorFilter = tint
                    )
                }
            }
        }
    }
}
