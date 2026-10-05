package com.bibin.visioneye.navigation.routing

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Mockable service contract for resolving destination queries to geographic coordinates.
 */
interface GeocodingService {
    /**
     * Resolves [query] into a [GeocodingResult].
     *
     * Never throws exceptions; maps all network, timeout, or parsing failures to [GeocodingResult.Error].
     */
    suspend fun geocode(query: String): GeocodingResult
}

/**
 * Deterministic JSON parser for OpenStreetMap Nominatim responses.
 * Decoupled from the HTTP transport layer to enable fast, offline unit testing.
 */
object NominatimGeocodingParser {
    /**
     * Parses a Nominatim JSON response array.
     *
     * @param jsonString Raw response payload string.
     * @return [GeocodingResult.Success] on valid match, [GeocodingResult.NoResults] if empty array,
     * or [GeocodingResult.Error] if malformed.
     */
    fun parse(jsonString: String): GeocodingResult {
        if (jsonString.isBlank()) {
            return GeocodingResult.Error("Empty response body from geocoding server")
        }
        return try {
            val trimmed = jsonString.trim()
            if (!trimmed.startsWith("[")) {
                return GeocodingResult.Error("Malformed response: expected JSON array but received object or invalid text")
            }
            val jsonArray = JSONArray(trimmed)
            if (jsonArray.length() == 0) {
                return GeocodingResult.NoResults
            }
            val first = jsonArray.getJSONObject(0)
            val displayName = first.optString("display_name", "").trim()
            val latStr = first.optString("lat", "")
            val lonStr = first.optString("lon", "")

            val lat = latStr.toDoubleOrNull()
            val lon = lonStr.toDoubleOrNull()

            if (lat == null || lon == null) {
                return GeocodingResult.Error("Missing or invalid latitude/longitude in geocoding result")
            }

            GeocodingResult.Success(
                displayName = if (displayName.isNotBlank()) displayName else "Lat: $lat, Lon: $lon",
                latitude = lat,
                longitude = lon
            )
        } catch (e: Exception) {
            GeocodingResult.Error("Malformed JSON response: ${e.message}", e)
        }
    }
}

/**
 * Production implementation of [GeocodingService] querying OpenStreetMap Nominatim.
 *
 * @param baseUrl Base URL of Nominatim search endpoint (configurable for tests or self-hosted instances).
 * @param client OkHttpClient instance with configured timeouts.
 * @param ioDispatcher Coroutine dispatcher for executing blocking HTTP operations off the UI thread.
 * @param userAgent Header identifying the application in compliance with OSM usage policy.
 */
class NominatimGeocodingService(
    private val baseUrl: String = "https://nominatim.openstreetmap.org/search",
    private val client: OkHttpClient = defaultClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val userAgent: String = "VisionEye-AcademicPrototype/1.0 (Android; Accessibility-Research)"
) : GeocodingService {

    override suspend fun geocode(query: String): GeocodingResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return GeocodingResult.EmptyQuery
        }

        return withContext(ioDispatcher) {
            try {
                val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
                val url = if (baseUrl.contains("?")) {
                    "$baseUrl&q=$encodedQuery&format=json&limit=1"
                } else {
                    "$baseUrl?q=$encodedQuery&format=json&limit=1"
                }

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext GeocodingResult.Error(
                            "Geocoding request failed with HTTP ${response.code}: ${response.message}"
                        )
                    }
                    val bodyString = response.body?.string() ?: ""
                    NominatimGeocodingParser.parse(bodyString)
                }
            } catch (e: IOException) {
                GeocodingResult.Error("Network failure during geocoding: ${e.message}", e)
            } catch (e: Exception) {
                GeocodingResult.Error("Unexpected geocoding error: ${e.message}", e)
            }
        }
    }

    companion object {
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }
}
