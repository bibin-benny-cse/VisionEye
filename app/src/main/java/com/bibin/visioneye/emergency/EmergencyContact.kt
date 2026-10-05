package com.bibin.visioneye.emergency

/**
 * Model representing an emergency contact for the VisionEye SOS subsystem.
 *
 * @property name Human-readable name of the contact (e.g. "Mother", "Guardian").
 * @property phoneNumber Phone number configured to receive emergency SMS notifications.
 */
data class EmergencyContact(
    val name: String,
    val phoneNumber: String
) {
    /**
     * Sanitizes and validates the contact fields.
     */
    fun isValid(): Boolean {
        return name.trim().isNotBlank() && isValidPhoneNumber(phoneNumber)
    }

    companion object {
        private val PHONE_REGEX = Regex("""^\+?[0-9]{7,15}$""")

        /**
         * Validates whether a phone number adheres to standard international or local formats.
         *
         * Allows an optional leading '+' followed by 7 to 15 numeric digits after removing
         * formatting characters (spaces, dashes, parentheses).
         */
        fun isValidPhoneNumber(phone: String?): Boolean {
            if (phone.isNullOrBlank()) return false
            val cleaned = phone.replace(Regex("""[\s\-\(\)\.]"""), "")
            return PHONE_REGEX.matches(cleaned)
        }

        /**
         * Sanitizes a phone number by stripping spaces, hyphens, parentheses, and dots.
         */
        fun sanitizePhoneNumber(phone: String): String {
            return phone.replace(Regex("""[\s\-\(\)\.]"""), "")
        }
    }
}
