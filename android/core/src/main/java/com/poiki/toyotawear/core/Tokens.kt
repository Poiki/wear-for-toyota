package com.poiki.toyotawear.core

import org.json.JSONObject

data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
    val uuid: String,
    val brand: String,
) {
    fun isFresh(marginMs: Long = 5 * 60_000L) = System.currentTimeMillis() + marginMs < expiresAtMs

    fun toJson(): String = JSONObject()
        .put("accessToken", accessToken)
        .put("refreshToken", refreshToken)
        .put("expiresAtMs", expiresAtMs)
        .put("uuid", uuid)
        .put("brand", brand)
        .toString()

    companion object {
        fun fromJson(s: String): Tokens {
            val o = JSONObject(s)
            return Tokens(
                o.getString("accessToken"),
                o.getString("refreshToken"),
                o.getLong("expiresAtMs"),
                o.getString("uuid"),
                o.optString("brand").ifEmpty { "T" },
            )
        }
    }
}

/** Toyota's backend answered with an error. [apiCode] is Toyota's own code (APIGW-403, CTP-REMOTE-40006...) when present. */
class ToyotaError(val httpCode: Int, val apiCode: String?, message: String) : Exception(message)

/** Login or token refresh was rejected: the user has to link the watch again from the phone. */
class ToyotaLoginError(message: String) : Exception(message)
