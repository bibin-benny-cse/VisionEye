package com.bibin.visioneye.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TextChunkerTest {

    private lateinit var chunker: TextChunker

    @Before
    fun setUp() {
        // Use a test-friendly chunk limit (e.g. 80 chars) to rigorously test boundaries
        chunker = TextChunker(maxChunkLength = 80)
    }

    @Test
    fun chunk_blankOrEmptyText_returnsEmptyList() {
        assertEquals(emptyList<String>(), chunker.chunk(""))
        assertEquals(emptyList<String>(), chunker.chunk("    "))
        assertEquals(emptyList<String>(), chunker.chunk("\n\n\t"))
    }

    @Test
    fun chunk_shortSentence_returnsSingleChunk() {
        val input = "Hello world from VisionEye."
        val chunks = chunker.chunk(input)

        assertEquals(1, chunks.size)
        assertEquals(input, chunks[0])
    }

    @Test
    fun chunk_multipleSentencesUnderLimit_accumulatesIntoSingleChunk() {
        val input = "Short one. Short two."
        val chunks = chunker.chunk(input)

        assertEquals(1, chunks.size)
        assertEquals("Short one. Short two.", chunks[0])
    }

    @Test
    fun chunk_multipleSentencesExceedingLimit_splitsAtSentenceBoundaries() {
        val input = "This is the first complete sentence. And here is the second complete sentence that is also long."
        val chunks = chunker.chunk(input)

        assertEquals(2, chunks.size)
        assertEquals("This is the first complete sentence.", chunks[0])
        assertEquals("And here is the second complete sentence that is also long.", chunks[1])
    }

    @Test
    fun chunk_longSentenceWithCommas_splitsAtPunctuation() {
        val input = "Although the road was dark and rainy, the traveller continued walking, hoping to find shelter soon."
        val chunks = chunker.chunk(input)

        assertTrue(chunks.size >= 2)
        for (chunk in chunks) {
            assertTrue("Chunk exceeds maxLength: '$chunk'", chunk.length <= 80)
        }
        assertEquals(
            "Although the road was dark and rainy, the traveller continued walking, hoping to find shelter soon.",
            chunks.joinToString(" ")
        )
    }

    @Test
    fun chunk_longUnpunctuatedSentence_splitsAtWordBoundaries() {
        val input = "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty"
        val chunks = chunker.chunk(input)

        assertTrue(chunks.size > 1)
        for (chunk in chunks) {
            assertTrue("Chunk length ${chunk.length} exceeds limit 80", chunk.length <= 80)
            // Ensure no partial words: words shouldn't be sliced in half
            val words = chunk.split(" ")
            for (w in words) {
                assertTrue("Word should not be empty", w.isNotEmpty())
            }
        }

        // Recombined words must match original input
        val recombined = chunks.joinToString(" ")
        assertEquals(input, recombined)
    }

    @Test
    fun chunk_multipleParagraphs_preservesParagraphOrder() {
        val input = "Paragraph one sentence.\n\nParagraph two sentence.\n\nParagraph three sentence."
        val chunks = chunker.chunk(input)

        assertEquals(3, chunks.size)
        assertEquals("Paragraph one sentence.", chunks[0])
        assertEquals("Paragraph two sentence.", chunks[1])
        assertEquals("Paragraph three sentence.", chunks[2])
    }

    @Test
    fun chunk_multilineSentenceInSameParagraph_mergesIntoSingleCoherentSentence() {
        val input = "The quick brown fox jumped\nover the lazy dog in the\ngreen meadow."
        val chunks = chunker.chunk(input)

        assertEquals(1, chunks.size)
        assertEquals("The quick brown fox jumped over the lazy dog in the green meadow.", chunks[0])
    }

    @Test
    fun chunk_hyphenatedWordAcrossLineBreak_healsWordSeamlessly() {
        val input = "Winston Smith felt the experi-\nence of freedom in his heart."
        val chunks = chunker.chunk(input)

        assertEquals(1, chunks.size)
        assertEquals("Winston Smith felt the experience of freedom in his heart.", chunks[0])
    }

    @Test
    fun chunk_sentencesWithAbbreviations_doesNotSplitOnAbbreviation() {
        val input = "Dr. Smith met with Prof. Jones at 10 a.m. yesterday."
        val chunks = chunker.chunk(input)

        assertEquals(1, chunks.size)
        assertEquals("Dr. Smith met with Prof. Jones at 10 a.m. yesterday.", chunks[0])
    }
}
