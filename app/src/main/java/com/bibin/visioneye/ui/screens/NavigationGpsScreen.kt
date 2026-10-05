package com.bibin.visioneye.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bibin.visioneye.navigation.GpsStatus
import com.bibin.visioneye.navigation.NavigationController
import com.bibin.visioneye.navigation.NavigationGpsDiagnostics
import com.bibin.visioneye.navigation.RouteSearchState
import com.bibin.visioneye.navigation.guidance.GuidanceProgress
import com.bibin.visioneye.navigation.guidance.GuidanceStatus
import com.bibin.visioneye.navigation.location.LocationFix
import com.bibin.visioneye.navigation.routing.Route

import com.bibin.visioneye.ui.theme.EmergencyRed
import com.bibin.visioneye.ui.theme.HighContrastBlack
import com.bibin.visioneye.ui.theme.HighContrastBorder
import com.bibin.visioneye.ui.theme.HighContrastCard
import com.bibin.visioneye.ui.theme.HighContrastCyan
import com.bibin.visioneye.ui.theme.HighContrastSurface
import com.bibin.visioneye.ui.theme.HighContrastTextMuted
import com.bibin.visioneye.ui.theme.HighContrastWhite
import com.bibin.visioneye.ui.theme.HighContrastYellow
import com.bibin.visioneye.ui.theme.NavigationTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt


/**
 * Dedicated high-contrast screen for outdoor GPS navigation diagnostics and physical testing.
 *
 * Displays real-time location metrics (latitude, longitude, accuracy, speed, bearing, status)
 * with WCAG AAA accessibility, large touch targets, and full TalkBack semantics.
 *
 * @param navigationController Controller managing the GPS acquisition lifecycle.
 * @param onStopNavigation Callback invoked to halt navigation and return to the dashboard.
 */
@Composable
fun NavigationGpsScreen(
    navigationController: NavigationController,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val diagnostics by navigationController.diagnostics.collectAsState()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        navigationController.onPermissionResult(granted)
    }

    // Intercept hardware back button to cleanly stop navigation
    BackHandler {
        navigationController.deactivate()
        onStopNavigation()
    }

    // Manage screen lifecycle: activate GPS on composition, cleanly deactivate on exit
    DisposableEffect(lifecycleOwner) {
        navigationController.activate()
        onDispose {
            navigationController.deactivate()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = HighContrastBlack
    ) {
        if (!hasLocationPermission) {
            LocationPermissionPrompt(
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onCancel = {
                    navigationController.deactivate()
                    onStopNavigation()
                }
            )
        } else {
            NavigationDiagnosticDashboard(
                diagnostics = diagnostics,
                navigationController = navigationController,
                onStopNavigation = {
                    navigationController.deactivate()
                    onStopNavigation()
                }
            )
        }
    }
}

