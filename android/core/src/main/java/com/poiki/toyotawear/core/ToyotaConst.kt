package com.poiki.toyotawear.core

/**
 * Static identifiers of the official MyToyota (EU) client, as published in the open-source
 * pytoyoda project (const.py / controller.py). Only the Toyota/Lexus realm ("tme") is supported.
 */
internal object ToyotaConst {
    const val API_BASE = "https://ctpa-oneapi.tceu-ctp-prd.toyotaconnectedeurope.io"
    private const val LOGIN_BASE = "https://b2c-login.toyota-europe.com"

    const val CLIENT_VERSION = "2.14.0"
    const val CLIENT_ID = "oneapp"
    const val REDIRECT_URI = "com.toyota.oneapp:/oauth2Callback"
    const val BASIC_AUTH = "basic b25lYXBwOm9uZWFwcA=="
    const val API_KEY = "tTZipv6liF74PwMfk9Ed68AQ0bISswwf3iHQdqcF"
    const val USER_AGENT = "okhttp/4.10.0"
    const val REGION = "EU"

    const val AUTHENTICATE = "$LOGIN_BASE/json/realms/root/realms/tme/authenticate?authIndexType=service&authIndexValue=oneapp"
    const val AUTHORIZE = "$LOGIN_BASE/oauth2/realms/root/realms/tme/authorize?client_id=oneapp&scope=openid+profile+write" +
        "&response_type=code&redirect_uri=com.toyota.oneapp:/oauth2Callback&code_challenge=plain&code_challenge_method=plain"
    const val TOKEN = "$LOGIN_BASE/oauth2/realms/root/realms/tme/access_token"

    const val VEHICLES = "/v2/vehicle/guid"
    const val STATUS = "/v1/vehicle/status"
    const val WAKE_STATUS = "/v1/remote/status"
    const val ELECTRIC = "/v1/vehicle/electric/status"
    const val TELEMETRY = "/v3/telemetry"
    const val LOCATION = "/v1/location"
    const val CLIMATE_STATUS = "/v1/vehicle/climate-status"
    const val CLIMATE_SETTINGS = "/v1/vehicle/climate-settings"
    const val CLIMATE_CONTROL = "/v2/remote/climate-control"
    const val COMMAND = "/v1/global/remote/command"
}
