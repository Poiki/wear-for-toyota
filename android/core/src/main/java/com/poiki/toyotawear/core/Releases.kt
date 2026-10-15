package com.poiki.toyotawear.core

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.ceil

/** The update channel for sideloaded installs: this project's GitHub releases. Blocking: call off the main thread. */
object Releases {
    private const val LATEST = "https://api.github.com/repos/Poiki/wear-for-toyota/releases/latest"

    class Apk(val version: String, val url: String, val size: Long? = null, val sha256: String? = null)

    data class DownloadProgress(val bytes: Long, val total: Long?, val elapsedMs: Long) {
        val percent: Int? get() = total?.takeIf { it > 0 }?.let { (bytes.toDouble() * 100 / it).toInt().coerceIn(0, 100) }
        val remainingSeconds: Long? get() = when {
            total == null || total <= 0 || bytes <= 0 || elapsedMs < 1_000 -> null
            bytes >= total -> 0
            else -> ceil((total - bytes).toDouble() / bytes * elapsedMs / 1_000).toLong()
        }
    }

    /** The latest release's APK named [prefix]…apk when its version is above [current]; null when there is none. Throws when GitHub can't be reached. */
    fun newer(current: String, prefix: String): Apk? {
        val r = Http.request("GET", LATEST, mapOf("accept" to "application/vnd.github+json"))
        if (r.code != 200) throw IOException("GitHub ${r.code}")
        return pick(JSONObject(r.body), current, prefix)
    }

    internal fun pick(release: JSONObject, current: String, prefix: String): Apk? {
        val version = release.getString("tag_name").removePrefix("v")
        if (!isNewer(version, current)) return null
        val assets = release.getJSONArray("assets")
        val matches = (0 until assets.length()).map { assets.getJSONObject(it) }.filter {
            it.getString("name").let { name -> name.startsWith(prefix) && name.endsWith(".apk") }
        }
        if (matches.isEmpty()) return null
        if (matches.size != 1) throw IOException("Ambiguous release APK")
        val asset = matches.single()
        val url = asset.getString("browser_download_url")
        if (!url.startsWith("https://github.com/Poiki/wear-for-toyota/releases/download/")) throw IOException("Unexpected release source")
        val digest = asset.optString("digest").takeIf { it.isNotBlank() && it != "null" }
        if (digest != null && !digest.matches(Regex("sha256:[a-fA-F0-9]{64}"))) throw IOException("Invalid APK digest")
        return Apk(version, url, asset.optLong("size").takeIf { it > 0 }, digest?.removePrefix("sha256:")?.lowercase())
    }

    /** Streams to the install session, without keeping a second APK in app storage. */
    fun download(apk: Apk, output: OutputStream, onProgress: (DownloadProgress) -> Unit) {
        val connection = URL(apk.url).openConnection().apply {
            connectTimeout = 20_000
            readTimeout = 20_000
            useCaches = false
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            if (connection is HttpURLConnection && connection.responseCode != 200) throw IOException("APK download rejected")
            val length = connection.contentLengthLong.takeIf { it > 0 }
            if (apk.size != null && length != null && apk.size != length) throw IOException("APK length mismatch")
            connection.getInputStream().use { copy(it, output, apk.size ?: length, apk.sha256, onProgress) }
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    }

    internal fun copy(
        input: InputStream, output: OutputStream, total: Long?, sha256: String?, onProgress: (DownloadProgress) -> Unit,
        clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
    ) {
        val started = clockMs()
        var lastReport = started
        var bytes = 0L
        val digest = sha256?.let { MessageDigest.getInstance("SHA-256") }
        onProgress(DownloadProgress(0, total, 0))
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            bytes += count
            if (total != null && bytes > total) throw IOException("APK exceeds expected length")
            output.write(buffer, 0, count)
            digest?.update(buffer, 0, count)
            val now = clockMs()
            if (now - lastReport >= 250) {
                onProgress(DownloadProgress(bytes, total, now - started))
                lastReport = now
            }
        }
        if (bytes == 0L || (total != null && bytes != total)) throw IOException("Incomplete APK")
        if (digest != null && digest.digest().joinToString("") { "%02x".format(it) } != sha256) throw IOException("APK checksum mismatch")
        onProgress(DownloadProgress(bytes, total, clockMs() - started))
    }

    /** Only call with this app's private directories; public downloads belong to the user. */
    fun cleanInstallers(directory: File) {
        if (!directory.exists()) return
        val files = directory.listFiles() ?: throw IOException("Cannot read installer cache")
        files.filter { it.isFile && (it.name == "update.apk" || (it.name.startsWith("wear-for-toyota-watch") && it.name.endsWith(".apk"))) }.forEach {
            if (!it.delete()) throw IOException("Cannot remove old installer")
        }
    }

    /** Numeric dotted comparison: 0.10.0 > 0.9.1; missing parts count as 0. */
    internal fun isNewer(version: String, current: String): Boolean {
        val a = version.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }
}
