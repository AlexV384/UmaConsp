package com.example.umaconsp.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.umaconsp.UmaconspApplication
import com.example.umaconsp.utils.AiApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

private const val TAG = "RemoteAiProvider"
private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024

class RemoteAiProvider : AiProvider {

    override suspend fun sendImages(images: List<Uri>): Flow<AiEvent> = callbackFlow {
        val context = UmaconspApplication.instance.getAppContext()
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        builder.addFormDataPart("message", """Что написано на изображении? Верни только JSON.""")

        for (uri in images) {
            val bitmap = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                null
            } ?: throw IOException("Failed to decode image from $uri")

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

        var eventSource: EventSource? = null
        val factory = EventSources.createFactory(AiApi.client)

        eventSource = factory.newEventSource(request, object : okhttp3.sse.EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val json = JSONObject(data)
                    when {
                        json.has("token") -> {
                            trySend(AiEvent.Token(json.getString("token")))
                        }
                        json.has("thinking") -> {
                            trySend(AiEvent.Thinking(json.getString("thinking")))
                        }
                        json.has("error") -> {
                            trySend(AiEvent.Error(IOException(json.getString("error"))))
                            eventSource.cancel()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error", e)
                    trySend(AiEvent.Error(e))
                }
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(AiEvent.Complete)
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                Log.e(TAG, "SSE failure", t)
                trySend(AiEvent.Error(t ?: IOException("Unknown error")))
                close()
            }
        })

        awaitClose {
            eventSource?.cancel()
        }
    }.flowOn(Dispatchers.IO)
}