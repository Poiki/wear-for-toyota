package com.poiki.toyotawear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
                    item { ConsumptionChart(history.chartTrips) }
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
                        trip.evShare?.let { share -> item {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                TripMetric(stringResource(R.string.trips_ev_share), tripNumber(share) + "%")
                                Canvas(Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 8.dp)) {
                                    val start = Offset(4.dp.toPx(), size.height / 2)
                                    val end = Offset(size.width - 4.dp.toPx(), start.y)
                                    drawLine(Color(0xFF393C43), start, end, size.height, StrokeCap.Round)
                                    if (share > 0) drawLine(NeonRed, start, Offset(start.x + (end.x - start.x) * (share / 100).toFloat(), start.y), size.height, StrokeCap.Round)
                                }
                            }
                        } }
                        if (trip.score != null) item { TripMetric(stringResource(R.string.trips_score), trip.score.toString()) }
                        item { Button(onClick = { selected = null }, modifier = Modifier.fillMaxWidth()) { TripLine(stringResource(R.string.trips_back)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsumptionChart(trips: List<Trip>) {
    val values = trips.map { it.consumption?.takeIf { value -> value.isFinite() } }
    val maximum = values.filterNotNull().maxOrNull() ?: return
    val ceiling = kotlin.math.ceil(maximum).coerceAtLeast(1.0)
    val title = stringResource(R.string.trips_trend)
    val hint = stringResource(R.string.trips_chart_hint)
    val missing = stringResource(R.string.trips_chart_missing)
    val description = trips.mapIndexed { index, trip ->
        tripDate(trip.startedAt) + ": " + (values[index]?.let { tripNumber(it) + " L/100 km" } ?: missing)
    }.joinToString("; ")
    Column(Modifier.fillMaxWidth().background(Color(0xFF14161A), RoundedCornerShape(16.dp))
        .border(.5.dp, NeonRed.copy(alpha = .6f), RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        TripLine(title)
        TripLine(stringResource(R.string.trips_chart_count, trips.size))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(tripNumber(ceiling) + " L/100 km", fontSize = 9.sp, color = Color(0xFFB9BDC4))
        }
        Canvas(Modifier.fillMaxWidth().height(64.dp).clearAndSetSemantics { contentDescription = "$title. $hint. $description" }) {
            val inset = 4.dp.toPx()
            val width = size.width - 2 * inset
            val height = size.height - 2 * inset
            val baseline = size.height - inset
            for (fraction in listOf(0f, .5f, 1f)) {
                val y = baseline - height * fraction
                drawLine(Color(0xFF393C43), Offset(inset, y), Offset(size.width - inset, y), .5.dp.toPx())
            }
            val points = values.mapIndexed { index, value -> value?.let {
                Offset(inset + width * (if (trips.size == 1) .5f else index.toFloat() / (trips.size - 1)),
                    baseline - height * (it / ceiling).toFloat())
            } }
            points.forEachIndexed { index, point ->
                if (point != null) {
                    // No smoothing or line across a missing reading: each point is one whole trip.
                    val previous = points.getOrNull(index - 1)
                    if (previous != null) {
                        val fill = Path().apply {
                            moveTo(previous.x, baseline); lineTo(previous.x, previous.y)
                            lineTo(point.x, point.y); lineTo(point.x, baseline); close()
                        }
                        drawPath(fill, Brush.verticalGradient(listOf(NeonRed.copy(alpha = .35f), Color.Transparent)))
                        drawLine(NeonRed.copy(alpha = .12f), previous, point, 5.dp.toPx(), StrokeCap.Round)
                        drawLine(NeonRed, previous, point, 1.5.dp.toPx(), StrokeCap.Round)
                    }
                    drawCircle(NeonRed, 2.5.dp.toPx(), point)
                    drawCircle(Color.White, 1.dp.toPx(), point)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", fontSize = 9.sp, color = Color(0xFFB9BDC4))
            Text(tripDate(trips.first().startedAt, "d MMM"), fontSize = 9.sp, color = Color(0xFFB9BDC4))
            if (trips.size > 1) Text(tripDate(trips.last().startedAt, "d MMM"), fontSize = 9.sp, color = Color(0xFFB9BDC4))
        }
        TripLine(hint)
        if (values.any { it == null }) TripLine(missing)
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
private fun tripDate(value: Long?, pattern: String = "d MMM · HH:mm"): String = value?.let {
    DateTimeFormatter.ofPattern(pattern, LocalConfiguration.current.locales[0])
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
} ?: "—"
