package com.example.umaconsp.ai

import org.json.JSONArray
import org.json.JSONObject

interface ResponseParser {
    fun parse(rawResponse: String): String
}

class DefaultResponseParser : ResponseParser {
    override fun parse(rawResponse: String): String {
        return try {
            val trimmed = rawResponse.trim()
            val jsonObject = JSONObject(trimmed)

            when {
                jsonObject.has("error") -> "Ошибка: ${jsonObject.getString("error")}"
                jsonObject.has("text") -> {
                    val textField = jsonObject.get("text")
                    when (textField) {
                        is String -> {
                            formatToMarkdown(JSONObject().put("text", textField))
                        }
                        is JSONArray -> {
                            formatToMarkdown(textField)
                        }
                        else -> "Не удалось распознать текст"
                    }
                }
                else -> "Не удалось распознать текст"
            }
        } catch (e: Exception) {
            "Ошибка парсинга ответа: ${e.message}"
        }
    }

    private fun formatToMarkdown(jsonObject: Any): String {
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
                    else -> formattedText
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
}