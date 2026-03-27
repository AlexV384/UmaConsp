package com.example.umaconsp.presentation.chat

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umaconsp.R
import com.example.umaconsp.UmaconspApplication
import com.example.umaconsp.domain.model.Message
import com.example.umaconsp.presentation.chatlist.ChatListViewModel
import com.example.umaconsp.utils.AiApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.*

// Теги для логирования
private const val TAG = "ChatViewModel"

// Задержка между обновлениями БД (мс) – снижает нагрузку на Room и UI
private const val THROTTLE_MS = 100L

// Максимальный размер одного файла (10 МБ). Каждый файл проверяется отдельно.
// При превышении отправка прерывается.
private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024

/**
 * ViewModel для экрана чата.
 * Управляет отправкой сообщений, потоковым получением ответов от AI,
 * статусом соединения, выбранными файлами (Uri) и взаимодействием с ChatListViewModel.
 *
 * @param chatId ID текущего чата
 * @param chatListViewModel Общая ViewModel для работы с БД и списком чатов
 */
class ChatViewModel(
    private val chatId: String,
    private val chatListViewModel: ChatListViewModel
) : ViewModel() {

    // ==================== Состояния (StateFlow) ====================

    // Текущий статус (подключен, отправка, генерация, ошибка и т.д.)
    private val _status = MutableStateFlow(getString(R.string.status_connected))
    val status: StateFlow<String> = _status

    // Список URI выбранных пользователем файлов (изображений) перед отправкой
    private val _selectedImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedImageUris: StateFlow<List<Uri>> = _selectedImageUris

    // ==================== Поля для управления потоковой передачей ====================

    // Текущее SSE-подключение (EventSource) – для возможности отмены
    private var currentEventSource: EventSource? = null

    // ID последнего отправленного пользовательского сообщения (используется для обновления размышлений)
    private var currentUserMessageId: String? = null

    // Временные метки для throttle – чтобы не обновлять БД слишком часто
    private var lastThinkingUpdate = 0L
    private var lastTokenUpdate = 0L

    // ==================== Жизненный цикл ====================

    /**
     * Вызывается при уничтожении ViewModel (закрытие экрана чата).
     * Отменяем активное SSE-подключение, чтобы не было утечек и ненужных фоновых операций.
     */
    override fun onCleared() {
        super.onCleared()
        currentEventSource?.cancel()
    }

    // ==================== Вспомогательные методы ====================

    /**
     * Проверяет, существует ли ещё чат в базе данных.
     * Используется для защиты от ситуации, когда чат был удалён во время генерации ответа.
     */
    private suspend fun isChatExists(): Boolean = chatListViewModel.getChat(chatId) != null

    /**
     * Получает строку из ресурсов приложения (удобно для ViewModel, у которой нет прямого доступа к Context).
     */
    private fun getString(resId: Int, vararg args: Any): String {
        return UmaconspApplication.instance.getAppContext().getString(resId, *args)
    }

    // ==================== Отправка сообщения ====================

    /**
     * Отправляет сообщение пользователя (текст + файлы).
     * @param text Текст сообщения (может быть пустым, если есть файлы)
     */
    fun sendMessage(text: String) {
        // Если нет ни текста, ни выбранных файлов – ничего не делаем
        if (text.isBlank() && _selectedImageUris.value.isEmpty()) return

        viewModelScope.launch {
            // Проверяем существование чата перед отправкой
            if (!isChatExists()) {
                Log.w(TAG, "Cannot send message, chat $chatId does not exist")
                return@launch
            }

            // Сохраняем выбранные файлы и сразу очищаем выбор (чтобы пользователь видел пустую область превью)
            val currentFiles = _selectedImageUris.value
            clearSelectedImages()

            // Создаём сообщение пользователя и сохраняем в БД
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                text = text,
                isUser = true,
                imageUrls = currentFiles.map { it.toString() }  // сохраняем URI как строки
            )
            chatListViewModel.addMessage(chatId, userMessage)
            currentUserMessageId = userMessage.id  // запоминаем ID, чтобы потом обновлять размышления

            _status.value = getString(R.string.status_sending)

            try {
                // Отправляем запрос: если есть файлы – multipart с файлами, иначе только текст
                if (currentFiles.isNotEmpty()) {
                    sendMessageWithFilesStream(text, currentFiles)
                } else {
                    sendTextMessageStream(text)
                }
            } catch (e: Exception) {
                // Обработка ошибок (нет сети, таймаут, слишком большой файл и т.п.)
                Log.e(TAG, "Ошибка при отправке", e)
                val errorMsg = getString(R.string.error_connection_message, e.message ?: "")
                if (isChatExists()) {
                    chatListViewModel.addMessage(chatId, Message(
                        id = UUID.randomUUID().toString(),
                        text = errorMsg,
                        isUser = false
                    ))
                }
                _status.value = errorMsg
                currentUserMessageId = null
            }
        }
    }

    // ==================== Формирование HTTP-запросов ====================

    /**
     * Отправляет текстовое сообщение (без файлов) в потоковом режиме (SSE).
     */
    private fun sendTextMessageStream(text: String) {
        // Строим multipart-тело с одним полем "message"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("message", text)
            .build()

        val request = Request.Builder()
            .url(AiApi.baseUrl)                // адрес сервера (из настроек)
            .post(body)
            .addHeader("Accept", "text/event-stream")  // важно: сервер должен вернуть SSE
            .build()

        startEventSource(request)
    }

    /**
     * Отправляет сообщение с одним или несколькими файлами.
     * Файлы читаются в память как ByteArray и добавляются в multipart-запрос.
     * Каждый файл проверяется на размер (не более MAX_FILE_SIZE_BYTES).
     */
    private suspend fun sendMessageWithFilesStream(text: String, uris: List<Uri>) {
        val context = UmaconspApplication.instance.getAppContext()
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)

        // Добавляем текстовое поле
        builder.addFormDataPart("message", text)

        // Для каждого URI читаем файл целиком в байты
        for (uri in uris) {
            // Определяем MIME-тип (например, image/png, image/jpeg)
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

            // Открываем поток на чтение
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open stream for $uri")

            // Читаем все байты файла
            val bytes = inputStream.use { it.readBytes() }

            // Проверяем размер файла – если превышает лимит, прерываем отправку
            if (bytes.size > MAX_FILE_SIZE_BYTES) {
                throw IOException("File too large: ${bytes.size / 1024 / 1024} MB > 10 MB")
            }

            // Берём имя файла (обычно последний сегмент URI)
            val fileName = uri.lastPathSegment ?: "file"

            // Создаём часть multipart с именем "image"
            val part = MultipartBody.Part.createFormData(
                "image",
                fileName,
                bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            builder.addPart(part)
        }

        val body = builder.build()
        val request = Request.Builder()
            .url(AiApi.baseUrl)
            .post(body)
            .addHeader("Accept", "text/event-stream")
            .build()

        startEventSource(request)
    }

    // ==================== Обработка SSE потока ====================

    /**
     * Запускает SSE-соединение и обрабатывает поступающие события.
     * Использует throttle для ограничения частоты обновлений БД.
     */
    private fun startEventSource(request: Request) {
        // Отменяем предыдущее соединение (если есть)
        currentEventSource?.cancel()

        val factory = EventSources.createFactory(AiApi.client)
        currentEventSource = factory.newEventSource(request, object : okhttp3.sse.EventSourceListener() {
            // Накопленные данные в рамках одного ответа
            private var currentAiMessageId: String? = null
            private var accumulatedText = ""
            private var accumulatedThinking = ""

            /**
             * Вызывается при успешном открытии соединения.
             * Создаём пустое сообщение ассистента в БД, которое будем постепенно заполнять.
             */
            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                viewModelScope.launch {
                    if (!isChatExists()) {
                        Log.w(TAG, "Chat $chatId deleted, cancelling event source")
                        eventSource.cancel()
                        return@launch
                    }
                    _status.value = getString(R.string.status_generating)
                    val tempId = UUID.randomUUID().toString()
                    currentAiMessageId = tempId
                    accumulatedText = ""
                    accumulatedThinking = ""
                    Log.d(TAG, "Created new AI message with id $tempId")
                    chatListViewModel.addMessage(chatId, Message(tempId, "", isUser = false, thinking = null))
                }
            }

            /**
             * Обрабатывает каждое SSE-событие (строку JSON).
             * Поддерживает поля: thinking, token, error.
             */
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val json = org.json.JSONObject(data)

                    // === РАЗМЫШЛЕНИЯ (thinking) ===
                    if (json.has("thinking")) {
                        val thinking = json.getString("thinking")
                        accumulatedThinking += thinking
                        currentUserMessageId?.let { userMsgId ->
                            val now = System.currentTimeMillis()
                            // throttle: обновляем не чаще чем THROTTLE_MS
                            if (now - lastThinkingUpdate >= THROTTLE_MS) {
                                viewModelScope.launch {
                                    chatListViewModel.updateThinking(chatId, userMsgId, accumulatedThinking)
                                }
                                lastThinkingUpdate = now
                            }
                        }
                        return
                    }

                    // === ТОКЕН ОСНОВНОГО ОТВЕТА ===
                    if (json.has("token")) {
                        val token = json.getString("token")
                        accumulatedText += token
                        currentAiMessageId?.let { msgId ->
                            val now = System.currentTimeMillis()
                            if (now - lastTokenUpdate >= THROTTLE_MS) {
                                viewModelScope.launch {
                                    if (isChatExists()) {
                                        chatListViewModel.updateMessage(chatId, msgId, accumulatedText, null)
                                    }
                                }
                                lastTokenUpdate = now
                            }
                        }
                    } else if (json.has("error")) {
                        // === ОШИБКА ===
                        val error = json.getString("error")
                        viewModelScope.launch {
                            if (isChatExists()) {
                                chatListViewModel.addMessage(chatId, Message(
                                    id = UUID.randomUUID().toString(),
                                    text = error,
                                    isUser = false
                                ))
                            } else {
                                eventSource.cancel()
                            }
                        }
                        _status.value = getString(R.string.error_connection)
                        eventSource.cancel()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, getString(R.string.error_parse), e)
                }
            }

            /**
             * Вызывается, когда сервер завершил отправку (поле done: true).
             * Выполняем финальное обновление сообщения ассистента и сбрасываем состояния.
             */
            override fun onClosed(eventSource: EventSource) {
                viewModelScope.launch {
                    currentAiMessageId?.let { msgId ->
                        if (isChatExists()) {
                            Log.d(TAG, "Final update for message $msgId: text=$accumulatedText")
                            chatListViewModel.updateMessage(chatId, msgId, accumulatedText, null)
                        }
                    }
                    _status.value = getString(R.string.status_response_received)
                    currentAiMessageId = null
                    accumulatedText = ""
                    accumulatedThinking = ""
                    currentUserMessageId = null
                }
            }

            /**
             * Вызывается при ошибке соединения или разрыве.
             */
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                Log.e(TAG, "SSE failure", t)
                val errorMsg = t?.message ?: ""
                _status.value = getString(R.string.error_connection_message, errorMsg)
                viewModelScope.launch {
                    if (isChatExists()) {
                        chatListViewModel.addMessage(chatId, Message(
                            id = UUID.randomUUID().toString(),
                            text = getString(R.string.error_connection_message, errorMsg),
                            isUser = false
                        ))
                    }
                }
                currentUserMessageId = null
            }
        })
    }

    // ==================== Управление выбранными файлами ====================

    fun addSelectedImage(uri: Uri) {
        _selectedImageUris.value = _selectedImageUris.value + uri
    }

    fun removeSelectedImage(uri: Uri) {
        _selectedImageUris.value = _selectedImageUris.value.filter { it != uri }
    }

    fun clearSelectedImages() {
        _selectedImageUris.value = emptyList()
    }
}