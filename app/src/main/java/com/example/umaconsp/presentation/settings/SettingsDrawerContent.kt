package com.example.umaconsp.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.umaconsp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawerContent(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    serverIp: String,
    onIpChange: (String) -> Unit,
    onModelDirPicked: suspend (uri: Uri) -> Unit,
    modelList: List<String>,
    onLocalModelPicked: suspend (name: String) -> Unit,
    useLocalModel: Boolean,
    onUseLocalModelChange: (Boolean) -> Unit,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Серверная обработка",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = !useLocalModel,
                onCheckedChange = { isChecked ->
                    onUseLocalModelChange(!isChecked)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!useLocalModel) {
            OutlinedTextField(
                value = serverIp,
                onValueChange = onIpChange,
                label = { Text(stringResource(R.string.settings_server_ip)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            ImportModel(
                modifier = Modifier.fillMaxWidth(),
                onPicked = onModelDirPicked
            )

            Spacer(modifier = Modifier.height(16.dp))

            var expanded by remember { mutableStateOf(false) }
            var selectedItem by remember { mutableStateOf("(unload)") }
            var isModelLoading by remember { mutableStateOf(false) }

            val dropdownItems = listOf("(unload)") + modelList

            LaunchedEffect(modelList) {
                if (selectedItem !in dropdownItems) {
                    selectedItem = "(unload)"
                }
            }

            LaunchedEffect(selectedItem) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                AnimatedVisibility(visible = isModelLoading) {
                    Row {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(38.dp).padding(2.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
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