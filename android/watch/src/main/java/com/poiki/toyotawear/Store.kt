package com.poiki.toyotawear

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.poiki.toyotawear.core.Releases
import com.poiki.toyotawear.core.Snapshot
import com.poiki.toyotawear.core.Tokens
import com.poiki.toyotawear.core.ToyotaApi
import com.poiki.toyotawear.core.ToyotaAuth
import com.poiki.toyotawear.core.ToyotaError
import com.poiki.toyotawear.core.ToyotaLoginError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL

/**
 * Single source of truth on the watch: encrypted tokens (and the password, only if the user saves it), the
 * vehicle list, the last raw Toyota payloads per vehicle (one JSON string each, the only cache), the Snapshot
 * of the selected vehicle, a small cached picture per car and the self-update. No database, no background work.
 */
object Store {
    data class CommandResult(val at: Long, val text: String, val ok: Boolean)

    /** Tokens are on the watch. They are only decrypted for the first request: the Keystore costs ~60 ms at startup. */
    val linked = MutableStateFlow(false)
    val vehicles = MutableStateFlow<List<JSONObject>>(emptyList())
    val selectedVin = MutableStateFlow<String?>(null)
    val snapshot = MutableStateFlow<Snapshot?>(null)
    val carImages = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val climateTemp = MutableStateFlow<Double?>(null)
    val busy = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    /** Outcome of the last remote command; the UI shows it and vibrates once. */
    val result = MutableStateFlow<CommandResult?>(null)
    /** A newer watch APK on GitHub, offered once per check. */
    val update = MutableStateFlow<Releases.Apk?>(null)
    /** The update APK is downloading. */
    val updating = MutableStateFlow(false)

    const val TEMP_MIN = 18.0
    const val TEMP_MAX = 29.0
    const val TEMP_STEP = 0.5
    private const val CLIMATE_MINUTES = 10
    private const val CAR_IMAGE_WIDTH = 320
    private const val CREDENTIALS = "credentials"
    private const val WATCH_APK = "wear-for-toyota-watch"
    private const val UPDATE_CHECK_MS = 24 * 3600_000L
    /** Data read this recently (e.g. prefetched while the garage was showing) is shown at once; older data never is. */
    private const val FRESH_MS = 2 * 60_000L

    @Volatile private var tokens: Tokens? = null
    private lateinit var app: Context
    private lateinit var vault: Vault
    /** The saved password, only if the user asked for it: its own strict Keystore key. */
    private lateinit var secrets: Vault
    private lateinit var cache: SharedPreferences
    private val api = ToyotaApi { freshTokens() }
    private val climateSaved = mutableMapOf<String, JSONObject?>()

    // ponytail: fixed ~30 s wake schedule; tune once real cars have been measured.
    private val wakePollMs = longArrayOf(5_000, 5_000, 10_000, 10_000)

    @Synchronized
    fun init(context: Context) {
        if (::vault.isInitialized) return
        app = context.applicationContext
        vault = Vault(app)
        secrets = Vault(app, "toyota-credentials", strict = true)
        cache = app.getSharedPreferences("cache", Context.MODE_PRIVATE)
        linked.value = vault.has("tokens")
        vehicles.value = cache.getString("vehicles", null)
            ?.let { s -> runCatching { JSONArray(s).let { a -> List(a.length()) { i -> a.getJSONObject(i) } } }.getOrNull() }
            ?: emptyList()
        selectedVin.value = cache.getString("selected", null) ?: vehicles.value.firstOrNull()?.optString("vin")
        carImages.value = vehicles.value.mapNotNull { v ->
            val vin = v.optString("vin")
            carFile(vin).takeIf { it.exists() }?.let { f -> BitmapFactory.decodeFile(f.path)?.let { vin to it } }
        }.toMap()
    }

    /** Opens a car: its data only if it was read moments ago, otherwise the screen waits for Toyota. */
    fun select(vin: String) {
        selectedVin.value = vin
        cache.edit().putString("selected", vin).apply()
        snapshot.value = raw(vin)?.takeIf { System.currentTimeMillis() - it.optLong("fetchedAt") < FRESH_MS }
            ?.let { runCatching { Snapshot.from(it) }.getOrNull() }
        climateTemp.value = null
        result.value = null
        error.value = null
    }

    fun saveTokens(t: Tokens?) {
        vault.write("tokens", t?.toJson())
        tokens = t
        linked.value = t != null
    }

