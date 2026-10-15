package com.poiki.toyotawear.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CancellationException

class ToyotaApiTest {
    private val tokens = Tokens("test-access", "test-refresh", Long.MAX_VALUE, "test-user", "T")

    @Test
    fun unauthorizedReadRenewsOnceAndUsesTheNewToken() {
        var current = tokens
        var renewals = 0
        var calls = 0
        val api = ToyotaApi({ current }, { rejected ->
            assertEquals(tokens, rejected)
            renewals++
            tokens.copy(accessToken = "renewed", refreshToken = "rotated").also { current = it }
        }) { _, _, headers, _, _, _ ->
            calls++
            if (calls == 1) HttpResult(401, "", null) else {
                assertEquals("Bearer renewed", headers["authorization"])
                HttpResult(200, """{"payload":{"state":"ok"}}""", null)
            }
        }
        assertEquals("ok", api.status("TEST").getString("state"))
        assertEquals(2, calls)
        assertEquals(1, renewals)
        assertEquals("rotated", current.refreshToken)
    }

    @Test
    fun secondUnauthorizedReadEndsTheSessionWithoutALoop() {
        var renewals = 0
        var calls = 0
        val api = ToyotaApi({ tokens }, { renewals++; tokens }) { _, _, _, _, _, _ -> calls++; HttpResult(401, "", null) }
        assertThrows(ToyotaLoginError::class.java) { api.status("TEST") }
        assertEquals(1, renewals)
        assertEquals(2, calls)
    }

    @Test
    fun unauthorizedCommandRenewsForLaterButNeverResends() {
        var renewals = 0
        var calls = 0
        val api = ToyotaApi({ tokens }, { renewals++; tokens }) { _, _, _, _, _, _ -> calls++; HttpResult(401, "", null) }
        assertEquals(401, assertThrows(ToyotaError::class.java) { api.command("TEST", "door-lock") }.httpCode)
        assertEquals(1, renewals)
        assertEquals(1, calls)
    }

    @Test
    fun renewalCannotSwitchAccounts() {
        var calls = 0
        val api = ToyotaApi({ tokens }, { tokens.copy(uuid = "another-account") }) { _, _, _, _, _, _ -> calls++; HttpResult(401, "", null) }
        assertThrows(ToyotaLoginError::class.java) { api.status("TEST") }
        assertEquals(1, calls)
    }

    @Test
    fun invalidatedSessionDiscardsAnInflightResponseAndCannotSendAgain() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var calls = 0
        val api = ToyotaApi({ tokens }, null) { _, _, _, _, _, _ ->
            calls++
            entered.countDown()
            assertTrue(release.await(3, TimeUnit.SECONDS))
            HttpResult(200, """{"payload":{"private":"old-account"}}""", null)
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pending = executor.submit<JSONObject> { api.status("OLD") }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            api.invalidate()
            release.countDown()
            val error = assertThrows(java.util.concurrent.ExecutionException::class.java) { pending.get(2, TimeUnit.SECONDS) }
            assertTrue(error.cause is CancellationException)
            assertThrows(CancellationException::class.java) { api.command("OLD", "door-unlock") }
            assertEquals(1, calls)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun climateVerificationUsesBoundedReadsWithoutGatewayRetry() {
        var calls = 0
        val api = ToyotaApi({ tokens }, null) { _, _, _, _, redirects, timeout ->
            calls++
            assertFalse(redirects)
            assertEquals(1234, timeout)
            HttpResult(503, "", null)
        }
        assertThrows(ToyotaError::class.java) { api.climateStatus("TEST", timeoutMs = 1234, retry = false) }
        assertEquals(1, calls)
    }

    @Test
    fun remoteCommandsAreNotRetried() {
        val commands: List<(ToyotaApi) -> Unit> = listOf(
            { it.command("TESTVIN", "door-lock") },
            { it.command("TESTVIN", "door-unlock") },
            { it.wake("TESTVIN") },
            { it.climate("TESTVIN", true, 21.0, 10, null) },
            { it.climate("TESTVIN", false, 21.0, 10, null) },
        )
        for (code in listOf(429, 503)) {
            for (command in commands) {
                var calls = 0
                val api = ToyotaApi({ tokens }, null) { method, _, _, _, _, _ ->
                    assertEquals("POST", method)
                    assertEquals("A remote command must be sent only once", 1, ++calls)
                    HttpResult(code, "", null)
                }
                assertEquals(code, assertThrows(ToyotaError::class.java) { command(api) }.httpCode)
                assertEquals(1, calls)
            }
        }
    }

    @Test
    fun readsRetryTransientFailures() {
        for (code in listOf(429, 503)) {
            var calls = 0
            val api = ToyotaApi({ tokens }, null) { method, _, _, _, _, _ ->
                assertEquals("GET", method)
                if (++calls == 1) HttpResult(code, "", null)
                else HttpResult(200, """{"payload":{"state":"ok"}}""", null)
            }
            assertEquals("ok", api.status("TESTVIN").getString("state"))
            assertEquals(2, calls)
        }
    }

    @Test
    fun backendRedirectsAreRejected() {
        var calls = 0
        val api = ToyotaApi({ tokens }, null) { _, _, _, _, followRedirects, _ ->
            calls++
            assertFalse("Bearer and API keys must not follow redirects", followRedirects)
            HttpResult(302, "", "https://other.invalid/")
        }
        assertEquals(302, assertThrows(ToyotaError::class.java) { api.status("TESTVIN") }.httpCode)
        assertEquals(1, calls)
    }

    @Test
    fun commandNetworkFailureIsNotRetried() {
        var calls = 0
        val api = ToyotaApi({ tokens }, null) { _, _, _, _, _, _ ->
            calls++
            throw IOException("test timeout")
        }
        assertThrows(IOException::class.java) { api.command("TESTVIN", "door-lock") }
        assertEquals(1, calls)
    }

    @Test
    fun concurrentReadsDoNotOverlapTransport() {
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondAttempted = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val calls = AtomicInteger()
        val api = ToyotaApi({ tokens }, null) { _, _, _, _, _, _ ->
            if (calls.incrementAndGet() == 1) {
                firstEntered.countDown()
                assertTrue("First read was never released", releaseFirst.await(5, TimeUnit.SECONDS))
            } else {
                secondEntered.countDown()
            }
            HttpResult(200, """{"payload":{"state":"ok"}}""", null)
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<JSONObject> { api.status("FIRST") }
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS))
            val second = executor.submit<JSONObject> {
                secondAttempted.countDown()
                api.status("SECOND")
            }
            assertTrue(secondAttempted.await(2, TimeUnit.SECONDS))
            assertFalse("Concurrent reads reached transport together", secondEntered.await(200, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            assertEquals("ok", first.get(2, TimeUnit.SECONDS).getString("state"))
            assertEquals("ok", second.get(2, TimeUnit.SECONDS).getString("state"))
            assertEquals(2, calls.get())
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        }
    }
}
