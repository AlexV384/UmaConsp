package com.example.umaconsp.presentation.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umaconsp.R
import com.example.umaconsp.UmaconspApplication
import com.example.umaconsp.presentation.documentlist.DocumentListViewModel
import com.example.umaconsp.utils.AiApi
import com.example.umaconsp.utils.formatToMarkdown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

private const val TAG = "DocumentViewModel"
private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024

class DocumentViewModel(
    private val documentId: String,
    private val documentListViewModel: DocumentListViewModel
) : ViewModel() {

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text

    private val _isEditing = MutableStateFlow(true)
    val isEditing: StateFlow<Boolean> = _isEditing

    private val _status = MutableStateFlow(getString(R.string.status_connected))
    val status: StateFlow<String> = _status

    private val _selectedImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedImageUris: StateFlow<List<Uri>> = _selectedImageUris

    private var currentEventSource: EventSource? = null

    init {
        viewModelScope.launch {
            _text.value = documentListViewModel.getDocumentContent(documentId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentEventSource?.cancel()
    }

    private suspend fun isDocumentExists(): Boolean = documentListViewModel.getDocument(documentId) != null

    private fun getString(resId: Int, vararg args: Any): String {
        return UmaconspApplication.instance.getAppContext().getString(resId, *args)
    }

    fun toggleEditing() {
        _isEditing.value = !_isEditing.value
    }

    fun updateText(newText: String) {
        _text.value = newText
        viewModelScope.launch {
            documentListViewModel.updateDocumentContent(documentId, newText)
        }
    }

    fun sendImagesToModel() {
        if (selectedImageUris.value.isEmpty()) return
        viewModelScope.launch {
            if (!isDocumentExists()) return@launch
            _status.value = getString(R.string.status_sending)
            try {
                sendMessageWithFilesStream(selectedImageUris.value)
                clearSelectedImages()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending images", e)
                _status.value = getString(R.string.error_connection_message, e.message ?: "")
            }
        }
    }

    private suspend fun sendMessageWithFilesStream(uris: List<Uri>) {
        val context = UmaconspApplication.instance.getAppContext()
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        builder.addFormDataPart("message", """Что написано на изображении? Верни только JSON.""")

        for (uri in uris) {
            // Пытаемся загрузить Bitmap из URI
            val bitmap = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                null
            } ?: throw IOException("Failed to decode image from $uri")

            // Перекодируем Bitmap в JPEG (качество 90%)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val bytes = outputStream.toByteArray()
            bitmap.recycle()

            if (bytes.size > MAX_FILE_SIZE_BYTES) {
                throw IOException("File too large: ${bytes.size / 1024 / 1024} MB > 10 MB")
            }

            val fileName = uri.lastPathSegment ?: "file"
            val part = MultipartBody.Part.createFormData(
                "image",
                fileName,
                bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
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

    private fun startEventSource(request: Request) {
        currentEventSource?.cancel()
        val factory = EventSources.createFactory(AiApi.client)
        currentEventSource = factory.newEventSource(request, object : okhttp3.sse.EventSourceListener() {
            private var accumulatedText = ""

            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                _status.value = getString(R.string.status_generating)
                accumulatedText = ""
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val json = JSONObject(data)
                    if (json.has("token")) {
                        accumulatedText += json.getString("token")
                    } else if (json.has("thinking")) {
                        accumulatedText += json.getString("thinking")
                    } else if (json.has("error")) {
                        _status.value = json.getString("error")
                        eventSource.cancel()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error", e)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                val resultText = parseResponseJson(accumulatedText)
                updateText(_text.value + "\n\n" + resultText)
                _status.value = getString(R.string.status_response_received)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                Log.e(TAG, "SSE failure", t)
                _status.value = getString(R.string.error_connection_message, t?.message ?: "")
            }
        })
    }

    /**
     * Парсит JSON-ответ, полученный от модели.
     * Поддерживает два формата:
     * 1. { "text": "строка", ... }
     * 2. { "text": [ { "text": "строка1", ... }, ... ] }
     * В случае ошибки возвращает сообщение об ошибке.
     */
    private fun parseResponseJson(response: String): String {
        return try {
            val trimmed = response.trim()
            val jsonObject = JSONObject(trimmed)

            when {
                jsonObject.has("error") -> "Ошибка: ${jsonObject.getString("error")}"
                jsonObject.has("text") -> {
                    val textField = jsonObject.get("text")
                    when (textField) {
                        is String -> {
                            // Если поле text – строка, создаём объект с этим текстом и передаём в форматтер
                            formatToMarkdown(JSONObject().put("text", textField))
                        }
                        is JSONArray -> {
                            // Если поле text – массив, передаём его в форматтер
                            formatToMarkdown(textField)
                        }
                        else -> "Не удалось распознать текст"
                    }
                }
                else -> "Не удалось распознать текст"
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parsing error", e)
            "Ошибка парсинга ответа: ${e.message}"
        }
    }

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