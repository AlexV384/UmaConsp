package com.example.umaconsp.presentation.chat

import android.Manifest
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.umaconsp.R
import com.example.umaconsp.domain.model.Message
import com.example.umaconsp.utils.LocalChatListViewModel
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Экран чата. Отображает историю сообщений, поле ввода и кнопки.
 * Получает список сообщений из ChatListViewModel через Flow и отображает их в LazyColumn.
 * Управляет отправкой сообщений через ChatViewModel.
 *
 * @param chatId Идентификатор текущего чата
 * @param onBack Функция возврата назад (закрыть экран)
 * @param onOpenSettings Функция открытия бокового меню настроек
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    // Получаем общую ViewModel списка чатов через CompositionLocal
    val chatListViewModel = LocalChatListViewModel.current
    // Создаём ViewModel для текущего чата, привязанную к chatId
    val viewModel: ChatViewModel = remember(chatId) {
        ChatViewModel(chatId, chatListViewModel)
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Подписка на список сообщений этого чата через Flow
    val messagesFlow = remember(chatId) {
        chatListViewModel.getMessagesFlow(chatId)
    }
    val messages by messagesFlow.collectAsState(initial = emptyList())
    // Статус подключения и генерации
    val status by viewModel.status.collectAsState()
    // Выбранные изображения перед отправкой
    val selectedImageUris by viewModel.selectedImageUris.collectAsState()
    // Состояние прокрутки списка
    val listState = rememberLazyListState()

    // Заголовок чата, загружаем при старте и обновляем при переименовании
    var chatTitle by remember { mutableStateOf("Чат") }
    LaunchedEffect(chatId) {
        chatTitle = chatListViewModel.getChat(chatId)?.title ?: "Чат"
    }

    // Автоматическая прокрутка вниз при появлении новых сообщений
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    var showRenameDialog by remember { mutableStateOf(false) }

    // Лаунчер для сохранения чата в файл (создание документа)
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it).use { outputStream ->
                    val chatText = buildChatText(messages)
                    outputStream?.write(chatText.encodeToByteArray())
                }
                Toast.makeText(context, R.string.chat_saved, Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Toast.makeText(context, "${context.getString(R.string.save_error)}: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } ?: Toast.makeText(context, R.string.cannot_get_uri, Toast.LENGTH_SHORT).show()
    }


    // Лаунчер для выбора нескольких изображений из галереи
    val multipleImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { viewModel.addSelectedImage(it) }
        }
    }


    // Лаунчер для запроса разрешения на чтение медиа (Android 13+)
    val permissionGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            multipleImagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            Toast.makeText(context, R.string.permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    val imagePicker = {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ требует разрешения READ_MEDIA_IMAGES
                permissionGalleryLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }

            else -> {
                // Для старых версий разрешение READ_EXTERNAL_STORAGE уже должно быть запрошено
                multipleImagePickerLauncher.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            }
        }
    }

    val camera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { isSuccess ->
        if (isSuccess) {
            Log.d("PhotoTaker", "The photo is taken!")
        }
    }

    val cameraLauncher = {
        viewModel.createPictureUri(context)
        camera.launch(viewModel.takenPhotoUri.value)
        viewModel.addSelectedImage(viewModel.takenPhotoUri.value)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                title = {
                    Text(
                        text = chatTitle,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    // Кнопка сохранения чата
                    IconButton(onClick = {
                        saveLauncher.launch("chat_${chatTitle}_${System.currentTimeMillis()}.txt")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.save_chat),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    // Кнопка настроек
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    // Кнопка переименования чата
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.rename_chat),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(paddingValues)
        ) {
            // Карточка статуса (соединение, генерация, ошибка)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (status.contains("ошибка", ignoreCase = true)) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                )
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.contains("ошибка", ignoreCase = true)) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }

            // Список сообщений (обратный порядок, чтобы новые сообщения были внизу)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                state = listState,
                reverseLayout = true
            ) {
                items(
                    items = messages.reversed(),
                    key = { it.id }
                ) { message ->
                    MessageItem(message)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Превью выбранных изображений перед отправкой
            if (selectedImageUris.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = selectedImageUris,
                        key = { it.toString() }
                    ) { uri ->
                        SelectedImagePreview(
                            imageUri = uri,
                            onRemove = { viewModel.removeSelectedImage(uri) }
                        )
                    }
                }
            }


            // Поле ввода и кнопки
            InputSection(
                onSendMessage = { text ->
                    viewModel.sendMessage(text)
                },
                onAddImage = imagePicker,
                onTakePhoto = cameraLauncher
            )
        }
    }

    // Диалог переименования чата
    RenameChatDialog(
        show = showRenameDialog,
        currentTitle = chatTitle,
        onDismiss = { showRenameDialog = false },
        onRename = { newName ->
            scope.launch {
                chatListViewModel.renameChat(chatId, newName)
                chatTitle = newName
            }
            showRenameDialog = false
        }
    )
}

