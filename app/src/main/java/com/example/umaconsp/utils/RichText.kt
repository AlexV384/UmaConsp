package com.example.umaconsp.utils

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RichText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = parseHtmlBlocks(markdown)

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            BasicText(
                text = parseMarkdown(block.text),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = color,
                    textAlign = block.alignment
                )
            )
            if (index != blocks.lastIndex) {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private data class HtmlBlock(val text: String, val alignment: TextAlign)

private fun parseHtmlBlocks(markdown: String): List<HtmlBlock> {
    val blocks = mutableListOf<HtmlBlock>()
    var remaining = markdown.trim()

    while (remaining.isNotEmpty()) {
        val divStart = remaining.indexOf("<div")
        if (divStart == -1) {
            if (remaining.isNotBlank()) blocks.add(HtmlBlock(remaining, TextAlign.Start))
            break
        }

        if (divStart > 0) {
            val before = remaining.substring(0, divStart)
            if (before.isNotBlank()) blocks.add(HtmlBlock(before.trim(), TextAlign.Start))
        }

        val closeTagStart = remaining.indexOf("</div>", divStart)
        if (closeTagStart == -1) {
            blocks.add(HtmlBlock(remaining.substring(divStart).trim(), TextAlign.Start))
            break
        }

        val openTagEnd = remaining.indexOf('>', divStart)
        if (openTagEnd == -1 || openTagEnd > closeTagStart) {
            remaining = remaining.substring(closeTagStart + 6)
            continue
        }

        val openTag = remaining.substring(divStart, openTagEnd + 1)
        val alignAttr = openTag.substringAfter("align=\"", "").substringBefore("\"")
        val alignment = when (alignAttr) {
            "center" -> TextAlign.Center
            "right" -> TextAlign.Right
            else -> TextAlign.Start
        }

        val innerText = remaining.substring(openTagEnd + 1, closeTagStart)
        blocks.add(HtmlBlock(innerText.trim(), alignment))

        remaining = remaining.substring(closeTagStart + 6).trimStart()
    }

    return blocks
}

private fun parseMarkdown(markdown: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val length = markdown.length

        fun headerSize(level: Int): TextUnit = when (level) {
            1 -> 25.sp
            2 -> 21.sp
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
                    val endOfLine = markdown.indexOf('\n', startContent).let { if (it == -1) length else it }
                    val headerText = markdown.substring(startContent, endOfLine).trim()

                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = headerSize(level)
                        )
                    ) {
                        append(headerText)
                    }

                    if (endOfLine < length) append('\n')
                    i = endOfLine + if (endOfLine < length) 1 else 0
                    continue
                }
            }

            if (ch == '*' && i + 1 < length && markdown[i + 1] == '*') {
                val end = markdown.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(markdown.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }

            if (ch == '*' && (i == 0 || markdown[i - 1] != '\\')) {
                val end = markdown.indexOf('*', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(markdown.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }

            if (ch == '_' && i + 1 < length && markdown[i + 1] == '_') {
                val end = markdown.indexOf("__", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(markdown.substring(i + 2, end))
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
