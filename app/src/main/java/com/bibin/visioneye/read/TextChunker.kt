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

        // Split into paragraphs by newline
        val paragraphs = trimmed.split(Regex("\n+"))

        for (paragraph in paragraphs) {
            val pTrimmed = paragraph.trim()
            if (pTrimmed.isEmpty()) continue

            // Split into sentences using punctuation boundaries (. ! ?)
            val sentences = pTrimmed.split(Regex("(?<=[.!?])\\s+"))

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