    /** Keeps the password under the strict key, or forgets it when [password] is null. False when this watch's Keystore refused it. */
    fun saveCredentials(email: String, password: String?): Boolean {
        secrets.write(CREDENTIALS, null)
        if (password == null) return true
        return runCatching { secrets.write(CREDENTIALS, JSONObject().put("email", email).put("password", password).toString()) }.isSuccess
    }

    fun unlink() {
        saveTokens(null)
        secrets.write(CREDENTIALS, null)
        cache.edit().clear().apply()
        vehicles.value.forEach { carFile(it.optString("vin")).delete() }
        vehicles.value = emptyList()
        selectedVin.value = null
        snapshot.value = null
        carImages.value = emptyMap()
        climateTemp.value = null
        result.value = null
        error.value = null
    }

    /** Standalone login on the watch. The password is kept only with [savePassword], to sign in again when the session dies. */
    suspend fun login(email: String, password: String, brand: String, savePassword: Boolean) = work(R.string.busy_login) {
        saveTokens(ToyotaAuth.login(email, password, brand))
        if (!saveCredentials(email, password.takeIf { savePassword })) error.value = app.getString(R.string.err_save_password)
    }

    /** Vehicle list from the cache, or from Toyota once. The garage lists the cars only when their pictures are ready (or failed). */
    suspend fun loadVehicles() = work(R.string.busy_vehicles) {
        if (vehicles.value.isNotEmpty()) return@work
        val all = api.vehicles()
        val list = List(all.length()) { all.getJSONObject(it) }
        if (list.isEmpty()) throw ToyotaError(404, null, "no vehicles")
        list.forEach { v ->
            val vin = v.getString("vin")
            if (carImages.value[vin] == null) runCatching { fetchCarImage(vin, v.optString("image")) }
        }
        cache.edit().putString("vehicles", all.toString()).apply()
        vehicles.value = list
        if (list.none { it.optString("vin") == selectedVin.value }) select(list.first().getString("vin"))
    }

    /** Reads the selected vehicle; with [wake] it first asks the car for fresh status and polls until its timestamp advances. */
    suspend fun refresh(wake: Boolean) = work(if (wake) R.string.busy_wake else R.string.busy_refresh) {
        // An error, not a silent return: the vehicle screen retries automatically only while there is no error.
        val (vin, vehicle) = current() ?: throw ToyotaError(404, null, "selected vehicle not in the list")
        readAll(vin, vehicle, wake)
    }

    /** door-lock / door-unlock, then verification by reading the car's real state. */
    suspend fun lock(lock: Boolean) = work(R.string.busy_sending) {
        val (vin, vehicle) = current() ?: return@work
        api.command(vin, if (lock) "door-lock" else "door-unlock")
        busy.value = app.getString(R.string.busy_verifying)
        readAll(vin, vehicle, wake = true)
        val ok = snapshot.value?.locked == lock
        result.value = CommandResult(
            System.currentTimeMillis(),
            app.getString(
                when {
                    ok && lock -> R.string.result_locked
                    ok -> R.string.result_unlocked
                    else -> R.string.result_unconfirmed
                },
            ),
            ok,
        )
    }

    /** Starts or stops the climate at [climateTemp] for CLIMATE_MINUTES, then reads the climate status once. */
    suspend fun climate(start: Boolean) = work(R.string.busy_sending) {
        val (vin, _) = current() ?: return@work
        val accepted = api.climate(vin, start, climateTemp.value ?: 21.0, CLIMATE_MINUTES, climateSaved[vin])
        if (!accepted) {
            result.value = CommandResult(System.currentTimeMillis(), app.getString(R.string.result_rejected), false)
            return@work
        }
        busy.value = app.getString(R.string.busy_verifying)
        delay(8_000)
        optional { api.climateStatus(vin) }?.let { cl -> update(vin) { it.put("climate", cl) } }
        val status = snapshot.value?.climate
        val ok = if (start) status == "starting" || status == "running" else status == null || status == "stopped" || status == "stopping"
        result.value = CommandResult(
            System.currentTimeMillis(),
            app.getString(
                when {
                    ok && start -> R.string.result_climate_on
                    ok -> R.string.result_climate_off
                    else -> R.string.result_unconfirmed
                },
            ),
            ok,
        )
    }

    /** Toyota's saved climate settings give the initial dial temperature (default 21 °C). */
    suspend fun loadClimateSettings() = withContext(Dispatchers.IO) {
        if (climateTemp.value != null) return@withContext
        val vin = selectedVin.value ?: return@withContext
        val saved = optional { api.climateSettings(vin) ?: JSONObject() }
        climateSaved[vin] = saved
        climateTemp.value = saved?.optJSONObject("temperature")?.optDouble("value")?.takeIf { !it.isNaN() } ?: 21.0
    }

