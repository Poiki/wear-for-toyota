package com.poiki.toyotawear

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.poiki.toyotawear.core.Snapshot
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** "My Garage": one big card per car. Nothing is listed until the cars and their pictures are ready. */
@Composable
fun GarageScreen(
    vehicles: List<JSONObject>,
    images: Map<String, Bitmap>,
    busy: String?,
    error: String?,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    onUnlink: () -> Unit,
) {
    if (vehicles.isEmpty()) {
        WaitFace(car = null, text = error ?: stringResource(R.string.busy_vehicles), working = error == null) {
            if (error != null) {
                Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.retry)) }
                OutlinedButton(onClick = onUnlink, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.unlink)) }
            }
        }
        return
    }
    val pager = rememberPagerState(pageCount = { vehicles.size })
    HorizontalPager(state = pager) { page ->
        val vehicle = vehicles[page]
        val vin = vehicle.optString("vin")
        Dial {
            Box(Modifier.fillMaxSize().cockpit()) {
                Column(Modifier.align(Alignment.TopCenter).offset(y = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.garage_title), fontSize = 14.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                    if (vehicles.size > 1) Text("${number(page + 1)}/${number(vehicles.size)}", fontSize = 8.sp, lineHeight = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Card(onClick = { onSelect(vin) }, contentPadding = PaddingValues(10.dp),
                    colors = androidx.wear.compose.material3.CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.size(174.dp, 126.dp).align(Alignment.TopCenter).offset(y = 48.dp)
                        .clip(RoundedCornerShape(24.dp)).cockpit().border(.5.dp, Color(0xFF393C43), RoundedCornerShape(24.dp))) {
                    BasicText(Snapshot.displayName(vehicle), modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(fontSize = 13.sp, lineHeight = 15.sp, color = Color.White, textAlign = TextAlign.Center),
                        maxLines = 2, autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 13.sp))
                    CarPicture(images[vin], Modifier.fillMaxWidth().height(74.dp).padding(top = 4.dp))
                }
                OutlinedButton(onClick = onUnlink, enabled = busy == null,
                    modifier = Modifier.size(128.dp, 40.dp).align(Alignment.TopCenter).offset(y = 174.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.unlink), fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** One car: a wait face until Toyota answers (old data is never shown), then two dials, status and controls. */
@Composable
fun VehicleScreen(
    snapshot: Snapshot?,
    car: Bitmap?,
    busy: String?,
    error: String?,
    onRetry: () -> Unit,
    onErrorShown: () -> Unit,
    onWake: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onClimate: () -> Unit,
    onMap: (Double, Double) -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    Crossfade(targetState = snapshot != null, animationSpec = tween(450), label = "vehicle") { ready ->
        val s = snapshot
        if (!ready || s == null) {
            WaitFace(car, text = error ?: busy ?: stringResource(R.string.loading), working = error == null) {
                if (error != null) Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.retry)) }
            }
        } else {
            val pager = rememberPagerState(pageCount = { 2 })
            HorizontalPager(state = pager) { page ->
                AnimatedPage(pageIndex = page, pagerState = pager) {
                    Dial {
                        if (page == 0) StatusFace(s, car, busy, onWake, onLock, { confirm = true }, onClimate, onMap)
                        else ControlsFace(s, busy, onLock, { confirm = true }, onClimate, onMap)
                    }
                }
            }
            if (busy != null) WaitFace(car, busy, working = true)
            ErrorNotice(error, onDismiss = onErrorShown)
        }
    }
    Dialog(visible = confirm, onDismissRequest = { confirm = false }) {
        Dial {
            Box(Modifier.fillMaxSize().cockpit()) {
                Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) { BrandHeader() }
                CarPicture(car, Modifier.size(166.dp, 74.dp).align(Alignment.TopCenter).offset(y = 48.dp).graphicsLayer { alpha = .35f })
                NeonGauge({ .3f }, Modifier.size(66.dp).align(Alignment.TopCenter).offset(y = 55.dp)) {
                    Icon(painterResource(R.drawable.ic_lock_open), contentDescription = null, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.align(Alignment.TopCenter).offset(y = 123.dp).width(170.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.confirm_unlock), fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.unlock_explanation), fontSize = 9.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
                }
                Row(Modifier.align(Alignment.TopCenter).offset(y = 168.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { confirm = false }, modifier = Modifier.size(68.dp, 40.dp).neonSurface(),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) {
                        FitText(stringResource(R.string.cancel), TextStyle(fontSize = 10.sp, lineHeight = 12.sp), Color.White, Modifier.fillMaxWidth(), minFontSize = 7.sp)
                    }
                    Button(onClick = { confirm = false; onUnlock() }, modifier = Modifier.size(68.dp, 40.dp).neonSurface(active = true),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) {
                        FitText(stringResource(R.string.unlock), TextStyle(fontSize = 10.sp, lineHeight = 12.sp), Color.White, Modifier.fillMaxWidth(), minFontSize = 7.sp)
                    }
                }
            }
        }
    }
}

/** On the dials a problem gets the whole screen (a cross and the text, self-dismissing) instead of a cut-off line. */
@Composable
private fun ErrorNotice(error: String?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") } // keeps the text while the notice animates out
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(error) {
        if (error != null) {
            text = error
            haptic.performHapticFeedback(HapticFeedbackType.Reject)
        }
    }
    ConfirmationDialog(
        visible = error != null,
        onDismissRequest = onDismiss,
        text = { Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall) },
        colors = ConfirmationDialogDefaults.failureColors(),
    ) { ConfirmationDialogDefaults.GenericFailureIcon() }
}

