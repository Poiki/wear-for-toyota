package com.poiki.toyotawear.core

import org.json.JSONObject
import java.time.Instant

data class Trip(
    val id: String, val startedAt: Long?, val distanceKm: Double?, val seconds: Double?,
    val fuelLitres: Double?, val evKm: Double?, val score: Int?,
) {
    val consumption: Double? get() = distanceKm?.takeIf { it > 0 }?.let { km -> fuelLitres?.let { 100 * it / km } }
    val averageSpeed: Double? get() = seconds?.takeIf { it > 0 }?.let { s -> distanceKm?.let { 3600 * it / s } }
    val evShare: Double? get() = distanceKm?.takeIf { it > 0 }?.let { km -> evKm?.takeIf { it <= km }?.let { 100 * it / km } }
}

data class TripHistory(val trips: List<Trip>, val total: Int?) {
    val measured = trips.filter { it.consumption != null }
    // Missing consumption keeps its place as a gap; trips without a date cannot be positioned.
    val chartTrips: List<Trip> get() = trips.filter { it.startedAt != null }.sortedBy { it.startedAt }.takeLast(12)
    val averageConsumption: Double? get() = measured.takeIf { it.isNotEmpty() }?.let { data ->
        100 * data.sumOf { it.fuelLitres!! } / data.sumOf { it.distanceKm!! }
    }
    val distanceKm: Double? get() = trips.mapNotNull { it.distanceKm }.takeIf { it.isNotEmpty() }?.sum()

    companion object {
        fun from(payload: JSONObject): TripHistory {
            val rows = payload.optJSONArray("trips")
            val trips = (0 until (rows?.length() ?: 0)).mapNotNull { index ->
                val row = rows?.optJSONObject(index) ?: return@mapNotNull null
                val s = row.optJSONObject("summary") ?: return@mapNotNull null
                Trip(
                    id = row.optString("id").ifEmpty { index.toString() },
                    startedAt = runCatching { Instant.parse(s.optString("startTs")).toEpochMilli() }.getOrNull(),
                    distanceKm = number(s, "length")?.div(1000),
                    seconds = number(s, "duration"),
                    fuelLitres = number(s, "fuelConsumption")?.div(1000),
                    evKm = number(row.optJSONObject("hdc"), "evDistance")?.div(1000),
                    score = number(row.optJSONObject("scores"), "global")?.takeIf { it <= 100 }?.toInt(),
                )
            }.sortedByDescending { it.startedAt }
            val total = number(payload.optJSONObject("_metadata")?.optJSONObject("pagination"), "totalCount")?.toInt()
            return TripHistory(trips, total)
        }

        private fun number(o: JSONObject?, key: String): Double? =
            o?.optDouble(key)?.takeIf { it.isFinite() && it >= 0 }
    }
}
