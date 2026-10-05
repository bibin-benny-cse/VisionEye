package com.bibin.visioneye.emergency

import com.bibin.visioneye.navigation.location.LocationFix

/**
 * Generator and validator for emergency SOS SMS messages.
 *
 * Formats concise, actionable distress messages containing:
 * - Clear identification of the assistance request
 * - Universal web map link when valid coordinates are present
 * - Explicit indication when location is approximate or unavailable
 * - Zero unnecessary personal or tracking data
 */
object EmergencyMessageFormatter {

    const val BASE_ALERT = "VisionEye SOS: Emergency assistance requested."

    /**
     * Constructs a web-accessible map link using Google Maps query syntax.
     */
    fun formatMapLink(latitude: Double, longitude: Double): String {
        return "https://maps.google.com/?q=$latitude,$longitude"
    }

    /**
     * Generates the complete SMS content based on current location availability.
     *
     * @param locationFix Current valid location fix, or null if GPS was not acquired.
     * @return Formatted plain-text message suitable for standard SMS transmission.
     */
    fun formatMessage(locationFix: LocationFix?): String {
        return if (locationFix != null && locationFix.latitude != 0.0 && locationFix.longitude != 0.0) {
            val mapLink = formatMapLink(locationFix.latitude, locationFix.longitude)
            val accuracySuffix = if (locationFix.accuracyMeters < 1000f) {
                " (~${locationFix.accuracyMeters.toInt()}m accuracy)"
            } else ""
            "$BASE_ALERT\nLocation: $mapLink\nApproximate coords: ${locationFix.latitude}, ${locationFix.longitude}$accuracySuffix"
        } else {
            "$BASE_ALERT\nLocation unavailable."
        }
    }
}
