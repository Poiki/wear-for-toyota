package com.poiki.toyotawear

import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import com.poiki.toyotawear.core.Releases
import com.poiki.toyotawear.core.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        // Debug builds only: inject tokens without a phone, e.g.
        // adb shell am start -n com.poiki.toyotawear/.MainActivity --es tokens "$(cat tokens.json)"
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            intent.getStringExtra("tokens")?.let { Store.saveTokens(Tokens.fromJson(it)) }
            if (intent.getBooleanExtra("demo", false)) Store.seedDemo() // adb shell am start -n …/.MainActivity --ez demo true
            // --es result "Vehículo cerrado" [--ei resultDelayMs 8000]: fakes a command outcome ("!" prefix = failure) to preview the dialogs.
            intent.getStringExtra("result")?.let { text ->
                lifecycleScope.launch {
                    delay(intent.getIntExtra("resultDelayMs", 0).toLong())
                    Store.result.value = Store.CommandResult(System.currentTimeMillis(), text.removePrefix("!"), !text.startsWith("!"))
                }
            }
        }
        setContent { ToyotaTheme { App(onMap = ::openMap, unlockGate = ::unlockGate) } }
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
            if (Store.tokens.value != null) lifecycleScope.launch { Store.refresh(wake = true) }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

private val ToyotaRed = Color(0xFFEB0A1E)

@Composable
private fun ToyotaTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = ColorScheme(
        primary = ToyotaRed,
        primaryDim = Color(0xFFB8081A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF5A0A12),
        onPrimaryContainer = Color(0xFFFFDAD8),
    ),
    content = content,
)

private enum class Route { Garage, Vehicle, Lock, Climate }

@Composable
private fun App(onMap: (Double, Double) -> Unit, unlockGate: () -> String?) {
    val tokens by Store.tokens.collectAsState()
    val error by Store.error.collectAsState()
    val busy by Store.busy.collectAsState()
    val result by Store.result.collectAsState()
    val update by Store.update.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var login by remember { mutableStateOf(false) }
    var offer by remember { mutableStateOf<Releases.Apk?>(null) } // outlives Store.update so the dialog can animate out
    val stack = remember { mutableStateListOf(Route.Garage) }

    LaunchedEffect(Unit) { Store.checkUpdate() }
    LaunchedEffect(update) { update?.let { offer = it } }
    LaunchedEffect(tokens) {
        if (tokens != null) {
            login = false
            Store.loadVehicles()
            if (Store.snapshot.value == null) Store.refresh(wake = false)
        }
    }
    LaunchedEffect(result) { result?.let { haptic.performHapticFeedback(if (it.ok) HapticFeedbackType.Confirm else HapticFeedbackType.Reject) } }
    BackHandler(enabled = login || stack.size > 1) { if (login) login = false else stack.removeAt(stack.size - 1) }

    val screen = when {
        tokens != null -> "app"
        login -> "login"
        else -> "link"
    }
    AppScaffold {
        // One short fade between the three top-level states; nothing animates while idle.
        Crossfade(targetState = screen, animationSpec = tween(250), label = "screen") { target ->
            when (target) {
                "app" -> Stack(stack, onMap, unlockGate)
                "login" -> SwipeToDismissBox(onDismissed = { login = false }) { isBackground ->
                    if (isBackground) {
                        LinkScreen(busy = null, error = null, onLoginHere = {})
                    } else {
                        LoginScreen(busy = busy, error = error) { email, password, lexus, savePassword ->
                            scope.launch { Store.login(email, password, if (lexus) "L" else "T", savePassword) }
                        }
                    }
                }
                else -> LinkScreen(busy = busy, error = error, onLoginHere = { Store.error.value = null; login = true })
            }
        }
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
                                    Store.error.value = context.getString(R.string.err_install_permission)
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
private fun Stack(stack: SnapshotStateList<Route>, onMap: (Double, Double) -> Unit, unlockGate: () -> String?) {
    val top = stack.last()
    if (stack.size == 1) {
        Screen(top, stack, onMap, unlockGate)
    } else {
        val below = stack[stack.size - 2]
        SwipeToDismissBox(onDismissed = { stack.removeAt(stack.size - 1) }, backgroundKey = below, contentKey = top) { isBackground ->
            Screen(if (isBackground) below else top, stack, onMap, unlockGate)
        }
    }
}

@Composable
private fun Screen(route: Route, stack: SnapshotStateList<Route>, onMap: (Double, Double) -> Unit, unlockGate: () -> String?) {
    val vehicles by Store.vehicles.collectAsState()
    val images by Store.carImages.collectAsState()
    val selectedVin by Store.selectedVin.collectAsState()
    val snapshot by Store.snapshot.collectAsState()
    val climateTemp by Store.climateTemp.collectAsState()
    val busy by Store.busy.collectAsState()
    val error by Store.error.collectAsState()
    val result by Store.result.collectAsState()
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
                scope.launch { Store.refresh(wake = false) }
            },
            onUnlink = { Store.unlink() },
        )
        Route.Vehicle -> VehicleScreen(
            snapshot = snapshot,
            car = images[selectedVin],
            busy = busy,
            error = error,
            onWake = { scope.launch { Store.refresh(wake = true) } },
            onLock = { Store.result.value = null; stack.add(Route.Lock) },
            onClimate = {
                Store.result.value = null
                stack.add(Route.Climate)
                scope.launch { Store.loadClimateSettings() }
            },
            onMap = onMap,
        )
        Route.Lock -> LockScreen(
            snapshot = snapshot,
            busy = busy,
            error = error,
            result = result,
            onLock = { scope.launch { Store.lock(true) } },
            onUnlock = {
                val reason = unlockGate()
                if (reason != null) Store.error.value = reason else scope.launch { Store.lock(false) }
            },
        )
        Route.Climate -> ClimateScreen(
            snapshot = snapshot,
            temp = climateTemp ?: 21.0,
            busy = busy,
            error = error,
            result = result,
            onTemp = Store::setClimateTemp,
            onToggle = { start -> scope.launch { Store.climate(start) } },
        )
    }
}
