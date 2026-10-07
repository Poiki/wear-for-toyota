package com.poiki.toyotawear.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Base64

/** One check per non-trivial piece: Toyota payload parsing (real pytoyoda fixtures), the login helpers and the update check. */
class CoreTest {
    private fun fixture(name: String) = JSONObject(javaClass.getResource("/$name")!!.readText())

    @Test
    fun snapshotFromFixtures() {
        val raw = JSONObject()
            .put("vehicle", fixture("v2_vehicleguid.json").getJSONArray("payload").getJSONObject(0))
            .put("status", fixture("v1_global_remote_status.json").getJSONObject("payload"))
            .put("telemetry", fixture("v3_telemetry.json").getJSONObject("payload"))
            .put("electric", fixture("v1_global_remote_electric_status.json").getJSONObject("payload"))
            .put("location", fixture("v1_location_ok.json").getJSONObject("payload"))
            .put("fetchedAt", 42L)
        val s = Snapshot.from(raw)
        assertEquals("RAV4", s.alias)
        assertTrue(s.remoteActive)
        assertTrue(s.electric)
        assertEquals(true, s.locked)
        assertEquals(0, s.openDoors)
        assertEquals(0, s.openWindows)
        assertEquals(false, s.hoodOpen)
        assertEquals(Instant.parse("2026-06-30T20:29:07.000Z").toEpochMilli(), s.statusAt)
        assertEquals(30420.0, s.odometerKm!!, 0.0)
        assertEquals(99, s.fuelPct)
        assertEquals(634.0, s.fuelRangeKm!!, 0.0)
        assertEquals(79, s.batteryPct)
        assertEquals(57.3, s.evRangeKm!!, 0.001)
        assertEquals("none", s.charging)
        assertEquals(52.169516, s.lat!!, 1e-6)
        assertEquals(Instant.parse("2023-11-27T07:42:41Z").toEpochMilli(), s.locationAt)
        assertNull(s.climate)
        assertEquals(42L, s.fetchedAt)
    }

    @Test
    fun remoteServicesWithoutDisplayField() {
        val vehicle = fixture("v2_vehicleguid.json").getJSONArray("payload").getJSONObject(0)
        val raw = JSONObject().put("vehicle", vehicle)
        vehicle.remove("remoteDisplay")
        assertTrue(Snapshot.from(raw).remoteActive) // Active subscription, field omitted by Toyota.
        vehicle.put("remoteSubscriptionStatus", "EXPIRED")
        assertFalse(Snapshot.from(raw).remoteActive)
        vehicle.remove("remoteSubscriptionStatus")
        assertFalse(Snapshot.from(raw).remoteActive)
        vehicle.put("remoteSubscriptionStatus", "ACTIVE").put("remoteDisplay", "9")
        assertFalse(Snapshot.from(raw).remoteActive) // Explicit account block wins over subscription.
        vehicle.put("remoteDisplay", JSONObject.NULL)
        assertTrue(Snapshot.from(raw).remoteActive)
    }

    @Test
    fun tripConsumptionUsesWeightedDistanceAndPreservesMissingData() {
        val payload = JSONObject("""{"trips":[
            {"id":"short","summary":{"startTs":"2026-10-04T10:00:00Z","length":10000,"duration":600,"fuelConsumption":1000},"hdc":{"evDistance":2000}},
            {"id":"long","summary":{"startTs":"2026-10-05T10:00:00Z","length":90000,"duration":3600,"fuelConsumption":4500}},
            {"id":"electric","summary":{"length":10000,"fuelConsumption":0}},
            {"id":"missing","summary":{"length":5000}},
            {"id":"idle","summary":{"length":0,"fuelConsumption":10}},
            {"summary":null}
        ],"_metadata":{"pagination":{"totalCount":70}}}""")
        val h = TripHistory.from(payload)
        assertEquals(70, h.total)
        assertEquals(5, h.trips.size)
        assertEquals(3, h.measured.size)
        val fuelOnly = TripHistory(h.trips.filter { it.id == "short" || it.id == "long" }, null)
        assertEquals(10.0, fuelOnly.trips.first { it.id == "short" }.consumption!!, 0.001)
        assertEquals(5.0, fuelOnly.trips.first { it.id == "long" }.consumption!!, 0.001)
        assertEquals(5.5, fuelOnly.averageConsumption!!, 0.001) // (10*10 + 5*90) / 100, not 7.5.
        assertEquals(5.0, h.averageConsumption!!, 0.001) // 5.5 L / 110 km, not average of rates.
        assertEquals(115.0, h.distanceKm!!, 0.001)
        assertEquals("long", h.trips.first().id)
        assertEquals(60.0, h.trips.first { it.id == "short" }.averageSpeed!!, 0.001)
        assertEquals(20.0, h.trips.first { it.id == "short" }.evShare!!, 0.001)
        assertEquals(0.0, h.trips.first { it.id == "electric" }.consumption!!, 0.0)
        assertNull(h.trips.first { it.id == "missing" }.consumption)
        assertNull(h.trips.first { it.id == "idle" }.consumption)
        assertNull(TripHistory.from(JSONObject()).averageConsumption)
    }

