package com.poiki.toyotawear.core

import org.json.JSONObject

/** V2 accepts seat modes, not the low/medium/high levels returned by the read API. */
object Climate {
    enum class Option(val group: String, val key: String, val capability: String, val ventilation: String? = null) {
        Front("heatingOptions", "frontDefroster", "frontDefogger"),
        Rear("heatingOptions", "rearDefogger", "rearDefogger"),
        Wheel("heatingOptions", "steeringHeater", "steeringHeater"),
        Driver("seatOptions", "driverSeat", "frontDriverSeatHeater", "frontDriverSeatVentilation"),
        Passenger("seatOptions", "passengerSeat", "frontPassengerSeatHeater", "frontPassengerSeatVentilation"),
        RearDriver("seatOptions", "rearDriverSeat", "rearDriverSeatHeater", "rearDriverSeatVentilation"),
        RearPassenger("seatOptions", "rearPassengerSeat", "rearPassengerSeatHeater", "rearPassengerSeatVentilation"),
    }

    data class Choice(val option: Option, val modes: List<String>, val value: String?)

    private fun mode(option: Option, value: Any?): String? = if (option.group == "seatOptions") {
        when (value) {
            "off", "heater", "ventilation" -> value as String
            "low", "medium", "high" -> "heater"
            else -> null
        }
    } else when (value) {
        "on", true -> "on"
        "off", false -> "off"
        else -> null
    }

    fun value(settings: JSONObject?, option: Option): String? = mode(option, settings?.optJSONObject(option.group)?.opt(option.key))

    /** A reported field is evidence of support; an explicit capability=false takes precedence. */
    fun choices(vehicle: JSONObject, settings: JSONObject): List<Choice> {
        val caps = vehicle.optJSONObject("extendedCapabilities")
        return Option.entries.mapNotNull { option ->
            val current = value(settings, option)
            val heat = caps?.opt(option.capability)
            val vent = option.ventilation?.let { caps?.opt(it) }
            val modes = mutableListOf("off")
            if (heat == true || (heat != false && current != null && current != "ventilation")) {
                modes += if (option.ventilation == null) "on" else "heater"
            }
            if (option.ventilation != null && (vent == true || (vent != false && current == "ventilation"))) modes += "ventilation"
            if (modes.size > 1) Choice(option, modes, current?.takeIf { it in modes }) else null
        }
    }

    /** Copies only recognized fields; callers can stage choices without mutating the saved Toyota defaults. */
    fun settings(saved: JSONObject?, choices: List<Choice> = emptyList()): JSONObject {
        val out = JSONObject()
        for (option in Option.entries) {
            val desired = choices.firstOrNull { it.option == option }?.value ?: value(saved, option)
            val normalized = mode(option, desired) ?: continue
            val group = out.optJSONObject(option.group) ?: JSONObject().also { out.put(option.group, it) }
            group.put(option.key, normalized)
        }
        return out
    }

    fun request(start: Boolean, tempC: Double, durationMin: Int, saved: JSONObject?): JSONObject {
        val out = JSONObject().put("command", if (start) "start" else "stop")
        if (!start) return out
        require(tempC.isFinite() && tempC in 18.0..29.0 && durationMin in 1..20)
        out.put("temperature", JSONObject().put("unit", "C").put("value", tempC))
            .put("duration", durationMin).put("saveSettings", false)
        val normalized = settings(saved)
        for (group in listOf("heatingOptions", "seatOptions")) normalized.optJSONObject(group)?.let { out.put(group, it) }
        return out
    }

    /** Missing or failed reads never confirm execution, including stop. */
    fun confirmed(start: Boolean, desired: JSONObject?, status: JSONObject?): Boolean {
        val state = status?.optString("status")
        if (!start) return state == "stopped" || state == "stopping"
        if (state != "running" && state != "starting") return false
        return Option.entries.all { option ->
            val expected = value(desired, option)
            expected == null || value(status, option) == expected
        }
    }
}
