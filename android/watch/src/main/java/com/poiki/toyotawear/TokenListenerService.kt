package com.poiki.toyotawear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.poiki.toyotawear.core.Tokens
import org.json.JSONObject

/** Receives the tokens the phone app sends once after the user logs in there, plus the password if they chose to save it. */
class TokenListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != TOKENS_PATH) return
        Store.init(this)
        runCatching {
            val json = String(event.data)
            Store.saveTokens(Tokens.fromJson(json))
            Store.error.value = null
            val o = JSONObject(json)
            // A locked watch refuses the strict key: the password is then not kept, and the app says so.
            if (!Store.saveCredentials(o.optString("email"), o.optString("password").ifEmpty { null })) {
                Store.error.value = getString(R.string.err_save_password)
            }
        }
    }

    companion object {
        const val TOKENS_PATH = "/toyota/tokens"
    }
}
