package com.poiki.toyotawear

import android.app.KeyguardManager
import android.app.LocaleManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.edgeSwipeToDismiss
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import com.poiki.toyotawear.core.Releases
import com.poiki.toyotawear.core.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val debug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        super.onCreate(savedInstanceState)
        if (debug && Build.VERSION.SDK_INT >= 33) intent.getStringExtra("locale")?.let {
            getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(it)
        }
        Store.init(this)
        // Debug builds only: inject tokens without a phone, e.g.
        // adb shell am start -n com.poiki.toyotawear/.MainActivity --es tokens "$(cat tokens.json)"
        if (debug) {
            intent.getStringExtra("tokens")?.let { Store.saveTokens(Tokens.fromJson(it)) }
            if (intent.getBooleanExtra("demo", false)) Store.seedDemo() // adb shell am start -n …/.MainActivity --ez demo true
            if (intent.getBooleanExtra("demo", false)) {
                // Local UI checks: no real Toyota commands or credentials required.
                if (intent.getBooleanExtra("climateRunning", false)) Store.snapshot.value = Store.snapshot.value?.copy(climate = "running")
                if (intent.getBooleanExtra("doorOpen", false)) Store.snapshot.value = Store.snapshot.value?.copy(openDoors = 1)
                intent.getStringExtra("busy")?.let { Store.busy.value = it }
            }
            // --es result "Vehículo cerrado" [--ei resultDelayMs 8000]: fakes a command outcome ("!" prefix = failure) to preview the dialogs.
            intent.getStringExtra("result")?.let { text ->
                lifecycleScope.launch {
                    delay(intent.getIntExtra("resultDelayMs", 0).toLong())
                    Store.result.value = Store.CommandResult(System.currentTimeMillis(), text.removePrefix("!"), !text.startsWith("!"))
                }
            }
        }
        val preview = if (debug && intent.getBooleanExtra("demo", false)) intent.getStringExtra("preview") else null
        setContent { ToyotaTheme { App(onMap = ::openMap, unlockGate = ::unlockGate, preview = preview) } }
    }

    private fun openMap(lat: Double, lon: Double) {
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(getString(R.string.map_label))})")
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure { Store.error.value = getString(R.string.err_no_maps) }
    }

    /**
     * Unlocking only asks for the on-screen confirmation (user's choice: no watch PIN required).
     * If the watch does have a lock screen, it still must be unlocked at that moment.
     */
    private fun unlockGate(): String? {
        val keyguard = getSystemService(KeyguardManager::class.java)
        return if (keyguard.isDeviceLocked) getString(R.string.err_watch_locked) else null
    }

    /** Extra hardware button (if the system hands it to apps) = ask the car for fresh data. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        Log.d("ToyotaWear", "keyDown $keyCode")
        if (keyCode == KeyEvent.KEYCODE_STEM_1 || keyCode == KeyEvent.KEYCODE_STEM_2 || keyCode == KeyEvent.KEYCODE_STEM_3) {
            if (Store.linked.value) lifecycleScope.launch { Store.refresh(wake = true) }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

private val ToyotaRed = Color(0xFFFF1838)

@Composable
private fun ToyotaTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = ColorScheme(
        primary = ToyotaRed,
        primaryDim = Color(0xFFB8081A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF5A0A12),
        onPrimaryContainer = Color(0xFFFFDAD8),
        background = Color.Black,
        onBackground = Color.White,
        surfaceContainer = Color(0xFF14161A),
        onSurface = Color(0xFFF5F6F8),
        onSurfaceVariant = Color(0xFFA9ADB4),
    ),
    content = content,
)

private enum class Route { Garage, Vehicle, Climate, Trips }

@Composable
private fun App(onMap: (Double, Double) -> Unit, unlockGate: () -> String?, preview: String? = null) {
    val linked by Store.linked.collectAsState()
    val error by Store.error.collectAsState()
    val busy by Store.busy.collectAsState()
    val result by Store.result.collectAsState()
    val update by Store.update.collectAsState()
    val updating by Store.updating.collectAsState()
    val context = LocalContext.current
    val installPermissionError = stringResource(R.string.err_install_permission)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var login by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(preview == "about") }
    var offer by remember { mutableStateOf<Releases.Apk?>(null) } // outlives Store.update so the dialog can animate out
    val stack = remember {
        mutableStateListOf(Route.Garage).apply {
            if (preview == "vehicle" || preview == "climate" || preview == "trips") add(Route.Vehicle)
            if (preview == "climate") add(Route.Climate)
            if (preview == "trips") add(Route.Trips)
        }
    }

    LaunchedEffect(Unit) { Store.checkUpdate() }
    LaunchedEffect(update) { update?.let { offer = it } }
    LaunchedEffect(linked) {
        if (linked) {
            login = false
            Store.loadVehicles()
            // Prefetch the last car while the garage shows: opening it is then usually instant.
            if (Store.snapshot.value == null) Store.refresh(wake = false)
        }
    }
    LaunchedEffect(result) { result?.let { haptic.performHapticFeedback(if (it.ok) HapticFeedbackType.Confirm else HapticFeedbackType.Reject) } }
    BackHandler(enabled = login || stack.size > 1) { if (login) login = false else stack.removeAt(stack.size - 1) }

    val screen = when {
        linked -> "app"
        login -> "login"
        else -> "link"
    }
    AppScaffold(timeText = {}) {
        // One short fade between the three top-level states; nothing animates while idle.
        Crossfade(targetState = screen, animationSpec = tween(250), label = "screen") { target ->
            when (target) {
                "app" -> Stack(stack, onMap, unlockGate) { about = true }
                "login" -> SwipeToDismissBox(onDismissed = { login = false }) { isBackground ->
                    if (isBackground) {
                        LinkScreen(error = null, onLoginHere = {}, onAbout = {})
                    } else {
                        LoginScreen(busy = busy, error = error) { email, password, lexus, savePassword ->
                            scope.launch { Store.login(email, password, if (lexus) "L" else "T", savePassword) }
                        }
                    }
                }
                else -> LinkScreen(error = error, onLoginHere = { Store.error.value = null; login = true }, onAbout = { about = true })
            }
        }
        // One dialog for every command outcome, whichever screen sent it.
        ResultDialogs(result)
        Dialog(visible = about, onDismissRequest = { about = false }) {
            AboutScreen(onClear = {
                Store.unlink()
                stack.clear()
                stack.add(Route.Garage)
                login = false
                about = false
            })
        }
        if (updating) WaitFace(car = null, text = stringResource(R.string.busy_update), working = true)
        offer?.let { apk ->
            AlertDialog(
                visible = update != null,
                onDismissRequest = { Store.update.value = null },
                confirmButton = {
                    AlertDialogDefaults.ConfirmButton(onClick = {
                        if (context.packageManager.canRequestPackageInstalls()) {
                            Store.update.value = null
                            scope.launch { Store.installUpdate(apk) }
                        } else {
                            // One-time "Install unknown apps" switch for this app; the offer stays up for when the user comes back.
                            runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }
                                .onFailure {
                                    Store.update.value = null
                                    Store.error.value = installPermissionError
                                }
                        }
                    })
                },
                title = { Text(stringResource(R.string.update_title, apk.version)) },
                text = { Text(stringResource(R.string.update_text)) },
            )
        }
    }
}

/** Minimal back stack: the top screen slides over the previous one and swipes away to go back. */
@Composable
private fun Stack(stack: SnapshotStateList<Route>, onMap: (Double, Double) -> Unit, unlockGate: () -> String?, onAbout: () -> Unit) {
    val top = stack.last()
    if (stack.size == 1) {
        Screen(top, stack, onMap, unlockGate, onAbout)
    } else {
        val below = stack[stack.size - 2]
        val dismissState = rememberSwipeToDismissBoxState()
        val snapshot by Store.snapshot.collectAsState()
        val busy by Store.busy.collectAsState()
        // The pager owns horizontal drags; the native edge modifier handles returning to the garage.
        val paged = top == Route.Vehicle && snapshot != null && busy == null
        SwipeToDismissBox(onDismissed = { stack.removeAt(stack.size - 1) }, state = dismissState,
            userSwipeEnabled = !paged, backgroundKey = below, contentKey = top) { isBackground ->
            Box(if (paged && !isBackground) Modifier.edgeSwipeToDismiss(dismissState) else Modifier) {
                Screen(if (isBackground) below else top, stack, onMap, unlockGate, onAbout)
            }
        }
    }
}

