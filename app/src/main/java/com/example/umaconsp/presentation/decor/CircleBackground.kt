package com.example.umaconsp.presentation.decor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

@Composable
public fun CircleBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawScatteredCircles()
        }
    }
}

private fun DrawScope.drawScatteredCircles() {
    val colors = listOf(
        Color(0xFF2196F3).copy(alpha = 0.1f), // Blue
        Color(0xFFFF9800).copy(alpha = 0.08f), // Orange
        Color(0xFF4CAF50).copy(alpha = 0.06f), // Green
        Color.Gray.copy(alpha = 0.05f)
    )
    
    repeat(20) { index ->
        val color = colors[index % colors.size]
        val radius = Random.nextFloat() * 40f + 10f // Random radius between 10-50
        val x = Random.nextFloat() * size.width
        val y = Random.nextFloat() * size.height
        
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(x, y)
        )
    }
}