package com.poiki.toyotawear.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * MyToyota EU backend: reads, the "wake" request and the remote commands. Blocking; one request at a
 * time (Toyota's gateway rate-limits bursts). [tokens] must return fresh tokens, refreshing if needed.
 */
class ToyotaApi(private val tokens: () -> Tokens) {

    fun vehicles(): JSONArray = call("GET", ToyotaConst.VEHICLES, null).getJSONArray("payload")
    fun status(vin: String): JSONObject = payload("GET", ToyotaConst.STATUS, vin)
    fun telemetry(vin: String): JSONObject = payload("GET", ToyotaConst.TELEMETRY, vin)
    fun electric(vin: String): JSONObject = payload("GET", ToyotaConst.ELECTRIC, vin)
    fun location(vin: String): JSONObject = payload("GET", ToyotaConst.LOCATION, vin)
    fun climateStatus(vin: String): JSONObject = payload("GET", ToyotaConst.CLIMATE_STATUS, vin)

    /** Saved climate settings (temperature, duration, options); Toyota answers an empty body when remote services are inactive. */
    fun climateSettings(vin: String): JSONObject? = call("GET", ToyotaConst.CLIMATE_SETTINGS, vin).optJSONObject("payload")

    /** Asks the car to upload fresh status. True when Toyota accepted the request (returnCode 000000). */
    fun wake(vin: String): Boolean =
        call("POST", ToyotaConst.WAKE_STATUS, vin).optJSONObject("payload")?.optString("returnCode") == "000000"

    /** door-lock / door-unlock. Toyota only acknowledges; execution must be verified by reading the status afterwards. */
    fun command(vin: String, name: String) {
        call("POST", ToyotaConst.COMMAND, vin, JSONObject().put("command", name).toString())
    }

    /** Starts or stops the climate in one request. True when Toyota accepted it (some cars omit returnCode). */
    fun climate(vin: String, start: Boolean, tempC: Double, durationMin: Int, saved: JSONObject?): Boolean {
        val body = JSONObject()
            .put("command", if (start) "start" else "stop")
            .put("duration", durationMin)
            .put("temperature", JSONObject().put("unit", "C").put("value", tempC))
            .put("saveSettings", false)
        saved?.optJSONObject("heatingOptions")?.let { body.put("heatingOptions", it) }
        saved?.optJSONObject("seatOptions")?.let { body.put("seatOptions", it) }
        val code = call("POST", ToyotaConst.CLIMATE_CONTROL, vin, body.toString()).optJSONObject("payload")?.optString("returnCode")
        return code.isNullOrEmpty() || code == "000000"
    }

    private fun payload(method: String, path: String, vin: String): JSONObject = call(method, path, vin).getJSONObject("payload")

    private fun call(method: String, path: String, vin: String?, body: String? = null): JSONObject {
        var backoffMs = 2_000L
        val bytes = body?.toByteArray() ?: if (method == "POST") ByteArray(0) else null
        while (true) {
            val r = Http.request(method, ToyotaConst.API_BASE + path, headers(vin), bytes)
            if (r.code in 200..299) return if (r.body.isBlank()) JSONObject() else JSONObject(r.body)
            if ((r.code == 429 || r.code >= 500) && backoffMs <= 8_000) { // 2 s, 4 s, 8 s like pytoyoda
                Thread.sleep(backoffMs)
                backoffMs *= 2
                continue
            }
            throw ToyotaError(r.code, apiCode(r.body), "Toyota ${r.code} ${apiCode(r.body) ?: ""}".trim())
        }
    }

    private fun apiCode(body: String): String? = runCatching {
        JSONObject(body).getJSONObject("status").getJSONArray("messages").getJSONObject(0).getString("responseCode")
    }.getOrNull()

    private fun headers(vin: String?): Map<String, String> {
        val t = tokens()
        val h = mutableMapOf(
            "x-api-key" to ToyotaConst.API_KEY,
            "API_KEY" to ToyotaConst.API_KEY,
            "x-guid" to t.uuid,
            "guid" to t.uuid,
            "x-client-ref" to hmacSha256(ToyotaConst.CLIENT_VERSION, t.uuid),
            "x-correlationid" to UUID.randomUUID().toString(),
            "x-appversion" to ToyotaConst.CLIENT_VERSION,
            "x-channel" to "ONEAPP",
            "x-brand" to t.brand,
            "x-region" to ToyotaConst.REGION,
            "x-user-region" to ToyotaConst.REGION,
            "authorization" to "Bearer ${t.accessToken}",
            "user-agent" to ToyotaConst.USER_AGENT,
            "accept" to "application/json",
            "content-type" to "application/json",
        )
        if (t.brand == "L") {
            h["x-appbrand"] = "L"
            h["brand"] = "L"
        }
        if (vin != null) h["vin"] = vin
        return h
    }

    private fun hmacSha256(key: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return mac.doFinal(message.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
