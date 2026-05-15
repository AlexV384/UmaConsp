package com.example.umaconsp.ai

interface ResponseParser {
    fun parse(rawResponse: String): String
}

class PlaintextResponseParser : ResponseParser {
    override fun parse(rawResponse: String): String {
        return cleanRawResponse(rawResponse)
    }

    private fun cleanRawResponse(rawResponse: String): String {
        var result = rawResponse.trim()
        result = result.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        result = result.replace(Regex("```(?:json)?\\s*(.*?)\\s*```", RegexOption.DOT_MATCHES_ALL), "$1").trim()
        return result
    }
}