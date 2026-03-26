package com.example.umaconsp.presentation.settings

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.umaconsp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Компонент содержимого бокового меню (Drawer) с настройками.
 * Позволяет пользователю переключать тёмную тему и изменять IP-адрес сервера.
 *
 * @param isDarkTheme Текущее состояние тёмной темы (true — включена)
 * @param onThemeChange Callback при изменении переключателя темы
 * @param serverIp Текущий IP-адрес сервера (строковое представление)
 * @param onIpChange Callback при изменении текста в поле ввода IP
 * @param onModelDirPicked Callback при выборе модели
 * @param modelList Список доступных моделей
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawerContent(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    serverIp: String,
    onIpChange: (String) -> Unit,
    onModelDirPicked: suspend (uri: Uri) -> Unit,
    modelList: List<String>,
    onLocalModelPicked: suspend (name: String) -> Unit = {}
) {
    // Вертикальный контейнер, занимающий весь экран с фоном
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface) // цвет фона текущей темы
            .padding(16.dp)                               // внутренние отступы
    ) {
        // Заголовок раздела настроек
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Строка: текст "Тёмная тема" и переключатель Switch
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
                checked = isDarkTheme,                 // текущее состояние
                onCheckedChange = onThemeChange,       // лямбда при изменении
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        // Отступ между элементами
        Spacer(modifier = Modifier.height(16.dp))

        // Поле ввода IP-адреса сервера
        OutlinedTextField(
            value = serverIp,
            onValueChange = onIpChange,      // обновление при каждом вводе
            label = { Text(stringResource(R.string.settings_server_ip)) },
            singleLine = true,               // многострочный ввод не нужен
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        ImportModel(
            modifier = Modifier.fillMaxWidth(),
            onPicked = onModelDirPicked,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Dropdown selection для моделей
        var expanded by remember { mutableStateOf(false) }
        var selectedItem by remember { mutableStateOf("(unload)") }
        var isModelLoading by remember { mutableStateOf(false) }

        // Создаем список с (unload) всегда в начале
        val dropdownItems = listOf("(unload)") + modelList

        // Обновляем выбранный элемент при изменении списка моделей
        LaunchedEffect(modelList) {
            if (selectedItem !in dropdownItems) {
                selectedItem = "(unload)"
            }
        }
        LaunchedEffect(selectedItem) {
            // This block runs every time selectedModel changes
            // It's non-blocking and runs in a coroutine scope
            isModelLoading = true
            try {
                withContext(Dispatchers.IO) {
                    if (selectedItem == "(unload)") {
                        // Handle unload
                    } else {
                        // Handle loading the selected model
                        onLocalModelPicked(selectedItem)
                    }
                }
            } finally {
                isModelLoading = false
            }
        }

        Row (modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically){
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedItem,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.select_local)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    enabled = !isModelLoading
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    dropdownItems.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item) },
                            onClick = {
                                selectedItem = item
                                expanded = false
                            }
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = isModelLoading
            ) {
                Row {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(38.dp)
                            .padding(2.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
