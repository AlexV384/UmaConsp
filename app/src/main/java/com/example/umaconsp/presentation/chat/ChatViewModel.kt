package com.example.umaconsp.presentation.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSources
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

private const val TAG = "ChatViewModel"                 // для логирования
private const val MAX_IMAGE_SIZE = 1024                 // максимальный размер стороны изображения после сжатия (пикселей)
private const val IMAGE_QUALITY = 80                    // качество JPEG при сжатии (0-100)
private const val THROTTLE_MS = 100L                    // минимальный интервал между обновлениями БД (мс)

/**
 * ViewModel для экрана чата. Управляет отправкой сообщений, потоковым получением ответов,
 * статусом соединения, выбранными изображениями и взаимодействием с ChatListViewModel.
 *
 * @param chatId Идентификатор чата, с которым работает ViewModel
 * @param chatListViewModel Общая ViewModel для работы со списком чатов и БД
 */
class ChatViewModel(
    private val chatId: String,
    private val chatListViewModel: ChatListViewModel
) : ViewModel() {

    // ==================== Состояния ====================

    // Статус: подключение, генерация, ошибка и т.д.
    private val _status = MutableStateFlow(getString(R.string.status_connected))
    val status: StateFlow<String> = _status

    // Список URI выбранных пользователем изображений перед отправкой
    private val _selectedImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedImageUris: StateFlow<List<Uri>> = _selectedImageUris

    // Текущее SSE-подключение (для возможности отмены)
    private var currentEventSource: EventSource? = null

    // ID последнего отправленного пользовательского сообщения (используется для обновления размышлений)
    private var currentUserMessageId: String? = null

    // Временные метки последних обновлений БД для throttle (избегаем слишком частых записей)
    private var lastThinkingUpdate = 0L
    private var lastTokenUpdate = 0L

    // ==================== Жизненный цикл ====================

    override fun onCleared() {
        super.onCleared()
        // При закрытии экрана закрываем SSE-соединение
        currentEventSource?.cancel()
    }

    // ==================== Вспомогательные функции ====================

    /**
     * Проверяет, существует ли ещё чат в БД. Используется для защиты от гонок,
     * когда чат могли удалить во время генерации.
     */
    private suspend fun isChatExists(): Boolean = chatListViewModel.getChat(chatId) != null

    /**
     * Получение строки ресурса из Application контекста (т.к. ViewModel не имеет доступа к Activity).
     */
    private fun getString(resId: Int, vararg args: Any): String {
        return UmaconspApplication.instance.getAppContext().getString(resId, *args)
    }

    // ==================== Отправка сообщений ====================

    /**
     * Отправляет сообщение пользователя.
     * Если есть выбранные изображения — отправляет их вместе с текстом.
     * @param text Текст сообщения (может быть пустым, если есть изображения)
     */
    fun sendMessage(text: String) {
        if (text.isBlank() && _selectedImageUris.value.isEmpty()) return

        viewModelScope.launch {
            // Проверяем существование чата перед отправкой
            if (!isChatExists()) {
                Log.w(TAG, "Cannot send message, chat $chatId does not exist")
                return@launch
            }

            // Сохраняем текущие изображения и очищаем выбор
            val currentImages = _selectedImageUris.value.map { it.toString() }
            clearSelectedImages()

            // Создаём сообщение пользователя и сохраняем в БД
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                text = text,
                isUser = true,
                imageUrls = currentImages
            )
            chatListViewModel.addMessage(chatId, userMessage)
            currentUserMessageId = userMessage.id

            _status.value = getString(R.string.status_sending)

            try {
                // Отправляем запрос в зависимости от наличия изображений
                if (currentImages.isNotEmpty()) {
                    sendMessageWithImagesStream(text, currentImages.map { Uri.parse(it) })
                } else {
                    sendTextMessageStream(text)
                }
            } catch (e: Exception) {
                // Обработка ошибок при отправке (например, нет сети)
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

    // ==================== Построение запросов ====================

    /**
     * Отправляет текстовое сообщение (без изображений) с использованием SSE.
     */
    private fun sendTextMessageStream(text: String) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("message", text)
            .build()

        val request = Request.Builder()
            .url(AiApi.baseUrl)
            .post(body)
            .addHeader("Accept", "text/event-stream")   // важно для SSE
            .build()

        startEventSource(request)
    }

    /**
     * Отправляет сообщение с изображениями.
     * Изображения сжимаются до MAX_IMAGE_SIZE и сохраняются во временные файлы,
     * затем добавляются в multipart-запрос.
     */
    private suspend fun sendMessageWithImagesStream(text: String, uris: List<Uri>) {
        val context = UmaconspApplication.instance.getAppContext()
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        builder.addFormDataPart("message", text)

        val tempFiles = mutableListOf<File>()

        for (uri in uris) {
            // Читаем исходное изображение
            val inputStream = context.contentResolver.openInputStream(uri) ?: continue
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Масштабируем, сохраняя пропорции
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > height) MAX_IMAGE_SIZE.toFloat() / width else MAX_IMAGE_SIZE.toFloat() / height
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)

            // Сжимаем в JPEG
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, outputStream)
            val compressedData = outputStream.toByteArray()

            // Сохраняем во временный файл
            val tempFile = File(
                context.cacheDir,
                "temp_image_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"
            )
            tempFile.writeBytes(compressedData)
            tempFiles.add(tempFile)

            // Добавляем часть в multipart
            builder.addFormDataPart("image", tempFile.name, tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull()))
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
     * Использует throttle для снижения частоты обновлений БД.
     */
    private fun startEventSource(request: Request) {
        // Отменяем предыдущее соединение, если есть
        currentEventSource?.cancel()
        val factory = EventSources.createFactory(AiApi.client)
        currentEventSource = factory.newEventSource(request, object : okhttp3.sse.EventSourceListener() {
            // Накопленные данные в рамках одного ответа
            private var currentAiMessageId: String? = null
            private var accumulatedText = ""
            private var accumulatedThinking = ""

            /**
             * Вызывается при успешном открытии соединения.
             * Создаём пустое сообщение ассистента в БД, которое будем постепенно обновлять.
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

                    // Размышления модели (отправляются до основного ответа)
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

                    // Токен основного ответа
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
                        // Ошибка от сервера
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
             * Выполняем финальное обновление сообщения ассистента,
             * очищаем временные файлы и сбрасываем состояния.
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
                    // Удаляем временные файлы изображений
                    val cacheDir = UmaconspApplication.instance.getAppContext().cacheDir
                    cacheDir.listFiles()?.filter { it.name.startsWith("temp_image_") }?.forEach { it.delete() }
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

    // ==================== Управление выбранными изображениями ====================

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