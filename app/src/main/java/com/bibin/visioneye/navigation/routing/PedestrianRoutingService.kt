package com.bibin.visioneye.navigation.routing

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Mockable service contract for walking route retrieval.
 */
interface PedestrianRoutingService {
    /**
     * Calculates an on-foot pedestrian route from origin to destination coordinates.
     *
     * @param startLat Latitude of user origin.
     * @param startLon Longitude of user origin.
     * @param destLat Latitude of destination.
     * @param destLon Longitude of destination.
     * @param destinationName Human-readable destination label.
     * @return [RoutingResult.Success] on path found, [RoutingResult.NoRouteFound] if unreachable,
     * or [RoutingResult.Error] on error. Never throws unhandled exceptions.
     */
    suspend fun calculateRoute(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        destinationName: String = ""
    ): RoutingResult
}

/**
 * Deterministic parser for OSRM walking route JSON responses.
 * Separated from the HTTP transport layer to ensure comprehensive and deterministic unit testing.
 */
object OsrmRouteParser {

    /**
     * Parses an OSRM response JSON string.
     *
     * @param jsonString Raw response payload string.
     * @param destinationName Label for destination to attach to [Route].
     */
    fun parse(jsonString: String, destinationName: String = ""): RoutingResult {
        if (jsonString.isBlank()) {
            return RoutingResult.Error("Empty response body from routing server")
        }

        return try {
            val root = JSONObject(jsonString)
            val code = root.optString("code", "")

            if (code.equals("NoRoute", ignoreCase = true)) {
                return RoutingResult.NoRouteFound
            }

            if (!code.equals("Ok", ignoreCase = true)) {
                val message = root.optString("message", "Route calculation failed with code: $code")
                return RoutingResult.Error(message)
            }

            val routesArray = root.optJSONArray("routes")
            if (routesArray == null || routesArray.length() == 0) {
                return RoutingResult.NoRouteFound
            }

            val primaryRoute = routesArray.getJSONObject(0)
            val totalDistance = primaryRoute.optDouble("distance", 0.0)
            val totalDuration = primaryRoute.optDouble("duration", 0.0)

            val legsArray = primaryRoute.optJSONArray("legs")
            val stepsList = mutableListOf<RouteStep>()

            if (legsArray != null && legsArray.length() > 0) {
                val leg = legsArray.getJSONObject(0)
                val stepsArray = leg.optJSONArray("steps")
                if (stepsArray != null) {
                    for (i in 0 until stepsArray.length()) {
                        val stepObj = stepsArray.getJSONObject(i)
                        val stepDistance = stepObj.optDouble("distance", 0.0)
                        val stepDuration = stepObj.optDouble("duration", 0.0)
                        val streetName = stepObj.optString("name", "").trim()

                        val maneuver = stepObj.optJSONObject("maneuver")
                        val maneuverType = maneuver?.optString("type", "") ?: ""
                        val modifier = maneuver?.optString("modifier", "") ?: ""

                        var lat = 0.0
                        var lon = 0.0
                        val locArray = maneuver?.optJSONArray("location")
                        if (locArray != null && locArray.length() >= 2) {
                            // Note: OSRM location format is [lon, lat]
                            lon = locArray.optDouble(0, 0.0)
                            lat = locArray.optDouble(1, 0.0)
                        }

                        val bearingBefore = if (maneuver?.has("bearing_before") == true) {
                            maneuver.optDouble("bearing_before").toFloat()
                        } else null

                        val bearingAfter = if (maneuver?.has("bearing_after") == true) {
                            maneuver.optDouble("bearing_after").toFloat()
                        } else null

                        val stepGeomList = mutableListOf<RoutePoint>()
                        val stepGeomObj = stepObj.optJSONObject("geometry")
                        if (stepGeomObj != null) {
                            val coordsArr = stepGeomObj.optJSONArray("coordinates")
                            if (coordsArr != null) {
                                for (k in 0 until coordsArr.length()) {
                                    val pt = coordsArr.optJSONArray(k)
                                    if (pt != null && pt.length() >= 2) {
                                        stepGeomList.add(RoutePoint(latitude = pt.optDouble(1, 0.0), longitude = pt.optDouble(0, 0.0)))
                                    }
                                }
                            }
                        }

                        val instruction = buildInstruction(maneuverType, modifier, streetName)

                        stepsList.add(
                            RouteStep(
                                instruction = instruction,
                                distanceMeters = stepDistance,
                                durationSeconds = stepDuration,
                                latitude = lat,
                                longitude = lon,
                                maneuverType = maneuverType,
                                modifier = modifier,
                                bearingBefore = bearingBefore,
                                bearingAfter = bearingAfter,
                                streetName = streetName,
                                stepGeometry = stepGeomList
                            )
                        )
                    }
                }
            }

            // Extract high-resolution route polyline geometry if provided as GeoJSON
            val routeGeomList = mutableListOf<RoutePoint>()
            val geomObj = primaryRoute.optJSONObject("geometry")
            if (geomObj != null) {
                val coordsArray = geomObj.optJSONArray("coordinates")
                if (coordsArray != null) {
                    for (j in 0 until coordsArray.length()) {
                        val pt = coordsArray.optJSONArray(j)
                        if (pt != null && pt.length() >= 2) {
                            routeGeomList.add(RoutePoint(latitude = pt.optDouble(1, 0.0), longitude = pt.optDouble(0, 0.0)))
                        }
                    }
                }
            }

            // Safe fallback: If full GeoJSON geometry is absent, synthesize polyline from step maneuver points
            if (routeGeomList.isEmpty()) {
                stepsList.forEach { step ->
                    routeGeomList.add(RoutePoint(step.latitude, step.longitude))
                }
            }

            val resolvedDestination = if (destinationName.isNotBlank()) {
                destinationName
            } else {
                "Destination"
            }

            RoutingResult.Success(
                Route(
                    destination = resolvedDestination,
                    totalDistanceMeters = totalDistance,
                    totalDurationSeconds = totalDuration,
                    steps = stepsList,
                    geometry = routeGeomList
                )
            )
        } catch (e: Exception) {
            RoutingResult.Error("Malformed JSON response: ${e.message}", e)

        }
    }

