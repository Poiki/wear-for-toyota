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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.HorizontalPagerScaffold
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
import java.util.Locale
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
    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding) {
            item { ListHeader { Text(stringResource(R.string.garage_title)) } }
            items(vehicles.size) { i ->
                val v = vehicles[i]
                val vin = v.optString("vin")
                Card(onClick = { onSelect(vin) }, modifier = Modifier.fillMaxWidth().enter(i)) {
                    CarPicture(images[vin], Modifier.fillMaxWidth().height(96.dp))
                    Text(
                        Snapshot.displayName(v),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item { OutlinedButton(onClick = onUnlink, enabled = busy == null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.unlink)) } }
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
    Crossfade(targetState = snapshot != null, animationSpec = tween(450), label = "vehicle") { ready ->
        val s = snapshot
        if (!ready || s == null) {
            WaitFace(car, text = error ?: busy ?: stringResource(R.string.loading), working = error == null) {
                if (error != null) Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.retry)) }
            }
        } else {
            val pager = rememberPagerState(pageCount = { 2 })
            HorizontalPagerScaffold(pagerState = pager) {
                HorizontalPager(state = pager) { page ->
                    AnimatedPage(pageIndex = page, pagerState = pager) {
                        Dial { if (page == 0) StatusFace(s, busy, onWake) else ControlsFace(s, busy, onLock, onUnlock, onClimate, onMap) }
                    }
                }
            }
            ErrorNotice(error, onDismiss = onErrorShown)
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
    ) { ConfirmationDialogDefaults.FailureIcon() }
}

/** Status dial: lock state, fuel (or battery) with its arc, range, mileage and when the car last reported. */
@Composable
private fun StatusFace(s: Snapshot, busy: String?, onWake: () -> Unit) {
    val energy = s.fuelPct ?: s.batteryPct
    val arc = remember { Animatable(0f) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(energy) {
        val target = (energy ?: 0) / 100f
        if (reduceMotion) arc.snapTo(target) else arc.animateTo(target, tween(1100, easing = FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxSize()) {
        if (energy != null) {
            CircularProgressIndicator(
                progress = { arc.value },
                modifier = Modifier.fillMaxSize().padding(3.dp),
                startAngle = 135f,
                endAngle = 45f,
                strokeWidth = 6.dp,
            )
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LockChip(s, Modifier.enter(0))
            if (energy != null) {
                Row(Modifier.padding(top = 8.dp).enter(1), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(if (s.fuelPct != null) R.drawable.ic_fuel else R.drawable.ic_battery),
                        contentDescription = stringResource(if (s.fuelPct != null) R.string.fuel else R.string.battery),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("${countUp(energy)}%", style = MaterialTheme.typography.displaySmall, maxLines = 1)
                }
            }
            (s.fuelRangeKm ?: s.evRangeKm)?.let { range ->
                FitText(stringResource(R.string.range_format, number(countUp(range.roundToInt()))), MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.enter(1))
            }
            s.odometerKm?.let { odometer ->
                Row(Modifier.padding(top = 6.dp).enter(2), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_odometer), contentDescription = stringResource(R.string.odometer), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    FitText(km(odometer.roundToInt()), MaterialTheme.typography.titleMedium, MaterialTheme.colorScheme.onSurface)
                }
            }
            // When the car last reported; tapping asks the car itself for fresh data.
            CompactButton(
                onClick = onWake,
                enabled = busy == null,
                colors = ButtonDefaults.filledTonalButtonColors(),
                icon = {
                    if (busy != null) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    else Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.refresh), modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.padding(top = 8.dp).enter(3),
            ) { FitText(busy ?: whenText(s.statusAt ?: s.fetchedAt), MaterialTheme.typography.labelMedium, MaterialTheme.colorScheme.onSurface) }
        }
    }
}

/** Controls dial: lock, unlock (confirmed), climate and the map as round buttons; the running one shows a ring. */
@Composable
private fun ControlsFace(
    s: Snapshot,
    busy: String?,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onClimate: () -> Unit,
    onMap: (Double, Double) -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(busy) { if (busy == null) running = null }
    val lat = s.lat
    val lon = s.lon
    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Crossfade(targetState = busy, label = "controlsLine") { line ->
            if (line == null) LockChip(s, compact = true) else FitText(line, MaterialTheme.typography.labelMedium, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Control(R.drawable.ic_lock, R.string.lock, working = running == 0, enabled = busy == null, modifier = Modifier.enter(0)) { running = 0; onLock() }
            Control(R.drawable.ic_lock_open, R.string.unlock, working = running == 1, enabled = busy == null, modifier = Modifier.enter(1)) { confirm = true }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Control(R.drawable.ic_fan, R.string.climate, working = false, enabled = true, highlighted = s.climate == "running" || s.climate == "starting", modifier = Modifier.enter(2), onClick = onClimate)
            if (lat != null && lon != null) Control(R.drawable.ic_location, R.string.map, working = false, enabled = true, modifier = Modifier.enter(3)) { onMap(lat, lon) }
        }
    }
    AlertDialog(
        visible = confirm,
        onDismissRequest = { confirm = false },
        confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = { confirm = false; running = 1; onUnlock() }) },
        title = { Text(stringResource(R.string.confirm_unlock)) },
    )
}

