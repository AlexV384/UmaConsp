package com.example.umaconsp.utils

import androidx.compose.runtime.compositionLocalOf
import com.example.umaconsp.presentation.chatlist.ChatListViewModel

/**
 * CompositionLocal для проброса экземпляра ChatListViewModel вниз по дереву Compose.
 *
 * Используется вместо передачи ViewModel через параметры компонентов,
 * что упрощает код и избегает "prop drilling" (передачи через множество промежуточных компонентов).
 *
 * Создаётся в MainActivity через CompositionLocalProvider:
 * ```
 * CompositionLocalProvider(LocalChatListViewModel provides chatListViewModel) {
 *     // ... дерево Compose
 * }
 * ```
 *
 * В дочерних компонентах (ChatScreen, ChatListScreen и др.) доступ к ViewModel осуществляется через:
 * ```
 * val viewModel = LocalChatListViewModel.current
 * ```
 *
 * @throws IllegalStateException если CompositionLocal не был предоставлен (обычно происходит
 *         при попытке доступа вне провайдера).
 */
val LocalChatListViewModel =
    compositionLocalOf<ChatListViewModel> {
        // Значение по умолчанию — выброс исключения, так как без провайдера
        // обращение к ViewModel не имеет смысла
        error("No ChatListViewModel provided")
    }