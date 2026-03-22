package com.example.umaconsp.presentation.chatlist

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.*
import java.util.*
import com.example.umaconsp.UmaconspApplication
import com.example.umaconsp.data.database.ChatEntity
import com.example.umaconsp.data.database.MessageEntity
import com.example.umaconsp.domain.model.Message

/**
 * Модель данных для отображения в списке чатов.
 * Содержит только поля, необходимые для UI.
 *
 * @property id Уникальный идентификатор чата
 * @property title Название чата
 * @property lastMessage Текст последнего сообщения (обрезанный до 50 символов)
 * @property lastMessageTime Время последнего сообщения (для сортировки)
 */
data class ChatItem(
    val id: String,
    val title: String,
    val lastMessage: String? = null,
    val lastMessageTime: Long = System.currentTimeMillis()
)

/**
 * ViewModel для экрана списка чатов.
 * Отвечает за:
 * - Получение списка чатов из БД (как Flow)
 * - Получение потока сообщений для конкретного чата
 * - Создание, удаление, переименование чатов
 * - Добавление и обновление сообщений
 *
 * Вся работа с БД выполняется через Room, результаты обёрнуты в Flow для автоматического обновления UI.
 */
class ChatListViewModel : ViewModel() {

    // ==================== База данных ====================
    private val db = UmaconspApplication.database

    // ==================== События ====================
    // SharedFlow для уведомления об удалении чата (используется в MainActivity для навигации)
    private val _chatDeleted = MutableSharedFlow<String>()
    val chatDeleted: SharedFlow<String> = _chatDeleted.asSharedFlow()

    // ==================== Потоки данных ====================

    /**
     * Поток списка чатов, отсортированных по времени последнего сообщения (новые сверху).
     * При изменении таблицы chats (добавление, удаление, обновление) поток автоматически эмитит новое значение.
     *
     * Преобразуем ChatEntity в ChatItem (упрощённая модель для UI).
     */
    val chats: Flow<List<ChatItem>> = db.chatDao().getAllChats().map { list ->
        list.map { chatEntity ->
            ChatItem(
                id = chatEntity.id,
                title = chatEntity.title,
                lastMessage = chatEntity.lastMessage,
                lastMessageTime = chatEntity.lastMessageTime
            )
        }
    }

    /**
     * Получить поток сообщений для указанного чата.
     * Сообщения сортируются по времени (от старых к новым).
     * Преобразуем MessageEntity в domain.model.Message.
     *
     * @param chatId Идентификатор чата
     * @return Flow<List<Message>> — автоматически обновляется при изменениях в таблице messages
     */
    fun getMessagesFlow(chatId: String): Flow<List<Message>> {
        return db.messageDao().getMessagesForChat(chatId).map { entities ->
            entities.map { entity ->
                Message(
                    id = entity.id,
                    text = entity.text,
                    isUser = entity.isUser,
                    imageUrls = entity.imageUrls.split(",").filter { it.isNotEmpty() },
                    thinking = entity.thinking
                )
            }
        }
    }

    // ==================== Операции с чатами ====================

    /**
     * Создаёт новый чат с автоматическим названием "Чат N".
     * @return ID созданного чата
     */
    suspend fun createNewChat(): String {
        val newId = UUID.randomUUID().toString()
        // Определяем номер нового чата по количеству существующих
        val count = db.chatDao().getAllChats().firstOrNull()?.size ?: 0
        val newChat = ChatEntity(
            id = newId,
            title = "Чат ${count + 1}",
            lastMessage = null,
            lastMessageTime = System.currentTimeMillis()
        )
        db.chatDao().insertChat(newChat)
        return newId
    }

    /**
     * Удаляет чат по его ID.
     * Также эмитит событие _chatDeleted для навигации.
     */
    suspend fun deleteChat(chatId: String) {
        val chat = db.chatDao().getChatById(chatId) ?: return
        db.chatDao().deleteChat(chat)
        _chatDeleted.emit(chatId)  // уведомляем подписчиков (MainActivity) о необходимости закрыть экран чата
    }

    /**
     * Переименовывает чат.
     */
    suspend fun renameChat(chatId: String, newTitle: String) {
        val chat = db.chatDao().getChatById(chatId) ?: return
        val updated = chat.copy(title = newTitle)
        db.chatDao().updateChat(updated)
    }

    /**
     * Возвращает ChatItem по ID чата или null, если чат не найден.
     * Используется для проверки существования чата и получения его заголовка.
     */
    suspend fun getChat(chatId: String): ChatItem? {
        val chatEntity = db.chatDao().getChatById(chatId)
        return chatEntity?.let {
            ChatItem(it.id, it.title, it.lastMessage, it.lastMessageTime)
        }
    }

    // ==================== Операции с сообщениями ====================

    /**
     * Добавляет новое сообщение в чат.
     * После добавления обновляет поле lastMessage в чате (текст последнего сообщения).
     *
     * @param chatId ID чата
     * @param message Сообщение для добавления
     */
    suspend fun addMessage(chatId: String, message: Message) {
        // Проверяем существование чата (защита от удаления чата во время отправки)
        if (getChat(chatId) == null) return

        // Сохраняем сообщение
        val messageEntity = MessageEntity(
            id = message.id,
            chatId = chatId,
            text = message.text,
            isUser = message.isUser,
            imageUrls = message.imageUrls.joinToString(","),
            timestamp = System.currentTimeMillis(),
            thinking = message.thinking
        )
        db.messageDao().insertMessage(messageEntity)

        // Обновляем последнее сообщение в чате (берем первые 50 символов для компактности)
        val chat = db.chatDao().getChatById(chatId)
        if (chat != null) {
            val updatedChat = chat.copy(
                lastMessage = message.text.take(50),
                lastMessageTime = System.currentTimeMillis()
            )
            db.chatDao().updateChat(updatedChat)
        }
    }

    /**
     * Обновляет текст сообщения и (опционально) поле thinking.
     * Используется для потокового вывода ответа — постепенное обновление текста сообщения ассистента.
     *
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param newText Новый полный текст сообщения
     * @param newThinking Новое значение thinking (если null — оставляем старый)
     */
    suspend fun updateMessage(chatId: String, messageId: String, newText: String, newThinking: String? = null) {
        if (getChat(chatId) == null) return

        val existing = db.messageDao().getMessageById(messageId)
        if (existing != null && existing.chatId == chatId) {
            val updated = existing.copy(
                text = newText,
                thinking = newThinking ?: existing.thinking
            )
            db.messageDao().updateMessage(updated)
        }
    }

    /**
     * Обновляет только поле thinking пользовательского сообщения.
     * Используется для потокового отображения размышлений модели.
     *
     * @param chatId ID чата
     * @param messageId ID сообщения (сообщения пользователя)
     * @param thinking Новый полный текст размышлений
     */
    suspend fun updateThinking(chatId: String, messageId: String, thinking: String) {
        if (getChat(chatId) == null) return
        val existing = db.messageDao().getMessageById(messageId)
        if (existing != null && existing.chatId == chatId) {
            val updated = existing.copy(thinking = thinking)
            db.messageDao().updateMessage(updated)
        }
    }
}