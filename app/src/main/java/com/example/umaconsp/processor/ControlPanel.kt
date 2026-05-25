package com.example.umaconsp.processor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Обработка изображения",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingSlider(
                    title = "Яркость",
                    valueLabel = String.format("%.2f", brightness),
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = -1f..1f
                )

                SettingSlider(
                    title = "Контраст",
                    valueLabel = String.format("%.2f", contrast),
                    value = contrast,
                    onValueChange = { contrast = it },
                    valueRange = 0f..2f
                )

                SettingSlider(
                    title = "Порог ч/б",
                    valueLabel = threshold.toString(),
                    value = threshold.toFloat(),
                    onValueChange = { threshold = it.toInt() },
                    valueRange = 0f..255f
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = deskew,
                        onCheckedChange = { deskew = it }
                    )
                    Text("Выровнять перекос (deskew)")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Изменение размера",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = targetWidth,
                        onValueChange = { targetWidth = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Ширина") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Text("×")
                    OutlinedTextField(
                        value = targetHeight,
                        onValueChange = { targetHeight = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Высота") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = keepAspect,
                        onCheckedChange = { keepAspect = it }
                    )
                    Text("Сохранять пропорции")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Масштаб",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onZoomIn) { Text("Zoom +") }
                    Button(onClick = onZoomOut) { Text("Zoom -") }
                    TextButton(onClick = {
                        brightness = 0f
                        contrast = 1f
                        threshold = 128
                        deskew = false
                        targetWidth = ""
                        targetHeight = ""
                        keepAspect = true
                        onReset()
                    }) {
                        Text("Сбросить")
                    }
                }
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Применить")
        }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = valueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = 100
        )
    }
}
