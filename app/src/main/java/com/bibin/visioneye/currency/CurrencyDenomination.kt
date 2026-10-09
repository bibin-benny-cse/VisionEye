package com.bibin.visioneye.currency

/**
 * Enumerates Indian Rupee (INR) banknote denominations recognized by the currency detection model.
 *
 * Preserves the exact 6-class mapping from the training data.yaml:
 * - Class 0 = ₹10
 * - Class 1 = ₹100
 * - Class 2 = ₹20
 * - Class 3 = ₹200
 * - Class 4 = ₹50
 * - Class 5 = ₹500
 */
enum class CurrencyDenomination(
    val classId: Int,
    val numericValue: Int,
    val label: String,
    val spokenName: String,
    val pluralSpokenName: String
) {
    TEN(
        classId = 0,
        numericValue = 10,
        label = "10",
        spokenName = "10 rupee note",
        pluralSpokenName = "10 rupee notes"
    ),
    ONE_HUNDRED(
        classId = 1,
        numericValue = 100,
        label = "100",
        spokenName = "100 rupee note",
        pluralSpokenName = "100 rupee notes"
    ),
    TWENTY(
        classId = 2,
        numericValue = 20,
        label = "20",
        spokenName = "20 rupee note",
        pluralSpokenName = "20 rupee notes"
    ),
    TWO_HUNDRED(
        classId = 3,
        numericValue = 200,
        label = "200",
        spokenName = "200 rupee note",
        pluralSpokenName = "200 rupee notes"
    ),
    FIFTY(
        classId = 4,
        numericValue = 50,
        label = "50",
        spokenName = "50 rupee note",
        pluralSpokenName = "50 rupee notes"
    ),
    FIVE_HUNDRED(
        classId = 5,
        numericValue = 500,
        label = "500",
        spokenName = "500 rupee note",
        pluralSpokenName = "500 rupee notes"
    );

    companion object {
        private val classIdMap = entries.associateBy { it.classId }
        private val labelMap = entries.associateBy { it.label }

        fun fromClassId(classId: Int): CurrencyDenomination? = classIdMap[classId]

        fun fromLabel(label: String): CurrencyDenomination? = labelMap[label.trim()]

        /**
         * Formats a clear, natural spoken utterance for a list of detected denominations.
         * Groups identical notes (e.g. "Two 100 rupee notes") and sorts distinct notes
         * from highest denomination to lowest (e.g. "500 rupee note and 100 rupee note").
         */
        fun formatSpokenAnnouncement(denominations: List<CurrencyDenomination>): String {
            if (denominations.isEmpty()) return ""

            // Group by denomination and sort by numerical value descending
            val counts = denominations.groupingBy { it }.eachCount()
            val sortedEntries = counts.entries.sortedByDescending { it.key.numericValue }

            val phrases = sortedEntries.map { (denom, count) ->
                when (count) {
                    1 -> denom.spokenName
                    2 -> "Two ${denom.pluralSpokenName}"
                    3 -> "Three ${denom.pluralSpokenName}"
                    4 -> "Four ${denom.pluralSpokenName}"
                    5 -> "Five ${denom.pluralSpokenName}"
                    else -> "$count ${denom.pluralSpokenName}"
                }
            }

            return when (phrases.size) {
                1 -> phrases[0]
                2 -> "${phrases[0]} and ${phrases[1]}"
                else -> {
                    val head = phrases.dropLast(1).joinToString(", ")
                    "$head, and ${phrases.last()}"
                }
            }
        }
    }
}