    fun setClimateTemp(value: Double) {
        climateTemp.value = (Math.round(value / TEMP_STEP) * TEMP_STEP).coerceIn(TEMP_MIN, TEMP_MAX)
    }

    /** Debug builds only: a fake car so every screen renders on an emulator without a Toyota account. */
    fun seedDemo() {
        val now = java.time.Instant.now().toString()
        val vehicle = JSONObject().put("vin", "DEMO").put("modelName", "Corolla Touring Sports - MY24").put("fuelType", "B").put("remoteDisplay", "7")
        val locked = JSONObject().put("lockStatus", JSONObject().put("status", "locked")).put("openStatus", JSONObject().put("status", "close"))
        val raw = JSONObject()
            .put("vehicle", vehicle)
            .put("status", JSONObject().put("lastUpdateTimestamp", now).put("doors", JSONObject().put("driver", locked).put("passenger", locked)))
            .put("telemetry", JSONObject().put("odometer", JSONObject().put("value", 45334).put("unit", "km")).put("fuelLevel", 43).put("distanceToEmpty", JSONObject().put("value", 251).put("unit", "km")).put("timestamp", now))
            .put("location", JSONObject().put("lastTimestamp", now).put("vehicleLocation", JSONObject().put("latitude", 40.4168).put("longitude", -3.7038)))
            .put("climate", JSONObject().put("status", "stopped"))
            .put("fetchedAt", System.currentTimeMillis())
        cache.edit().putString("vehicles", JSONArray().put(vehicle).toString()).apply()
        vehicles.value = listOf(vehicle)
        store("DEMO", raw)
        select("DEMO")
        climateTemp.value = 21.0
        tokens = Tokens("demo", "demo", Long.MAX_VALUE, "demo", "T") // in memory only; real calls fail with 401
        linked.value = true
    }

    private suspend fun work(label: Int, block: suspend () -> Unit) = withContext(Dispatchers.IO) {
        if (busy.value != null) return@withContext
        busy.value = app.getString(label)
        error.value = null
        try {
            block()
        } catch (e: ToyotaLoginError) {
            if (!linked.value) {
                error.value = app.getString(R.string.err_login)
            } else {
                error.value = humanize(e)
                saveTokens(null)
            }
        } catch (e: Exception) {
            Log.w("ToyotaWear", "${app.getString(label)} failed: ${e.javaClass.simpleName}: ${e.message}")
            error.value = humanize(e)
        } finally {
            busy.value = null
        }
    }

    private suspend fun readAll(vin: String, vehicle: JSONObject, wake: Boolean) {
        val out = JSONObject().put("vehicle", vehicle)
        if (wake) {
            val accepted = api.wake(vin)
            val before = Snapshot.timestamp(raw(vin)?.optJSONObject("status")) ?: 0L
            var advanced = false
            for (ms in wakePollMs) {
                delay(ms)
                val st = api.status(vin)
                out.put("status", st)
                if ((Snapshot.timestamp(st) ?: 0L) > before) {
                    advanced = true
                    break
                }
            }
            if (!accepted) error.value = app.getString(R.string.err_wake_rejected)
            else if (!advanced) error.value = app.getString(R.string.err_no_response)
            busy.value = app.getString(R.string.busy_refresh)
        }
        if (!out.has("status")) out.put("status", api.status(vin))
        out.put("telemetry", optional { api.telemetry(vin) })
        if (Snapshot.isElectric(vehicle)) out.put("electric", optional { api.electric(vin) })
        out.put("fetchedAt", System.currentTimeMillis())
        store(vin, out) // the status face can show lock, energy and mileage now; position and climate follow
        out.put("location", optional { api.location(vin) })
        out.put("climate", optional { api.climateStatus(vin) })
        store(vin, out)
    }

    private fun store(vin: String, out: JSONObject) {
        cache.edit().putString("raw:$vin", out.toString()).apply()
        if (vin == selectedVin.value) snapshot.value = Snapshot.from(out)
    }