    /**
     * Synthesizes a natural, accessible walking instruction from OSRM maneuver semantics.
     */
    fun buildInstruction(maneuverType: String, modifier: String, streetName: String): String {
        val cleanName = if (streetName.isNotBlank()) streetName else "path"
        val cleanMod = modifier.trim().lowercase()

        return when (maneuverType.trim().lowercase()) {
            "depart" -> "Head toward $cleanName"
            "arrive" -> "Arrive at destination"
            "turn" -> if (cleanMod.isNotBlank()) "Turn $cleanMod onto $cleanName" else "Turn onto $cleanName"
            "continue" -> if (cleanMod.isNotBlank() && cleanMod != "straight") "Continue $cleanMod on $cleanName" else "Continue on $cleanName"
            "new name" -> "Continue onto $cleanName"
            "end of road" -> if (cleanMod.isNotBlank()) "At the end of the road, turn $cleanMod onto $cleanName" else "Turn onto $cleanName"
            "fork" -> if (cleanMod.isNotBlank()) "Take the $cleanMod fork onto $cleanName" else "Take the fork onto $cleanName"
            "roundabout", "rotary" -> "Enter the roundabout toward $cleanName"
            else -> {
                if (cleanMod.isNotBlank()) {
                    "${maneuverType.replaceFirstChar { it.uppercase() }} $cleanMod onto $cleanName"
                } else if (maneuverType.isNotBlank()) {
                    "${maneuverType.replaceFirstChar { it.uppercase() }} onto $cleanName"
                } else {
                    "Proceed on $cleanName"
                }
            }
        }
    }
}

/**
 * Production pedestrian routing client communicating with an OSRM instance using the foot profile.
 *
 * Explicitly requests pedestrian/walking mode (`/routed-foot/route/v1/foot/`).
 *
 * @param baseUrl Base OSRM endpoint for foot routing.
 * @param client OkHttpClient instance with configured timeouts.
 * @param ioDispatcher Coroutine dispatcher for offloading blocking HTTP I/O.
 * @param userAgent Header identifying the application in compliance with OSM usage policy.
 */
class OsrmPedestrianRoutingService(
    private val baseUrl: String = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/",
    private val client: OkHttpClient = defaultClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val userAgent: String = "VisionEye-AcademicPrototype/1.0 (Android; Accessibility-Research)"
) : PedestrianRoutingService {

    override suspend fun calculateRoute(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        destinationName: String
    ): RoutingResult {
        return withContext(ioDispatcher) {
            try {
                // OSRM coordinates are specified as: {longitude},{latitude};{longitude},{latitude}
                val coords = String.format(
                    Locale.US,
                    "%.6f,%.6f;%.6f,%.6f",
                    startLon, startLat, destLon, destLat
                )
                val fullUrl = if (baseUrl.endsWith("/")) {
                    "${baseUrl}$coords?overview=full&steps=true&geometries=geojson"
                } else {
                    "$baseUrl/$coords?overview=full&steps=true&geometries=geojson"
                }


                val request = Request.Builder()
                    .url(fullUrl)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext RoutingResult.Error(
                            "Routing request failed with HTTP ${response.code}: ${response.message}"
                        )
                    }
                    val bodyString = response.body?.string() ?: ""
                    OsrmRouteParser.parse(bodyString, destinationName)
                }
            } catch (e: IOException) {
                RoutingResult.Error("Network failure during route calculation: ${e.message}", e)
            } catch (e: Exception) {
                RoutingResult.Error("Unexpected routing error: ${e.message}", e)
            }
        }
    }

    companion object {
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}
