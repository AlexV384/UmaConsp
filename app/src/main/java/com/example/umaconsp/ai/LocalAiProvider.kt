package com.example.umaconsp.ai

import android.content.ContentResolver
import android.net.Uri
import com.example.umaconsp.UmaconspApplication
import com.example.umaconsp.llamacpp.Native
import com.textimage.processor.ImageProcessor
import com.textimage.processor.ImageUtil
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
                    val unprocessed = ImageUtil.decode(UmaconspApplication.instance.getAppContext(), uri)!!
                    val resized = ImageProcessor.resize(unprocessed, 800, 800, true)
                    val bytes = ImageUtil.encode(resized)
                    bytes
                }
            }

            // If there are no images, just complete the flow
            if (byteArrays.isEmpty()) {
                awaitClose { /* nothing to do */ }
                return@callbackFlow
            }

            val callback = object : Native.TokenCallback {
                override fun onToken(token: String) {
                    trySend(AiEvent.Token(token))
                }

                override fun onTerminator() {
                    trySend(AiEvent.Complete)
                    close() // End the flow when terminator is received
                }
            }
            Native.conversePub(byteArrays[0], callback)
            awaitClose {}
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