package com.example.umaconsp.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.umaconsp.R
import kotlinx.coroutines.launch

private const val MODE_TESSERACT = "tesseract"
private const val MODE_LOCAL = "local_model"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawerContent(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    modelMode: String,
    onModelModeChange: (String) -> Unit,
    ocrLanguage: String,
    onOcrLanguageChange: (String) -> Unit,
    onModelDirPicked: suspend (uri: Uri) -> Unit,
    modelList: List<String>,
    onLocalModelPicked: suspend (name: String) -> Unit,
    exportFolderUri: String?,
    onExportFolderPicked: suspend (uri: Uri) -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedMode by remember(modelMode) { mutableStateOf(modelMode) }
    var selectedLanguage by remember(ocrLanguage) { mutableStateOf(ocrLanguage) }
    var selectedModel by remember { mutableStateOf("(unload)") }

    val modeTitle = when (selectedMode) {
        MODE_TESSERACT -> "Tesseract OCR"
        MODE_LOCAL -> "Локальная модель"
        else -> selectedMode
    }

    val languageTitle = when (selectedLanguage) {
        "rus" -> "Русский"
        "eng" -> "Английский"
        "rus+eng" -> "Русский + английский"
        else -> selectedLanguage
    }

    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { scope.launch { onExportFolderPicked(it) } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        SectionCard(
            title = "Оформление",
            icon = Icons.Default.Palette
        ) {
            SettingSwitchRow(
                title = stringResource(R.string.settings_dark_theme),
                subtitle = "Более комфортно для чтения в темноте.",
                checked = isDarkTheme,
                onCheckedChange = onThemeChange
            )
        }

        SectionCard(
            title = "Экспорт конспектов",
            icon = Icons.Default.FolderOpen
        ) {
            Text(
                text = if (exportFolderUri.isNullOrBlank()) {
                    "Папка для сохранения не выбрана."
                } else {
                    "Текущая папка:\n$exportFolderUri"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { exportPicker.launch(Uri.EMPTY) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (exportFolderUri.isNullOrBlank()) "Выбрать папку" else "Изменить папку")
            }
        }

        SectionCard(
            title = "Режим распознавания",
            icon = Icons.Default.Science
        ) {
            ModeOption(
                title = "Tesseract OCR",
                subtitle = "Быстро, офлайн, стабильно.",
                selected = selectedMode == MODE_TESSERACT,
                onClick = {
                    selectedMode = MODE_TESSERACT
                    onModelModeChange(MODE_TESSERACT)
                }
            )

            ModeOption(
                title = "Локальная модель",
                subtitle = "Для более продвинутой обработки текста.",
                selected = selectedMode == MODE_LOCAL,
                onClick = {
                    selectedMode = MODE_LOCAL
                    onModelModeChange(MODE_LOCAL)
                }
            )

            if (selectedMode == MODE_TESSERACT) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Текущий язык: $languageTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (selectedMode == MODE_TESSERACT) {
            SectionCard(
                title = "Язык OCR",
                icon = Icons.Default.Translate
            ) {
                LanguageOption(
                    title = "Русский",
                    subtitle = "Лучше всего для кириллицы.",
                    selected = selectedLanguage == "rus",
                    onClick = {
                        selectedLanguage = "rus"
                        onOcrLanguageChange("rus")
                    }
                )
                LanguageOption(
                    title = "Английский",
                    subtitle = "Для латиницы и англоязычных документов.",
                    selected = selectedLanguage == "eng",
                    onClick = {
                        selectedLanguage = "eng"
                        onOcrLanguageChange("eng")
                    }
                )
                LanguageOption(
                    title = "Русский + английский",
                    subtitle = "Универсально, но иногда чуть менее точно.",
                    selected = selectedLanguage == "rus+eng",
                    onClick = {
                        selectedLanguage = "rus+eng"
                        onOcrLanguageChange("rus+eng")
                    }
                )
            }
        }

        if (selectedMode == MODE_LOCAL) {
            SectionCard(
                title = "Локальная модель",
                icon = Icons.Default.ModelTraining
            ) {
                Text(
                    text = "Импортируйте модель .gguf и выберите её из списка.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                ImportModel(
                    modifier = Modifier.fillMaxWidth(),
                    onPicked = onModelDirPicked
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Выбранная модель: $selectedModel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                modelList.ifEmpty {
                    listOf("(unload)")
                }.plus(emptyList()).forEach { _ -> }

                val items = listOf("(unload)") + modelList
                items.forEach { item ->
                    ModelChoice(
                        title = item,
                        selected = selectedModel == item,
                        onClick = {
                            selectedModel = item
                            scope.launch { onLocalModelPicked(item) }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ModeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModelChoice(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(title)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(title)
        }
    }
}
