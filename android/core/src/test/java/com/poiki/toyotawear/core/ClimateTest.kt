package com.poiki.toyotawear.core

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class ClimateTest {
    private fun status(state: String, at: Long = 2_000) = JSONObject()
        .put("status", state).put("updatedAt", Instant.ofEpochMilli(at).toString())

    private fun <T> immediate(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return checkNotNull(result).getOrThrow()
    }

    @Test
    fun verificationWaitsThroughStaleAndStartingStatesUntilRunning() {
        val replies = listOf(status("running", 999), status("starting"), status("running", 3_000))
        val seen = mutableListOf<String>()
        var calls = 0
        var clock = 0L
        val result = immediate { Climate.awaitStatus(true, null, status("stopped", 1_000),
            read = { timeout -> assertTrue(timeout in 1..10_000); replies[calls++] },
            pause = { clock += it }, onStatus = { seen += it.getString("status") }, clockMs = { clock }) }
        assertEquals(3, calls)
        assertEquals(listOf("starting", "running"), seen)
        assertTrue(Climate.confirmed(true, null, result, status("stopped", 1_000)))
        assertEquals(8_000, clock)
    }

    @Test
    fun verificationKeepsTheNewestReadingAndHasABoundedDeadline() {
        var calls = 0
        var clock = 0L
        val result = immediate { Climate.awaitStatus(true, null, status("stopped", 1_000),
            read = { calls++; if (calls == 1) status("starting", 3_000) else status("stopped", 2_000) },
            pause = { clock += it }, clockMs = { clock }) }
        assertEquals("starting", result?.getString("status"))
        assertFalse(Climate.confirmed(true, null, result, status("stopped", 1_000)))
        assertTrue(calls in 2..9)
        assertTrue(clock < 60_000)
    }

    @Test
    fun undatedReadDoesNotLetAnOlderDatedStateConfirmTheCommand() {
        val baseline = status("running", 1_000)
        val replies = listOf(status("stopping", 3_000), JSONObject().put("status", "stopping"), status("stopped", 2_000))
        var calls = 0
        var clock = 0L
        val result = immediate { Climate.awaitStatus(false, null, baseline,
            read = { replies[(calls++).coerceAtMost(replies.lastIndex)] },
            pause = { clock += it }, clockMs = { clock }) }
        assertEquals("stopping", result?.getString("status"))
        assertFalse(Climate.confirmed(false, null, result, baseline))
    }

    @Test
    fun temporaryReadFailuresRecoverButAuthenticationAndCancellationPropagate() {
        var calls = 0
        var clock = 0L
        val result = immediate { Climate.awaitStatus(false, null, status("running", 1_000),
            read = { when (++calls) { 1 -> throw IOException("timeout"); 2 -> throw ToyotaError(503, null, "unavailable"); else -> status("stopped") } },
            pause = { clock += it }, clockMs = { clock }) }
        assertTrue(Climate.confirmed(false, null, result, status("stopped", 1_000)))
        for (failure in listOf(ToyotaLoginError("expired"), CancellationException("account changed"), ToyotaError(403, null, "denied"))) {
            val thrown = assertThrows(failure.javaClass) { immediate { Climate.awaitStatus(true, null, status("stopped", 0), read = { throw failure }, pause = {}) } }
            assertEquals(failure, thrown)
        }
    }

    @Test
    fun noResponseNeverProducesASuccessfulState() {
        var clock = 0L
        val result = immediate { Climate.awaitStatus(true, null, status("stopped", 1_000),
            read = { throw IOException("offline") }, pause = { clock += it }, clockMs = { clock }) }
        assertNull(result)
        assertFalse(Climate.confirmed(true, null, result, status("stopped", 1_000)))
    }

    @Test
    fun reportedStartTimeAndBaselineAvoidDependingOnTheWatchClock() {
        val running = JSONObject().put("status", "running").put("startedAt", Instant.ofEpochMilli(2_000).toString())
        assertTrue(Climate.confirmed(true, null, running, status("stopped", 1_000)))
        assertFalse(Climate.confirmed(true, null, running, status("stopped", 2_000)))
        assertEquals(3_000L, Climate.timestamp(running.put("updatedAt", Instant.ofEpochMilli(3_000).toString())))
    }

    @Test
    fun cabinTemperatureAndCountdownUseOnlyReportedData() {
        assertEquals(20.0, Climate.currentTemperature(JSONObject("""{"currentTemperature":{"value":68,"unit":"F"}}"""))!!, 0.001)
        assertNull(Climate.currentTemperature(JSONObject("""{"currentTemperature":{"value":20,"unit":"K"}}""")))
        assertNull(Climate.currentTemperature(JSONObject()))
        val running = JSONObject("""{"status":"running","startedAt":"2026-10-15T10:00:00Z","duration":10}""")
        val end = Climate.endsAt(running)
        assertEquals(Instant.parse("2026-10-15T10:10:00Z").toEpochMilli(), end)
        assertEquals(8, Climate.remainingMinutes(end, Instant.parse("2026-10-15T10:02:01Z").toEpochMilli()))
        assertEquals(0, Climate.remainingMinutes(end, end!! + 1))
        assertNull(Climate.remainingMinutes(null, 0))
        assertNull(Climate.endsAt(running.put("status", "stopped")))
        assertNull(Climate.endsAt(running.put("status", "running").put("duration", -1)))
        assertNull(Climate.endsAt(running.put("duration", 10).put("startedAt", "invalid")))
    }

    @Test
    fun transitionsDoNotConfirmCompletion() {
        assertFalse(Climate.confirmed(true, null, status("starting"), status("stopped", 1_000)))
        assertFalse(Climate.confirmed(false, null, status("stopping"), status("stopped", 1_000)))
        assertTrue(Climate.confirmed(true, null, status("running"), status("stopped", 1_000)))
        assertTrue(Climate.confirmed(false, null, status("stopped"), status("stopped", 1_000)))
    }

    @Test
    fun oldOrUndatedReadsDoNotConfirmClimateCommands() {
        for ((start, state) in listOf(true to "running", false to "stopped")) {
            val baseline = status(state, 1_000)
            assertFalse(Climate.confirmed(start, null, null, baseline))
            assertFalse(Climate.confirmed(start, null, JSONObject().put("status", state), baseline))
            assertFalse(Climate.confirmed(start, null, status(state).put("updatedAt", "invalid"), baseline))
            assertFalse(Climate.confirmed(start, null, status(state, 999), status("stopped", 1_000)))
            assertFalse(Climate.confirmed(start, null, status(state, 1_000), status("stopped", 1_000)))
            assertTrue(Climate.confirmed(start, null, status(state), status("stopped", 1_000)))
        }
    }

    @Test
    fun undatedStopsCanConfirmAnObservedTransitionButMissingBaselineCannot() {
        val stopped = JSONObject().put("status", "stopped")
        val running = status("running")
        assertTrue(Climate.confirmed(true, null, running, stopped))
        assertTrue(Climate.confirmed(false, null, stopped, running))
        assertFalse(Climate.confirmed(true, null, running, null))
        assertFalse(Climate.confirmed(false, null, stopped, null))
        assertFalse(Climate.confirmed(true, null, running, JSONObject().put("status", "unknown")))
        assertFalse(Climate.confirmed(true, null, running, status("stopped", 3_000)))
    }

    @Test
    fun advisoryRefreshRejectionStillReadsTheAuthoritativeState() {
        for (code in listOf(401, 403, 404, 503)) {
            var reads = 0
            val result = immediate { Climate.awaitStatus(true, null, status("stopped", 1_000),
                read = { reads++; status("running") }, pause = {}, refresh = { throw ToyotaError(code, null, "refresh unavailable") }) }
            assertTrue(Climate.confirmed(true, null, result, status("stopped", 1_000)))
            assertEquals(1, reads)
        }
        assertThrows(ToyotaLoginError::class.java) { immediate { Climate.awaitStatus(true, null, null,
            read = { throw AssertionError("Session errors must propagate") }, pause = {}, refresh = { throw ToyotaLoginError("expired") }) } }
    }

    @Test
    fun requestFromSupportedChoicesOmitsDisabledSavedOptions() {
        val vehicle = JSONObject("""{"extendedCapabilities":{"steeringHeater":false,"frontDriverSeatHeater":true}}""")
        val saved = JSONObject("""{"heatingOptions":{"steeringHeater":"on"},"seatOptions":{"driverSeat":"high"}}""")
        val choices = Climate.choices(vehicle, saved)
        val body = Climate.request(true, 21.0, 10, Climate.settings(null, choices))
        assertFalse(body.has("heatingOptions"))
        assertTrue(body.getJSONObject("seatOptions").getString("driverSeat") == "heater")
    }
}
