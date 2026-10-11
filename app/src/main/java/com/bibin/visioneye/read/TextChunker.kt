package com.bibin.visioneye.read

/**
 * Splits formatted document text into sequential, speech-safe chunks for Text-To-Speech playback.
 *
 * Avoids flooding the TTS audio buffer with long multi-paragraph documents, while
 * preserving natural speech cadences by breaking cleanly at sentence or clause boundaries.
 *
 * @property maxChunkLength Target maximum character length for a single spoken chunk (default: 180 chars).
 */
class TextChunker(
    val maxChunkLength: Int = 180
) {
    /**
     * Splits [text] into an ordered list of speech-safe chunks.
     *
     * @param text Clean extracted document text.
     * @return List of sequential non-empty strings suitable for TTS.
     */
    fun chunk(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()

        // Split into paragraphs by double-newline
        val paragraphs = trimmed.split(Regex("(?:\r?\n){2,}"))

        for (paragraph in paragraphs) {
            // Merge hyphenated linebreaks across words
            val unhyphenated = paragraph.replace(Regex("([a-zA-Z])-[\t ]*\r?\n[\t ]*([a-zA-Z])"), "$1$2")
            // Merge single linebreaks into spaces within the paragraph
            val mergedLines = unhyphenated.replace(Regex("[\t ]*\r?\n[\t ]*"), " ")
            val pTrimmed = mergedLines.replace(Regex("\\s+"), " ").trim()
            if (pTrimmed.isEmpty()) continue

            // Split into clean sentences
            val sentences = splitSentences(pTrimmed)

            var currentChunk = StringBuilder()

            for (sentence in sentences) {
                val sTrimmed = sentence.trim()
                if (sTrimmed.isEmpty()) continue

                if (sTrimmed.length > maxChunkLength) {
                    // Flush accumulated sentences first
                    if (currentChunk.isNotEmpty()) {
                        chunks.add(currentChunk.toString().trim())
                        currentChunk = StringBuilder()
                    }
                    // Break long sentence into sub-clause fragments
                    val subClauses = splitLongSentence(sTrimmed, maxChunkLength)
                    chunks.addAll(subClauses)
                } else if (currentChunk.length + sTrimmed.length + 1 <= maxChunkLength) {
                    if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                    currentChunk.append(sTrimmed)
                } else {
                    if (currentChunk.isNotEmpty()) {
                        chunks.add(currentChunk.toString().trim())
                    }
                    currentChunk = StringBuilder(sTrimmed)
                }
            }

            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
            }
        }

        return chunks.filter { it.isNotBlank() }
    }

    /**
     * Splits a paragraph into complete sentences while preventing false splits
     * on common abbreviations ("Dr.", "Mr.", "U.S.") or initials.
     */
    internal fun splitSentences(paragraph: String): List<String> {
        val rawTokens = paragraph.split(Regex("(?<=[.!?])\\s+"))
        if (rawTokens.size <= 1) return rawTokens.filter { it.isNotBlank() }

        val abbreviations = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "vs", "etc",
            "eg", "ie", "no", "vol", "pp", "p", "fig", "approx", "est", "st", "al"
        )

        val sentences = mutableListOf<String>()
        var currentSentence = StringBuilder()

        for (i in rawTokens.indices) {
            val token = rawTokens[i].trim()
            if (token.isEmpty()) continue

            if (currentSentence.isNotEmpty()) {
                currentSentence.append(" ")
            }
            currentSentence.append(token)

            // Check if currentSentence ends with a true sentence boundary
            val lastWord = currentSentence.split(Regex("\\s+")).lastOrNull()?.trimEnd('.', '!', '?')?.lowercase() ?: ""
            val isAbbr = abbreviations.contains(lastWord)
            val isSingleInitial = lastWord.length == 1 && lastWord[0].isLetter()

            // If it ends with . and is an abbreviation or initial, do not treat as sentence end unless it's the last token
            if (i < rawTokens.size - 1 && (isAbbr || isSingleInitial)) {
                continue
            }

            // Valid sentence boundary reached
            sentences.add(currentSentence.toString().trim())
            currentSentence = StringBuilder()
        }

        if (currentSentence.isNotEmpty()) {
            sentences.add(currentSentence.toString().trim())
        }

        return sentences.filter { it.isNotBlank() }
    }

    private fun splitLongSentence(sentence: String, maxLength: Int): List<String> {
        val result = mutableListOf<String>()

        // Try splitting by secondary punctuation: comma, semicolon, colon
        val clauses = sentence.split(Regex("(?<=[,;:])\\s+"))
        var current = StringBuilder()

        for (clause in clauses) {
            val c = clause.trim()
            if (c.isEmpty()) continue

            if (c.length > maxLength) {
                if (current.isNotEmpty()) {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                // Split long fragment by whitespace without breaking words
                result.addAll(splitByWordBoundaries(c, maxLength))
            } else if (current.length + c.length + 1 <= maxLength) {
                if (current.isNotEmpty()) current.append(" ")
                current.append(c)
            } else {
                if (current.isNotEmpty()) {
                    result.add(current.toString().trim())
                }
                current = StringBuilder(c)
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }

        return result
    }

    private fun splitByWordBoundaries(text: String, maxLength: Int): List<String> {
        val words = text.split(Regex("\\s+"))
        val result = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            if (current.length + word.length + 1 <= maxLength) {
                if (current.isNotEmpty()) current.append(" ")
                current.append(word)
            } else {
                if (current.isNotEmpty()) {
                    result.add(current.toString().trim())
                }
                current = StringBuilder(word)
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }

        return result
    }
}
