package com.bibin.visioneye.navigation.routing

/**
 * Result of a geocoding operation to resolve a destination query into geographic coordinates.
 */
sealed interface GeocodingResult {
    /**
     * Successfully resolved a geographic destination.
     *
     * @property displayName Formatted readable address or landmark name.
     * @property latitude WGS84 latitude coordinate [-90.0, 90.0].
     * @property longitude WGS84 longitude coordinate [-180.0, 180.0].
     */
    data class Success(
        val displayName: String,
        val latitude: Double,
        val longitude: Double
    ) : GeocodingResult

    /**
     * Input destination query was empty, blank, or whitespace.
     */
    data object EmptyQuery : GeocodingResult

    /**
     * Valid query executed but zero geographic locations were found.
     */
    data object NoResults : GeocodingResult

    /**
     * Operation failed due to network failure, HTTP error, timeout, or malformed payload.
     *
     * @property message Human-readable error description.
     * @property cause Underlying exception if available.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : GeocodingResult
}