@Composable
private fun Control(icon: Int, label: Int, working: Boolean, enabled: Boolean, modifier: Modifier = Modifier, highlighted: Boolean = false, onClick: () -> Unit) {
    Column(modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            FilledIconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
                colors = if (highlighted) IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.filledTonalIconButtonColors(),
            ) { Icon(painterResource(icon), contentDescription = stringResource(label), modifier = Modifier.size(24.dp)) }
            if (working) CircularProgressIndicator(modifier = Modifier.fillMaxSize(), strokeWidth = 3.dp)
        }
        FitText(stringResource(label), MaterialTheme.typography.labelSmall, MaterialTheme.colorScheme.onSurface)
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
        val pill = if (compact) Modifier else Modifier.background(back, CircleShape).padding(horizontal = 14.dp, vertical = 6.dp)
        Row(pill, verticalAlignment = Alignment.CenterVertically) {
            Crossfade(targetState = s.locked == true, label = "lockIcon") { locked ->
                Icon(painterResource(if (locked) R.drawable.ic_lock else R.drawable.ic_lock_open), contentDescription = null, modifier = Modifier.size(if (compact) 16.dp else 20.dp), tint = fore)
            }
            Spacer(Modifier.width(6.dp))
            FitText(lockText(s), if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, fore)
        }
        if ((s.openDoors ?: 0) > 0) FitText(stringResource(R.string.door_open), MaterialTheme.typography.labelSmall, scheme.error, Modifier.padding(top = 2.dp))
    }
}

/** Full-screen wait: an indeterminate ring along the edge, the car if known, one line of text and optional actions. */
@Composable
fun WaitFace(car: Bitmap?, text: String, working: Boolean, actions: @Composable ColumnScope.() -> Unit = {}) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        if (working) CircularProgressIndicator(modifier = Modifier.fillMaxSize().padding(3.dp), strokeWidth = 6.dp)
        Column(Modifier.padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
        colors = if (ok) ConfirmationDialogDefaults.successColors() else ConfirmationDialogDefaults.failureColors(),
    ) {
        if (ok) ConfirmationDialogDefaults.SuccessIcon() else ConfirmationDialogDefaults.FailureIcon()
    }
}

