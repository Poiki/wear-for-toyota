package com.poiki.toyotawear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToyotaAuthTest {
    @Test
    fun pkceUsesS256AndFreshVerifiers() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", ToyotaAuth.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val first = ToyotaAuth.codeVerifier()
        val second = ToyotaAuth.codeVerifier()
        assertNotEquals(first, second)
        assertTrue(first.matches(Regex("[A-Za-z0-9_-]{86}")))
        assertTrue(second.matches(Regex("[A-Za-z0-9_-]{86}")))
        val url = ToyotaAuth.authorizeUrl(first)
        assertTrue(url.startsWith(ToyotaConst.AUTHORIZE + "&"))
        assertTrue(url.contains("code_challenge=${ToyotaAuth.codeChallenge(first)}&code_challenge_method=S256"))
    }

    @Test
    fun callbackRequiresTheExpectedRedirectAndOneSuccessfulCode() {
        val callback = ToyotaConst.REDIRECT_URI
        assertEquals("a+b", ToyotaAuth.codeFromLocation("$callback?code=a%2Bb&iss=https%3A%2F%2Fexample.invalid"))
        for (location in listOf(
            "https://other.invalid/?code=stolen", "$callback/other?code=a", "$callback?code=a&code=b",
            "$callback?error=denied&code=a", "$callback?code=", "$callback?code=%ZZ", "$callback?code=a#fragment",
        )) assertNull(location, ToyotaAuth.codeFromLocation(location))
    }
}