    @Test
    fun chartKeepsRecentChronologyAndMissingConsumption() {
        val trips = (15 downTo 1).map { index ->
            Trip(index.toString(), index.toLong(), 10.0, null, if (index == 10) null else 0.0, null, null)
        } + Trip("undated", null, 10.0, null, 1.0, null, null)
        val chart = TripHistory(trips, 40).chartTrips
        assertEquals((4..15).map { it.toString() }, chart.map { it.id })
        assertNull(chart.first { it.id == "10" }.consumption)
        assertEquals(0.0, chart.last().consumption!!, 0.0)
        assertEquals(1, TripHistory(listOf(trips.first()), null).chartTrips.size)
        assertTrue(TripHistory(listOf(trips.last()), null).chartTrips.isEmpty())
        assertTrue(TripHistory(emptyList(), null).chartTrips.isEmpty())
    }

    @Test
    fun snapshotWithOnlyVehicle() {
        val s = Snapshot.from(JSONObject().put("vehicle", JSONObject().put("vin", "X").put("modelName", "Yaris")))
        assertEquals("Yaris", s.alias)
        assertNull(s.locked)
        assertNull(s.fuelRangeKm)
    }

    @Test
    fun displayNameIgnoresVinNickname() {
        // Toyota fills nickName with the VIN when the user never named the car (seen on a real account).
        val v = JSONObject().put("vin", "SB1ZTEST0000000000").put("nickName", "SB1ZTEST0000000000").put("modelName", "Corolla Hybrid Touring Sports")
        assertEquals("Corolla Hybrid Touring Sports", Snapshot.displayName(v))
        assertEquals("Mi coche", Snapshot.displayName(v.put("nickName", "Mi coche")))
        assertEquals("Toyota", Snapshot.displayName(JSONObject().put("vin", "X")))
    }

    @Test
    fun authHelpers() {
        assertEquals("abc", ToyotaAuth.codeFromLocation("com.toyota.oneapp:/oauth2Callback?code=abc&iss=https%3A%2F%2Fx"))
        assertNull(ToyotaAuth.codeFromLocation("com.toyota.oneapp:/oauth2Callback?error=denied"))

        val data = JSONObject(
            """{"authId":"a","callbacks":[
                {"type":"NameCallback","output":[{"name":"prompt","value":"User Name"}],"input":[{"name":"IDToken1","value":""}]},
                {"type":"PasswordCallback","output":[{"name":"prompt","value":"Password"}],"input":[{"name":"IDToken2","value":""}]}]}""",
        )
        ToyotaAuth.fillCallbacks(data, "a@b.c", "pw")
        val cbs = data.getJSONArray("callbacks")
        assertEquals("a@b.c", cbs.getJSONObject(0).getJSONArray("input").getJSONObject(0).getString("value"))
        assertEquals("pw", cbs.getJSONObject(1).getJSONArray("input").getJSONObject(0).getString("value"))

        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"uuid":"u-1","aud":"oneappsdkclient"}""".toByteArray())
        val tokens = ToyotaAuth.tokensFromResponse(
            JSONObject().put("access_token", "A").put("refresh_token", "R").put("id_token", "h.$payload.s").put("expires_in", 3600),
            "T",
        )
        assertEquals("u-1", tokens.uuid)
        assertTrue(tokens.isFresh())
        assertEquals(tokens, Tokens.fromJson(tokens.toJson()))
    }

    @Test
    fun releasePicksNewerWatchApk() {
        assertTrue(Releases.isNewer("0.10.0", "0.9.1"))
        assertTrue(Releases.isNewer("1.0", "0.9.9"))
        assertFalse(Releases.isNewer("0.2.0", "0.2.0"))
        assertFalse(Releases.isNewer("0.1.9", "0.2"))

        val release = JSONObject(
            """{"tag_name":"v0.2.0","assets":[
                {"name":"wear-for-toyota-phone-0.2.0.apk","browser_download_url":"https://x/phone.apk"},
                {"name":"wear-for-toyota-watch-0.2.0.apk","browser_download_url":"https://x/watch.apk"}]}""",
        )
        val apk = Releases.pick(release, "0.1.0", "wear-for-toyota-watch")!!
        assertEquals("0.2.0", apk.version)
        assertEquals("https://x/watch.apk", apk.url)
        assertNull(Releases.pick(release, "0.2.0", "wear-for-toyota-watch"))
    }
}
