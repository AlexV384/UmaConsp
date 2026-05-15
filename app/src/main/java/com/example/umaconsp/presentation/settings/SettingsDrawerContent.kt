package com.example.umaconsp.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.umaconsp.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawerContent(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    modelMode: String,
    onModelModeChange: (String) -> Unit,
    onModelDirPicked: suspend (uri: Uri) -> Unit,
    modelList: List<String>,
    onLocalModelPicked: suspend (name: String) -> Unit,
    exportFolderUri: String?,
    onExportFolderPicked: suspend (uri: Uri) -> Unit
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Выбор режима обработки
        Text(
            text = "Режим распознавания",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        val modes = listOf("google_mlkit", "local_model")
        val modeLabels = mapOf(
            "google_mlkit" to "Tesseract OCR",
            "local_model" to "Локальная модель (llama)"
        )
        var expanded by remember { mutableStateOf(false) }
        var selectedMode by remember { mutableStateOf(modelMode) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = modeLabels[selectedMode] ?: selectedMode,
                onValueChange = {},
                readOnly = true,
                label = { Text("Режим") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                modes.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(modeLabels[mode] ?: mode) },
                        onClick = {
                            selectedMode = mode
                            onModelModeChange(mode)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Дополнительные настройки для локальной модели
        if (selectedMode == "local_model") {
            ImportModel(
                modifier = Modifier.fillMaxWidth(),
                onPicked = onModelDirPicked
            )
            Spacer(modifier = Modifier.height(8.dp))
            var modelExpanded by remember { mutableStateOf(false) }
            var selectedModel by remember { mutableStateOf("(unload)") }
            val dropdownItems = listOf("(unload)") + modelList

            ExposedDropdownMenuBox(
                expanded = modelExpanded,
                onExpandedChange = { modelExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.select_local)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = modelExpanded,
                    onDismissRequest = { modelExpanded = false }
                ) {
                    dropdownItems.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item) },
                            onClick = {
                                selectedModel = item
                                modelExpanded = false
                                scope.launch {
                                    onLocalModelPicked(item)
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Экспорт конспектов",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val exportPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                scope.launch { onExportFolderPicked(uri) }
            }
        }

        Button(
            onClick = { exportPicker.launch(Uri.EMPTY) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (exportFolderUri != null) "Изменить папку для экспорта" else "Выбрать папку для экспорта")
        }

        if (exportFolderUri != null) {
            Text(
                text = "Папка выбрана: $exportFolderUri",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.settings_dark_theme),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = isDarkTheme,
                onCheckedChange = onThemeChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}