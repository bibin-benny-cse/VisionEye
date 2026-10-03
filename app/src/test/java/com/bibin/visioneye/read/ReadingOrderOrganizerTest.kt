package com.bibin.visioneye.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReadingOrderOrganizerTest {

    private lateinit var organizer: ReadingOrderOrganizer

    @Before
    fun setUp() {
        organizer = ReadingOrderOrganizer()
    }

    @Test
    fun organize_emptyList_returnsEmptyString() {
        val result = organizer.organize(emptyList())
        assertEquals("", result)
    }

    @Test
    fun organize_whitespaceOnlyLines_returnsEmptyString() {
        val lines = listOf(
            RecognizedLine("   ", 10f, 10f, 100f, 30f),
            RecognizedLine("\t\n", 10f, 40f, 100f, 60f)
        )
        val result = organizer.organize(lines)
        assertEquals("", result)
    }

    @Test
    fun organize_singleLineWithIrregularWhitespace_normalizesWhitespace() {
        val line = RecognizedLine("  Hello    World   from VisionEye!  ", 10f, 10f, 200f, 30f)
        val result = organizer.organize(listOf(line))
        assertEquals("Hello World from VisionEye!", result)
    }

    @Test
    fun organize_singleColumn_ordersTopToBottom() {
        // Feed lines in reverse order (bottom to top)
        val line3 = RecognizedLine("Third paragraph at bottom.", 50f, 150f, 300f, 170f)
        val line2 = RecognizedLine("Second paragraph in middle.", 50f, 100f, 300f, 120f)
        val line1 = RecognizedLine("First paragraph at top.", 50f, 50f, 300f, 70f)

        val result = organizer.organize(listOf(line3, line2, line1))
        val expected = "First paragraph at top.\nSecond paragraph in middle.\nThird paragraph at bottom."
        assertEquals(expected, result)
    }

    @Test
    fun organize_sameVisualRow_ordersLeftToRight() {
        // Multiple fragments on the same horizontal line with high vertical overlap
        val rightFragment = RecognizedLine("World!", 120f, 52f, 180f, 70f)
        val leftFragment = RecognizedLine("Hello", 40f, 50f, 100f, 68f)

        val result = organizer.organize(listOf(rightFragment, leftFragment))
        assertEquals("Hello World!", result)
    }

    @Test
    fun organize_multiColumnPage_readsLeftColumnBeforeRightColumn() {
        // Two-column layout:
        // Left Column (X ~ 50 to 180):
        //   L1: "Left col line 1" (Y=100)
        //   L2: "Left col line 2" (Y=130)
        //   L3: "Left col line 3" (Y=160)
        // Right Column (X ~ 250 to 380):
        //   R1: "Right col line 1" (Y=100)
        //   R2: "Right col line 2" (Y=130)
        //   R3: "Right col line 3" (Y=160)
        val l1 = RecognizedLine("Left col line 1", 50f, 100f, 180f, 120f)
        val r1 = RecognizedLine("Right col line 1", 250f, 100f, 380f, 120f)
        val l2 = RecognizedLine("Left col line 2", 50f, 130f, 180f, 150f)
        val r2 = RecognizedLine("Right col line 2", 250f, 130f, 380f, 150f)
        val l3 = RecognizedLine("Left col line 3", 50f, 160f, 180f, 180f)
        val r3 = RecognizedLine("Right col line 3", 250f, 160f, 380f, 180f)

        // Shuffled lines
        val shuffled = listOf(r1, l2, r3, l1, r2, l3)
        val result = organizer.organize(shuffled)

        val expectedLeft = "Left col line 1\nLeft col line 2\nLeft col line 3"
        val expectedRight = "Right col line 1\nRight col line 2\nRight col line 3"

        assertTrue("Expected left column to precede right column", result.indexOf(expectedLeft) < result.indexOf(expectedRight))
        assertEquals("$expectedLeft\n\n$expectedRight", result)
    }

    @Test
    fun organize_multiColumnWithHeaderAndFooter_readsInExpectedOrder() {
        // Header spanning across the top (X=50 to 380, Y=30)
        val header = RecognizedLine("DAILY NEWSPAPER TITLE", 50f, 30f, 380f, 55f)

        // Left column lines (Y=80 to 140)
        val l1 = RecognizedLine("Left article start.", 50f, 80f, 180f, 100f)
        val l2 = RecognizedLine("Left article continue.", 50f, 110f, 180f, 130f)
        val l3 = RecognizedLine("Left article end.", 50f, 140f, 180f, 160f)

        // Right column lines (Y=80 to 140)
        val r1 = RecognizedLine("Right article start.", 250f, 80f, 380f, 100f)
        val r2 = RecognizedLine("Right article continue.", 250f, 110f, 380f, 130f)
        val r3 = RecognizedLine("Right article end.", 250f, 140f, 380f, 160f)

        // Footer spanning across the bottom (Y=190)
        val footer = RecognizedLine("Page 1 of 12 — Copyright 2026", 50f, 190f, 380f, 210f)

        val lines = listOf(r2, header, l3, r1, footer, l1, r3, l2)
        val result = organizer.organize(lines)

        val headerIdx = result.indexOf("DAILY NEWSPAPER TITLE")
        val leftIdx = result.indexOf("Left article start.")
        val rightIdx = result.indexOf("Right article start.")
        val footerIdx = result.indexOf("Page 1 of 12 — Copyright 2026")

        assertTrue(headerIdx < leftIdx)
        assertTrue(leftIdx < rightIdx)
        assertTrue(rightIdx < footerIdx)
    }

    @Test
    fun organize_textPreservation_preservesExactWordsAndNumbers() {
        // Strict preservation: no spell correction, no missing word invention
        val lines = listOf(
            RecognizedLine("Section 4.2: Invarient (misspelled intentionally)", 20f, 20f, 300f, 40f),
            RecognizedLine("Formula: x + 2y = 42.5; Alpha: 0.001", 20f, 50f, 300f, 70f)
        )

        val result = organizer.organize(lines)
        assertTrue(result.contains("Invarient (misspelled intentionally)"))
        assertTrue(result.contains("Formula: x + 2y = 42.5; Alpha: 0.001"))
    }
}
