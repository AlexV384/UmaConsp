package com.example.umaconsp.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
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
        savePdfWithFormatting(subfolderUri, cleanTitle, content)
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

    private suspend fun savePdfWithFormatting(folderUri: Uri, title: String, markdownContent: String) =
        withContext(Dispatchers.IO) {
            val pdfFile = File(context.cacheDir, "${title}.pdf")
            if (pdfFile.exists()) pdfFile.delete()

            generateStyledPdf(pdfFile, markdownContent)

            val pdfUri = createFileInFolder(folderUri, "$title.pdf", "application/pdf")
            context.contentResolver.openOutputStream(pdfUri)?.use { out ->
                pdfFile.inputStream().use { it.copyTo(out) }
            }
            pdfFile.delete()
        }

    private suspend fun generateStyledPdf(pdfFile: File, markdownContent: String) {
        withContext(Dispatchers.Default) {
            val pageWidth = 595   // A4 width in points (72 dpi)
            val pageHeight = 842  // A4 height
            val margin = 50
            val textWidth = pageWidth - 2 * margin

            val document = PdfDocument()
            var yOffset = margin
            var currentPage = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create())

            // Разбираем контент на блоки с выравниванием
            val blocks = parseContentBlocks(markdownContent)

            for (block in blocks) {
                val lines = block.text.split("\n")
                for (line in lines) {
                    if (line.isBlank()) continue

                    val styledText = applyMarkdownStyles(line)

                    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 12f
                    }
                    val layout = StaticLayout.Builder.obtain(styledText, 0, styledText.length, textPaint, textWidth)
                        .setAlignment(block.alignment)
                        .setLineSpacing(0f, 1.2f)
                        .build()

                    if (yOffset + layout.height > pageHeight - margin) {
                        document.finishPage(currentPage)
                        currentPage = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create())
                        yOffset = margin
                    }

                    val canvas = currentPage.canvas
                    canvas.save()
                    canvas.translate(margin.toFloat(), yOffset.toFloat())
                    layout.draw(canvas)
                    canvas.restore()

                    yOffset += layout.height
                }
            }

            document.finishPage(currentPage)
            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }
            document.close()
        }
    }

    private fun parseContentBlocks(markdown: String): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        var remaining = markdown
        var currentAlignment = Layout.Alignment.ALIGN_NORMAL

        while (remaining.isNotEmpty()) {
            val divStart = remaining.indexOf("<div")
            if (divStart == -1) {
                if (remaining.isNotBlank()) {
                    blocks.add(ContentBlock(remaining, currentAlignment))
                }
                break
            }

            if (divStart > 0) {
                val before = remaining.substring(0, divStart)
                if (before.isNotBlank()) {
                    blocks.add(ContentBlock(before, currentAlignment))
                }
            }

            val closeTagStart = remaining.indexOf("</div>", divStart)
            if (closeTagStart == -1) break

            val openTagEnd = remaining.indexOf('>', divStart)
            if (openTagEnd != -1 && openTagEnd < closeTagStart) {
                val openTag = remaining.substring(divStart, openTagEnd + 1)
                val alignAttr = openTag.substringAfter("align=\"", "").substringBefore("\"")
                currentAlignment = when (alignAttr) {
                    "center" -> Layout.Alignment.ALIGN_CENTER
                    "right" -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                }
                val innerText = remaining.substring(openTagEnd + 1, closeTagStart)
                if (innerText.isNotBlank()) {
                    blocks.add(ContentBlock(innerText, currentAlignment))
                }
            }
            remaining = remaining.substring(closeTagStart + 6)
        }
        return blocks
    }

    private fun applyMarkdownStyles(text: String): android.text.SpannableString {
        val spannable = android.text.SpannableString(text)

        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        boldRegex.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val italicRegex = Regex("\\*(.*?)\\*")
        italicRegex.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            spannable.setSpan(StyleSpan(Typeface.ITALIC), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val underlineRegex = Regex("__(.*?)__")
        underlineRegex.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            spannable.setSpan(UnderlineSpan(), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val headerRegex = Regex("^(#{1,3}) (.*)$", RegexOption.MULTILINE)
        headerRegex.findAll(text).forEach { match ->
            val level = match.groupValues[1].length
            val sizeMultiplier = when (level) {
                1 -> 2.0f
                2 -> 1.5f
                else -> 1.2f
            }
            val start = match.range.first
            val end = match.range.last + 1
            spannable.setSpan(android.text.style.RelativeSizeSpan(sizeMultiplier), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return spannable
    }

    private data class ContentBlock(val text: String, val alignment: Layout.Alignment)

    private suspend fun createFileInFolder(folderUri: Uri, fileName: String, mimeType: String): Uri =
        withContext(Dispatchers.IO) {
            val documentFile = DocumentFile.fromTreeUri(context, folderUri)
                ?: throw IllegalArgumentException("Invalid folder URI")
            val file = documentFile.createFile(mimeType, fileName)
                ?: throw IOException("Failed to create file")
            file.uri
        }

    private fun convertUnderlineToHtml(markdown: String): String =
        markdown.replace(Regex("__(.*?)__"), "<u>$1</u>")

    private fun stripMarkdown(markdown: String): String = markdown
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("\\*(.*?)\\*"), "$1")
        .replace(Regex("__(.*?)__"), "$1")
        .replace(Regex("^#+\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("<div.*?>"), "")
        .replace(Regex("</div>"), "")
}