/** The reference cockpit: real car, compact energy dial, known data and three direct actions. */
@Composable
private fun StatusFace(
    s: Snapshot, car: Bitmap?, busy: String?, onWake: () -> Unit,
    onLock: () -> Unit, onUnlock: () -> Unit, onClimate: () -> Unit, onMap: (Double, Double) -> Unit,
) {
    val energy = s.fuelPct ?: s.batteryPct
    val arc = remember { Animatable(0f) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(energy) {
        val target = ((energy ?: 0) / 100f).coerceIn(0f, 1f)
        if (reduceMotion) arc.snapTo(target) else arc.animateTo(target, tween(800, easing = FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxSize().cockpit()) {
        Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) { BrandHeader(page = 0) }
        CarPicture(car, Modifier.size(133.dp, 58.dp).align(Alignment.TopCenter).offset(x = (-23).dp, y = 49.dp).enter())
        if (energy != null) {
            NeonGauge({ arc.value }, Modifier.size(49.dp).align(Alignment.TopEnd).offset(x = (-18).dp, y = 56.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${countUp(energy)}%", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Icon(painterResource(if (s.fuelPct != null) R.drawable.ic_fuel else R.drawable.ic_battery),
                        contentDescription = stringResource(if (s.fuelPct != null) R.string.fuel else R.string.battery), modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 107.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LockChip(s)
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.range_label), fontSize = 8.sp, lineHeight = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FitText((s.fuelRangeKm ?: s.evRangeKm)?.let { km(it.roundToInt()) } ?: "—", TextStyle(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold), Color.White)
                }
                Spacer(Modifier.height(22.dp).width(.5.dp).background(Color(0xFF36383E)))
                Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.odometer), fontSize = 8.sp, lineHeight = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    FitText(s.odometerKm?.let { km(it.roundToInt()) } ?: "—", TextStyle(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold), Color.White)
                }
                Spacer(Modifier.height(22.dp).width(.5.dp).background(Color(0xFF36383E)))
                FilledIconButton(onClick = onWake, enabled = busy == null, modifier = Modifier.size(48.dp, 24.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.refresh) + ", " + whenText(s.statusAt ?: s.fetchedAt), modifier = Modifier.size(12.dp))
                        Text(shortTimeText(s.statusAt ?: s.fetchedAt), fontSize = 8.sp, lineHeight = 10.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.TopCenter).offset(y = 154.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.remoteActive) NeonAction(if (s.locked == true) R.drawable.ic_lock_open else R.drawable.ic_lock,
                if (s.locked == true) R.string.unlock else R.string.lock, enabled = busy == null,
                active = s.locked == false, onClick = if (s.locked == true) onUnlock else onLock)
            if (s.remoteActive) NeonAction(R.drawable.ic_fan, R.string.climate, enabled = busy == null,
                active = s.climate == "running" || s.climate == "starting", onClick = onClimate)
            val lat = s.lat
            val lon = s.lon
            if (lat != null && lon != null) NeonAction(R.drawable.ic_location, R.string.map) { onMap(lat, lon) }
        }
    }
}

