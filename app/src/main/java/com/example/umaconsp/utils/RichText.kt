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

/**
 * Рендерит HTML с поддержкой тегов <b>, <i>, <u>, <div align="..."> и Markdown-заголовков (#).
 * Заголовки уровня 1-3: #, ##, ### (пробел после # обязателен) преобразуются в жирный текст с увеличенным шрифтом.
 *
 * @param html Строка с HTML-разметкой
 * @param modifier Модификатор для Column
 * @param color Цвет текста
 */
@Composable
fun RichText(
    html: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = parseHtmlBlocks(html)
    Column(modifier = modifier) {
        blocks.forEach { block ->
            BasicText(
                text = buildStyledText(block.text, color),
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

private fun parseHtmlBlocks(html: String): List<HtmlBlock> {
    val blocks = mutableListOf<HtmlBlock>()
    var remaining = html
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
private fun buildStyledText(html: String, color: Color): AnnotatedString {
    return buildAnnotatedString {
        val styleStack = mutableListOf<SpanStyle>()
        var i = 0
        val length = html.length
        val defaultFontSize = MaterialTheme.typography.bodyLarge.fontSize

        fun fontSizeForHeader(level: Int): TextUnit = when (level) {
            1 -> 24.sp
            2 -> 20.sp
            3 -> 18.sp
            else -> 16.sp
        }

        while (i < length) {
            val ch = html[i]
            if (ch == '<') {
                val endTag = html.indexOf('>', i)
                if (endTag == -1) {
                    append(html.substring(i))
                    break
                }
                val tag = html.substring(i + 1, endTag).trim()
                i = endTag + 1

                when {
                    tag.startsWith("b") || tag == "strong" -> {
                        styleStack.add(SpanStyle(fontWeight = FontWeight.Bold))
                        pushStyle(styleStack.last())
                    }
                    tag.startsWith("/b") || tag == "/strong" -> {
                        if (styleStack.isNotEmpty()) styleStack.removeAt(styleStack.lastIndex)
                        if (styleStack.isNotEmpty()) pushStyle(styleStack.last())
                        else pop()
                    }
                    tag.startsWith("i") || tag == "em" -> {
                        styleStack.add(SpanStyle(fontStyle = FontStyle.Italic))
                        pushStyle(styleStack.last())
                    }
                    tag.startsWith("/i") || tag == "/em" -> {
                        if (styleStack.isNotEmpty()) styleStack.removeAt(styleStack.lastIndex)
                        if (styleStack.isNotEmpty()) pushStyle(styleStack.last())
                        else pop()
                    }
                    tag.startsWith("u") -> {
                        styleStack.add(SpanStyle(textDecoration = TextDecoration.Underline))
                        pushStyle(styleStack.last())
                    }
                    tag.startsWith("/u") -> {
                        if (styleStack.isNotEmpty()) styleStack.removeAt(styleStack.lastIndex)
                        if (styleStack.isNotEmpty()) pushStyle(styleStack.last())
                        else pop()
                    }
                    // Игнорируем прочие теги (div, p и т.д.)
                }
            } else {
                val nextTag = html.indexOf('<', i)
                val segment = if (nextTag == -1) html.substring(i) else html.substring(i, nextTag)
                // Разбиваем сегмент на строки для поддержки заголовков Markdown
                val lines = segment.split('\n')
                for ((lineIndex, rawLine) in lines.withIndex()) {
                    val trimmedStart = rawLine.trimStart()
                    if (trimmedStart.startsWith("#") && trimmedStart.length > 1 && trimmedStart[1] == ' ') {
                        // Заголовок Markdown: считаем количество '#' в начале
                        val headerLevel = trimmedStart.takeWhile { it == '#' }.length
                        val headerText = trimmedStart.substring(headerLevel).trimStart()
                        val headerStyle = SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = fontSizeForHeader(headerLevel)
                        )
                        withStyle(headerStyle) {
                            append(headerText)
                        }
                    } else {
                        // Обычная строка
                        append(rawLine)
                    }
                    if (lineIndex < lines.size - 1) append('\n')
                }
                i += segment.length
            }
        }
    }
}