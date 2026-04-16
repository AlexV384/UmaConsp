package com.example.umaconsp.data.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
                stream.flush()
            }
        }

    private suspend fun saveTxt(folderUri: Uri, title: String, plainText: String) =
        withContext(Dispatchers.IO) {
            val fileUri = createFileInFolder(folderUri, "$title.txt", "text/plain")
            context.contentResolver.openOutputStream(fileUri)?.use { stream ->
                stream.write(plainText.toByteArray())
                stream.flush()
            }
        }

    private suspend fun savePdfWithFormatting(folderUri: Uri, title: String, markdownContent: String) =
        withContext(Dispatchers.IO) {
            val pdfFile = File(context.cacheDir, "${title}.pdf")
            if (pdfFile.exists()) pdfFile.delete()
            generateStyledPdf(pdfFile, markdownContent)
            if (!pdfFile.exists() || pdfFile.length() == 0L) {
                throw IOException("Generated PDF file is missing or empty: ${pdfFile.absolutePath}")
            }
            delay(100)
            val pdfUri = createFileInFolder(folderUri, "$title.pdf", "application/pdf")
            context.contentResolver.openOutputStream(pdfUri)?.use { out ->
                pdfFile.inputStream().use { input ->
                    input.copyTo(out)
                    out.flush()
                }
            }
            pdfFile.delete()
        }

    private suspend fun generateStyledPdf(pdfFile: File, markdownContent: String) {
        withContext(Dispatchers.Default) {
            try {
                val pageWidth = 595
                val pageHeight = 842
                val margin = 50
                val textWidth = pageWidth - 2 * margin
                val document = PdfDocument()
                var yOffset = margin
                var currentPage = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create())
                val blocks = parseContentBlocks(markdownContent)
                for (block in blocks) {
                    val lines = block.text.split("\n")
                    for (line in lines) {
                        if (line.isBlank()) continue

                        val styledText = applyMarkdownStylesAndClean(line)

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
                    out.flush()
                }
                document.close()
            } catch (e: Exception) {
                pdfFile.delete()
                throw IOException("PDF generation failed", e)
            }
        }
    }

    private fun parseContentBlocks(markdown: String): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        var remaining = markdown
        val alignmentStack = ArrayDeque<Layout.Alignment>()
        alignmentStack.addLast(Layout.Alignment.ALIGN_NORMAL)

        while (remaining.isNotEmpty()) {
            val divStart = remaining.indexOf("<div")
            if (divStart == -1) {
                if (remaining.isNotBlank()) {
                    blocks.add(ContentBlock(remaining, alignmentStack.last()))
                }
                break
            }

            if (divStart > 0) {
                val before = remaining.substring(0, divStart)
                if (before.isNotBlank()) {
                    blocks.add(ContentBlock(before, alignmentStack.last()))
                }
            }

            val closeTagStart = remaining.indexOf("</div>", divStart)
            if (closeTagStart == -1) break

            val openTagEnd = remaining.indexOf('>', divStart)
            if (openTagEnd != -1 && openTagEnd < closeTagStart) {
                val openTag = remaining.substring(divStart, openTagEnd + 1)
                val alignAttr = openTag.substringAfter("align=\"", "").substringBefore("\"")
                val newAlignment = when (alignAttr) {
                    "center" -> Layout.Alignment.ALIGN_CENTER
                    "right" -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                }
                alignmentStack.addLast(newAlignment)
                val innerText = remaining.substring(openTagEnd + 1, closeTagStart)
                if (innerText.isNotBlank()) {
                    blocks.add(ContentBlock(innerText, alignmentStack.last()))
                }
                alignmentStack.removeLast()
            }
            remaining = remaining.substring(closeTagStart + 6)
        }
        return blocks
    }

    private fun applyMarkdownStylesAndClean(text: String): SpannableString {
        val cleaned = StringBuilder()
        val markers = mutableListOf<Triple<Int, Int, String>>()

        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        markers.add(Triple(cleaned.length, cleaned.length + (end - i - 2), "bold"))
                        cleaned.append(text.substring(i + 2, end))
                        i = end + 2
                    } else {
                        cleaned.append(text[i])
                        i++
                    }
                }
                text.startsWith("__", i) -> {
                    val end = text.indexOf("__", i + 2)
                    if (end != -1) {
                        markers.add(Triple(cleaned.length, cleaned.length + (end - i - 2), "underline"))
                        cleaned.append(text.substring(i + 2, end))
                        i = end + 2
                    } else {
                        cleaned.append(text[i])
                        i++
                    }
                }
                text[i] == '*' && (i == 0 || text[i - 1] != '\\') -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1 && (end == i + 1 || text[end - 1] != '\\')) {
                        markers.add(Triple(cleaned.length, cleaned.length + (end - i - 1), "italic"))
                        cleaned.append(text.substring(i + 1, end))
                        i = end + 1
                    } else {
                        cleaned.append(text[i])
                        i++
                    }
                }
                (text[i] == '#') && (i == 0 || text[i - 1] == '\n') -> {
                    var level = 0
                    var j = i
                    while (j < text.length && text[j] == '#') {
                        level++
                        j++
                    }
                    if (j < text.length && text[j] == ' ') {
                        val startContent = j + 1
                        val endOfLine = text.indexOf('\n', startContent)
                        val headerText = if (endOfLine == -1) text.substring(startContent) else text.substring(startContent, endOfLine)
                        val startPos = cleaned.length
                        cleaned.append(headerText)
                        val endPos = cleaned.length
                        markers.add(Triple(startPos, endPos, "header$level"))
                        i = if (endOfLine == -1) text.length else endOfLine
                        if (i < text.length && text[i] == '\n') {
                            cleaned.append('\n')
                            i++
                        }
                        continue
                    } else {
                        cleaned.append(text[i])
                        i++
                    }
                }
                else -> {
                    cleaned.append(text[i])
                    i++
                }
            }
        }

        val spannable = SpannableString(cleaned.toString())
        for ((start, end, type) in markers) {
            when {
                type == "bold" -> spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                type == "italic" -> spannable.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                type == "underline" -> spannable.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                type.startsWith("header") -> {
                    val level = type.substring(6).toIntOrNull() ?: 1
                    val sizeMultiplier = when (level) {
                        1 -> 2.0f
                        2 -> 1.5f
                        else -> 1.2f
                    }
                    spannable.setSpan(RelativeSizeSpan(sizeMultiplier), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        return spannable
    }

    private suspend fun createFileInFolder(folderUri: Uri, fileName: String, mimeType: String): Uri =
        withContext(Dispatchers.IO) {
            val documentFile = DocumentFile.fromTreeUri(context, folderUri)
                ?: throw IllegalArgumentException("Invalid folder URI")
            val existingFile = documentFile.findFile(fileName)
            if (existingFile != null && existingFile.exists()) {
                runCatching { existingFile.delete() }
                delay(100)
            }
            val file = documentFile.createFile(mimeType, fileName)
                ?: throw IOException("Failed to create file")
            file.uri
        }

    private data class ContentBlock(val text: String, val alignment: Layout.Alignment)

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