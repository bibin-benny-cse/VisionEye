package com.bibin.visioneye.navigation.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Deterministic unit tests for [GeocodingService] and [NominatimGeocodingParser].
 *
 * Verifies:
 * - Empty / blank queries
 * - Parsing valid Nominatim JSON array response
 * - Zero results (empty JSON array)
 * - Malformed JSON responses
 * - HTTP error status codes (e.g. 500, 404)
 * - Network failures / connection timeouts
 * - Proper URL encoding of destination queries
 */
class GeocodingServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var service: NominatimGeocodingService

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

        service = NominatimGeocodingService(
            baseUrl = mockWebServer.url("/search").toString(),
            client = client,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `empty and blank queries return EmptyQuery without making network requests`() = runBlocking {
        val resultEmpty = service.geocode("")
        assertTrue(resultEmpty is GeocodingResult.EmptyQuery)

        val resultWhitespace = service.geocode("   \t\n  ")
        assertTrue(resultWhitespace is GeocodingResult.EmptyQuery)

        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `valid result correctly parses displayName and coordinates`() = runBlocking {
        val sampleJson = """
            [
              {
                "place_id": 123456,
                "lat": "12.9715987",
                "lon": "77.5945627",
                "display_name": "MG Road, Bengaluru, Karnataka, India"
              }
            ]
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(sampleJson)
        )

        val result = service.geocode("MG Road")
        assertTrue("Expected Success, got: $result", result is GeocodingResult.Success)

        val success = result as GeocodingResult.Success
        assertEquals("MG Road, Bengaluru, Karnataka, India", success.displayName)
        assertEquals(12.9715987, success.latitude, 0.000001)
        assertEquals(77.5945627, success.longitude, 0.000001)

        val recordedRequest = mockWebServer.takeRequest()
        assertTrue(recordedRequest.path?.contains("q=MG+Road") == true || recordedRequest.path?.contains("q=MG%20Road") == true)
        assertEquals("application/json", recordedRequest.getHeader("Accept"))
        assertNotNull(recordedRequest.getHeader("User-Agent"))
    }

    @Test
    fun `zero results returns NoResults`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        val result = service.geocode("Nonexistent Place 9999")
        assertTrue("Expected NoResults, got: $result", result is GeocodingResult.NoResults)
    }

    @Test
    fun `malformed response returns Error without crashing`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{ not an array and invalid json")
        )

        val result = service.geocode("Library")
        assertTrue("Expected Error, got: $result", result is GeocodingResult.Error)
        val error = result as GeocodingResult.Error
        assertTrue(error.message.contains("Malformed", ignoreCase = true))
    }

    @Test
    fun `missing coordinates in json returns Error`() {
        val invalidCoordJson = """
            [
              {
                "display_name": "Missing coords",
                "lat": "invalid_lat",
                "lon": "77.5"
              }
            ]
        """.trimIndent()

        val parsed = NominatimGeocodingParser.parse(invalidCoordJson)
        assertTrue(parsed is GeocodingResult.Error)
    }

    @Test
    fun `HTTP error response returns Error with status code`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val result = service.geocode("Central Park")
        assertTrue(result is GeocodingResult.Error)
        val error = result as GeocodingResult.Error
        assertTrue(error.message.contains("500"))
    }

    @Test
    fun `network failure returns Error without throwing unhandled exception`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )

        val result = service.geocode("Airport")
        assertTrue(result is GeocodingResult.Error)
        val error = result as GeocodingResult.Error
        assertTrue(error.message.contains("failure", ignoreCase = true))
    }

    @Test
    fun `query parameters are properly URL encoded`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]")
        )

        service.geocode("St. John's & Baker Street #4")
        val req = mockWebServer.takeRequest()
        assertTrue(req.path?.contains("St.+John%27s+%26+Baker+Street+%234") == true ||
                   req.path?.contains("St.%20John") == true)
    }
}
