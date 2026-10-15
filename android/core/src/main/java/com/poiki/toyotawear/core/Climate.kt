package com.poiki.toyotawear.core

import org.json.JSONObject
import java.time.Instant
import java.io.IOException
import kotlin.math.ceil

/** V2 accepts seat modes, not the low/medium/high levels returned by the read API. */
object Climate {
    fun timestamp(status: JSONObject?): Long? = listOf("updatedAt", "startedAt").mapNotNull { key ->
        status?.optString(key)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    }.maxOrNull()

    fun currentTemperature(status: JSONObject?): Double? {
        val temperature = status?.optJSONObject("currentTemperature") ?: return null
        val value = temperature.optDouble("value").takeIf { it.isFinite() } ?: return null
        return when (temperature.optString("unit")) {
            "C" -> value
            "F" -> (value - 32) * 5 / 9
            else -> null
        }
    }

    fun endsAt(status: JSONObject?): Long? {
        if (status?.optString("status") !in setOf("running", "starting")) return null
        val startedAt = runCatching { Instant.parse(status?.optString("startedAt")).toEpochMilli() }.getOrNull() ?: return null
        val duration = status?.optDouble("duration")?.takeIf { it.isFinite() && it > 0 && it <= 1440 } ?: return null
        return runCatching { Math.addExact(startedAt, (duration * 60_000).toLong()) }.getOrNull()
    }

    fun remainingMinutes(endsAt: Long?, now: Long): Int? = endsAt?.let { ceil(((it.toDouble() - now) / 60_000).coerceAtLeast(0.0)).toInt() }

    fun isFresh(status: JSONObject?, baseline: JSONObject?): Boolean {
        if (status == null || baseline == null) return false
        val before = timestamp(baseline)
        val after = timestamp(status)
        if (before != null && after != null) return after > before
        // Toyota can omit timestamps when stopped; a changed, recognized state is still an observed transition.
        val states = setOf("running", "starting", "stopped", "stopping")
        val previous = baseline.optString("status")
        val current = status.optString("status")
        return previous in states && current in states && previous != current
    }

    /** Poll reads only: the physical command must be sent exactly once by the caller. */
    suspend fun awaitStatus(
        start: Boolean, desired: JSONObject?, baseline: JSONObject?,
        read: (Int) -> JSONObject, pause: suspend (Long) -> Unit,
        onStatus: (JSONObject) -> Unit = {}, clockMs: () -> Long = { System.nanoTime() / 1_000_000 }, refresh: () -> Unit = {},
    ): JSONObject? {
        val deadline = clockMs() + 60_000
        var latest: JSONObject? = null
        var newestAt = timestamp(baseline)
        try {
            refresh()
        } catch (_: IOException) {
            // The GET remains authoritative if the advisory refresh cannot reach the service.
        } catch (e: ToyotaError) {
            if (e.httpCode !in setOf(401, 403, 404, 429) && e.httpCode !in 500..599) throw e
        }
        // shortcut: a one-minute verification window; calibrate the schedule with measured vehicle latency.
        for (wait in longArrayOf(0, 3_000, 5_000, 8_000, 10_000, 10_000, 10_000, 10_000, 10_000)) {
            val remaining = deadline - clockMs()
            if (remaining <= wait) break
            pause(wait)
            val timeout = ((deadline - clockMs()) / 2).coerceIn(1, 10_000).toInt()
            val status = try {
                read(timeout)
            } catch (_: IOException) {
                continue
            } catch (e: ToyotaError) {
                if (e.httpCode == 429 || e.httpCode in 500..599) continue else throw e
            }
            val nextAt = timestamp(status)
            if (nextAt != null && newestAt != null && nextAt < newestAt) continue
            if (nextAt != null) newestAt = nextAt
            latest = status
            onStatus(status)
            if (confirmed(start, desired, latest, baseline)) return latest
        }
        return latest
    }
    enum class Option(val group: String, val key: String, val capability: String, val ventilation: String? = null) {
        Front("heatingOptions", "frontDefroster", "frontDefogger"),
        Rear("heatingOptions", "rearDefogger", "rearDefogger"),
        Wheel("heatingOptions", "steeringHeater", "steeringHeater"),
        Driver("seatOptions", "driverSeat", "frontDriverSeatHeater", "frontDriverSeatVentilation"),
        Passenger("seatOptions", "passengerSeat", "frontPassengerSeatHeater", "frontPassengerSeatVentilation"),
        RearDriver("seatOptions", "rearDriverSeat", "rearDriverSeatHeater", "rearDriverSeatVentilation"),
        RearPassenger("seatOptions", "rearPassengerSeat", "rearPassengerSeatHeater", "rearPassengerSeatVentilation"),
    }

    data class Choice(val option: Option, val modes: List<String>, val value: String?)

    private fun mode(option: Option, value: Any?): String? = if (option.group == "seatOptions") {
        when (value) {
            "off", "heater", "ventilation" -> value as String
            "low", "medium", "high" -> "heater"
            else -> null
        }
    } else when (value) {
        "on", true -> "on"
        "off", false -> "off"
        else -> null
    }

    fun value(settings: JSONObject?, option: Option): String? = mode(option, settings?.optJSONObject(option.group)?.opt(option.key))

    /** A reported field is evidence of support; an explicit capability=false takes precedence. */
    fun choices(vehicle: JSONObject, settings: JSONObject): List<Choice> {
        val caps = vehicle.optJSONObject("extendedCapabilities")
        return Option.entries.mapNotNull { option ->
            val current = value(settings, option)
            val heat = caps?.opt(option.capability)
            val vent = option.ventilation?.let { caps?.opt(it) }
            val modes = mutableListOf("off")
            if (heat == true || (heat != false && current != null && current != "ventilation")) {
                modes += if (option.ventilation == null) "on" else "heater"
            }
            if (option.ventilation != null && (vent == true || (vent != false && current == "ventilation"))) modes += "ventilation"
            if (modes.size > 1) Choice(option, modes, current?.takeIf { it in modes }) else null
        }
    }

    /** Copies only recognized fields; callers can stage choices without mutating the saved Toyota defaults. */
    fun settings(saved: JSONObject?, choices: List<Choice> = emptyList()): JSONObject {
        val out = JSONObject()
        for (option in Option.entries) {
            val desired = choices.firstOrNull { it.option == option }?.value ?: value(saved, option)
            val normalized = mode(option, desired) ?: continue
            val group = out.optJSONObject(option.group) ?: JSONObject().also { out.put(option.group, it) }
            group.put(option.key, normalized)
        }
        return out
    }

    fun request(start: Boolean, tempC: Double, durationMin: Int, saved: JSONObject?): JSONObject {
        val out = JSONObject().put("command", if (start) "start" else "stop")
        if (!start) return out
        require(tempC.isFinite() && tempC in 18.0..29.0 && durationMin in 1..20)
        out.put("temperature", JSONObject().put("unit", "C").put("value", tempC))
            .put("duration", durationMin).put("saveSettings", false)
        val normalized = settings(saved)
        for (group in listOf("heatingOptions", "seatOptions")) normalized.optJSONObject(group)?.let { out.put(group, it) }
        return out
    }

    /** Missing or failed reads never confirm execution, including stop. */
    fun confirmed(start: Boolean, desired: JSONObject?, status: JSONObject?, baseline: JSONObject?): Boolean {
        if (!isFresh(status, baseline)) return false
        val state = status?.optString("status")
        if (!start) return state == "stopped"
        if (state != "running") return false
        return Option.entries.all { option ->
            val expected = value(desired, option)
            expected == null || value(status, option) == expected
        }
    }
}
