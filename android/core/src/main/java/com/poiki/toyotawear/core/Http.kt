package com.poiki.toyotawear.core

import java.net.HttpURLConnection
import java.net.URL

internal class HttpResult(val code: Int, val body: String, val location: String?)

/** Thin blocking wrapper over the platform HttpURLConnection: TLS, gzip and HTTP/1.1 are all we need. */
internal object Http {
    fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray? = null,
        followRedirects: Boolean = true,
        timeoutMs: Int = 20_000,
    ): HttpResult {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.instanceFollowRedirects = followRedirects
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return HttpResult(code, text, c.getHeaderField("Location"))
        } finally {
            c.disconnect()
        }
    }
}
