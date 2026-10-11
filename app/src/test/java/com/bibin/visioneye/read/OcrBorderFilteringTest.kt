package com.bibin.visioneye.read

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OcrBorderFilteringTest {

    private lateinit var extractor: OcrTextExtractor
    private val imageW = 1200
    private val imageH = 1800

    @Before
    fun setUp() {
        extractor = OcrTextExtractor()
    }

    @Test
    fun legitimatePageNumberNearMargin_isPreserved() {
        // Page number "42" near the right border: left=1160, right=1195 (width=35 > 14px, alphanumeric)
        val isArtifact = extractor.isInvalidOrBorderArtifact(
            left = 1160,
            top = 40,
            right = 1195,
            bottom = 65,
            text = "42",
            imageWidth = imageW,
            imageHeight = imageH
        )
        assertFalse("Legitimate page number must NOT be discarded", isArtifact)
    }

    @Test
    fun legitimateSingleDigitPageNumber_isPreserved() {
        // Standalone digit "1" with valid glyph width (e.g. 18px)
        val isArtifact = extractor.isInvalidOrBorderArtifact(
            left = 1170,
            top = 40,
            right = 1188,
            bottom = 65,
            text = "1",
            imageWidth = imageW,
            imageHeight = imageH
        )
        assertFalse("Single digit page number must NOT be discarded", isArtifact)
    }

    @Test
    fun legitimateChapterHeadingNearTopEdge_isPreserved() {
        // Header "CHAPTER 3" close to top edge (top=1)
        val isArtifact = extractor.isInvalidOrBorderArtifact(
            left = 400,
            top = 1,
            right = 800,
            bottom = 40,
            text = "CHAPTER 3",
            imageWidth = imageW,
            imageHeight = imageH
        )
        assertFalse("Chapter header must NOT be discarded", isArtifact)
    }

    @Test
    fun legitimateFootnoteNearBottomEdge_isPreserved() {
        // Footnote near bottom border (bottom=1799)
        val isArtifact = extractor.isInvalidOrBorderArtifact(
            left = 100,
            top = 1760,
            right = 1100,
            bottom = 1799,
            text = "1. Notes and references on page geometry.",
            imageWidth = imageW,
            imageHeight = imageH
        )
        assertFalse("Footnote near bottom edge must NOT be discarded", isArtifact)
    }

    @Test
    fun slicedSymbolAtLeftBorder_isFiltered() {
        // Sliced edge glyph "|" touching left boundary (left=0, right=6, width=6 < 14)
        val isArtifact = extractor.isInvalidOrBorderArtifact(
            left = 0,
            top = 300,
            right = 6,
            bottom = 330,
            text = "|",
            imageWidth = imageW,
            imageHeight = imageH
        )
        assertTrue("Sliced margin vertical line artifact must be filtered", isArtifact)
    }

    @Test
    fun slicedBracketAtRightBorder_isFiltered() {
        // Sliced bracket "]" touching right boundary (left=1194, right=1199, width=5 < 14)
        val isArtifact = extractor.isInvalidOrBorderArtifact(
            left = 1194,
            top = 500,
            right = 1199,
            bottom = 530,
            text = "]",
            imageWidth = imageW,
            imageHeight = imageH
        )
        assertTrue("Sliced margin bracket artifact must be filtered", isArtifact)
    }

    @Test
    fun degenerateZeroAreaBox_isFiltered() {
        val isArtifact = extractor.isInvalidOrBorderArtifact(
            left = 100,
            top = 100,
            right = 100, // width = 0
            bottom = 120,
            text = "Word",
            imageWidth = imageW,
            imageHeight = imageH
        )
        assertTrue("Zero width box must be filtered", isArtifact)
    }

    @Test
    fun blankOrWhitespaceText_isFiltered() {
        val isArtifact = extractor.isInvalidOrBorderArtifact(
            left = 100,
            top = 100,
            right = 300,
            bottom = 140,
            text = "   \n\t  ",
            imageWidth = imageW,
            imageHeight = imageH
        )
        assertTrue("Whitespace only text must be filtered", isArtifact)
    }

    @Test
    fun completelyOutsideImageBounds_isFiltered() {
        val isLeftOutside = extractor.isInvalidOrBorderArtifact(
            left = -50,
            top = 100,
            right = -2,
            bottom = 140,
            text = "Text",
            imageWidth = imageW,
            imageHeight = imageH
        )
        assertTrue("Box to the left of image must be filtered", isLeftOutside)

        val isRightOutside = extractor.isInvalidOrBorderArtifact(
            left = 1205,
            top = 100,
            right = 1300,
            bottom = 140,
            text = "Text",
            imageWidth = imageW,
            imageHeight = imageH
        )
        assertTrue("Box to the right of image must be filtered", isRightOutside)
    }
}