@Composable
private fun Screen(route: Route, stack: SnapshotStateList<Route>, onMap: (Double, Double) -> Unit, unlockGate: () -> String?, onAbout: () -> Unit) {
    val vehicles by Store.vehicles.collectAsState()
    val images by Store.carImages.collectAsState()
    val selectedVin by Store.selectedVin.collectAsState()
    val snapshot by Store.snapshot.collectAsState()
    val climateTemp by Store.climateTemp.collectAsState()
    val busy by Store.busy.collectAsState()
    val error by Store.error.collectAsState()
    val scope = rememberCoroutineScope()
    when (route) {
        Route.Garage -> GarageScreen(
            vehicles = vehicles,
            images = images,
            busy = busy,
            error = error,
            onSelect = { vin ->
                Store.select(vin)
                stack.add(Route.Vehicle)
            },
            onRetry = { scope.launch { Store.loadVehicles() } },
            onAbout = onAbout,
        )
        Route.Vehicle -> {
            // Reads the car whenever it has no fresh data and nothing is running; an error waits for "Retry".
            LaunchedEffect(selectedVin, snapshot == null, busy == null, error == null) {
                if (snapshot == null && busy == null && error == null) Store.refresh(wake = false)
            }
            VehicleScreen(
                snapshot = snapshot,
                car = images[selectedVin],
                busy = busy,
                error = error,
                onRetry = { Store.error.value = null },
                onErrorShown = { Store.error.value = null },
                onWake = { scope.launch { Store.refresh(wake = true) } },
                onLock = { scope.launch { Store.lock(true) } },
                onUnlock = {
                    val reason = unlockGate()
                    if (reason != null) Store.error.value = reason else scope.launch { Store.lock(false) }
                },
                onClimate = {
                    Store.result.value = null
                    stack.add(Route.Climate)
                    scope.launch { Store.loadClimateSettings() }
                },
                onMap = onMap,
                onTrips = { stack.add(Route.Trips) },
            )
        }
        Route.Climate -> ClimateScreen(
            snapshot = snapshot,
            car = images[selectedVin],
            temp = climateTemp ?: 21.0,
            busy = busy,
            error = error,
            onTemp = Store::setClimateTemp,
            onToggle = { start -> scope.launch { Store.climate(start) } },
        )
        Route.Trips -> {
            val history by Store.tripHistory.collectAsState()
            val loading by Store.tripsLoading.collectAsState()
            val tripError by Store.tripsError.collectAsState()
            LaunchedEffect(selectedVin) { Store.loadTrips() }
            TripsScreen(history, loading, tripError) { scope.launch { Store.loadTrips() } }
        }
    }
}
