package com.example.umaconsp.utils

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme


@Composable
fun RichText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = parseHtmlBlocks(markdown)
    Column(modifier = modifier) {
        blocks.forEach { block ->
            BasicText(
                text = parseMarkdown(block.text, color),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = color,
                    textAlign = block.alignment
                )
            )
        }
    }
}

private data class HtmlBlock(val text: String, val alignment: TextAlign)

private fun parseHtmlBlocks(markdown: String): List<HtmlBlock> {
    val blocks = mutableListOf<HtmlBlock>()
    var remaining = markdown
    var currentAlignment = TextAlign.Start

    while (remaining.isNotEmpty()) {
        val divStart = remaining.indexOf("<div")
        if (divStart == -1) {
            if (remaining.isNotBlank()) {
                blocks.add(HtmlBlock(remaining, currentAlignment))
            }
            break
        }

        if (divStart > 0) {
            val before = remaining.substring(0, divStart)
            if (before.isNotBlank()) {
                blocks.add(HtmlBlock(before, currentAlignment))
            }
        }

        val closeTagStart = remaining.indexOf("</div>", divStart)
        if (closeTagStart == -1) {
            blocks.add(HtmlBlock(remaining.substring(divStart), currentAlignment))
            break
        }

        val openTagEnd = remaining.indexOf('>', divStart)
        if (openTagEnd == -1 || openTagEnd > closeTagStart) {
            remaining = remaining.substring(closeTagStart + 6)
            continue
        }

        val openTag = remaining.substring(divStart, openTagEnd + 1)
        val alignAttr = openTag.substringAfter("align=\"", "").substringBefore("\"")
        val newAlignment = when (alignAttr) {
            "center" -> TextAlign.Center
            "right" -> TextAlign.Right
            else -> TextAlign.Start
        }

        val innerText = remaining.substring(openTagEnd + 1, closeTagStart)
        blocks.add(HtmlBlock(innerText, newAlignment))

        remaining = remaining.substring(closeTagStart + 6)
    }
    return blocks
}

@Composable
private fun parseMarkdown(markdown: String, color: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val length = markdown.length

        fun fontSizeForHeader(level: Int): TextUnit = when (level) {
            1 -> 24.sp
            2 -> 20.sp
            3 -> 18.sp
            else -> 16.sp
        }

        while (i < length) {
            val ch = markdown[i]
            if (ch == '#' && (i == 0 || markdown[i - 1] == '\n')) {
                var level = 0
                var j = i
                while (j < length && markdown[j] == '#') {
                    level++
                    j++
                }
                if (j < length && markdown[j] == ' ') {
                    val startContent = j + 1
                    val endOfLine = markdown.indexOf('\n', startContent)
                    val headerText = if (endOfLine == -1) markdown.substring(startContent) else markdown.substring(startContent, endOfLine)
                    val headerStyle = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSizeForHeader(level)
                    )
                    withStyle(headerStyle) {
                        append(headerText)
                    }
                    i = if (endOfLine == -1) length else endOfLine
                    if (i < length && markdown[i] == '\n') {
                        append('\n')
                        i++
                    }
                    continue
                }
            }

            if (ch == '*' && i + 1 < length && markdown[i + 1] == '*') {
                val end = markdown.indexOf("**", i + 2)
                if (end != -1) {
                    val content = markdown.substring(i + 2, end)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(content)
                    }
                    i = end + 2
                    continue
                }
            } else if (ch == '*' && (i == 0 || markdown[i - 1] != '\\')) {
                val end = markdown.indexOf('*', i + 1)
                if (end != -1 && (end == i + 1 || markdown[end - 1] != '\\')) {
                    val content = markdown.substring(i + 1, end)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(content)
                    }
                    i = end + 1
                    continue
                }
            } else if (ch == '_' && i + 1 < length && markdown[i + 1] == '_') {
                val end = markdown.indexOf("__", i + 2)
                if (end != -1) {
                    val content = markdown.substring(i + 2, end)
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(content)
                    }
                    i = end + 2
                    continue
                }
            }
            append(ch)
            i++
        }
    }
}