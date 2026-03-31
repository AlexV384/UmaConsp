package com.example.umaconsp.ai

import android.content.ContentResolver
import android.net.Uri
import com.example.umaconsp.UmaconspApplication
import com.example.umaconsp.llamacpp.Native
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream


class LocalAiProvider : AiProvider {
    override suspend fun sendImages(images: List<Uri>): Flow<AiEvent> =
        callbackFlow {
            // Convert each Uri → ByteArray on the IO dispatcher
            val byteArrays = images.map { uri ->
                withContext(Dispatchers.IO) {
                    uriToByteArray(UmaconspApplication.instance.contentResolver, uri)
                }
            }

            // If there are no images, just complete the flow
            if (byteArrays.isEmpty()) {
                awaitClose { /* nothing to do */ }
                return@callbackFlow
            }

            // Run the native work on the IO dispatcher
            withContext(Dispatchers.IO) {
                try {
                    // `Native.conversePub` receives the first byte array.
                    // It reports progress via the provided callback.
                    Native.conversePub(byteArrays[0]) { token ->
                        trySend(AiEvent.Token(token))
                    }
                    // TODO: implement per‑image callbacks and emit
                    // AiEvent.Token / AiEvent.Complete as needed
                } catch (e: Exception) {
                    // Propagate any exception as an error event
                    trySend(AiEvent.Error(e))
                } finally {
                    // Signal completion of the flow
                    awaitClose { /* no‑op */ }
                }
            }
        }.flowOn(Dispatchers.IO)
}
private suspend fun uriToByteArray(
    resolver: ContentResolver,
    uri: Uri
): ByteArray = withContext(Dispatchers.IO) {
    resolver.openInputStream(uri)?.use { input: InputStream? ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)          // 8KB buffer
        var bytesRead: Int
        while (input?.read(buffer).also { bytesRead = it!! } != -1) {
            output.write(buffer, 0, bytesRead)
        }
        output.toByteArray()
    } ?: throw IOException("Unable to open $uri")
}