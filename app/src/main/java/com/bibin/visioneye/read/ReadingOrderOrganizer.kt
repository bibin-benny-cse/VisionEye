package com.bibin.visioneye.read

import kotlin.math.max
import kotlin.math.min

/**
 * Geometric representation of an OCR-recognized line of text with its bounding coordinates.
 */
data class RecognizedLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Organizes raw OCR text lines into natural human reading order based on geometric layout.
 *
 * Rules:
 * - Single-column pages: top-to-bottom, left-to-right within the same visual row.
 * - Multi-column pages: finish the left column completely before reading the next column.
 * - Same visual row: lines with significant vertical overlap (> 40%) are read left-to-right.
 * - Text preservation: retains exact recognized characters; normalizes excessive whitespace.
 *   Strictly DOES NOT perform spelling correction, paraphrasing, or word invention.
 */
class ReadingOrderOrganizer {

    /**
     * Organizes a list of [RecognizedLine] into a coherent reading-order formatted string.
     *
     * @param lines Unordered or arbitrarily ordered text lines from OCR.
     * @return Cleanly ordered, line-preserved English text string.
     */
    fun organize(lines: List<RecognizedLine>): String {
        // 1. Clean individual lines: trim and normalize internal repeated whitespace
        val cleanedLines = lines.mapNotNull { line ->
            val normalized = line.text.replace(Regex("\\s+"), " ").trim()
            if (normalized.isNotEmpty()) {
                line.copy(text = normalized)
            } else {
                null
            }
        }

        if (cleanedLines.isEmpty()) return ""
        if (cleanedLines.size == 1) return cleanedLines.first().text

        // 2. Check for multi-column layout
        val columnPartition = detectColumns(cleanedLines)
        return if (columnPartition != null) {
            val sections = mutableListOf<String>()

            if (columnPartition.headerLines.isNotEmpty()) {
                val headerText = orderSingleColumn(columnPartition.headerLines)
                if (headerText.isNotEmpty()) sections.add(headerText)
            }

            if (columnPartition.leftColumnLines.isNotEmpty()) {
                val leftText = orderSingleColumn(columnPartition.leftColumnLines)
                if (leftText.isNotEmpty()) sections.add(leftText)
            }

            if (columnPartition.rightColumnLines.isNotEmpty()) {
                val rightText = orderSingleColumn(columnPartition.rightColumnLines)
                if (rightText.isNotEmpty()) sections.add(rightText)
            }

            if (columnPartition.footerLines.isNotEmpty()) {
                val footerText = orderSingleColumn(columnPartition.footerLines)
                if (footerText.isNotEmpty()) sections.add(footerText)
            }

            sections.joinToString("\n\n")
        } else {
            // Standard single-column page
            orderSingleColumn(cleanedLines)
        }
    }

