package tk.mallumo.cordova.kplug.location

import android.location.Location

data class LocationResponse(
        var identifier: String = "",
        val state: State,
        val provider: String = "unknown",
        val accuracy: Float = 0.0f,
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val altitude: Double = 0.0,
        val dt: Long = System.currentTimeMillis(),
        val bearing: Float = 0.0f,
        val speed: Float = 0.0f) {

    enum class State {
        IDLE,
        NEW_LOCATION,
        PROVIDER_ENABLED,
        PROVIDER_DISABLED
    }
}

fun Location.ofLocationResponse(state: LocationResponse.State) = LocationResponse(
        state = state,
        accuracy = accuracy,
        lat = latitude,
        lon = longitude,
        dt = time,
        altitude = altitude,
        bearing = bearing,
        speed = speed,
        provider = provider ?: "unknown")