package com.bibin.visioneye.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [CurrencyDenomination] verifying class mappings,
 * numeric values, labels, and spoken phrase formatting.
 */
class CurrencyDenominationTest {

    @Test
    fun classMapping_preservesTrainedYamlClassesExactly() {
        // Requirement 4:
        // 0 -> ₹10
        // 1 -> ₹100
        // 2 -> ₹20
        // 3 -> ₹200
        // 4 -> ₹50
        // 5 -> ₹500
        assertEquals(CurrencyDenomination.TEN, CurrencyDenomination.fromClassId(0))
        assertEquals(CurrencyDenomination.ONE_HUNDRED, CurrencyDenomination.fromClassId(1))
        assertEquals(CurrencyDenomination.TWENTY, CurrencyDenomination.fromClassId(2))
        assertEquals(CurrencyDenomination.TWO_HUNDRED, CurrencyDenomination.fromClassId(3))
        assertEquals(CurrencyDenomination.FIFTY, CurrencyDenomination.fromClassId(4))
        assertEquals(CurrencyDenomination.FIVE_HUNDRED, CurrencyDenomination.fromClassId(5))

        // Check reverse classId mapping
        assertEquals(0, CurrencyDenomination.TEN.classId)
        assertEquals(1, CurrencyDenomination.ONE_HUNDRED.classId)
        assertEquals(2, CurrencyDenomination.TWENTY.classId)
        assertEquals(3, CurrencyDenomination.TWO_HUNDRED.classId)
        assertEquals(4, CurrencyDenomination.FIFTY.classId)
        assertEquals(5, CurrencyDenomination.FIVE_HUNDRED.classId)
    }

    @Test
    fun numericValues_areAccurate() {
        assertEquals(10, CurrencyDenomination.TEN.numericValue)
        assertEquals(100, CurrencyDenomination.ONE_HUNDRED.numericValue)
        assertEquals(20, CurrencyDenomination.TWENTY.numericValue)
        assertEquals(200, CurrencyDenomination.TWO_HUNDRED.numericValue)
        assertEquals(50, CurrencyDenomination.FIFTY.numericValue)
        assertEquals(500, CurrencyDenomination.FIVE_HUNDRED.numericValue)
    }

    @Test
    fun fromClassId_outOfBounds_returnsNull() {
        assertNull(CurrencyDenomination.fromClassId(-1))
        assertNull(CurrencyDenomination.fromClassId(6))
        assertNull(CurrencyDenomination.fromClassId(100))
    }

    @Test
    fun fromLabel_resolvesAllDenominations() {
        assertEquals(CurrencyDenomination.TEN, CurrencyDenomination.fromLabel("10"))
        assertEquals(CurrencyDenomination.ONE_HUNDRED, CurrencyDenomination.fromLabel("100"))
        assertEquals(CurrencyDenomination.TWENTY, CurrencyDenomination.fromLabel("20"))
        assertEquals(CurrencyDenomination.TWO_HUNDRED, CurrencyDenomination.fromLabel("200"))
        assertEquals(CurrencyDenomination.FIFTY, CurrencyDenomination.fromLabel("50"))
        assertEquals(CurrencyDenomination.FIVE_HUNDRED, CurrencyDenomination.fromLabel("500"))

        // With trimming
        assertEquals(CurrencyDenomination.FIVE_HUNDRED, CurrencyDenomination.fromLabel("  500  "))
        assertNull(CurrencyDenomination.fromLabel("unknown"))
    }

    @Test
    fun formatSpokenAnnouncement_emptyList_returnsEmpty() {
        assertEquals("", CurrencyDenomination.formatSpokenAnnouncement(emptyList()))
    }

    @Test
    fun formatSpokenAnnouncement_singleNote_clearPhrasing() {
        assertEquals("10 rupee note", CurrencyDenomination.formatSpokenAnnouncement(listOf(CurrencyDenomination.TEN)))
        assertEquals("500 rupee note", CurrencyDenomination.formatSpokenAnnouncement(listOf(CurrencyDenomination.FIVE_HUNDRED)))
        assertEquals("100 rupee note", CurrencyDenomination.formatSpokenAnnouncement(listOf(CurrencyDenomination.ONE_HUNDRED)))
    }

    @Test
    fun formatSpokenAnnouncement_multipleIdenticalNotes_pluralPhrase() {
        assertEquals(
            "Two 100 rupee notes",
            CurrencyDenomination.formatSpokenAnnouncement(listOf(CurrencyDenomination.ONE_HUNDRED, CurrencyDenomination.ONE_HUNDRED))
        )
        assertEquals(
            "Three 500 rupee notes",
            CurrencyDenomination.formatSpokenAnnouncement(
                listOf(CurrencyDenomination.FIVE_HUNDRED, CurrencyDenomination.FIVE_HUNDRED, CurrencyDenomination.FIVE_HUNDRED)
            )
        )
    }

    @Test
    fun formatSpokenAnnouncement_multipleDistinctNotes_sortedDescendingAndConjoined() {
        val notes = listOf(CurrencyDenomination.TEN, CurrencyDenomination.FIVE_HUNDRED)
        // 500 is higher than 10, so it appears first
        assertEquals(
            "500 rupee note and 10 rupee note",
            CurrencyDenomination.formatSpokenAnnouncement(notes)
        )

        val threeNotes = listOf(CurrencyDenomination.TWENTY, CurrencyDenomination.FIVE_HUNDRED, CurrencyDenomination.ONE_HUNDRED)
        assertEquals(
            "500 rupee note, 100 rupee note, and 20 rupee note",
            CurrencyDenomination.formatSpokenAnnouncement(threeNotes)
        )
    }
}