    private fun raw(vin: String): JSONObject? = cache.getString("raw:$vin", null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private inline fun update(vin: String, mutate: (JSONObject) -> Unit) {
        val r = raw(vin) ?: return
        mutate(r)
        store(vin, r)
    }

    private fun current(): Pair<String, JSONObject>? {
        val vin = selectedVin.value ?: return null
        val vehicle = vehicles.value.firstOrNull { it.optString("vin") == vin } ?: return null
        return vin to vehicle
    }

    /** Toyota "selectively 500s" some endpoints: one failed optional read must not sink the whole refresh. */
    private fun <T> optional(block: () -> T): T? = runCatching(block).getOrNull()

    private fun carFile(vin: String) = File(app.filesDir, "car-$vin.png")

    /** Downloads Toyota's picture of the car once, shrinks it to 320 px and keeps it as a small PNG. */
    private fun fetchCarImage(vin: String, url: String) {
        if (url.isBlank()) return
        val bytes = URL(url).openStream().use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply { inSampleSize = maxOf(1, bounds.outWidth / CAR_IMAGE_WIDTH) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return
        val bmp = if (decoded.width > CAR_IMAGE_WIDTH) {
            Bitmap.createScaledBitmap(decoded, CAR_IMAGE_WIDTH, decoded.height * CAR_IMAGE_WIDTH / decoded.width, true)
        } else {
            decoded
        }
        carFile(vin).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        carImages.value = carImages.value + (vin to bmp)
    }

    @Synchronized
    private fun freshTokens(): Tokens {
        val t = tokens
            ?: runCatching { vault.read("tokens")?.let(Tokens::fromJson) }.getOrNull()?.also { tokens = it }
            ?: throw ToyotaLoginError("no tokens")
        if (t.isFresh()) return t
        val next = try {
            ToyotaAuth.refresh(t)
        } catch (e: ToyotaLoginError) {
            relogin(t.brand) ?: throw e
        }
        return next.also { saveTokens(it) }
    }

    /** The refresh token was rejected: sign in again with the saved password, if there is one. A rejected password is forgotten. */
    private fun relogin(brand: String): Tokens? {
        val saved = secrets.read(CREDENTIALS)?.let(::JSONObject) ?: return null
        return try {
            ToyotaAuth.login(saved.getString("email"), saved.getString("password"), brand)
        } catch (e: ToyotaLoginError) {
            secrets.write(CREDENTIALS, null)
            throw e
        }
    }

    /** At most once a day, counted from the last answer GitHub gave: one small request, silent when offline. */
    suspend fun checkUpdate() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - cache.getLong("updateCheckedAt", 0) < UPDATE_CHECK_MS) return@withContext
        val current = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: return@withContext
        runCatching { Releases.newer(current, WATCH_APK) }.onSuccess {
            cache.edit().putLong("updateCheckedAt", now).apply()
            update.value = it
        }
    }

    /**
     * Streams the APK straight into a PackageInstaller session. Android only accepts it if it is this app, signed
     * with the same key and not older; then it asks the user to confirm and restarts the app ([InstallReceiver]).
     */
    suspend fun installUpdate(apk: Releases.Apk) = withContext(Dispatchers.IO) {
        if (updating.value) return@withContext
        updating.value = true
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(app.packageName) }
        try {
            installer.openSession(installer.createSession(params)).use { session ->
                try {
                    val download = URL(apk.url).openConnection().apply {
                        connectTimeout = 20_000
                        readTimeout = 20_000
                    }
                    download.getInputStream().use { input ->
                        session.openWrite("update.apk", 0, -1).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    val status = PendingIntent.getBroadcast(app, 0, Intent(app, InstallReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                    session.commit(status.intentSender)
                } catch (e: Exception) {
                    session.abandon()
                    throw e
                }
            }
        } catch (e: Exception) {
            Log.w("ToyotaWear", "update failed: ${e.javaClass.simpleName}: ${e.message}")
            error.value = app.getString(R.string.err_update, humanize(e))
        } finally {
            updating.value = false
        }
    }

    fun humanize(e: Throwable): String = when (e) {
        is ToyotaLoginError -> app.getString(R.string.err_session)
        is ToyotaError -> when {
            e.httpCode == 404 -> app.getString(R.string.err_no_vehicles)
            e.httpCode == 429 -> app.getString(R.string.err_rate)
            e.httpCode >= 500 -> app.getString(R.string.err_service)
            e.httpCode == 401 || e.httpCode == 403 -> app.getString(R.string.err_access, e.apiCode ?: e.httpCode.toString())
            e.apiCode == "CTP-REMOTE-40006" -> app.getString(R.string.err_unsupported)
            else -> app.getString(R.string.err_generic, e.apiCode ?: e.httpCode.toString())
        }
        is IOException -> app.getString(R.string.err_offline)
        else -> app.getString(R.string.err_unknown, e.javaClass.simpleName)
    }
}

/** PackageInstaller reports here (not exported, so nobody else can feed it intents): system confirmation, or why it failed. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> Unit // installed (the app restarts) or the user said no
            else -> Store.error.value = context.getString(R.string.err_update, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "")
        }
    }
}
