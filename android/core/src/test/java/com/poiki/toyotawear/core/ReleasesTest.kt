package com.poiki.toyotawear.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class ReleasesTest {
    @Test
    fun progressUsesBytesAndElapsedTimeAndLeavesUnknownTotalsUnknown() {
        val halfway = Releases.DownloadProgress(500, 1_000, 2_000)
        assertEquals(50, halfway.percent)
        assertEquals(2L, halfway.remainingSeconds)
        assertNull(Releases.DownloadProgress(0, 1_000, 1_000).remainingSeconds)
        assertNull(Releases.DownloadProgress(500, null, 2_000).percent)
        assertNull(Releases.DownloadProgress(500, null, 2_000).remainingSeconds)
        assertNull(Releases.DownloadProgress(500, 1_000, 200).remainingSeconds)
        assertEquals(0L, Releases.DownloadProgress(1_000, 1_000, 2_000).remainingSeconds)
    }

    @Test
    fun downloadReportsActualBytesAndVerifiesSizeAndDigest() {
        val bytes = ByteArray(40_000) { it.toByte() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val output = ByteArrayOutputStream()
        val seen = mutableListOf<Releases.DownloadProgress>()
        var clock = 0L
        Releases.copy(ByteArrayInputStream(bytes), output, bytes.size.toLong(), digest, { seen += it }, { clock += 300; clock })
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(0L, seen.first().bytes)
        assertEquals(bytes.size.toLong(), seen.last().bytes)
        assertEquals(100, seen.last().percent)
        assertTrue(seen.zipWithNext().all { (a, b) -> b.bytes >= a.bytes })
        assertThrows(IOException::class.java) { Releases.copy(ByteArrayInputStream(bytes), ByteArrayOutputStream(), bytes.size + 1L, null, {}) }
        assertThrows(IOException::class.java) { Releases.copy(ByteArrayInputStream(bytes), ByteArrayOutputStream(), bytes.size - 1L, null, {}) }
        assertThrows(IOException::class.java) { Releases.copy(ByteArrayInputStream(bytes), ByteArrayOutputStream(), bytes.size.toLong(), "0".repeat(64), {}) }
    }

    @Test
    fun cancellationAndReadFailuresAreNotHidden() {
        assertThrows(CancellationException::class.java) {
            Releases.copy(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), null, null, { throw CancellationException() })
        }
        val broken = object : java.io.InputStream() { override fun read(): Int = throw IOException("disconnected") }
        assertThrows(IOException::class.java) { Releases.copy(broken, ByteArrayOutputStream(), null, null, {}) }
        val seen = mutableListOf<Releases.DownloadProgress>()
        Releases.copy(ByteArrayInputStream(byteArrayOf(1, 2)), ByteArrayOutputStream(), null, null, { seen += it })
        assertNull(seen.last().percent)
        assertEquals(2L, seen.last().bytes)
    }

    @Test
    fun metadataKeepsSizeAndDigestAndRejectsAmbiguousOrUnexpectedAssets() {
        val asset = JSONObject().put("name", "wear-for-toyota-watch-0.3.5.apk")
            .put("browser_download_url", "https://github.com/Poiki/wear-for-toyota/releases/download/v0.3.5/watch.apk")
            .put("size", 123).put("digest", "sha256:" + "a".repeat(64))
        val release = JSONObject().put("tag_name", "v0.3.5").put("assets", org.json.JSONArray().put(asset))
        val apk = Releases.pick(release, "0.3.4", "wear-for-toyota-watch")!!
        assertEquals(123L, apk.size)
        assertEquals("a".repeat(64), apk.sha256)
        assertThrows(IOException::class.java) { Releases.pick(release.put("assets", org.json.JSONArray().put(asset).put(asset)), "0.3.4", "wear-for-toyota-watch") }
        release.put("assets", org.json.JSONArray().put(asset))
        asset.put("browser_download_url", "https://other.example/watch.apk")
        assertThrows(IOException::class.java) { Releases.pick(release, "0.3.4", "wear-for-toyota-watch") }
    }

    @Test
    fun cleanupDeletesOnlyOwnedInstallerNames() {
        val dir = Files.createTempDirectory("toyota-updates").toFile()
        try {
            val installers = listOf("update.apk", "wear-for-toyota-watch-0.1.0.apk", "wear-for-toyota-watch-0.3.4.apk")
            val keep = listOf("car-DEMO.png", "tokens.json", "other.apk")
            (installers + keep).forEach { dir.resolve(it).writeText("fixture") }
            Releases.cleanInstallers(dir)
            assertTrue(installers.none { dir.resolve(it).exists() })
            assertTrue(keep.all { dir.resolve(it).exists() })
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
}
