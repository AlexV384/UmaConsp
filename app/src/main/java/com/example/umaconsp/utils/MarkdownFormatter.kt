package com.example.umaconsp.utils

import org.json.JSONArray
import org.json.JSONObject

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
                        "bold" -> "**${escapeHtml(text)}**"
                        "italic" -> "*${escapeHtml(text)}*"
                        "underline" -> "__${escapeHtml(text)}__"
                        else -> escapeHtml(text)
                    }
                    if (isHeader) {
                        styledText = "# $styledText"
                    }
                    styledText
                }
                is JSONArray -> {
                    (0 until text.length())
                        .map { i -> formatToMarkdown(text.get(i)) }
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")
                }
                else -> ""
            }

            if (formattedText.isBlank()) return ""

            when (alignment) {
                "center" -> "<div align=\"center\">$formattedText</div>"
                "right" -> "<div align=\"right\">$formattedText</div>"
                else -> formattedText // left – просто возвращаем текст
            }
        }
        else -> jsonObject.toString()
    }
}

private fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}