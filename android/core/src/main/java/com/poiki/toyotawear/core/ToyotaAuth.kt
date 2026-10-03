package com.poiki.toyotawear.core

import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/**
 * The MyToyota login as pytoyoda implements it: ForgeRock JSON callbacks → SSO cookie →
 * OAuth2 authorization code → tokens. Plain HTTPS, no browser. Blocking: call off the main thread.
 */
object ToyotaAuth {
    fun login(email: String, password: String, brand: String = "T"): Tokens {
        val tokenId = authenticate(email, password)
        val code = authorize(tokenId)
        return exchange(mapOf("grant_type" to "authorization_code", "code" to code), brand)
    }

    /** Uses the rotating refresh token; the password is never needed again. */
    fun refresh(t: Tokens): Tokens =
        exchange(mapOf("grant_type" to "refresh_token", "refresh_token" to t.refreshToken), t.brand)

    private fun authenticate(email: String, password: String): String {
        var data = JSONObject()
        val headers = mapOf("content-type" to "application/json", "accept" to "application/json", "user-agent" to ToyotaConst.USER_AGENT)
        repeat(10) {
            val r = Http.request("POST", ToyotaConst.AUTHENTICATE, headers, data.toString().toByteArray())
            if (r.code == 429 || r.code >= 500) throw ToyotaError(r.code, null, "authenticate ${r.code}")
            if (r.code != 200) throw ToyotaLoginError("Toyota rechazó el inicio de sesión (${r.code})")
            data = JSONObject(r.body)
            if (data.has("tokenId")) return data.getString("tokenId")
            fillCallbacks(data, email, password)
        }
        throw ToyotaLoginError("Toyota no devolvió sesión")
    }

    /** Fills the ForgeRock callbacks in place so the same JSON can be posted back. */
    internal fun fillCallbacks(data: JSONObject, email: String, password: String) {
        val cbs = data.optJSONArray("callbacks") ?: return
        for (i in 0 until cbs.length()) {
            val cb = cbs.getJSONObject(i)
            val prompt = cb.optJSONArray("output")?.optJSONObject(0)?.optString("value")
            when (cb.optString("type")) {
                "NameCallback" -> if (prompt == "User Name") cb.getJSONArray("input").getJSONObject(0).put("value", email)
                "PasswordCallback" -> cb.getJSONArray("input").getJSONObject(0).put("value", password)
                "TextOutputCallback" -> if (prompt == "User Not Found") throw ToyotaLoginError("Usuario no encontrado")
            }
        }
    }

    private fun authorize(tokenId: String): String {
        val headers = mapOf("cookie" to "iPlanetDirectoryPro=$tokenId", "user-agent" to ToyotaConst.USER_AGENT)
        val r = Http.request("GET", ToyotaConst.AUTHORIZE, headers, followRedirects = false)
        if (r.code == 429 || r.code >= 500) throw ToyotaError(r.code, null, "authorize ${r.code}")
        if (r.code != 302) throw ToyotaLoginError("Autorización rechazada (${r.code})")
        return codeFromLocation(r.location ?: "") ?: throw ToyotaLoginError("Toyota no devolvió código de autorización")
    }

    internal fun codeFromLocation(location: String): String? = location.substringAfter('?', "").split('&')
        .firstOrNull { it.startsWith("code=") }
        ?.substringAfter('=')
        ?.let { URLDecoder.decode(it, "UTF-8") }
        ?.ifEmpty { null }

    private fun exchange(params: Map<String, String>, brand: String): Tokens {
        val fixed = mapOf("client_id" to ToyotaConst.CLIENT_ID, "redirect_uri" to ToyotaConst.REDIRECT_URI, "code_verifier" to "plain")
        val form = (params + fixed).entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val headers = mapOf(
            "authorization" to ToyotaConst.BASIC_AUTH,
            "content-type" to "application/x-www-form-urlencoded",
            "user-agent" to ToyotaConst.USER_AGENT,
        )
        val r = Http.request("POST", ToyotaConst.TOKEN, headers, form.toByteArray())
        if (r.code == 429 || r.code >= 500) throw ToyotaError(r.code, null, "token endpoint ${r.code}")
        if (r.code != 200) throw ToyotaLoginError("Toyota rechazó el token (${r.code})")
        return tokensFromResponse(JSONObject(r.body), brand)
    }

    internal fun tokensFromResponse(o: JSONObject, brand: String): Tokens {
        val uuid = jwtClaim(o.getString("id_token"), "uuid") ?: throw ToyotaLoginError("id_token sin uuid")
        val expiresAt = System.currentTimeMillis() + o.getLong("expires_in") * 1000
        return Tokens(o.getString("access_token"), o.getString("refresh_token"), expiresAt, uuid, brand)
    }

    /** Reads one claim from a JWT payload without verifying the signature (same as pytoyoda). */
    internal fun jwtClaim(jwt: String, claim: String): String? {
        val payload = jwt.split('.').getOrNull(1) ?: return null
        val json = String(Base64.getUrlDecoder().decode(payload))
        return JSONObject(json).optString(claim).ifEmpty { null }
    }
}
