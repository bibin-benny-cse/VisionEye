package com.bibin.visioneye.navigation.routing

/**
 * Geographic coordinate point along a route polyline.
 *
 * @property latitude WGS84 latitude coordinate [-90.0, 90.0].
 * @property longitude WGS84 longitude coordinate [-180.0, 180.0].
 */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double
)

/**
 * Representation of a pedestrian walking route calculated between two points.
 *
 * @property destination Human-readable destination name.
 * @property totalDistanceMeters Total distance along the walking path in meters.
 * @property totalDurationSeconds Estimated walking duration in seconds.
 * @property steps Ordered list of walking maneuver steps.
 * @property geometry High-resolution geographic polyline path coordinates.
 */
data class Route(
    val destination: String,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val steps: List<RouteStep>,
    val geometry: List<RoutePoint> = emptyList()
)

/**
 * Individual navigational maneuver or leg along a pedestrian route.
 *
 * @property instruction Spoken or displayed navigation instruction (e.g. "Turn right onto Brigade Road").
 * @property distanceMeters Distance to traverse in this step in meters.
 * @property durationSeconds Estimated time to complete this step in seconds.
 * @property latitude Latitude at the start of this maneuver.
 * @property longitude Longitude at the start of this maneuver.
 * @property maneuverType OSRM maneuver type ("depart", "turn", "continue", "arrive", etc.).
 * @property modifier OSRM maneuver modifier ("left", "right", "slight left", "sharp right", "straight", etc.).
 * @property bearingBefore Heading in degrees approaching this maneuver.
 * @property bearingAfter Heading in degrees exiting this maneuver.
 * @property streetName Name of the street or path (if available).
 * @property stepGeometry Coordinates for this specific step leg.
 */
data class RouteStep(
    val instruction: String,
    val distanceMeters: Double,
    val durationSeconds: Double = 0.0,
    val latitude: Double,
    val longitude: Double,
    val maneuverType: String = "",
    val modifier: String = "",
    val bearingBefore: Float? = null,
    val bearingAfter: Float? = null,
    val streetName: String = "",
    val stepGeometry: List<RoutePoint> = emptyList()
)


/**
 * Result of a pedestrian routing calculation.
 */
sealed interface RoutingResult {
    /**
     * Successfully calculated a walking route.
     */
    data class Success(val route: Route) : RoutingResult

    /**
     * Routing service was reached, but no walkable path connects the coordinates.
     */
    data object NoRouteFound : RoutingResult

    /**
     * Failed due to network connection, HTTP error, or malformed response.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : RoutingResult
}
