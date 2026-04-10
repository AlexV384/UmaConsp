package com.example.umaconsp.processor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

data class ProcessParams(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val threshold: Int = 128,
    val deskew: Boolean = false,
    val targetWidth: Int = 0,
    val targetHeight: Int = 0,
    val keepAspect: Boolean = true
)

@Composable
fun ControlPanel(
    onApply: (ProcessParams) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var brightness by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(1f) }
    var threshold by remember { mutableStateOf(128) }
    var deskew by remember { mutableStateOf(false) }
    var targetWidth by remember { mutableStateOf("") }
    var targetHeight by remember { mutableStateOf("") }
    var keepAspect by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Обработка изображения",
            style = MaterialTheme.typography.titleMedium
        )

        // Brightness
        Text("Яркость: ${String.format("%.2f", brightness)}")
        Slider(
            value = brightness,
            onValueChange = { brightness = it },
            valueRange = -1f..1f,
            steps = 200
        )

        // Contrast
        Text("Контраст: ${String.format("%.2f", contrast)}")
        Slider(
            value = contrast,
            onValueChange = { contrast = it },
            valueRange = 0f..2f,
            steps = 200
        )

        // Threshold
        Text("Порог ч/б: $threshold")
        Slider(
            value = threshold.toFloat(),
            onValueChange = { threshold = it.toInt() },
            valueRange = 0f..255f,
            steps = 255
        )

        // Deskew
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = deskew,
                onCheckedChange = { deskew = it }
            )
            Text("Выровнять перекос (deskew)")
        }

        // Size controls
        Text("Изменение размера:")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = targetWidth,
                onValueChange = { targetWidth = it },
                label = { Text("Ширина") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Text("×")
            OutlinedTextField(
                value = targetHeight,
                onValueChange = { targetHeight = it },
                label = { Text("Высота") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = keepAspect,
                onCheckedChange = { keepAspect = it }
            )
            Text("Сохранять пропорции")
        }

        // Zoom controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onZoomIn) {
                Text("Zoom +")
            }
            Button(onClick = onZoomOut) {
                Text("Zoom -")
            }
            Button(onClick = { 
                brightness = 0f
                contrast = 1f
                threshold = 128
                deskew = false
                targetWidth = ""
                targetHeight = ""
                onReset()
            }) {
                Text("Сбросить")
            }
        }

        Button(
            onClick = {
                onApply(
                    ProcessParams(
                        brightness = brightness,
                        contrast = contrast,
                        threshold = threshold,
                        deskew = deskew,
                        targetWidth = targetWidth.toIntOrNull() ?: 0,
                        targetHeight = targetHeight.toIntOrNull() ?: 0,
                        keepAspect = keepAspect
                    )
                )
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Применить")
        }
    }
}