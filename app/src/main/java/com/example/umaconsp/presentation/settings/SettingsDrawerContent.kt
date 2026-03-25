package com.example.umaconsp.presentation.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.umaconsp.R

/**
 * Компонент содержимого бокового меню (Drawer) с настройками.
 * Позволяет пользователю переключать тёмную тему и изменять IP-адрес сервера.
 *
 * @param isDarkTheme Текущее состояние тёмной темы (true — включена)
 * @param onThemeChange Callback при изменении переключателя темы
 * @param serverIp Текущий IP-адрес сервера (строковое представление)
 * @param onIpChange Callback при изменении текста в поле ввода IP
 */
@Composable
fun SettingsDrawerContent(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    serverIp: String,
    onIpChange: (String) -> Unit,
    onModelPicked: suspend (uri: Uri) -> Unit
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
            onPicked = onModelPicked,
        )
    }
}