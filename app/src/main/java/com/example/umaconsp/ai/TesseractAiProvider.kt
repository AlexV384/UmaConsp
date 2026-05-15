package com.example.umaconsp.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.umaconsp.UmaconspApplication
import com.example.umaconsp.ocr.OcrPostProcessor
import com.textimage.processor.ImageUtil
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class TesseractAiProvider : AiProvider {

    private val tessApi: TessBaseAPI by lazy {
        val api = TessBaseAPI()
        val dataPath = UmaconspApplication.instance.filesDir.toString() + "/tesseract/"

        val dir = File(dataPath + "tessdata")
        if (!dir.exists()) {
            dir.mkdirs()
            UmaconspApplication.instance.assets.list("tessdata")?.forEach { filename ->
                UmaconspApplication.instance.assets.open("tessdata/$filename").use { input ->
                    File(dir, filename).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }

        api.init(dataPath, "rus+eng")
        api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
        api
    }

    override suspend fun sendImages(images: List<Uri>): Flow<AiEvent> = callbackFlow {
        try {
            val context = UmaconspApplication.instance.getAppContext()
            val allText = mutableListOf<String>()

            tessApi

            for (uri in images) {
                val bitmap = withContext(Dispatchers.IO) {
                    val original = ImageUtil.decode(context, uri)
                        ?: throw RuntimeException("Failed to decode image from $uri")
                    original
                }

                tessApi.setImage(bitmap)
                val text = tessApi.utF8Text
                tessApi.clear()
                allText.add(text)
                bitmap.recycle()
            }

            val combinedText = allText.joinToString("\n\n")
            val markdown = OcrPostProcessor.textToMarkdown(combinedText)

            trySend(AiEvent.Token(markdown))
            trySend(AiEvent.Complete)
        } catch (e: Exception) {
            trySend(AiEvent.Error(e))
        } finally {
            close()
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)
}