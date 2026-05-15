package com.example.umaconsp.ocr

object OcrPostProcessor {

    private val ocrFixes = mapOf(
        "Encryptign" to "Encryption",
        "биту DES" to "бит у DES",
        "| июля" to "1 июля",
        "исевдослучайной" to "псевдослучайной",
        "аугентичности" to "аутентичности"
    )

    fun applyFixes(text: String): String {
        var result = text
        ocrFixes.forEach { (wrong, right) ->
            result = result.replace(wrong, right)
        }
        return result
    }

    fun fixHeadingNumber(headerText: String): String {
        val regex = Regex("""^(\d{2,})\s+(.*)""")
        val match = regex.find(headerText) ?: return headerText
        val (numStr, rest) = match.destructured
        if (numStr.length == 2 && numStr[0] in '1'..'9' && numStr[1] in '1'..'9') {
            val fixedNum = "${numStr[0]}.${numStr[1]}"
            return "$fixedNum $rest"
        }
        return headerText
    }

    fun textToMarkdown(rawText: String): String {
        val lines = rawText.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { applyFixes(it) }

        val markdown = mutableListOf<String>()
        val paragraphBuf = mutableListOf<String>()
        var listItemBuf: String? = null
        var inList = false

        fun flushParagraph() {
            if (paragraphBuf.isNotEmpty()) {
                markdown.add(paragraphBuf.joinToString(" "))
                paragraphBuf.clear()
            }
        }

        fun flushListItem() {
            if (listItemBuf != null) {
                markdown.add(listItemBuf!!)
                listItemBuf = null
            }
        }

        for (line in lines) {
            val lineFixed = fixHeadingNumber(line)

            val headMatch = Regex("""^(\d+(?:\.\d+)?)\s+(.*)""").find(lineFixed)
            if (headMatch != null) {
                flushParagraph()
                flushListItem()
                inList = false
                val level = if (headMatch.groupValues[1].contains('.')) 2 else 1
                val prefix = "#".repeat(level) + " "
                markdown.add("$prefix${headMatch.groupValues[1]} ${headMatch.groupValues[2]}")
                continue
            }

            val numListMatch = Regex("""^(\d+)\.\s+(.*)""").find(lineFixed)
            if (numListMatch != null) {
                flushParagraph()
                flushListItem()
                inList = true
                listItemBuf = "${numListMatch.groupValues[1]}. ${numListMatch.groupValues[2]}"
                continue
            }

            if (lineFixed.startsWith("•") || lineFixed.startsWith("-") ||
                Regex("""^\.\s+""").containsMatchIn(lineFixed)
            ) {
                flushParagraph()
                flushListItem()
                inList = true
                val clean = lineFixed.replace(Regex("""^[•\-\.]\s*"""), "").trim()
                listItemBuf = "- $clean"
                continue
            }

            if (inList && listItemBuf != null) {
                listItemBuf = listItemBuf + " " + lineFixed
                continue
            }

            flushListItem()
            inList = false
            paragraphBuf.add(lineFixed)
        }

        flushListItem()
        flushParagraph()
        return markdown.joinToString("\n\n")
    }
}