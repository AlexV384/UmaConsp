package com.example.umaconsp.data.export

import android.content.Context
import android.graphics.Paint
import android.net.Uri
import android.print.PrintAttributes
import android.print.pdf.PrintedPdfDocument
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DocumentExporter(private val context: Context) {

    suspend fun exportDocument(folderUri: Uri, documentId: String, title: String, content: String) {
        val cleanTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val subfolderName = "${cleanTitle}_$documentId"
        val subfolderUri = createSubfolder(folderUri, subfolderName)
        saveMarkdown(subfolderUri, cleanTitle, convertUnderlineToHtml(content))
        saveTxt(subfolderUri, cleanTitle, stripMarkdown(content))
        savePdf(subfolderUri, cleanTitle, content)
    }

    private suspend fun createSubfolder(parentUri: Uri, folderName: String): Uri =
        withContext(Dispatchers.IO) {
            val documentFile = DocumentFile.fromTreeUri(context, parentUri)
                ?: throw IllegalArgumentException("Invalid folder URI")
            val subfolder = documentFile.findFile(folderName) ?: documentFile.createDirectory(folderName)
            ?: throw IOException("Failed to create subfolder")
            subfolder.uri
        }

    private suspend fun saveMarkdown(folderUri: Uri, title: String, content: String) =
        withContext(Dispatchers.IO) {
            val fileUri = createFileInFolder(folderUri, "$title.md", "text/markdown")
            context.contentResolver.openOutputStream(fileUri)?.use { stream ->
                stream.write(content.toByteArray())
            }
        }

    private suspend fun saveTxt(folderUri: Uri, title: String, plainText: String) =
        withContext(Dispatchers.IO) {
            val fileUri = createFileInFolder(folderUri, "$title.txt", "text/plain")
            context.contentResolver.openOutputStream(fileUri)?.use { stream ->
                stream.write(plainText.toByteArray())
            }
        }

    private suspend fun savePdf(folderUri: Uri, title: String, markdownContent: String) =
        withContext(Dispatchers.IO) {
            val pdfFile = File(context.cacheDir, "${title}.pdf")
            generateSimplePdf(pdfFile, markdownContent, title)
            val pdfUri = createFileInFolder(folderUri, "$title.pdf", "application/pdf")
            context.contentResolver.openOutputStream(pdfUri)?.use { out ->
                pdfFile.inputStream().use { it.copyTo(out) }
            }
            pdfFile.delete()
        }

    private fun generateSimplePdf(pdfFile: File, markdownContent: String, title: String) {
        val plainText = stripMarkdown(markdownContent)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        val document = PrintedPdfDocument(context, attributes)
        val page = document.startPage(0)
        val canvas = page.canvas
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 12f
        }
        val lines = plainText.split("\n")
        var y = 50f
        val pageHeight = canvas.height.toFloat()
        for (line in lines) {
            canvas.drawText(line, 50f, y, paint)
            y += paint.textSize + 5
            if (y > pageHeight - 50) break
        }
        document.finishPage(page)
        FileOutputStream(pdfFile).use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    private suspend fun createFileInFolder(folderUri: Uri, fileName: String, mimeType: String): Uri =
        withContext(Dispatchers.IO) {
            val documentFile = DocumentFile.fromTreeUri(context, folderUri)
                ?: throw IllegalArgumentException("Invalid folder URI")
            val file = documentFile.createFile(mimeType, fileName)
                ?: throw IOException("Failed to create file")
            file.uri
        }

    private fun convertUnderlineToHtml(markdown: String): String {
        return markdown.replace(Regex("__(.*?)__"), "<u>$1</u>")
    }

    private fun stripMarkdown(markdown: String): String {
        return markdown
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("\\*(.*?)\\*"), "$1")
            .replace(Regex("__(.*?)__"), "$1")
            .replace(Regex("^#+\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("<div.*?>"), "")
            .replace(Regex("</div>"), "")
    }
}