/**
 * Формирует текстовое представление чата для экспорта в файл.
 * Каждое сообщение начинается с "Пользователь:" или "Ассистент:".
 * Если есть изображения, добавляется строка с их списком.
 */
private fun buildChatText(messages: List<Message>): String {
    return messages.joinToString("\n\n") { msg ->
        val prefix = if (msg.isUser) "Пользователь:" else "Ассистент:"
        val text = msg.text
        val images = if (msg.imageUrls.isNotEmpty()) {
            "\n[Изображения: ${msg.imageUrls.joinToString()}]"
        } else ""
        "$prefix $text$images"
    }
}

/**
 * Отображает одно сообщение, выбирая стиль в зависимости от того, кто автор.
 */
@Composable
fun MessageItem(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (message.isUser) {
            UserMessageBubble(message)
        } else {
            AiMessageBubble(message)
        }
    }
}

/**
 * Пузырёк сообщения пользователя (голубой фон, справа).
 * Содержит текст, блок размышлений (если есть) и изображения.
 */
@Composable
fun UserMessageBubble(message: Message) {
    var thinkingExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .wrapContentWidth()
            .padding(start = 60.dp, end = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Текст сообщения
            Text(
                text = message.text,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium
            )

            // Блок размышлений (thinking) – отображается, если есть
            if (!message.thinking.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    // Заголовок с иконкой раскрытия/сворачивания
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { thinkingExpanded = !thinkingExpanded }
                    ) {
                        Text(
                            text = stringResource(R.string.thinking_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (thinkingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Содержимое размышлений с анимацией
                    AnimatedVisibility(visible = thinkingExpanded) {
                        Text(
                            text = message.thinking,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Изображения (отображаем до 3 штук, остальные с индикатором)
            if (message.imageUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    message.imageUrls.take(3).forEach { url ->
                        Image(
                            painter = rememberAsyncImagePainter(model = url),
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (message.imageUrls.size > 3) {
                        Text("+${message.imageUrls.size - 3}", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

/**
 * Пузырёк сообщения ассистента (серый фон, слева).
 */
@Composable
fun AiMessageBubble(message: Message) {
    Card(
        modifier = Modifier
            .wrapContentWidth()
            .padding(start = 16.dp, end = 60.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = message.text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Превью выбранного изображения с кнопкой удаления.
 */
@Composable
fun SelectedImagePreview(imageUri: Uri, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data(imageUri)
                    .crossfade(true)
                    .build()
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Кнопка удаления в правом верхнем углу
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.button_remove),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Панель ввода сообщения: кнопка выбора изображения, текстовое поле и кнопка отправки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputSection(onSendMessage: (String) -> Unit, onAddImage: () -> Unit, onTakePhoto: () -> Unit) {
    var textFieldValue by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding() // поднимается над клавиатурой
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка добавления изображения
            IconButton(
                onClick = { menuExpanded = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.button_add_images),
                    tint = MaterialTheme.colorScheme.primary
                )

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    TextButton(
                        onClick = onAddImage
                    ) {
                        Text( text = "Выбрать из гелереи" )
                    }
                    TextButton(
                        onClick = onTakePhoto
                    ) {
                        Text(text = "Сделать фото")
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Текстовое поле
            TextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.hint_input_message)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(20.dp)
            )

            // Анимированный отступ и кнопка отправки
            AnimatedVisibility(visible = !textFieldValue.isEmpty()) {
                Row {
                    Spacer(modifier = Modifier.width(8.dp))

                    // Кнопка отправки (видна только если есть текст)
                    FilledIconButton(
                        onClick = {
                            if (textFieldValue.isNotBlank()) {
                                onSendMessage(textFieldValue)
                                textFieldValue = ""
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = stringResource(R.string.button_send),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Диалог переименования чата.
 */
@Composable
fun RenameChatDialog(
    show: Boolean,
    currentTitle: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newTitle by remember { mutableStateOf(currentTitle) }

    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.rename_chat)) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text(stringResource(R.string.chat_title)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onRename(newTitle)
                        } else {
                            onDismiss()
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}