    /**
     * Orders lines within a single column:
     * Groups lines into visual horizontal rows, sorts rows top-to-bottom,
     * and sorts elements within each row left-to-right.
     */
    fun orderSingleColumn(lines: List<RecognizedLine>): String {
        if (lines.isEmpty()) return ""
        if (lines.size == 1) return lines.first().text

        // Sort lines primarily by vertical top coordinate
        val sortedByTop = lines.sortedBy { it.top }

        // Group into visual rows where lines share significant vertical overlap
        val rows = mutableListOf<MutableList<RecognizedLine>>()

        for (line in sortedByTop) {
            val matchingRow = rows.lastOrNull { row ->
                val rowTop = row.minOf { it.top }
                val rowBottom = row.maxOf { it.bottom }
                val rowHeight = max(1f, rowBottom - rowTop)
                val lineH = max(1f, line.height)

                val overlap = max(0f, min(rowBottom, line.bottom) - max(rowTop, line.top))
                val overlapRatio = overlap / min(rowHeight, lineH)
                overlapRatio >= 0.40f
            }

            if (matchingRow != null) {
                matchingRow.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }

        // Within each row, sort left-to-right by horizontal coordinate
        val formattedRows = rows.map { row ->
            row.sortedBy { it.left }.joinToString(" ") { it.text }
        }

        return formattedRows.joinToString("\n")
    }

    private data class ColumnPartition(
        val headerLines: List<RecognizedLine>,
        val leftColumnLines: List<RecognizedLine>,
        val rightColumnLines: List<RecognizedLine>,
        val footerLines: List<RecognizedLine>
    )

    private fun detectColumns(lines: List<RecognizedLine>): ColumnPartition? {
        if (lines.size < 6) return null

        val minLeft = lines.minOf { it.left }
        val maxRight = lines.maxOf { it.right }
        val totalSpan = maxRight - minLeft
        if (totalSpan <= 0) return null

        // Check line widths: multi-column pages predominantly have narrow lines
        val sortedWidths = lines.map { it.width }.sorted()
        val medianWidth = sortedWidths[sortedWidths.size / 2]
        if (medianWidth > totalSpan * 0.60f) {
            // Lines are wide; standard single column
            return null
        }

        // Search for a central vertical gutter separating left and right lines
        val searchStart = minLeft + totalSpan * 0.35f
        val searchEnd = minLeft + totalSpan * 0.65f
        val numSteps = 20
        val step = (searchEnd - searchStart) / numSteps

        var bestGutterX = -1f
        var minCrossing = Int.MAX_VALUE
        var bestLeftCount = 0
        var bestRightCount = 0

        for (i in 0..numSteps) {
            val gx = searchStart + i * step
            var leftCount = 0
            var rightCount = 0
            var crossingCount = 0

            for (line in lines) {
                if (line.right <= gx + 2f) {
                    leftCount++
                } else if (line.left >= gx - 2f) {
                    rightCount++
                } else {
                    crossingCount++
                }
            }

            if (leftCount >= 2 && rightCount >= 2 && crossingCount < minCrossing) {
                minCrossing = crossingCount
                bestGutterX = gx
                bestLeftCount = leftCount
                bestRightCount = rightCount
            }
        }

        // Validate gutter quality: crossing lines must be minimal (e.g. at most 20% of lines)
        val maxAllowedCrossing = max(2, (lines.size * 0.20f).toInt())
        if (bestGutterX < 0 || minCrossing > maxAllowedCrossing || bestLeftCount < 2 || bestRightCount < 2) {
            return null
        }

        // Separate header/footer lines that cross the gutter from the column lines
        val gutter = bestGutterX
        val leftLines = mutableListOf<RecognizedLine>()
        val rightLines = mutableListOf<RecognizedLine>()
        val crossingLines = mutableListOf<RecognizedLine>()

        for (line in lines) {
            when {
                line.right <= gutter + 2f -> leftLines.add(line)
                line.left >= gutter - 2f -> rightLines.add(line)
                else -> crossingLines.add(line)
            }
        }

        val columnTop = min(
            leftLines.minOfOrNull { it.top } ?: Float.MAX_VALUE,
            rightLines.minOfOrNull { it.top } ?: Float.MAX_VALUE
        )
        val columnBottom = max(
            leftLines.maxOfOrNull { it.bottom } ?: Float.MIN_VALUE,
            rightLines.maxOfOrNull { it.bottom } ?: Float.MIN_VALUE
        )

        val headerLines = mutableListOf<RecognizedLine>()
        val footerLines = mutableListOf<RecognizedLine>()

        for (line in crossingLines) {
            if (line.bottom <= columnTop + 10f) {
                headerLines.add(line)
            } else if (line.top >= columnBottom - 10f) {
                footerLines.add(line)
            } else {
                // Ambiguous crossing: assign to whichever side contains its center
                if (line.centerX < gutter) {
                    leftLines.add(line)
                } else {
                    rightLines.add(line)
                }
            }
        }

        return ColumnPartition(
            headerLines = headerLines,
            leftColumnLines = leftLines,
            rightColumnLines = rightLines,
            footerLines = footerLines
        )
    }
}
