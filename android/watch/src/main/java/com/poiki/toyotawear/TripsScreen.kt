package com.poiki.toyotawear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import com.poiki.toyotawear.core.Trip
import com.poiki.toyotawear.core.TripHistory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TripsScreen(history: TripHistory?, loading: Boolean, error: String?, onRetry: () -> Unit) {
    var selected by remember { mutableStateOf<Trip?>(null) }
    val state = rememberTransformingLazyColumnState()
    Box(Modifier.fillMaxSize().cockpit()) {
        ScreenScaffold(scrollState = state) { padding ->
            TransformingLazyColumn(state = state, contentPadding = padding, modifier = Modifier.padding(horizontal = 22.dp)) {
                item { ListHeader { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_toyota_mark), contentDescription = null, modifier = Modifier.size(18.dp, 12.dp))
                    Text(stringResource(R.string.trips_title), fontSize = 14.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
                } } }
                item { TripLine(stringResource(R.string.trips_period)) }
                if (loading) item { TripLine(stringResource(R.string.loading)) }
                if (error != null) {
                    item { TripLine(error) }
                    item { Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { TripLine(stringResource(R.string.retry)) } }
                }
                if (history != null && history.trips.isNotEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().background(Color(0xFF14161A), RoundedCornerShape(16.dp))
                            .border(.5.dp, NeonRed.copy(alpha = .6f), RoundedCornerShape(16.dp)).padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            TripLine(stringResource(R.string.trips_average))
                            Text(tripNumber(history.averageConsumption), fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            TripLine("L/100 km")
                            TripLine(stringResource(R.string.trips_measured, history.measured.size))
                        }
                    }
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TripLine(history.total?.let { stringResource(R.string.trips_coverage, history.trips.size, it) }
                                ?: stringResource(R.string.trips_coverage_unknown, history.trips.size))
                            TripLine(tripNumber(history.distanceKm) + " km")
                        }
                    }
                    history.trips.forEach { trip ->
                        item {
                            Card(onClick = { selected = trip }, modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF14161A))) {
                                TripLine(tripDate(trip.startedAt))
                                TripLine(tripNumber(trip.distanceKm) + " km")
                                Text(tripNumber(trip.consumption) + " L/100 km", fontSize = 13.sp, color = NeonRed,
                                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                } else if (!loading && error == null) item { TripLine(stringResource(R.string.trips_empty)) }
            }
        }
    }
    Dialog(visible = selected != null, onDismissRequest = { selected = null }) {
        selected?.let { trip ->
            val details = rememberTransformingLazyColumnState()
            Box(Modifier.fillMaxSize().cockpit()) {
                ScreenScaffold(scrollState = details) { padding ->
                    TransformingLazyColumn(state = details, contentPadding = padding, modifier = Modifier.padding(horizontal = 26.dp)) {
                        item { ListHeader { Text(stringResource(R.string.trip_detail), textAlign = TextAlign.Center) } }
                        item { TripLine(tripDate(trip.startedAt)) }
                        item { TripMetric(stringResource(R.string.trips_consumption), tripNumber(trip.consumption) + " L/100 km") }
                        item { TripMetric(stringResource(R.string.trips_distance), tripNumber(trip.distanceKm) + " km") }
                        item { TripMetric(stringResource(R.string.trips_duration), tripNumber(trip.seconds?.div(60)) + " min") }
                        item { TripMetric(stringResource(R.string.fuel), tripNumber(trip.fuelLitres) + " L") }
                        item { TripMetric(stringResource(R.string.trips_speed), tripNumber(trip.averageSpeed) + " km/h") }
                        if (trip.evShare != null) item { TripMetric(stringResource(R.string.trips_ev_share), tripNumber(trip.evShare) + "%") }
                        if (trip.score != null) item { TripMetric(stringResource(R.string.trips_score), trip.score.toString()) }
                        item { Button(onClick = { selected = null }, modifier = Modifier.fillMaxWidth()) { TripLine(stringResource(R.string.trips_back)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripMetric(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TripLine(label)
        Text(value, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TripLine(text: String) = Text(text, fontSize = 10.sp, lineHeight = 13.sp,
    color = Color(0xFFB9BDC4), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

@Composable
private fun tripNumber(value: Double?): String = value?.let { String.format(LocalConfiguration.current.locales[0], "%.1f", it) } ?: "—"

@Composable
private fun tripDate(value: Long?): String = value?.let {
    DateTimeFormatter.ofPattern("d MMM · HH:mm", LocalConfiguration.current.locales[0])
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
} ?: "—"
