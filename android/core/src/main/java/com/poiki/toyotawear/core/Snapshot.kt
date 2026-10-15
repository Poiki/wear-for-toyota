package com.poiki.toyotawear.core

import org.json.JSONObject
import java.time.Instant

/**
 * What the watch shows, derived from the raw Toyota payloads cached as one JSON object with keys
 * vehicle, status, telemetry, electric, location, climate and fetchedAt. Every field keeps its own
 * Toyota timestamp so the UI can tell "last known" from "current".
 */
data class Snapshot(
    val vin: String,
    val alias: String,
    val model: String,
    val remoteActive: Boolean,
    val electric: Boolean,
    val locked: Boolean?,
    val openDoors: Int?,
    val openWindows: Int?,
    val hoodOpen: Boolean?,
    val statusAt: Long?,
    val odometerKm: Double?,
    val fuelPct: Int?,
    val fuelRangeKm: Double?,
    val telemetryAt: Long?,
    val batteryPct: Int?,
    val evRangeKm: Double?,
    val charging: String?,
    val electricAt: Long?,
    val lat: Double?,
    val lon: Double?,
    val locationAt: Long?,
    val climate: String?,
    val climateAt: Long?,
    val fetchedAt: Long,
    /** Oldest reported lock component; unknown if any reported lock has no valid timestamp. */
    val lockAt: Long? = null,
    val climateCurrentC: Double? = null,
    val climateEndsAt: Long? = null,
) {
    fun confirmsLock(vin: String, lock: Boolean, sentAt: Long): Boolean =
        this.vin == vin && locked == lock && statusAt?.let { it > sentAt } == true && lockAt?.let { it > sentAt } == true

    companion object {
        private val DOORS = listOf("driver", "passenger", "rearLeft", "rearRight", "rearBack")
        private val WINDOWS = listOf("driver", "passenger", "rearLeft", "rearRight")

        fun isElectric(vehicle: JSONObject): Boolean =
            vehicle.optBoolean("evVehicle") || vehicle.optString("fuelType") in setOf("E", "I")

        /** The user's nickname unless Toyota filled it with the VIN; otherwise the model name. */
        fun displayName(vehicle: JSONObject): String {
            val nick = vehicle.optString("nickName")
            if (nick.isNotBlank() && nick != vehicle.optString("vin")) return nick
            return vehicle.optString("modelName").ifEmpty { vehicle.optString("carlineName").ifEmpty { "Toyota" } }
        }

        fun timestamp(status: JSONObject?): Long? = status?.optString("lastUpdateTimestamp")?.let(::epoch)

        fun from(raw: JSONObject): Snapshot {
            val v = raw.getJSONObject("vehicle")
            val st = raw.optJSONObject("status")
            val te = raw.optJSONObject("telemetry")
            val el = raw.optJSONObject("electric")
            val lo = raw.optJSONObject("location")?.optJSONObject("vehicleLocation")
            val cl = raw.optJSONObject("climate")
            val doors = st?.optJSONObject("doors")
            val windows = st?.optJSONObject("windows")
            val reportedDoors = DOORS.filter { doors?.has(it) == true }.map { doors?.optJSONObject(it) }
            // An omitted component can be unsupported; an explicit unknown reading cannot confirm safety.
            val lockReadings = reportedDoors.filter { it == null || it.has("lockStatus") }.map { it?.optJSONObject("lockStatus") }
            val lockStates = lockReadings.map { it?.optString("status") }
            val lockTimes = lockReadings.map(::timestamp)
            return Snapshot(
                vin = v.getString("vin"),
                alias = displayName(v),
                model = v.optString("modelName"),
                // Some EU responses omit remoteDisplay; use the explicit subscription status then.
                remoteActive = v.optString("remoteDisplay").let { display ->
                    if (display.isNotBlank()) display == "7" else v.optString("remoteSubscriptionStatus") == "ACTIVE"
                },
                electric = isElectric(v),
                locked = when {
                    lockStates.isEmpty() -> null
                    lockStates.any { it == "unlocked" } -> false
                    lockStates.all { it == "locked" } -> true
                    else -> null
                },
                openDoors = openCount(reportedDoors.map { it?.optJSONObject("openStatus")?.optString("status") }),
                openWindows = openCount(WINDOWS.filter { windows?.has(it) == true }.map { windows?.optJSONObject(it)?.optString("status") }),
                hoodOpen = when (doors?.optJSONObject("hood")?.optJSONObject("openStatus")?.optString("status")) {
                    "open" -> true
                    "close" -> false
                    else -> null
                },
                statusAt = timestamp(st),
                odometerKm = te?.optJSONObject("odometer")?.let(::km),
                fuelPct = percent(te, "fuelLevel") ?: percent(el, "fuelLevel"),
                fuelRangeKm = te?.optJSONObject("distanceToEmpty")?.let(::km) ?: el?.optJSONObject("fuelRange")?.let(::km),
                telemetryAt = te?.optString("timestamp")?.let(::epoch),
                batteryPct = percent(el, "batteryLevel") ?: percent(te, "batteryLevel"),
                evRangeKm = el?.optJSONObject("evRange")?.let(::km),
                charging = el?.optString("chargingStatus")?.ifEmpty { null },
                electricAt = el?.optString("lastUpdateTimestamp")?.let(::epoch),
                lat = lo?.optDouble("latitude")?.takeIf { it.isFinite() && it in -90.0..90.0 },
                lon = lo?.optDouble("longitude")?.takeIf { it.isFinite() && it in -180.0..180.0 },
                locationAt = (lo?.optString("locationAcquisitionDatetime")?.ifEmpty { null }
                    ?: raw.optJSONObject("location")?.optString("lastTimestamp"))?.let(::epoch),
                climate = cl?.optString("status")?.ifEmpty { null },
                climateAt = cl?.optString("updatedAt")?.let(::epoch),
                fetchedAt = raw.optLong("fetchedAt"),
                lockAt = if (lockTimes.any { it == null }) null else lockTimes.filterNotNull().minOrNull(),
                climateCurrentC = Climate.currentTemperature(cl),
                climateEndsAt = Climate.endsAt(cl),
            )
        }

        private fun openCount(states: List<String?>): Int? {
            val count = states.count { it == "open" }
            // Preserve known openings as a minimum count even when another reported reading is unknown.
            return count.takeIf { it > 0 || (states.isNotEmpty() && states.all { state -> state == "close" }) }
        }

        private fun percent(o: JSONObject?, key: String): Int? =
            o?.optDouble(key)?.takeIf { it.isFinite() && it in 0.0..100.0 }?.toInt()

        private fun km(o: JSONObject): Double? {
            val value = o.optDouble("value")
            if (!value.isFinite() || value < 0) return null
            return when (o.optString("unit")) {
                "km" -> value
                "mi" -> (value * 1.60934).takeIf { it.isFinite() }
                else -> null
            }
        }

        private fun epoch(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
    }
}