/** Four controls use the same centered, safe round buttons as the dashboard. */
@Composable
private fun ControlsFace(
    s: Snapshot, busy: String?, onLock: () -> Unit, onUnlock: () -> Unit,
    onClimate: () -> Unit, onMap: (Double, Double) -> Unit,
) {
    Box(Modifier.fillMaxSize().cockpit()) {
        Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) { BrandHeader(page = 1) }
        Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) { LockChip(s, compact = true) }
        Row(Modifier.align(Alignment.TopCenter).offset(y = 84.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            NeonAction(R.drawable.ic_lock, R.string.lock, enabled = busy == null && s.remoteActive, onClick = onLock)
            NeonAction(R.drawable.ic_lock_open, R.string.unlock, enabled = busy == null && s.remoteActive, onClick = onUnlock)
        }
        Row(Modifier.align(Alignment.TopCenter).offset(y = 140.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            NeonAction(R.drawable.ic_fan, R.string.climate, enabled = busy == null && s.remoteActive,
                active = s.climate == "running" || s.climate == "starting", onClick = onClimate)
            val lat = s.lat
            val lon = s.lon
            if (lat != null && lon != null) NeonAction(R.drawable.ic_location, R.string.map) { onMap(lat, lon) }
        }
    }
}

/**
 * Lays [content] out as if the screen were 227 dp wide (the emulator; OnePlus Watch 3 is 233 dp) and scales it to the
 * real one, so a dial keeps its proportions from 192 dp watches up. The user's font scale is kept.
 */
@Composable
private fun Dial(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val k = (LocalConfiguration.current.screenWidthDp / 227f).coerceIn(0.8f, 1.1f)
    CompositionLocalProvider(LocalDensity provides Density(density.density * k, density.fontScale), content = content)
}

/** Lock state as a pill (or a plain line when [compact]): neutral when locked, Toyota red when not; an open door is called out. */
@Composable
private fun LockChip(s: Snapshot, modifier: Modifier = Modifier, compact: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val back by animateColorAsState(if (s.locked == false) scheme.primaryContainer else scheme.surfaceContainer, tween(400), label = "lockBack")
    val fore by animateColorAsState(
        when (s.locked) {
            true -> scheme.onSurface
            false -> scheme.onPrimaryContainer
            null -> scheme.onSurfaceVariant
        },
        tween(400),
        label = "lockFore",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val pill = if (compact) Modifier else Modifier.background(back, CircleShape).border(.5.dp, Color(0xFF44474E), CircleShape).padding(horizontal = 12.dp, vertical = 3.dp)
        Row(pill, verticalAlignment = Alignment.CenterVertically) {
            Crossfade(targetState = s.locked == true, label = "lockIcon") { locked ->
                Icon(painterResource(if (locked) R.drawable.ic_lock else R.drawable.ic_lock_open), contentDescription = null, modifier = Modifier.size(14.dp), tint = fore)
            }
            Spacer(Modifier.width(6.dp))
            val doorsOpen = (s.openDoors ?: 0) > 0
            FitText(lockText(s) + if (doorsOpen) " · " + stringResource(R.string.door_open) else "",
                TextStyle(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
                if (doorsOpen) scheme.error else fore, Modifier.widthIn(max = 124.dp))
        }
    }
}

/** Full-screen wait: an indeterminate ring along the edge, the car if known, one line of text and optional actions. */
@Composable
fun WaitFace(car: Bitmap?, text: String, working: Boolean, actions: @Composable ColumnScope.() -> Unit = {}) {
    Box(Modifier.fillMaxSize().cockpit(), contentAlignment = Alignment.Center) {
        if (working) CircularProgressIndicator(modifier = Modifier.fillMaxSize().padding(3.dp), strokeWidth = 6.dp)
        Column(Modifier.padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandHeader()
            Spacer(Modifier.height(12.dp))
            car?.let { CarPicture(it, Modifier.fillMaxWidth().height(64.dp).enter()) }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (working) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp).animateContentSize(),
            )
            actions()
        }
    }
}

/** Full-screen, self-dismissing confirmation of the last command: a check or a cross with the outcome text. */
@Composable
fun ResultDialogs(result: Store.CommandResult?) {
    var shown by remember { mutableStateOf<Long?>(null) }
    val pending = result != null && result.at != shown
    val ok = result?.ok == true
    ConfirmationDialog(
        visible = pending,
        onDismissRequest = { shown = result?.at },
        text = { Text(result?.text ?: "", textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium) },
        colors = if (ok) ConfirmationDialogDefaults.successColors(iconColor = Color.White, iconContainerColor = Color(0xFF3B0710), textColor = Color.White) else ConfirmationDialogDefaults.failureColors(),
    ) {
        NeonGauge({ if (ok) 1f else .25f }, Modifier.size(76.dp)) {
            if (ok) ConfirmationDialogDefaults.SuccessIcon() else ConfirmationDialogDefaults.GenericFailureIcon()
        }
    }
}

