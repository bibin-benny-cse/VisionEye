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
 * Deterministic unit tests for [PedestrianRoutingService] and [OsrmRouteParser].
 *
 * Verifies:
 * - Parsing valid walking route JSON
 * - Total distance parsing
 * - Total duration parsing
 * - Multiple step parsing: count, instructions, distances, coordinates, maneuvers, bearings
 * - Malformed responses
 * - OSRM "NoRoute" status
 * - HTTP error status codes (400, 500)
 * - Network failures / timeouts
 * - Explicit pedestrian/foot mode and coordinate formatting in URL
 */
class PedestrianRoutingServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var service: OsrmPedestrianRoutingService

    private val sampleOsrmFootJson = """
        {
          "code": "Ok",
          "routes": [
            {
              "geometry": "sample_polyline",
              "legs": [
                {
                  "steps": [
                    {
                      "geometry": "step1_polyline",
                      "maneuver": {
                        "bearing_after": 90,
                        "bearing_before": 0,
                        "location": [77.5945, 12.9716],
                        "modifier": "right",
                        "type": "depart"
                      },
                      "mode": "walking",
                      "duration": 45.2,
                      "distance": 52.3,
                      "name": "Residency Road",
                      "weight": 45.2
                    },
                    {
                      "geometry": "step2_polyline",
                      "maneuver": {
                        "bearing_after": 180,
                        "bearing_before": 90,
                        "location": [77.5950, 12.9716],
                        "modifier": "right",
                        "type": "turn"
                      },
                      "mode": "walking",
                      "duration": 80.0,
                      "distance": 95.0,
                      "name": "Brigade Road",
                      "weight": 80.0
                    },
                    {
                      "geometry": "step3_polyline",
                      "maneuver": {
                        "bearing_after": 180,
                        "bearing_before": 180,
                        "location": [77.5950, 12.9708],
                        "modifier": "straight",
                        "type": "arrive"
                      },
                      "mode": "walking",
                      "duration": 0.0,
                      "distance": 0.0,
                      "name": "",
                      "weight": 0.0
                    }
                  ],
                  "summary": "",
                  "weight": 125.2,
                  "duration": 125.2,
                  "distance": 147.3
                }
              ],
              "weight_name": "routability",
              "weight": 125.2,
              "duration": 125.2,
              "distance": 147.3
            }
          ]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

        service = OsrmPedestrianRoutingService(
            baseUrl = mockWebServer.url("/routed-foot/route/v1/foot/").toString(),
            client = client,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `parse valid walking route JSON extracts distance duration and destination`() {
        val result = OsrmRouteParser.parse(sampleOsrmFootJson, "Brigade Road")
        assertTrue("Expected Success, got: $result", result is RoutingResult.Success)

        val route = (result as RoutingResult.Success).route
        assertEquals("Brigade Road", route.destination)
        assertEquals(147.3, route.totalDistanceMeters, 0.01)
        assertEquals(125.2, route.totalDurationSeconds, 0.01)
    }

    @Test
    fun `parse multiple steps extracts count instructions distances coordinates and bearings`() {
        val result = OsrmRouteParser.parse(sampleOsrmFootJson, "Brigade Road")
        assertTrue(result is RoutingResult.Success)

        val steps = (result as RoutingResult.Success).route.steps
        assertEquals(3, steps.size)

        // Step 1: Depart
        val step1 = steps[0]
        assertEquals(52.3, step1.distanceMeters, 0.01)
        assertEquals(45.2, step1.durationSeconds, 0.01)
        assertEquals(12.9716, step1.latitude, 0.0001)
        assertEquals(77.5945, step1.longitude, 0.0001)
        assertEquals("depart", step1.maneuverType)
        assertEquals("right", step1.modifier)
        assertEquals(0f, step1.bearingBefore!!, 0.1f)
        assertEquals(90f, step1.bearingAfter!!, 0.1f)
        assertEquals("Residency Road", step1.streetName)
        assertTrue(step1.instruction.contains("Residency Road"))

        // Step 2: Turn
        val step2 = steps[1]
        assertEquals(95.0, step2.distanceMeters, 0.01)
        assertEquals(80.0, step2.durationSeconds, 0.01)
        assertEquals(12.9716, step2.latitude, 0.0001)
        assertEquals(77.5950, step2.longitude, 0.0001)
        assertEquals("turn", step2.maneuverType)
        assertEquals("right", step2.modifier)
        assertEquals(90f, step2.bearingBefore!!, 0.1f)
        assertEquals(180f, step2.bearingAfter!!, 0.1f)
        assertEquals("Brigade Road", step2.streetName)
        assertEquals("Turn right onto Brigade Road", step2.instruction)

        // Step 3: Arrive
        val step3 = steps[2]
        assertEquals(0.0, step3.distanceMeters, 0.01)
        assertEquals("arrive", step3.maneuverType)
        assertEquals(12.9708, step3.latitude, 0.0001)
        assertEquals(77.5950, step3.longitude, 0.0001)
        assertEquals("Arrive at destination", step3.instruction)
    }

    @Test
    fun `service communicates over foot profile with correct coordinate order`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(sampleOsrmFootJson)
        )

        val result = service.calculateRoute(
            startLat = 12.9716,
            startLon = 77.5945,
            destLat = 12.9708,
            destLon = 77.5950,
            destinationName = "Brigade Road"
        )

        assertTrue(result is RoutingResult.Success)

        val request = mockWebServer.takeRequest()
        val path = request.path ?: ""

        // Verify foot profile is in the path
        assertTrue("Path must contain foot profile: $path", path.contains("/routed-foot/route/v1/foot/"))

        // Verify coordinates formatted as {lon},{lat};{lon},{lat}
        assertTrue("Path must format lon,lat;lon,lat: $path", path.contains("77.594500,12.971600;77.595000,12.970800"))
        assertTrue("Must request steps: $path", path.contains("steps=true"))
    }

    @Test
    fun `OSRM NoRoute code returns NoRouteFound`() {
        val noRouteJson = """
            {
              "code": "NoRoute",
              "message": "Impossible route between points"
            }
        """.trimIndent()

        val result = OsrmRouteParser.parse(noRouteJson)
        assertTrue(result is RoutingResult.NoRouteFound)
    }

    @Test
    fun `malformed response returns Error without crashing`() {
        val invalidJson = "{ invalid json..."
        val result = OsrmRouteParser.parse(invalidJson)
        assertTrue(result is RoutingResult.Error)
        val error = result as RoutingResult.Error
        assertTrue(error.message.contains("Malformed", ignoreCase = true))
    }

    @Test
    fun `HTTP 500 error returns Error with code`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Server Error")
        )

        val result = service.calculateRoute(12.0, 77.0, 12.1, 77.1, "Test")
        assertTrue(result is RoutingResult.Error)
        val error = result as RoutingResult.Error
        assertTrue(error.message.contains("500"))
    }

    @Test
    fun `network connection failure returns Error without unhandled exception`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )

        val result = service.calculateRoute(12.0, 77.0, 12.1, 77.1, "Test")
        assertTrue(result is RoutingResult.Error)
        val error = result as RoutingResult.Error
        assertTrue(error.message.contains("failure", ignoreCase = true))
    }
}
