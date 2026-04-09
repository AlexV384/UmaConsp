package com.example.umaconsp.presentation.document

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umaconsp.R
import com.example.umaconsp.UmaconspApplication
import com.example.umaconsp.ai.AiEvent
import com.example.umaconsp.ai.AiProvider
import com.example.umaconsp.ai.ResponseParser
import com.example.umaconsp.presentation.documentlist.DocumentListViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "DocumentViewModel"

class DocumentViewModel(
    private val documentId: String,
    private val documentListViewModel: DocumentListViewModel,
    private val aiProvider: AiProvider,
    private val responseParser: ResponseParser
) : ViewModel() {

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text

    private val _isEditing = MutableStateFlow(true)
    val isEditing: StateFlow<Boolean> = _isEditing

    private val _status = MutableStateFlow(getString(R.string.status_connected))
    val status: StateFlow<String> = _status

    private val _selectedImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedImageUris: StateFlow<List<Uri>> = _selectedImageUris

    private val _takenPhotoUri = MutableStateFlow(Uri.EMPTY)
    val takenPhotoUri: StateFlow<Uri> = _takenPhotoUri
    init {
        viewModelScope.launch {
            _text.value = documentListViewModel.getDocumentContent(documentId)
        }
    }

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
            if (documentListViewModel.getDocument(documentId) == null) return@launch
            _status.value = getString(R.string.status_sending)
            try {
                var accumulatedText = ""
                aiProvider.sendImages(selectedImageUris.value)
                    .catch { e ->
                        Log.e(TAG, "AI stream error", e)
                        _status.value = getString(R.string.error_connection_message, e.message ?: "")
                    }
                    .collectLatest { event ->
                        when (event) {
                            is AiEvent.Token -> {
                                accumulatedText += event.text
                                _status.value = getString(R.string.status_generating)
                            }
                            is AiEvent.Thinking -> {
                                accumulatedText += event.text
                                _status.value = getString(R.string.status_generating)
                            }
                            is AiEvent.Error -> {
                                _status.value = getString(R.string.error_connection_message, event.throwable.message ?: "")
                            }
                            is AiEvent.Complete -> {
                                val resultText = responseParser.parse(accumulatedText)
                                updateText(_text.value + "\n\n" + resultText)
                                _status.value = getString(R.string.status_response_received)
                                clearSelectedImages()
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending images", e)
                _status.value = getString(R.string.error_connection_message, e.message ?: "")
            }
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
    fun createPictureUri(
        context: Context,
        provider: String = "${context.packageName}.provider",
        fileName: String = "picture_${System.currentTimeMillis()}",
        fileExtension: String = ".jpg"
    ) {
        val tempFile = File.createTempFile(
            fileName, fileExtension, context.filesDir
        ).apply {
            createNewFile()
        }

        _takenPhotoUri.value = FileProvider.getUriForFile(context.applicationContext, provider, tempFile)
    }
}