/** Climate as a dial: the arc is the temperature, the crown or the +/- buttons change it, the edge button starts or stops. */
@Composable
fun ClimateScreen(
    snapshot: Snapshot?,
    temp: Double,
    busy: String?,
    error: String?,
    result: Store.CommandResult?,
    onTemp: (Double) -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val running = snapshot?.climate == "running" || snapshot?.climate == "starting"
    val haptic = LocalHapticFeedback.current
    val focus = remember { FocusRequester() }
    // The crown reports many small scroll events; one temperature step per ROTARY_STEP_PX of travel.
    var travel by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val arc = remember { Animatable(0f) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(temp) {
        val target = ((temp - Store.TEMP_MIN) / (Store.TEMP_MAX - Store.TEMP_MIN)).toFloat()
        // Sweeps in on opening, then glides one step at a time.
        if (reduceMotion) arc.snapTo(target) else arc.animateTo(target, tween(if (arc.value == 0f) 700 else 160, easing = FastOutSlowInEasing))
    }
    Dial {
        Box(
            Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    travel += event.verticalScrollPixels
                    val steps = (travel / ROTARY_STEP_PX).toInt()
                    if (steps != 0) {
                        travel -= steps * ROTARY_STEP_PX
                        onTemp(temp + steps * Store.TEMP_STEP)
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                    true
                }
                .focusRequester(focus)
                .focusable(),
        ) {
            CircularProgressIndicator(
                progress = { arc.value },
                modifier = Modifier.fillMaxSize().padding(3.dp),
                startAngle = 135f,
                endAngle = 45f,
                strokeWidth = 8.dp,
            )
            Column(
                Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 36.dp, bottom = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.climate), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { onTemp(temp - Store.TEMP_STEP) }, modifier = Modifier.size(48.dp)) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    Text(tempText(temp), style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(horizontal = 10.dp))
                    FilledTonalIconButton(onClick = { onTemp(temp + Store.TEMP_STEP) }, modifier = Modifier.size(48.dp)) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
                val line = busy ?: result?.text ?: error ?: snapshot?.climate?.let { climateText(it) } ?: stringResource(R.string.climate_off)
                val lineColor = when {
                    busy != null -> MaterialTheme.colorScheme.onSurfaceVariant
                    error != null || result?.ok == false -> MaterialTheme.colorScheme.error
                    running || result?.ok == true -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = lineColor, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp).animateContentSize())
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                EdgeButton(onClick = { onToggle(!running) }, enabled = busy == null, buttonSize = EdgeButtonSize.Small) {
                    Text(stringResource(if (running) R.string.turn_off else R.string.turn_on))
                }
            }
        }
    }
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
                    icon = { Icon(painterResource(R.drawable.ic_login), contentDescription = null) },
                    label = { Text(stringResource(R.string.login_here)) },
                )
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
                ) { Text(busy ?: stringResource(R.string.sign_in)) }
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
private fun FitText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) = BasicText(
    text,
    modifier,
    style = style.copy(color = color, textAlign = TextAlign.Center),
    maxLines = 1,
    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize),
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
private fun climateText(status: String): String = when (status) {
    "running" -> stringResource(R.string.climate_on)
    "starting" -> stringResource(R.string.climate_starting)
    "stopping" -> stringResource(R.string.climate_stopping)
    "stopped" -> stringResource(R.string.climate_off)
    else -> status
}

private fun tempText(t: Double) = if (t % 1.0 == 0.0) "${t.toInt()}°" else String.format(Locale.getDefault(), "%.1f°", t)

private fun number(value: Int): String = NumberFormat.getIntegerInstance().format(value)

@Composable
private fun km(value: Int): String = stringResource(R.string.km_format, number(value))

/** "Today, 21:07" / "Yesterday, 18:30" / "30 Sep, 10:12", in the device language. */
@Composable
private fun whenText(epochMs: Long): String {
    val zone = ZoneId.systemDefault()
    val t = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = LocalDate.now(zone)
    val day = when (t.toLocalDate()) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> t.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
    return stringResource(R.string.when_format, day, t.format(DateTimeFormatter.ofPattern("HH:mm")))
}
