package com.poiki.toyotawear.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotTest {
    private fun snapshot(status: JSONObject? = null, telemetry: JSONObject? = null, location: JSONObject? = null): Snapshot =
        Snapshot.from(JSONObject().put("vehicle", JSONObject().put("vin", "TEST"))
            .put("status", status).put("telemetry", telemetry).put("location", location))

    @Test
    fun unknownLockCannotConfirmLockedAndKnownUnlockedWins() {
        for (state in listOf("unknown", "", JSONObject.NULL)) {
            val driver = JSONObject().put("lockStatus", JSONObject().put("status", state))
            val passenger = JSONObject().put("lockStatus", JSONObject().put("status", "locked"))
            val status = JSONObject().put("doors", JSONObject().put("driver", driver).put("passenger", passenger))
            assertNull(snapshot(status).locked)
            passenger.getJSONObject("lockStatus").put("status", "unlocked")
            assertEquals(false, snapshot(status).locked)
        }
        for (reading in listOf(JSONObject(), JSONObject.NULL)) {
            val doors = JSONObject().put("driver", JSONObject().put("lockStatus", reading))
                .put("passenger", JSONObject().put("lockStatus", JSONObject().put("status", "locked")))
            assertNull(snapshot(JSONObject().put("doors", doors)).locked)
        }
    }

    @Test
    fun omittedComponentsDoNotRequireFiveDoors() {
        val status = JSONObject("""{"doors":{"driver":{"lockStatus":{"status":"locked"},"openStatus":{"status":"close"}},"hood":{"openStatus":{"status":"close"}}},"windows":{"driver":{"status":"close"}}}""")
        val s = snapshot(status)
        assertEquals(true, s.locked)
        assertEquals(0, s.openDoors)
        assertEquals(0, s.openWindows)
        assertEquals(false, s.hoodOpen)
        val empty = snapshot(JSONObject("""{"doors":{},"windows":{}}"""))
        assertNull(empty.locked)
        assertNull(empty.openDoors)
        assertNull(empty.openWindows)
        assertNull(empty.hoodOpen)
    }

    @Test
    fun unknownOpenStatesNeverBecomeClosedAndKnownOpenIsPreserved() {
        for (state in listOf("unknown", "", JSONObject.NULL)) {
            val status = JSONObject("""{"doors":{"driver":{"openStatus":{}},"hood":{"openStatus":{}}},"windows":{"driver":{}}}""")
            status.getJSONObject("doors").getJSONObject("driver").getJSONObject("openStatus").put("status", state)
            status.getJSONObject("doors").getJSONObject("hood").getJSONObject("openStatus").put("status", state)
            status.getJSONObject("windows").getJSONObject("driver").put("status", state)
            assertNull(snapshot(status).openDoors)
            assertNull(snapshot(status).openWindows)
            assertNull(snapshot(status).hoodOpen)
            status.getJSONObject("doors").put("passenger", JSONObject("""{"openStatus":{"status":"open"}}"""))
            status.getJSONObject("windows").put("passenger", JSONObject("""{"status":"open"}"""))
            assertEquals(1, snapshot(status).openDoors)
            assertEquals(1, snapshot(status).openWindows)
        }
    }

    @Test
    fun percentagesMustBeWithinTheirActualRange() {
        for (value in listOf(-1, 101, 100.9, "Infinity", "NaN")) {
            val telemetry = JSONObject().put("fuelLevel", value).put("batteryLevel", value)
            val s = snapshot(telemetry = telemetry)
            assertNull(s.fuelPct)
            assertNull(s.batteryPct)
        }
        assertEquals(0, snapshot(telemetry = JSONObject().put("fuelLevel", 0)).fuelPct)
        assertEquals(100, snapshot(telemetry = JSONObject().put("batteryLevel", 100)).batteryPct)
    }

    @Test
    fun distancesRequireKnownUnitsAndFiniteNonNegativeValues() {
        for ((value, unit) in listOf(-1 to "km", "Infinity" to "km", "NaN" to "km", 1 to "yards", 1 to "", Double.MAX_VALUE to "mi")) {
            val telemetry = JSONObject().put("odometer", JSONObject().put("value", value).put("unit", unit))
            assertNull(snapshot(telemetry = telemetry).odometerKm)
        }
        assertEquals(1.60934, snapshot(telemetry = JSONObject("""{"odometer":{"value":1,"unit":"mi"}}""")).odometerKm!!, 0.000001)
        assertEquals(0.0, snapshot(telemetry = JSONObject("""{"distanceToEmpty":{"value":0,"unit":"km"}}""")).fuelRangeKm!!, 0.0)
    }

    @Test
    fun coordinatesRequireFiniteValuesWithinGeographicBounds() {
        for ((lat, lon) in listOf(91 to 181, -91 to -181, "Infinity" to "-Infinity", "NaN" to "NaN")) {
            val s = snapshot(location = JSONObject().put("vehicleLocation", JSONObject().put("latitude", lat).put("longitude", lon)))
            assertNull(s.lat)
            assertNull(s.lon)
        }
        val s = snapshot(location = JSONObject("""{"vehicleLocation":{"latitude":-90,"longitude":180}}"""))
        assertEquals(-90.0, s.lat!!, 0.0)
        assertEquals(180.0, s.lon!!, 0.0)
    }

    @Test
    fun lockConfirmationRequiresTheSameVehicleAndAReadAfterTheCommand() {
        val fresh = snapshot().copy(locked = true, statusAt = 101L, lockAt = 101L)
        assertTrue(fresh.confirmsLock("TEST", true, 100L))
        assertFalse(fresh.copy(statusAt = 100L).confirmsLock("TEST", true, 100L))
        assertFalse(fresh.copy(statusAt = 99L).confirmsLock("TEST", true, 100L))
        assertFalse(fresh.copy(statusAt = null).confirmsLock("TEST", true, 100L))
        assertFalse(fresh.copy(lockAt = null).confirmsLock("TEST", true, 100L))
        assertFalse(fresh.copy(locked = null).confirmsLock("TEST", true, 100L))
        assertFalse(fresh.confirmsLock("TEST", false, 100L))
        assertFalse(fresh.confirmsLock("OTHER", true, 100L))
        assertTrue(fresh.copy(locked = false).confirmsLock("TEST", false, 100L))
    }

    @Test
    fun newGlobalStatusCannotConfirmOldOrUndatedLockReadings() {
        val status = JSONObject("""{"lastUpdateTimestamp":"2026-10-15T10:00:02Z","doors":{"driver":{"lockStatus":{"status":"locked","lastUpdateTimestamp":"2026-10-15T10:00:02Z"}},"passenger":{"lockStatus":{"status":"locked","lastUpdateTimestamp":"2026-10-15T10:00:02Z"}}}}""")
        val sentAt = java.time.Instant.parse("2026-10-15T10:00:01Z").toEpochMilli()
        val reading = status.getJSONObject("doors").getJSONObject("driver").getJSONObject("lockStatus")
        assertTrue(snapshot(status).confirmsLock("TEST", true, sentAt))
        for (timestamp in listOf("2026-10-15T10:00:00Z", "2026-10-15T10:00:01Z", "invalid", JSONObject.NULL)) {
            reading.put("lastUpdateTimestamp", timestamp)
            assertFalse(snapshot(status).confirmsLock("TEST", true, sentAt))
        }
        reading.remove("lastUpdateTimestamp")
        assertFalse(snapshot(status).confirmsLock("TEST", true, sentAt))
        assertNull(snapshot(status).lockAt)
        reading.put("lastUpdateTimestamp", "2026-10-15T10:00:00Z")
        assertEquals(sentAt - 1000, snapshot(status).lockAt)
        assertNull(snapshot().lockAt)
    }
}