@Composable
private fun NavigationDiagnosticDashboard(
    diagnostics: NavigationGpsDiagnostics,
    navigationController: NavigationController,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NAVIGATION",
                    style = MaterialTheme.typography.headlineLarge,
                    color = NavigationTeal,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.semantics {
                        contentDescription = "Navigation Mode GPS Diagnostics Screen"
                    }
                )
                Text(
                    text = "Outdoor GPS Wayfinding Diagnostics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HighContrastTextMuted
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NavigationTeal.copy(alpha = 0.2f))
                    .border(1.dp, NavigationTeal, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "GPS MODE",
                    style = MaterialTheme.typography.labelMedium,
                    color = NavigationTeal,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Destination Input & Pedestrian Walking Route Card
        DestinationRoutingCard(
            diagnostics = diagnostics,
            onSearchDestination = { query ->
                navigationController.searchDestinationAndRoute(query)
            },
            onCancelSearch = {
                navigationController.cancelRouteSearch()
            }
        )

        // Active Turn-by-Turn Guidance Card (Phase 4)
        if (diagnostics.guidanceProgress.status != GuidanceStatus.IDLE) {
            ActiveGuidanceCard(
                guidance = diagnostics.guidanceProgress,
                onStopGuidance = {
                    navigationController.stopGuidance()
                }
            )
        }

        // GPS Permission & Status Row Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, NavigationTeal, RoundedCornerShape(16.dp)),

            colors = CardDefaults.cardColors(containerColor = HighContrastSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GPS PERMISSION",
                        style = MaterialTheme.typography.labelLarge,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (diagnostics.hasPermission) "GRANTED" else "DENIED",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (diagnostics.hasPermission) HighContrastCyan else EmergencyRed,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GPS STATUS",
                        style = MaterialTheme.typography.labelLarge,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.Bold
                    )

                    val statusLabel = when (diagnostics.status) {
                        GpsStatus.ACTIVE -> "ACTIVE"
                        GpsStatus.ACQUIRING -> "ACQUIRING..."
                        GpsStatus.UNAVAILABLE -> "UNAVAILABLE"
                        GpsStatus.PERMISSION_DENIED -> "PERMISSION DENIED"
                    }
                    val statusColor = when (diagnostics.status) {
                        GpsStatus.ACTIVE -> HighContrastCyan
                        GpsStatus.ACQUIRING -> HighContrastYellow
                        GpsStatus.UNAVAILABLE, GpsStatus.PERMISSION_DENIED -> EmergencyRed
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(alpha = 0.2f))
                            .border(1.dp, statusColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Live Coordinate & Precision Card
        val fix = diagnostics.locationFix
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, HighContrastBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = HighContrastCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "COORDINATE TELEMETRY",
                    style = MaterialTheme.typography.labelLarge,
                    color = HighContrastYellow,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Latitude
                MetricDisplayRow(
                    label = "Latitude",
                    value = fix?.let { String.format(Locale.US, "%.6f°", it.latitude) } ?: "Waiting for fix...",
                    isEmphasized = true
                )

                // Longitude
                MetricDisplayRow(
                    label = "Longitude",
                    value = fix?.let { String.format(Locale.US, "%.6f°", it.longitude) } ?: "Waiting for fix...",
                    isEmphasized = true
                )

                // Accuracy
                MetricDisplayRow(
                    label = "Accuracy",
                    value = fix?.let { String.format(Locale.US, "±%.1f meters", it.accuracyMeters) } ?: "N/A"
                )

                // Speed
                MetricDisplayRow(
                    label = "Speed",
                    value = fix?.speedMetersPerSecond?.let {
                        String.format(Locale.US, "%.1f m/s (%.1f km/h)", it, it * 3.6f)
                    } ?: "N/A"
                )

                // GPS Course / Bearing
                MetricDisplayRow(
                    label = "GPS Course / Bearing",
                    value = fix?.bearingDegrees?.let {
                        String.format(Locale.US, "%.1f°", it)
                    } ?: "N/A"
                )

                // Last Update Time
                val timeFormatted = diagnostics.lastUpdateTimeMs?.let {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
                    sdf.format(Date(it))
                } ?: "None"
                MetricDisplayRow(
                    label = "Last Fix Time",
                    value = timeFormatted
                )
            }
        }

        // COMPASS Diagnostic Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, HighContrastBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = HighContrastCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COMPASS",
                        style = MaterialTheme.typography.labelLarge,
                        color = HighContrastYellow,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    val compassStatusLabel = when (diagnostics.compassStatus) {
                        com.bibin.visioneye.navigation.orientation.CompassStatus.ACTIVE -> "ACTIVE"
                        com.bibin.visioneye.navigation.orientation.CompassStatus.INITIALIZING -> "INITIALIZING"
                        com.bibin.visioneye.navigation.orientation.CompassStatus.UNAVAILABLE -> "UNAVAILABLE"
                    }
                    val compassStatusColor = when (diagnostics.compassStatus) {
                        com.bibin.visioneye.navigation.orientation.CompassStatus.ACTIVE -> HighContrastCyan
                        com.bibin.visioneye.navigation.orientation.CompassStatus.INITIALIZING -> HighContrastYellow
                        com.bibin.visioneye.navigation.orientation.CompassStatus.UNAVAILABLE -> EmergencyRed
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(compassStatusColor.copy(alpha = 0.2f))
                            .border(1.dp, compassStatusColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = compassStatusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = compassStatusColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Heading
                MetricDisplayRow(
                    label = "Heading",
                    value = diagnostics.headingDegrees?.let {
                        String.format(Locale.US, "%.0f°", it)
                    } ?: "N/A",
                    isEmphasized = true
                )

                // Direction
                MetricDisplayRow(
                    label = "Direction",
                    value = diagnostics.cardinalDirection ?: "N/A",
                    isEmphasized = true
                )

                // Status
                MetricDisplayRow(
                    label = "Status",
                    value = when (diagnostics.compassStatus) {
                        com.bibin.visioneye.navigation.orientation.CompassStatus.ACTIVE -> "ACTIVE"
                        com.bibin.visioneye.navigation.orientation.CompassStatus.INITIALIZING -> "INITIALIZING"
                        com.bibin.visioneye.navigation.orientation.CompassStatus.UNAVAILABLE -> "UNAVAILABLE"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        // Large Accessible "Stop Navigation" Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NavigationTeal)
                .border(2.dp, HighContrastWhite, RoundedCornerShape(14.dp))
                .clickable(onClick = onStopNavigation)
                .semantics {
                    role = Role.Button
                    contentDescription = "Stop Navigation. Exits outdoor GPS guidance and returns to dashboard. Double tap to activate."
                }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "🛑", fontSize = 24.sp)
                Text(
                    text = "STOP NAVIGATION",
                    style = MaterialTheme.typography.titleMedium,
                    color = HighContrastBlack,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Text(
            text = "⚠️ White cane recommended. Assistive prototype.",
            style = MaterialTheme.typography.bodySmall,
            color = HighContrastTextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MetricDisplayRow(
    label: String,
    value: String,
    isEmphasized: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = HighContrastTextMuted
        )
        Text(
            text = value,
            style = if (isEmphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = if (isEmphasized) HighContrastWhite else HighContrastCyan,
            fontWeight = if (isEmphasized) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun LocationPermissionPrompt(
    onRequestPermission: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, HighContrastYellow, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = HighContrastSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "📍",
                    fontSize = 48.sp
                )

                Text(
                    text = "LOCATION PERMISSION REQUIRED",
                    style = MaterialTheme.typography.titleLarge,
                    color = HighContrastYellow,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "VisionEye requires GPS location access to provide pedestrian wayfinding, guidance alerts, and outdoor mobility assistance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HighContrastWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Grant Permission Action Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NavigationTeal)
                        .border(2.dp, HighContrastWhite, RoundedCornerShape(14.dp))
                        .clickable(onClick = onRequestPermission)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Grant Location Permission. Double tap to open system permission dialog."
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "GRANT PERMISSION",
                        style = MaterialTheme.typography.titleMedium,
                        color = HighContrastBlack,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Cancel Action Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HighContrastCard)
                        .border(1.dp, HighContrastBorder, RoundedCornerShape(12.dp))
                        .clickable(onClick = onCancel)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Cancel and return to dashboard. Double tap to dismiss."
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CANCEL",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationRoutingCard(
    diagnostics: NavigationGpsDiagnostics,
    onSearchDestination: (String) -> Unit,
    onCancelSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    var destinationInput by remember { mutableStateOf("") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, HighContrastYellow, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = HighContrastSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "DESTINATION & WALKING ROUTE",
                style = MaterialTheme.typography.labelLarge,
                color = HighContrastYellow,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )

            // Text Input Field
            OutlinedTextField(
                value = destinationInput,
                onValueChange = { destinationInput = it },
                label = {
                    Text(
                        text = "Enter destination",
                        color = HighContrastYellow,
                        fontWeight = FontWeight.Bold
                    )
                },
                placeholder = {
                    Text(
                        text = "e.g. Town Hall, Central Library",
                        color = HighContrastTextMuted
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = HighContrastWhite,
                    unfocusedTextColor = HighContrastWhite,
                    focusedBorderColor = HighContrastYellow,
                    unfocusedBorderColor = HighContrastBorder,
                    cursorColor = HighContrastYellow,
                    focusedContainerColor = HighContrastBlack,
                    unfocusedContainerColor = HighContrastBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Destination text input field. Enter destination name or address."
                    }
            )

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Large Accessible Search / Start Button
                val isSearching = diagnostics.routeState == RouteSearchState.GEOCODING ||
                    diagnostics.routeState == RouteSearchState.ROUTE_LOADING

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (destinationInput.isNotBlank() && !isSearching) NavigationTeal else NavigationTeal.copy(alpha = 0.5f))
                        .border(2.dp, HighContrastWhite, RoundedCornerShape(12.dp))
                        .clickable(
                            enabled = destinationInput.isNotBlank() && !isSearching
                        ) {
                            onSearchDestination(destinationInput)
                        }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Search destination and calculate walking route. Double tap to activate."
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔍 START / ROUTE",
                        style = MaterialTheme.typography.titleMedium,
                        color = HighContrastBlack,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Accessible Cancel Button
                if (diagnostics.routeState != RouteSearchState.IDLE ||
                    diagnostics.currentRoute != null ||
                    destinationInput.isNotBlank()
                ) {
                    Box(
                        modifier = Modifier
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(HighContrastCard)
                            .border(2.dp, EmergencyRed, RoundedCornerShape(12.dp))
                            .clickable {
                                destinationInput = ""
                                onCancelSearch()
                            }
                            .padding(horizontal = 16.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Cancel destination route search and clear input. Double tap to activate."
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CANCEL",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmergencyRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Finding destination banner
            if (diagnostics.routeState == RouteSearchState.GEOCODING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(HighContrastYellow.copy(alpha = 0.15f))
                        .border(1.dp, HighContrastYellow, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                        .semantics {
                            contentDescription = "Finding destination in progress."
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⏳ Finding destination...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HighContrastYellow,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Getting walking route banner
            if (diagnostics.routeState == RouteSearchState.ROUTE_LOADING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(HighContrastCyan.copy(alpha = 0.15f))
                        .border(1.dp, HighContrastCyan, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                        .semantics {
                            contentDescription = "Getting walking route in progress."
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🚶 Getting walking route...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HighContrastCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Error Banners
            if (diagnostics.routeState == RouteSearchState.GEOCODING_FAILED ||
                diagnostics.routeState == RouteSearchState.ROUTE_FAILED
            ) {
                val errorMsg = diagnostics.routeErrorMessage ?: "Unable to calculate walking route."
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EmergencyRed.copy(alpha = 0.15f))
                        .border(1.dp, EmergencyRed, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                        .semantics {
                            contentDescription = "Routing error: $errorMsg"
                        }
                ) {
                    Text(
                        text = "⚠️ $errorMsg",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EmergencyRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Route Summary Box
            val route = diagnostics.currentRoute
            if (route != null) {
                RouteSummaryCard(route = route)
            }
        }
    }
}

@Composable
private fun RouteSummaryCard(
    route: Route,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, HighContrastCyan, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = HighContrastBlack),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "✓ ROUTE READY",
                style = MaterialTheme.typography.labelLarge,
                color = HighContrastCyan,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )

            // Destination
            MetricDisplayRow(
                label = "Destination",
                value = route.destination,
                isEmphasized = true
            )

            // Distance
            val formattedDistance = if (route.totalDistanceMeters >= 1000.0) {
                String.format(Locale.US, "%.1f km", route.totalDistanceMeters / 1000.0)
            } else {
                "${route.totalDistanceMeters.toInt()} meters"
            }
            MetricDisplayRow(
                label = "Distance",
                value = formattedDistance,
                isEmphasized = true
            )

            // Estimated walking time
            val totalMins = (route.totalDurationSeconds / 60.0).roundToInt()
            val formattedDuration = if (totalMins >= 60) {
                val hrs = totalMins / 60
                val remMins = totalMins % 60
                if (remMins > 0) "$hrs hr $remMins mins" else "$hrs hr"
            } else if (totalMins > 0) {
                "$totalMins mins"
            } else {
                "< 1 min"
            }
            MetricDisplayRow(
                label = "Estimated walking time",
                value = formattedDuration,
                isEmphasized = true
            )

            // Number of steps
            MetricDisplayRow(
                label = "Number of steps",
                value = "${route.steps.size} steps",
                isEmphasized = true
            )
        }
    }
}

/**
 * High-contrast turn-by-turn guidance HUD card displaying real-time maneuver instructions,
 * distance countdown, remaining route metrics, and accessible STOP GUIDANCE button.
 */
@Composable
private fun ActiveGuidanceCard(
    guidance: GuidanceProgress,
    onStopGuidance: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, NavigationTeal, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = HighContrastSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Title + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NAVIGATION GUIDANCE",
                    style = MaterialTheme.typography.labelLarge,
                    color = NavigationTeal,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )

                val statusLabel = when (guidance.status) {
                    GuidanceStatus.TURN_NOW -> "TURN NOW"
                    GuidanceStatus.APPROACHING_TURN -> "APPROACHING TURN"
                    GuidanceStatus.OFF_ROUTE -> "OFF ROUTE"
                    GuidanceStatus.REROUTING -> "REROUTING..."
                    GuidanceStatus.ARRIVED -> "ARRIVED"
                    GuidanceStatus.STARTING -> "STARTING"
                    else -> "NAVIGATING"
                }

                val statusColor = when (guidance.status) {
                    GuidanceStatus.TURN_NOW -> HighContrastYellow
                    GuidanceStatus.OFF_ROUTE, GuidanceStatus.REROUTING -> EmergencyRed
                    GuidanceStatus.ARRIVED -> HighContrastCyan
                    else -> NavigationTeal
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.2f))
                        .border(1.dp, statusColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Current instruction callout box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, HighContrastBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = HighContrastBlack),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Current instruction",
                        style = MaterialTheme.typography.labelMedium,
                        color = HighContrastTextMuted
                    )
                    Text(
                        text = guidance.currentInstruction.ifBlank { "Follow route" },
                        style = MaterialTheme.typography.titleLarge,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics {
                            contentDescription = "Current instruction: ${guidance.currentInstruction}"
                        }
                    )
                }
            }

            // Metric Display Rows
            MetricDisplayRow(
                label = "Distance to maneuver",
                value = formatGuidanceDistance(guidance.distanceToNextManeuver),
                isEmphasized = true
            )

            MetricDisplayRow(
                label = "Remaining route",
                value = formatGuidanceDistance(guidance.remainingRouteDistance),
                isEmphasized = true
            )

            MetricDisplayRow(
                label = "Current step",
                value = "${guidance.currentStepIndex + 1} of ${guidance.totalSteps}",
                isEmphasized = true
            )

            MetricDisplayRow(
                label = "Status",
                value = when (guidance.status) {
                    GuidanceStatus.TURN_NOW -> "TURN NOW"
                    GuidanceStatus.APPROACHING_TURN -> "APPROACHING TURN"
                    GuidanceStatus.OFF_ROUTE -> "OFF ROUTE"
                    GuidanceStatus.REROUTING -> "REROUTING"
                    GuidanceStatus.ARRIVED -> "ARRIVED"
                    else -> "NAVIGATING"
                }
            )

            // Large Accessible "STOP GUIDANCE" Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HighContrastCard)
                    .border(2.dp, EmergencyRed, RoundedCornerShape(12.dp))
                    .clickable(onClick = onStopGuidance)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Stop guidance. Returns to route overview. Double tap to activate."
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "⏹", fontSize = 18.sp)
                    Text(
                        text = "STOP GUIDANCE",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmergencyRed,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

private fun formatGuidanceDistance(meters: Double): String {
    return if (meters >= 1000.0) {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    } else {
        "${meters.toInt()} m"
    }
}


