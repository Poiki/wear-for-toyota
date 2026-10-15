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
import com.poiki.toyotawear.core.Climate
import com.poiki.toyotawear.core.Snapshot
import com.poiki.toyotawear.core.Tokens
import com.poiki.toyotawear.core.ToyotaApi
import com.poiki.toyotawear.core.ToyotaAuth
import com.poiki.toyotawear.core.ToyotaError
import com.poiki.toyotawear.core.ToyotaLoginError
import com.poiki.toyotawear.core.TripHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    enum class UpdatePhase { Downloading, Preparing, Confirm, Installing, Failed }
    data class Installation(
        val apk: Releases.Apk?, val phase: UpdatePhase = UpdatePhase.Downloading,
        val progress: Releases.DownloadProgress? = null, val confirmation: Intent? = null, val error: String? = null,
    ) { val active: Boolean get() = phase != UpdatePhase.Failed }

    /** Tokens are on the watch. They are only decrypted for the first request: the Keystore costs ~60 ms at startup. */
    val linked = MutableStateFlow(false)
    val session = MutableStateFlow(0L)
    val vehicles = MutableStateFlow<List<JSONObject>>(emptyList())
    val selectedVin = MutableStateFlow<String?>(null)
    val snapshot = MutableStateFlow<Snapshot?>(null)
    val carImages = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val climateTemp = MutableStateFlow<Double?>(null)
    val climateChoices = MutableStateFlow<List<Climate.Choice>>(emptyList())
    val climateLoading = MutableStateFlow(false)
    val climateError = MutableStateFlow<String?>(null)
    val tripHistory = MutableStateFlow<TripHistory?>(null)
    val tripsLoading = MutableStateFlow(false)
    val tripsError = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    /** Outcome of the last remote command; the UI shows it and vibrates once. */
    val result = MutableStateFlow<CommandResult?>(null)
    /** A newer watch APK on GitHub, offered once per check. */
    val update = MutableStateFlow<Releases.Apk?>(null)
    val installation = MutableStateFlow<Installation?>(null)
    val updating: Boolean get() = installation.value?.active == true
    val updateChecking = MutableStateFlow(false)
    val updateStatus = MutableStateFlow<String?>(null)

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
    private val tokenRefreshLock = Any()
    private val installLock = Any()
    private var installSessionId: Int? = null
    private lateinit var app: Context
    private lateinit var vault: Vault
    /** The saved password, only if the user asked for it: its own strict Keystore key. */
    private lateinit var secrets: Vault
    private lateinit var cache: SharedPreferences
    @Volatile private var api = newApi(0L)
    private var climateLoadedVin: String? = null

    // shortcut: fixed ~30 s wake schedule; tune once real cars have been measured.
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
    @Synchronized
    fun select(vin: String) {
        if (vehicles.value.none { it.optString("vin") == vin }) return
        selectedVin.value = vin
        cache.edit().putString("selected", vin).apply()
        snapshot.value = raw(vin)?.takeIf { System.currentTimeMillis() - it.optLong("fetchedAt") < FRESH_MS }
            ?.apply { current()?.second?.let { put("vehicle", it) } }
            ?.let { runCatching { Snapshot.from(it) }.getOrNull() }
        climateTemp.value = null
        climateChoices.value = emptyList()
        climateLoading.value = false
        climateError.value = null
        climateLoadedVin = null
        tripHistory.value = null
        tripsError.value = null
        result.value = null
        error.value = null
    }

    private fun newApi(revision: Long) = ToyotaApi({ freshTokens(revision) }, { rejected -> freshTokens(revision, rejected.accessToken) })

    /** A new link invalidates old requests before exposing the new account. Token rotation uses freshTokens instead. */
    @Synchronized
    fun saveTokens(t: Tokens?, email: String = "", password: String? = null): Boolean {
        api.invalidate()
        tokens = null
        linked.value = false
        try {
            vault.write("tokens", null)
            secrets.write(CREDENTIALS, null)
            clearVehicleData()
            vault.write("tokens", t?.toJson())
            tokens = t
            return saveCredentials(email, password)
        } finally {
            session.value += 1
            api = newApi(session.value)
            linked.value = tokens != null
        }
    }

    /** Keeps the password under the strict key, or forgets it when [password] is null. False when this watch's Keystore refused it. */
    @Synchronized
    private fun saveCredentials(email: String, password: String?): Boolean {
        secrets.write(CREDENTIALS, null)
        if (password == null) return true
        return runCatching { secrets.write(CREDENTIALS, JSONObject().put("email", email).put("password", password).toString()) }.isSuccess
    }

    fun unlink() { saveTokens(null) }

    private fun clearVehicleData() {
        if (!cache.edit().clear().commit()) throw IOException("Could not clear previous Toyota account data")
        val files = app.filesDir.listFiles() ?: throw IOException("Could not read Toyota image cache")
        files.filter { it.isFile && it.name.startsWith("car-") && it.name.endsWith(".png") }.forEach {
            if (!it.delete()) throw IOException("Could not clear previous Toyota image")
        }
        vehicles.value = emptyList()
        selectedVin.value = null
        snapshot.value = null
        carImages.value = emptyMap()
        climateTemp.value = null
        climateChoices.value = emptyList()
        climateLoading.value = false
        climateError.value = null
        climateLoadedVin = null
        tripHistory.value = null
        tripsError.value = null
        tripsLoading.value = false
        busy.value = null
        result.value = null
        error.value = null
    }

    /** Standalone login on the watch. The password is kept only with [savePassword], to sign in again when the session dies. */
    suspend fun login(email: String, password: String, brand: String, savePassword: Boolean) = work(R.string.busy_login) { api ->
        val next = ToyotaAuth.login(email, password, brand)
        commit(api) {
            if (!saveTokens(next, email, password.takeIf { savePassword })) error.value = app.getString(R.string.err_save_password)
        }
    }

    /** Refreshes Toyota's vehicle metadata on launch; cached pictures need no second download. */
    suspend fun loadVehicles() = work(R.string.busy_vehicles) { api ->
        // Emulator previews have no Toyota session.
        if (tokens?.accessToken == "demo") return@work
        val all = api.vehicles()
        val list = List(all.length()) { all.getJSONObject(it) }
        if (list.isEmpty()) throw ToyotaError(404, null, "no vehicles")
        list.forEach { v ->
            val vin = v.getString("vin")
            Log.i("ToyotaWear", "Remote services: display=${v.optString("remoteDisplay")}, subscription=${v.optString("remoteSubscriptionStatus")}")
            if (carImages.value[vin] == null) optional { fetchCarImage(api, vin, v.optString("image")) }
        }
        commit(api) {
            cache.edit().putString("vehicles", all.toString()).apply()
            vehicles.value = list
            select(list.firstOrNull { it.optString("vin") == selectedVin.value }?.getString("vin") ?: list.first().getString("vin"))
        }
    }

    /** Reads the selected vehicle; with [wake] it first asks the car for fresh status and polls until its timestamp advances. */
    suspend fun refresh(wake: Boolean) = work(if (wake) R.string.busy_wake else R.string.busy_refresh) { api ->
        // An error, not a silent return: the vehicle screen retries automatically only while there is no error.
        val (vin, vehicle) = current() ?: throw ToyotaError(404, null, "selected vehicle not in the list")
        readAll(api, vin, vehicle, wake)
    }

    /** door-lock / door-unlock, then verification by reading the car's real state. */
    suspend fun lock(lock: Boolean) = work(R.string.busy_sending) { api ->
        val (vin, vehicle) = current() ?: return@work
        api.command(vin, if (lock) "door-lock" else "door-unlock")
        // Start after the API returns: this command may have waited behind another request.
        val confirmedAfter = System.currentTimeMillis()
        commit(api) { busy.value = app.getString(R.string.busy_verifying) }
        val refreshed = readAll(api, vin, vehicle, wake = true)
        val ok = refreshed && raw(vin)?.let(Snapshot::from)?.confirmsLock(vin, lock, confirmedAfter) == true
        commit(api) { result.value = CommandResult(
            System.currentTimeMillis(),
            app.getString(
                when {
                    ok && lock -> R.string.result_locked
                    ok -> R.string.result_unlocked
                    else -> R.string.result_unconfirmed
                },
            ),
            ok,
        ) }
    }

    /** Sends once, requests fresh climate data and polls the reported vehicle state. */
    suspend fun climate(start: Boolean) = work(R.string.busy_sending) { api ->
        val (vin, _) = current() ?: return@work
        if (climateLoading.value || tokens?.accessToken == "demo") return@work
        if (start && (climateLoadedVin != vin || climateError.value != null)) {
            commit(api) { error.value = climateError.value ?: app.getString(R.string.result_climate_unavailable) }
            return@work
        }
        val desired = Climate.settings(null, climateChoices.value)
        val before = optional { api.climateStatus(vin, timeoutMs = 5_000, retry = false) }
        val accepted = api.climate(vin, start, climateTemp.value ?: 21.0, CLIMATE_MINUTES, desired)
        if (!accepted) {
            commit(api) { result.value = CommandResult(System.currentTimeMillis(), app.getString(R.string.result_rejected), false) }
            return@work
        }
        commit(api) { busy.value = app.getString(R.string.busy_verifying) }
        val status = Climate.awaitStatus(start, desired, before,
            read = { timeout -> api.climateStatus(vin, timeoutMs = timeout, retry = false) },
            pause = { ms -> delay(ms); currentCoroutineContext().ensureActive(); api.checkActive() },
            onStatus = { cl -> update(api, vin) { it.put("climate", cl) } },
            refresh = { api.refreshClimateStatus(vin) },
        )
        val ok = Climate.confirmed(start, null, status, before)
        val fresh = Climate.isFresh(status, before)
        val text = app.getString(when {
            ok && start && !Climate.confirmed(true, desired, status, before) -> R.string.result_climate_options_unverified
            ok && start -> R.string.result_climate_on
            ok -> R.string.result_climate_off
            fresh && status?.optString("status") == "running" -> R.string.result_climate_still_on
            fresh && status?.optString("status") == "stopped" -> R.string.result_climate_still_off
            fresh && status?.optString("status") == "starting" -> R.string.result_climate_starting
            fresh && status?.optString("status") == "stopping" -> R.string.result_climate_stopping
            else -> R.string.result_climate_unavailable
        })
        commit(api) { result.value = CommandResult(System.currentTimeMillis(), text, ok) }
    }

    /** Toyota's saved climate settings give the initial dial temperature (default 21 °C). */
    suspend fun loadClimateSettings() = withContext(Dispatchers.IO) {
        val api = api
        val (vin, vehicle) = current() ?: return@withContext
        if (tokens?.accessToken == "demo" || climateLoadedVin == vin || climateLoading.value) return@withContext
        if (!commit(api) { climateLoading.value = true; climateError.value = null }) return@withContext
        try {
            val saved = api.climateSettings(vin) ?: throw ToyotaError(404, null, "no climate settings")
            commit(api) {
                if (selectedVin.value != vin || !linked.value) return@commit
                // Active climate status describes the current run; saved settings only describe defaults.
                val status = raw(vin)?.optJSONObject("climate")
                val current = Climate.settings(saved, Climate.Option.entries.mapNotNull { option ->
                    Climate.value(status, option)?.let { Climate.Choice(option, emptyList(), it) }
                })
                climateChoices.value = Climate.choices(vehicle, current)
                val temperature = saved.optJSONObject("temperature")
                val value = temperature?.optDouble("value")?.takeIf { it.isFinite() } ?: 21.0
                setClimateTemp(if (temperature?.optString("unit") == "F") (value - 32) * 5 / 9 else value)
                climateLoadedVin = vin
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ToyotaLoginError) {
            commit(api) { saveTokens(null); error.value = humanize(e) }
        } catch (e: Exception) {
            commit(api) { if (selectedVin.value == vin && linked.value) climateError.value = humanize(e) }
        } finally {
            commit(api) { if (selectedVin.value == vin) climateLoading.value = false }
        }
    }

    fun cycleClimateOption(option: Climate.Option) {
        if (busy.value != null || climateLoading.value) return
        climateChoices.value = climateChoices.value.map { choice ->
            if (choice.option != option) choice else choice.copy(value = choice.modes[(choice.modes.indexOf(choice.value) + 1) % choice.modes.size])
        }
    }

    fun setClimateTemp(value: Double) {
        if (!value.isFinite()) return
        climateTemp.value = (Math.round(value / TEMP_STEP) * TEMP_STEP).coerceIn(TEMP_MIN, TEMP_MAX)
    }

    /** On-demand trip read; its failure must not disable the vehicle controls. */
    suspend fun loadTrips() = withContext(Dispatchers.IO) {
        val api = api
        if (tokens?.accessToken == "demo" || tripsLoading.value) return@withContext
        val vin = selectedVin.value ?: return@withContext
        if (!commit(api) { tripsLoading.value = true; tripsError.value = null }) return@withContext
        try {
            val history = api.trips(vin)
            commit(api) { if (selectedVin.value == vin) tripHistory.value = history }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ToyotaLoginError) {
            commit(api) { saveTokens(null); error.value = humanize(e) }
        } catch (e: Exception) {
            commit(api) { if (selectedVin.value == vin) tripsError.value = humanize(e) }
        } finally {
            commit(api) { tripsLoading.value = false }
        }
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
        store(api, "DEMO", raw)
        select("DEMO")
        climateTemp.value = 21.0
        vehicle.put("extendedCapabilities", JSONObject().apply { Climate.Option.entries.forEach { put(it.capability, true); it.ventilation?.let { key -> put(key, true) } } })
        val demoSettings = JSONObject().put("heatingOptions", JSONObject().put("frontDefroster", "off").put("rearDefogger", "off").put("steeringHeater", "off"))
            .put("seatOptions", JSONObject().put("driverSeat", "off").put("passengerSeat", "off").put("rearDriverSeat", "off").put("rearPassengerSeat", "off"))
        climateChoices.value = Climate.choices(vehicle, demoSettings)
        climateLoadedVin = "DEMO"
        tokens = Tokens("demo", "demo", Long.MAX_VALUE, "demo", "T") // in memory only; real calls fail with 401
        tripHistory.value = TripHistory.from(JSONObject("""{"trips":[
            {"id":"1","summary":{"startTs":"2026-10-05T08:24:00Z","length":18400,"duration":1320,"fuelConsumption":956.8},"hdc":{"evDistance":3864},"scores":{"global":82}},
            {"id":"2","summary":{"startTs":"2026-10-04T10:24:00Z","length":36700,"duration":2520,"fuelConsumption":2165.3}},
            {"id":"3","summary":{"startTs":"2026-10-03T11:10:00Z","length":12100,"duration":900}},
            {"id":"4","summary":{"startTs":"2026-10-02T09:20:00Z","length":30000,"duration":1800,"fuelConsumption":1830}},
            {"id":"5","summary":{"startTs":"2026-10-01T14:10:00Z","length":20000,"duration":1600,"fuelConsumption":940}},
            {"id":"6","summary":{"startTs":"2026-09-30T16:30:00Z","length":25000,"duration":1800,"fuelConsumption":1375}}
        ],"_metadata":{"pagination":{"totalCount":12}}}"""))
        linked.value = true
    }

    private suspend fun work(label: Int, block: suspend (ToyotaApi) -> Unit) = withContext(Dispatchers.IO) {
        val api = api
        if (!synchronized(this@Store) { api === this@Store.api && busy.compareAndSet(null, app.getString(label)) }) return@withContext
        commit(api) { error.value = null; result.value = null }
        try {
            api.checkActive()
            block(api)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ToyotaLoginError) {
            commit(api) {
                val message = if (linked.value) humanize(e) else app.getString(R.string.err_login)
                if (linked.value) saveTokens(null)
                error.value = message
            }
        } catch (e: Exception) {
            commit(api) {
                Log.w("ToyotaWear", "${app.getString(label)} failed: ${e.javaClass.simpleName}")
                error.value = humanize(e)
            }
        } finally {
            commit(api) { busy.value = null }
        }
    }

    /** Commits and account replacement share a monitor, so a late response cannot repopulate a cleared account. */
    private inline fun commit(client: ToyotaApi, block: () -> Unit): Boolean = synchronized(this) {
        if (client !== api) false else { block(); true }
    }

    private suspend fun readAll(api: ToyotaApi, vin: String, vehicle: JSONObject, wake: Boolean): Boolean {
        val out = JSONObject().put("vehicle", vehicle)
        var refreshed = false
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
            commit(api) {
                if (!accepted) error.value = app.getString(R.string.err_wake_rejected)
                else if (!advanced) error.value = app.getString(R.string.err_no_response)
                busy.value = app.getString(R.string.busy_refresh)
            }
            refreshed = accepted && advanced
        }
        if (!out.has("status")) out.put("status", api.status(vin))
        out.put("telemetry", optional { api.telemetry(vin) })
        if (Snapshot.isElectric(vehicle)) out.put("electric", optional { api.electric(vin) })
        out.put("fetchedAt", System.currentTimeMillis())
        store(api, vin, out) // the status face can show lock, energy and mileage now; position and climate follow
        out.put("location", optional { api.location(vin) })
        out.put("climate", optional { api.climateStatus(vin) })
        store(api, vin, out)
        return refreshed
    }

    private fun store(api: ToyotaApi, vin: String, out: JSONObject) = commit(api) {
        cache.edit().putString("raw:$vin", out.toString()).apply()
        if (vin == selectedVin.value) snapshot.value = Snapshot.from(out)
    }

    private fun raw(vin: String): JSONObject? = cache.getString("raw:$vin", null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private inline fun update(api: ToyotaApi, vin: String, mutate: (JSONObject) -> Unit) = commit(api) {
        val r = raw(vin) ?: return@commit
        mutate(r)
        store(api, vin, r)
    }

    private fun current(): Pair<String, JSONObject>? {
        val vin = selectedVin.value ?: return null
        val vehicle = vehicles.value.firstOrNull { it.optString("vin") == vin } ?: return null
        return vin to vehicle
    }

    /** Toyota "selectively 500s" some endpoints: one failed optional read must not sink the whole refresh. */
    private fun <T> optional(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ToyotaLoginError) {
        throw e
    } catch (e: Exception) {
        if (e is ToyotaError && (e.httpCode == 401 || e.httpCode == 403)) throw e
        Log.w("ToyotaWear", "Optional read failed: ${e.javaClass.simpleName}")
        null
    }

    private fun carFile(vin: String) = File(app.filesDir, "car-$vin.png")

    /** Downloads Toyota's picture of the car once, shrinks it to 320 px and keeps it as a small PNG. */
    private fun fetchCarImage(api: ToyotaApi, vin: String, url: String) {
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
        commit(api) {
            carFile(vin).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            carImages.value = carImages.value + (vin to bmp)
        }
    }

    private fun checkSession(revision: Long) {
        if (revision != session.value) throw CancellationException("Toyota session changed")
    }

    private fun freshTokens(revision: Long, rejectedAccessToken: String? = null): Tokens = synchronized(tokenRefreshLock) {
        val t = synchronized(this) {
            checkSession(revision)
            tokens ?: vault.read("tokens")?.let(Tokens::fromJson)?.also { tokens = it }
                ?: throw ToyotaLoginError("no tokens")
        }
        if (rejectedAccessToken != null && t.accessToken != rejectedAccessToken) return t
        if (rejectedAccessToken == null && t.isFresh()) return t
        val next = try {
            ToyotaAuth.refresh(t)
        } catch (e: ToyotaLoginError) {
            relogin(revision, t.brand) ?: throw e
        }
        synchronized(this) {
            checkSession(revision)
            if (next.uuid != t.uuid || next.brand != t.brand) throw ToyotaLoginError("Toyota session identity changed")
            vault.write("tokens", next.toJson())
            tokens = next
        }
        next
    }

    /** The refresh token was rejected: sign in again with the saved password, if there is one. A rejected password is forgotten. */
    private fun relogin(revision: Long, brand: String): Tokens? {
        val saved = synchronized(this) {
            checkSession(revision)
            secrets.read(CREDENTIALS)?.let(::JSONObject)
        } ?: return null
        return try {
            ToyotaAuth.login(saved.getString("email"), saved.getString("password"), brand)
        } catch (e: ToyotaLoginError) {
            synchronized(this) { checkSession(revision); secrets.write(CREDENTIALS, null) }
            throw e
        }
    }

    /** Automatic checks wait a day after GitHub replies; manual checks bypass that interval and report errors. */
    suspend fun checkUpdate(force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - cache.getLong("updateCheckedAt", 0) < UPDATE_CHECK_MS) return@withContext
        if (!updateChecking.compareAndSet(false, true)) return@withContext
        if (force) updateStatus.value = null
        try {
            val current = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: return@withContext
            val newer = Releases.newer(current, WATCH_APK)
            cache.edit().putLong("updateCheckedAt", now).apply()
            update.value = newer
            updateStatus.value = if (newer == null) app.getString(R.string.about_up_to_date)
                else app.getString(R.string.update_title, newer.version)
        } catch (_: Exception) {
            if (force) updateStatus.value = app.getString(R.string.about_update_failed)
        } finally {
            updateChecking.value = false
        }
    }

    /** Abandon only this app's own self-update sessions and known private installer filenames. */
    suspend fun cleanUpdateInstallers() = withContext(Dispatchers.IO) {
        synchronized(installLock) {
            if (!updating) try {
                val interrupted = app.packageManager.packageInstaller.mySessions.any { it.appPackageName == app.packageName }
                cleanInstallers()
                if (interrupted) installation.value = Installation(null, UpdatePhase.Failed, error = app.getString(R.string.update_interrupted))
            } catch (e: Exception) {
                Log.w("ToyotaWear", "Update cleanup failed: ${e.javaClass.simpleName}")
                installation.value = Installation(null, UpdatePhase.Failed, error = app.getString(R.string.update_cleanup_failed))
            }
        }
    }

    private fun cleanInstallers() {
        val installer = app.packageManager.packageInstaller
        installer.mySessions.filter { it.appPackageName == app.packageName }.forEach { abandonInstall(it.sessionId) }
        listOfNotNull(app.filesDir, app.cacheDir, app.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), app.externalCacheDir)
            .forEach(Releases::cleanInstallers)
    }

    private fun abandonInstall(id: Int) {
        val installer = app.packageManager.packageInstaller
        if (installer.getSessionInfo(id)?.appPackageName != app.packageName) return
        try { installer.abandonSession(id) } catch (e: IllegalArgumentException) {
            if (installer.getSessionInfo(id) != null) throw e // The session may have finished between the two calls.
        }
    }

    /** Called by the foreground Activity, never by the broadcast receiver. */
    fun takeInstallConfirmation(): Intent? = synchronized(installLock) {
        val state = installation.value ?: return@synchronized null
        val confirmation = state.confirmation ?: return@synchronized null
        installation.value = state.copy(phase = UpdatePhase.Installing, confirmation = null)
        confirmation
    }

    fun cancelUpdate() = synchronized(installLock) {
        installSessionId?.let(::abandonInstall)
        installSessionId = null
        installation.value = null
    }

    fun updateFailed(apk: Releases.Apk?, message: String) = synchronized(installLock) {
        installSessionId?.let(::abandonInstall)
        installSessionId = null
        installation.value = Installation(apk, UpdatePhase.Failed, error = message)
    }

    /** Streams once to Android's install session; the system verifies package, signature and version. */
    suspend fun installUpdate(apk: Releases.Apk) = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        val installer = app.packageManager.packageInstaller
        val id = synchronized(installLock) {
            if (updating) return@withContext
            installation.value = Installation(apk, progress = Releases.DownloadProgress(0, apk.size, 0))
            try {
                cleanInstallers()
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    apk.size?.let(::setSize)
                }
                installer.createSession(params).also { installSessionId = it }
            } catch (e: Exception) {
                Log.w("ToyotaWear", "Update preparation failed: ${e.javaClass.simpleName}")
                updateFailed(apk, app.getString(R.string.update_cleanup_failed))
                return@withContext
            }
        }
        try {
            installer.openSession(id).use { session ->
                session.openWrite("update.apk", 0, apk.size ?: -1).use { out ->
                    Releases.download(apk, out) { progress ->
                        coroutine.ensureActive()
                        synchronized(installLock) {
                            if (installSessionId != id) throw CancellationException("Update cancelled")
                            installation.value = Installation(apk, progress = progress)
                        }
                        session.setStagingProgress((progress.percent ?: 0) / 100f)
                    }
                    session.fsync(out)
                }
                synchronized(installLock) {
                    if (installSessionId != id) throw CancellationException("Update cancelled")
                    installation.value = installation.value?.copy(phase = UpdatePhase.Preparing)
                    val status = PendingIntent.getBroadcast(app, id, Intent(app, InstallReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                    session.commit(status.intentSender)
                }
            }
        } catch (e: CancellationException) {
            synchronized(installLock) { if (installSessionId == id) cancelUpdate() }
            throw e
        } catch (e: Exception) {
            Log.w("ToyotaWear", "Update failed: ${e.javaClass.simpleName}")
            synchronized(installLock) { if (installSessionId == id) updateFailed(apk, app.getString(R.string.update_download_failed)) }
        }
    }

    fun installStatus(intent: Intent) = synchronized(installLock) {
        val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (id < 0 || (installSessionId != null && installSessionId != id)) return@synchronized
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (id != installSessionId && status != PackageInstaller.STATUS_PENDING_USER_ACTION) return@synchronized
        val state = installation.value
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (app.packageManager.packageInstaller.mySessions.none { it.sessionId == id && it.appPackageName == app.packageName }) return@synchronized
                @Suppress("DEPRECATION")
                val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                installSessionId = id
                if (confirmation == null) updateFailed(state?.apk, app.getString(R.string.update_install_unavailable))
                else installation.value = Installation(state?.apk, UpdatePhase.Confirm, state?.progress, confirmation)
            }
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> cancelUpdate()
            else -> updateFailed(state?.apk, app.getString(when (status) {
                PackageInstaller.STATUS_FAILURE_STORAGE -> R.string.update_space_failed
                else -> R.string.update_install_failed
            }))
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
        Store.init(context)
        Store.installStatus(intent)
    }
}
