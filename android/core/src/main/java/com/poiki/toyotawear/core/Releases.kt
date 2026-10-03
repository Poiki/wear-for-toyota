package com.poiki.toyotawear.core

import org.json.JSONObject
import java.io.IOException

/** The update channel for sideloaded installs: this project's GitHub releases. Blocking: call off the main thread. */
object Releases {
    private const val LATEST = "https://api.github.com/repos/Poiki/wear-for-toyota/releases/latest"

    class Apk(val version: String, val url: String)

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
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.getString("name")
            if (name.startsWith(prefix) && name.endsWith(".apk")) return Apk(version, a.getString("browser_download_url"))
        }
        return null
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
