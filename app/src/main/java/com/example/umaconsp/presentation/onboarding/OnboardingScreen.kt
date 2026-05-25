package com.example.umaconsp.presentation.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.umaconsp.presentation.settings.ImportModel
import kotlinx.coroutines.launch

private const val MODE_TESSERACT = "tesseract"
private const val MODE_LOCAL = "local_model"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit,
    onExportFolderPicked: suspend (Uri) -> Unit,
    onExportFolderSet: (String) -> Unit,
    onModelModeChange: (String) -> Unit,
    onOcrLanguageChange: (String) -> Unit,
    onModelDirPicked: suspend (Uri) -> Unit,
    modelList: List<String>,
    onLocalModelPicked: suspend (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var step by remember { mutableIntStateOf(0) }
    var selectedMode by remember { mutableStateOf(MODE_TESSERACT) }
    var selectedLanguage by remember { mutableStateOf("rus") }
    var selectedModel by remember { mutableStateOf("(unload)") }
    var exportFolderUriString by remember { mutableStateOf<String?>(null) }

    val totalSteps = 4

    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            exportFolderUriString = it.toString()
            scope.launch {
                onExportFolderPicked(it)
                onExportFolderSet(it.toString())
                step = 2
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Настройка Umaconsp",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (step + 1).toFloat() / totalSteps.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Шаг ${step + 1} из $totalSteps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )

            Spacer(Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (step) {
                        0 -> IntroStep(onNext = { step = 1 })

                        1 -> FolderStep(
                            exportFolderUriString = exportFolderUriString,
                            onPickFolder = { exportPicker.launch(Uri.EMPTY) },
                            onSkip = { step = 2 }
                        )

                        2 -> ModeStep(
                            selectedMode = selectedMode,
                            onModeSelected = { selectedMode = it },
                            onNext = {
                                onModelModeChange(selectedMode)
                                step = 3
                            }
                        )

                        3 -> {
                            if (selectedMode == MODE_TESSERACT) {
                                LanguageStep(
                                    selectedLanguage = selectedLanguage,
                                    onLanguageChange = { selectedLanguage = it },
                                    onFinish = {
                                        onOcrLanguageChange(selectedLanguage)
                                        onCompleted()
                                    }
                                )
                            } else {
                                LocalModelStep(
                                    selectedModel = selectedModel,
                                    modelList = modelList,
                                    onPickModelFolder = onModelDirPicked,
                                    onSelectModel = { model ->
                                        selectedModel = model
                                        scope.launch { onLocalModelPicked(model) }
                                    },
                                    onFinish = { onCompleted() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroStep(
    onNext: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(Modifier.height(8.dp))

        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(58.dp)
        )

        Text(
            text = "Добро пожаловать в Umaconsp!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Приложение распознаёт текст с фото и превращает его в удобный Markdown-конспект.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Что мы настроим",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "1. Папку для сохранения\n2. Режим распознавания\n3. Язык или локальную модель",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Начать настройку")
        }
    }
}

@Composable
private fun FolderStep(
    exportFolderUriString: String?,
    onPickFolder: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Шаг 1. Папка для конспектов",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Сюда будут сохраняться готовые файлы. Это можно будет изменить позже.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Рекомендация",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Выберите отдельную папку, чтобы потом было проще находить все конспекты.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (exportFolderUriString != null) {
            Text(
                text = "Папка уже выбрана",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Button(
            onClick = onPickFolder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Выбрать папку")
        }

        TextButton(onClick = onSkip) {
            Text("Пропустить на время")
        }
    }
}

@Composable
private fun ModeStep(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Шаг 2. Выберите режим распознавания",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Выбор сделан в виде радиокнопок — так сразу видно, что активен только один вариант.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )

        ModeOptionCard(
            title = "Tesseract OCR",
            subtitle = "Полностью офлайн, быстрый и простой вариант для обычного распознавания текста.",
            selected = selectedMode == MODE_TESSERACT,
            onClick = { onModeSelected(MODE_TESSERACT) }
        )

        ModeOptionCard(
            title = "Локальная модель",
            subtitle = "Более продвинутый режим. Нужна модель .gguf, зато всё работает офлайн.",
            selected = selectedMode == MODE_LOCAL,
            onClick = { onModeSelected(MODE_LOCAL) }
        )

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Продолжить")
        }
    }
}

@Composable
private fun LanguageStep(
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    onFinish: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Шаг 3. Язык распознавания",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Для русского текста обычно лучше выбрать только русский язык.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )

        LanguageOption(
            title = "Русский",
            subtitle = "Лучше всего для кириллицы.",
            selected = selectedLanguage == "rus",
            onClick = { onLanguageChange("rus") }
        )

        LanguageOption(
            title = "Английский",
            subtitle = "Для латиницы и англоязычных документов.",
            selected = selectedLanguage == "eng",
            onClick = { onLanguageChange("eng") }
        )

        LanguageOption(
            title = "Русский + английский",
            subtitle = "Универсально, но иногда чуть менее точно.",
            selected = selectedLanguage == "rus+eng",
            onClick = { onLanguageChange("rus+eng") }
        )

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Завершить настройку")
        }
    }
}

@Composable
private fun LocalModelStep(
    selectedModel: String,
    modelList: List<String>,
    onPickModelFolder: suspend (Uri) -> Unit,
    onSelectModel: (String) -> Unit,
    onFinish: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val items = remember(modelList) { listOf("(unload)") + modelList }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Шаг 3. Локальная модель",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Что нужно сделать",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Сначала добавьте файл модели (.gguf), затем выберите её из списка.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        ImportModel(
            onPicked = { uri ->
                scope.launch { onPickModelFolder(uri) }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Доступные модели",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )

        items.forEach { item ->
            ModelChoiceButton(
                title = item,
                selected = selectedModel == item,
                onClick = { onSelectModel(item) }
            )
        }

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Продолжить")
        }
    }
}

@Composable
private fun ModeOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
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
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
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
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
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
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun ModelChoiceButton(
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