/** Crown and +/- control the real Toyota set point; the dial is static between interactions. */
@Composable
fun ClimateScreen(
    snapshot: Snapshot?, car: Bitmap?, temp: Double, busy: String?, error: String?,
    onTemp: (Double) -> Unit, onToggle: (Boolean) -> Unit,
) {
    val running = snapshot?.climate == "running" || snapshot?.climate == "starting"
    val lowerTemperature = stringResource(R.string.temperature_down)
    val higherTemperature = stringResource(R.string.temperature_up)
    val haptic = LocalHapticFeedback.current
    val focus = remember { FocusRequester() }
    var travel by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val arc = remember { Animatable(0f) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(temp) {
        val target = ((temp - Store.TEMP_MIN) / (Store.TEMP_MAX - Store.TEMP_MIN)).toFloat().coerceIn(0f, 1f)
        if (reduceMotion) arc.snapTo(target) else arc.animateTo(target, tween(250, easing = FastOutSlowInEasing))
    }
    Dial {
        Box(Modifier.fillMaxSize().cockpit(cabin = running)
            .onRotaryScrollEvent { event ->
                if (busy == null && !running) {
                    travel += event.verticalScrollPixels
                    val steps = (travel / ROTARY_STEP_PX).toInt()
                    if (steps != 0) {
                        travel -= steps * ROTARY_STEP_PX
                        onTemp(temp + steps * Store.TEMP_STEP)
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }
                true
            }.focusRequester(focus).focusable()) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandHeader()
                Text(stringResource(R.string.climate), fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                Text(stringResource(if (running) R.string.climate_on else R.string.climate_set_hint), fontSize = 9.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            NeonGauge({ arc.value }, Modifier.size(106.dp).align(Alignment.TopCenter).offset(y = 68.dp), ticks = true) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_thermostat), contentDescription = null, tint = NeonRed, modifier = Modifier.size(17.dp))
                    FitText(tempText(temp) + "C", TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold), Color.White, Modifier.width(74.dp), minFontSize = 18.sp)
                    Text("${Store.TEMP_MIN.toInt()} – ${Store.TEMP_MAX.toInt()} °C", fontSize = 8.sp, lineHeight = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!running) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).offset(y = 103.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    FilledTonalIconButton(onClick = { onTemp(temp - Store.TEMP_STEP) }, enabled = busy == null && temp > Store.TEMP_MIN,
                        modifier = Modifier.size(48.dp).neonSurface().semantics { contentDescription = lowerTemperature }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent, contentColor = Color.White)) { Text("−", fontSize = 26.sp, lineHeight = 30.sp, textAlign = TextAlign.Center) }
                    FilledTonalIconButton(onClick = { onTemp(temp + Store.TEMP_STEP) }, enabled = busy == null && temp < Store.TEMP_MAX,
                        modifier = Modifier.size(48.dp).neonSurface().semantics { contentDescription = higherTemperature }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent, contentColor = Color.White)) { Text("+", fontSize = 26.sp, lineHeight = 30.sp, textAlign = TextAlign.Center) }
                }
            }
            Row(Modifier.align(Alignment.TopCenter).offset(y = 153.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_fan), contentDescription = null, modifier = Modifier.size(12.dp), tint = if (running) NeonRed else Color(0xFFADB1B8))
                Text(stringResource(if (running) R.string.climate_on else R.string.climate_off), fontSize = 9.sp, lineHeight = 11.sp, modifier = Modifier.padding(start = 4.dp))
            }
            Button(onClick = { onToggle(!running) }, enabled = busy == null && snapshot?.remoteActive == true,
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.size(132.dp, 40.dp).align(Alignment.TopCenter).offset(y = 171.dp).neonSurface(active = true),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_fan), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(if (running) R.string.turn_off else R.string.turn_on), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                }
            }
            if (busy != null) WaitFace(car, busy, working = true)
        }
    }
    ErrorNotice(error) { Store.error.value = null }
}

private const val ROTARY_STEP_PX = 48f

@Composable
fun LinkScreen(error: String?, onLoginHere: () -> Unit) {
    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding) {
            item { Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxWidth().height(72.dp).enter(0)) }
            item { Centered(stringResource(R.string.link_hint), MaterialTheme.typography.bodyMedium, modifier = Modifier.enter(1)) }
            item {
                Button(
                    onClick = onLoginHere,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).enter(2),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_login), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.login_here), fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            error?.let { item { Centered(it, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.error) } }
        }
    }
}

