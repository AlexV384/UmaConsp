package com.example.umaconsp.presentation.chatlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.umaconsp.R
import com.example.umaconsp.presentation.chat.RenameChatDialog
import kotlinx.coroutines.launch

/**
 * Экран списка чатов.
 * Отображает все существующие чаты в виде сетки (2 колонки).
 * Позволяет создать новый чат, открыть существующий, переименовать (долгий тап) или удалить (кнопка корзины).
 *
 * @param viewModel ViewModel списка чатов (ChatListViewModel)
 * @param onChatClick Callback при клике на чат — открыть экран чата
 * @param onCreateChat Callback для создания нового чата
 * @param onOpenSettings Callback для открытия бокового меню настроек
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onChatClick: (String) -> Unit,
    onCreateChat: () -> Unit,
    onOpenSettings: () -> Unit
) {
    // Подписываемся на поток списка чатов из ViewModel
    val chats by viewModel.chats.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // Состояния для диалогов
    var showRenameDialog by remember { mutableStateOf(false) }
    var selectedChat by remember { mutableStateOf<ChatItem?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var chatToDelete by remember { mutableStateOf<ChatItem?>(null) }
    var newTitle by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            // Плавающая кнопка для создания нового чата
            FloatingActionButton(
                onClick = onCreateChat,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_chat))
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    // Кнопка открытия настроек в левой части AppBar
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (chats.isEmpty()) {
                // Если нет чатов, показываем сообщение
                Text(
                    text = stringResource(R.string.no_chats),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // Сетка чатов с двумя колонками
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = chats,
                        key = { it.id }   // уникальный ключ для каждой карточки
                    ) { chat ->
                        ChatCard(
                            chat = chat,
                            onClick = { onChatClick(chat.id) },
                            onDelete = {
                                chatToDelete = chat
                                showDeleteConfirmation = true
                            },
                            onRename = {
                                selectedChat = chat
                                newTitle = chat.title
                                showRenameDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Диалог переименования чата
    RenameChatDialog(
        show = showRenameDialog,
        currentTitle = selectedChat?.title ?: "",
        onDismiss = { showRenameDialog = false },
        onRename = { newName ->
            val idToRename = selectedChat?.id
            if (idToRename != null) {
                scope.launch {
                    viewModel.renameChat(idToRename, newName)
                }
            }
            showRenameDialog = false
            selectedChat = null
        }
    )

    // Диалог подтверждения удаления чата
    DeleteChatConfirmationDialog(
        show = showDeleteConfirmation,
        onDismiss = { showDeleteConfirmation = false },
        onConfirm = {
            val idToDelete = chatToDelete?.id
            if (idToDelete != null) {
                scope.launch {
                    viewModel.deleteChat(idToDelete)
                }
            }
            showDeleteConfirmation = false
            chatToDelete = null
        }
    )
}

/**
 * Карточка чата в списке.
 * Содержит название чата, последнее сообщение (если есть) и кнопку удаления.
 * Поддерживает долгий тап для переименования.
 *
 * @param chat Данные чата (ChatItem)
 * @param onClick Обработчик клика (открыть чат)
 * @param onDelete Обработчик удаления
 * @param onRename Обработчик переименования (долгий тап)
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatCard(
    chat: ChatItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)   // квадратная карточка
            .combinedClickable(
                onClick = onClick,        // клик — открыть чат
                onLongClick = onRename    // долгий тап — переименовать
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box {
            // Основной контент карточки
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Название чата (максимум 2 строки)
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // Последнее сообщение (если есть)
                chat.lastMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            // Кнопка удаления в правом верхнем углу
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_chat),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Диалог подтверждения удаления чата.
 * Спрашивает пользователя, уверен ли он, что хочет удалить чат.
 */
@Composable
fun DeleteChatConfirmationDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.delete_chat_title)) },
            text = { Text(stringResource(R.string.delete_chat_confirmation)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.delete))
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