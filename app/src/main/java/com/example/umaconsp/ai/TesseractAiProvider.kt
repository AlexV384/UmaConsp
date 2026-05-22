package com.example.umaconsp.ai

import android.graphics.Bitmap
import android.net.Uri
import com.example.umaconsp.UmaconspApplication
import com.example.umaconsp.ocr.OcrPostProcessor
import com.textimage.processor.ImageProcessor
import com.textimage.processor.ImageUtil
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class TesseractAiProvider(private val language: String = "rus") : AiProvider {

    private val initMutex = Mutex()
    @Volatile private var tessApi: TessBaseAPI? = null
    @Volatile private var currentLanguage: String? = null

    private suspend fun getApi(): TessBaseAPI {
        if (tessApi != null && currentLanguage != language) {
            tessApi?.recycle()
            tessApi = null
        }
        initMutex.withLock {
            if (tessApi != null) return tessApi!!
            val api = TessBaseAPI()
            val dataPath = withContext(Dispatchers.IO) {
                val path = UmaconspApplication.instance.filesDir.toString() + "/tesseract/"
                val dir = File(path + "tessdata")
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
                path
            }
            api.init(dataPath, language)
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            api.setVariable("user_defined_dpi", "300")
            tessApi = api
            currentLanguage = language
            return api
        }
    }

    override suspend fun sendImages(images: List<Uri>): Flow<AiEvent> = callbackFlow {
        try {
            val context = UmaconspApplication.instance.getAppContext()
            val allText = mutableListOf<String>()

            val api = getApi()

            for (uri in images) {
                val bitmap = withContext(Dispatchers.IO) {
                    var original = ImageUtil.decode(context, uri)
                        ?: throw RuntimeException("Failed to decode image from $uri")

                    val maxWidth = 2500
                    val minWidth = 1200
                    val currentWidth = original.width
                    if (currentWidth > maxWidth) {
                        original = ImageProcessor.resize(original, maxWidth, maxWidth, true)
                    } else if (currentWidth < minWidth) {
                        original = ImageProcessor.resize(original, minWidth, minWidth, true)
                    }

                    val params = com.example.umaconsp.processor.ProcessParams(
                        brightness = 0.05f,
                        contrast = 1.2f,
                        threshold = 128,
                        deskew = false,
                        targetWidth = 0,
                        targetHeight = 0,
                        keepAspect = true
                    )
                    original = ImageProcessor.process(original, params)
                    original
                }

                api.setImage(bitmap)
                val text = api.utF8Text
                api.clear()
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