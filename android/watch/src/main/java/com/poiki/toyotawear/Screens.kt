package com.poiki.toyotawear

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
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
import kotlin.math.roundToLong

/** "My Garage": one big card per car, like MyToyota's garage. */
@Composable
fun GarageScreen(
    vehicles: List<JSONObject>,
    images: Map<String, Bitmap>,
    busy: String?,
    error: String?,
    onSelect: (String) -> Unit,
    onUnlink: () -> Unit,
) {
    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding) {
            item { ListHeader { Text(stringResource(R.string.garage_title)) } }
            if (vehicles.isEmpty()) item { StatusLine(busy ?: error ?: stringResource(R.string.no_vehicles), working = busy != null) }
            items(vehicles.size) { i ->
                val v = vehicles[i]
                val vin = v.optString("vin")
                Card(onClick = { onSelect(vin) }, modifier = Modifier.fillMaxWidth()) {
                    images[vin]?.let {
                        Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(96.dp), contentScale = ContentScale.Fit)
                    }
                    Text(
                        Snapshot.displayName(v),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (vehicles.isNotEmpty()) {
                busy?.let { item { StatusLine(it, working = true) } }
                error?.let { item { Centered(it, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.error) } }
            }
            item { OutlinedButton(onClick = onUnlink, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.unlink)) } }
        }
    }
}

/** MyToyota's dashboard on the wrist: metrics first, then the remote-control entries. */
@Composable
fun VehicleScreen(
    snapshot: Snapshot?,
    car: Bitmap?,
    busy: String?,
    error: String?,
    onWake: () -> Unit,
    onLock: () -> Unit,
    onClimate: () -> Unit,
    onMap: (Double, Double) -> Unit,
) {
    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(
        scrollState = state,
        edgeButton = {
            EdgeButton(onClick = onWake, enabled = busy == null && snapshot != null, buttonSize = EdgeButtonSize.Small) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.refresh), modifier = Modifier.size(24.dp))
            }
        },
    ) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding) {
            if (snapshot == null) {
                item { StatusLine(busy ?: error ?: stringResource(R.string.loading), working = busy != null) }
                return@TransformingLazyColumn
            }
            item { ListHeader { Text(snapshot.alias) } }
            car?.let {
                item { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(72.dp), contentScale = ContentScale.Fit) }
            }
            item { LockRow(snapshot) }
            item { Metrics(snapshot) }
            item { StatusLine(busy ?: whenText(snapshot.statusAt ?: snapshot.fetchedAt), working = busy != null) }
            error?.let { item { Centered(it, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.error) } }
            item { ListHeader { Text(stringResource(R.string.remote_control)) } }
            item { BigButton(R.drawable.ic_lock, stringResource(R.string.lock_unlock), lockText(snapshot), onLock) }
            item { BigButton(R.drawable.ic_fan, stringResource(R.string.climate), snapshot.climate?.let { climateText(it) } ?: stringResource(R.string.no_data), onClimate) }
            val lat = snapshot.lat
            val lon = snapshot.lon
            if (lat != null && lon != null) {
                item { BigButton(R.drawable.ic_location, stringResource(R.string.last_position), whenText(snapshot.locationAt ?: snapshot.fetchedAt)) { onMap(lat, lon) } }
            }
        }
    }
}

/** Lock and unlock share one screen; unlocking asks for confirmation. */
@Composable
fun LockScreen(
    snapshot: Snapshot?,
    busy: String?,
    error: String?,
    result: Store.CommandResult?,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding) {
            item { ListHeader { Text(snapshot?.alias ?: stringResource(R.string.lock_unlock)) } }
            snapshot?.let { item { LockRow(it) } }
            item { StatusLine(busy ?: snapshot?.let { whenText(it.statusAt ?: it.fetchedAt) } ?: "", working = busy != null) }
            result?.let { item { Centered(it.text, MaterialTheme.typography.bodyMedium, if (it.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) } }
            error?.let { item { Centered(it, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.error) } }
            item {
                Button(
                    onClick = onLock,
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    icon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(28.dp)) },
                    label = { Text(stringResource(R.string.lock), style = MaterialTheme.typography.titleMedium) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { confirm = true },
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    icon = { Icon(painterResource(R.drawable.ic_lock_open), contentDescription = null, modifier = Modifier.size(28.dp)) },
                    label = { Text(stringResource(R.string.unlock), style = MaterialTheme.typography.titleMedium) },
                )
            }
        }
    }
    AlertDialog(
        visible = confirm,
        onDismissRequest = { confirm = false },
        confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = { confirm = false; onUnlock() }) },
        title = { Text(stringResource(R.string.confirm_unlock)) },
    )
    ResultDialogs(result)
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
    val fraction = ((temp - Store.TEMP_MIN) / (Store.TEMP_MAX - Store.TEMP_MIN)).toFloat()
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
            progress = { fraction },
            modifier = Modifier.fillMaxSize().padding(4.dp),
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
            val line = busy ?: result?.text ?: error ?: stringResource(if (running) R.string.climate_on else R.string.climate_off)
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
    ResultDialogs(result)
}

private const val ROTARY_STEP_PX = 48f

@Composable
fun LinkScreen(busy: String?, error: String?, onLoginHere: () -> Unit) {
    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding) {
            item { Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxWidth().height(72.dp)) }
            item { Centered(stringResource(R.string.link_hint), MaterialTheme.typography.bodyMedium) }
            item {
                Button(
                    onClick = onLoginHere,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    icon = { Icon(painterResource(R.drawable.ic_login), contentDescription = null) },
                    label = { Text(stringResource(R.string.login_here)) },
                )
            }
            busy?.let { item { StatusLine(it, working = true) } }
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
private fun BigButton(icon: Int, label: String, secondary: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        icon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp)) },
        secondaryLabel = { Text(secondary) },
        label = { Text(label, style = MaterialTheme.typography.titleMedium) },
    )
}

/** Range as the hero figure, then the rest in a row: what matters on a wrist. */
@Composable
private fun Metrics(s: Snapshot) {
    val range = s.fuelRangeKm ?: s.evRangeKm
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        range?.let {
            Text(km(it), style = MaterialTheme.typography.displaySmall)
            Text(stringResource(R.string.range), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            s.fuelPct?.let { Figure("$it%", stringResource(R.string.fuel)) }
            s.batteryPct?.let { Figure("$it%", stringResource(R.string.battery)) }
            s.odometerKm?.let { Figure(km(it), stringResource(R.string.odometer)) }
        }
    }
}

@Composable
private fun Figure(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Date line, or the current step with a small spinner that only exists while a request is running. */
@Composable
fun StatusLine(text: String, working: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).animateContentSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (working) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LockRow(s: Snapshot) {
    val icon = if (s.locked == true) R.drawable.ic_lock else R.drawable.ic_lock_open
    val target = when (s.locked) {
        true -> MaterialTheme.colorScheme.onSurface
        false -> MaterialTheme.colorScheme.primary
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val color by animateColorAsState(target, tween(400), label = "lockColor")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(26.dp), tint = color)
        Spacer(Modifier.width(8.dp))
        Text(lockText(s), style = MaterialTheme.typography.titleMedium, color = color)
    }
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
fun Centered(text: String, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified) =
    Text(text, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), textAlign = TextAlign.Center, style = style, color = color)

@Composable
private fun lockText(s: Snapshot): String = when (s.locked) {
    true -> stringResource(if ((s.openDoors ?: 0) > 0) R.string.locked_door_open else R.string.locked)
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

@Composable
private fun km(value: Double): String = stringResource(R.string.km_format, NumberFormat.getIntegerInstance().format(value.roundToLong()))

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