/** Standalone login on the watch with the system keyboard. Saving the password is opt-in. */
@Composable
fun LoginScreen(busy: String?, error: String?, onSubmit: (email: String, password: String, lexus: Boolean, savePassword: Boolean) -> Unit) {
    val email = rememberTextFieldState()
    val password = rememberTextFieldState()
    var lexus by remember { mutableStateOf(false) }
    var savePassword by remember { mutableStateOf(false) }
    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding) {
            item { ListHeader { Text(stringResource(R.string.account_title)) } }
            item { Field(email, stringResource(R.string.email), KeyboardType.Email) }
            item { Field(password, stringResource(R.string.password), KeyboardType.Password, secure = true) }
            item { SwitchButton(checked = lexus, onCheckedChange = { lexus = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.is_lexus)) }) }
            item { SwitchButton(checked = savePassword, onCheckedChange = { savePassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.save_password)) }) }
            item {
                Button(
                    onClick = { onSubmit(email.text.toString().trim(), password.text.toString(), lexus, savePassword) },
                    enabled = busy == null && email.text.isNotBlank() && password.text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                ) { Text(busy ?: stringResource(R.string.sign_in), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            }
            error?.let { item { Centered(it, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.error) } }
            item { Centered(stringResource(if (savePassword) R.string.login_note_saved else R.string.login_note), MaterialTheme.typography.labelSmall, MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ---- pieces ----

@Composable
private fun CarPicture(picture: Bitmap?, modifier: Modifier) {
    if (picture != null) {
        Image(picture.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = modifier)
    }
}

/** One line that shrinks (down to 9 sp) instead of wrapping or being cut: label lengths vary a lot between languages. */
@Composable
private fun FitText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, minFontSize: TextUnit = 9.sp) = BasicText(
    text,
    modifier,
    style = style.copy(color = color, textAlign = TextAlign.Center),
    maxLines = 1,
    autoSize = TextAutoSize.StepBased(minFontSize = if (style.fontSize.value < minFontSize.value) style.fontSize else minFontSize, maxFontSize = style.fontSize),
)

/** Fades and lifts content in once; [order] staggers siblings by 70 ms. Runs in the graphics layer, so nothing recomposes. */
@Composable
private fun Modifier.enter(order: Int = 0): Modifier {
    val progress = remember { Animatable(0f) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(Unit) {
        if (reduceMotion) progress.snapTo(1f) else progress.animateTo(1f, tween(420, delayMillis = order * 70, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 12.dp.toPx()
    }
}

/** Counts up to [target] when it first appears and glides to later values; idle numbers never animate. */
@Composable
private fun countUp(target: Int): Int {
    val value = remember { Animatable(0f) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(target) {
        if (reduceMotion) value.snapTo(target.toFloat()) else value.animateTo(target.toFloat(), tween(900, easing = FastOutSlowInEasing))
    }
    return value.value.roundToInt()
}

@Composable
private fun Field(state: TextFieldState, hint: String, type: KeyboardType, secure: Boolean = false) {
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    val cursor = SolidColor(MaterialTheme.colorScheme.primary)
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fill = MaterialTheme.colorScheme.surfaceContainer
    val decorator = TextFieldDecorator { inner ->
        Box(Modifier.fillMaxWidth().background(fill, RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (state.text.isEmpty()) Text(hint, color = hintColor)
            inner()
        }
    }
    if (secure) {
        BasicSecureTextField(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            textStyle = style,
            cursorBrush = cursor,
            decorator = decorator,
            keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Done),
        )
    } else {
        BasicTextField(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            textStyle = style,
            cursorBrush = cursor,
            lineLimits = TextFieldLineLimits.SingleLine,
            decorator = decorator,
            keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next),
        )
    }
}

@Composable
fun Centered(text: String, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), textAlign = TextAlign.Center, style = style, color = color)

@Composable
private fun lockText(s: Snapshot): String = when (s.locked) {
    true -> stringResource(R.string.locked)
    false -> stringResource(R.string.unlocked)
    null -> stringResource(R.string.lock_unknown)
}

@Composable
private fun tempText(t: Double) = if (t % 1.0 == 0.0) "${t.toInt()}°" else String.format(LocalConfiguration.current.locales[0], "%.1f°", t)

@Composable
private fun number(value: Int): String = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(value)

@Composable
private fun km(value: Int): String = stringResource(R.string.km_format, number(value))

/** "Today, 21:07" / "Yesterday, 18:30" / "30 Sep, 10:12", in the device language. */
@Composable
private fun whenText(epochMs: Long): String {
    val zone = ZoneId.systemDefault()
    val t = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = LocalDate.now(zone)
    val locale = LocalConfiguration.current.locales[0]
    val day = when (t.toLocalDate()) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> t.format(DateTimeFormatter.ofPattern("d MMM", locale))
    }
    return stringResource(R.string.when_format, day, t.format(DateTimeFormatter.ofPattern("HH:mm")))
}

private fun shortTimeText(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d/M HH:mm"))
