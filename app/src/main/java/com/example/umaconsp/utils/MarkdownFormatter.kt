package com.example.umaconsp.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * Преобразует JSON-ответ от AI-модели в текст с HTML-разметкой (для совместимости с Compose).
 * Поддерживает:
 * - style: normal, bold, italic, underline
 * - alignment: left, center, right
 * - isHeader: true/false – оборачивает текст в <h1>...</h1>
 *
 * @param jsonObject JSON-объект или массив, полученный от модели.
 * @return Строка с HTML-разметкой.
 */
fun formatToMarkdown(jsonObject: Any): String {
    return when (jsonObject) {
        is JSONArray -> {
            (0 until jsonObject.length())
                .map { i -> formatToMarkdown(jsonObject.get(i)) }
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
        }
        is JSONObject -> {
            val text = jsonObject.opt("text") ?: return ""
            val style = jsonObject.optString("style", "normal")
            val alignment = jsonObject.optString("alignment", "left")
            val isHeader = jsonObject.optBoolean("isHeader", false)

            val formattedText = when (text) {
                is String -> {
                    var styledText = when (style) {
                        "bold" -> "<b>${escapeHtml(text)}</b>"
                        "italic" -> "<i>${escapeHtml(text)}</i>"
                        "underline" -> "<u>${escapeHtml(text)}</u>"
                        else -> escapeHtml(text)
                    }
                    if (isHeader) {
                        styledText = "# $styledText"
                    }
                    styledText
                }
                is JSONArray -> {
                    // Рекурсивно обрабатываем массив внутри поля text
                    (0 until text.length())
                        .map { i -> formatToMarkdown(text.get(i)) }
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")
                }
                else -> ""
            }

            if (formattedText.isBlank()) return ""

            // Применяем выравнивание
            when (alignment) {
                "center" -> "<div align=\"center\">$formattedText</div>"
                "right" -> "<div align=\"right\">$formattedText</div>"
                else -> formattedText // left – просто возвращаем текст
            }
        }
        else -> jsonObject.toString()
    }
}

/**
 * Экранирует HTML-спецсимволы, чтобы текст не ломал разметку.
 */
private fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}