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
        val dt: Long = System.currentTimeMillis(), //in case state of NEW_LOCATION is datetime generated by location provider
        val bearing: Float = 0.0f,
        val speed: Float = 0.0f) {

    enum class State {
        IDLE, // startup point
        NEW_LOCATION, // new location
        PROVIDER_ENABLED, // user enable location provider (GPS/NETWORK/OTHER)
        PROVIDER_DISABLED // user DISABLE location provider (GPS/NETWORK/OTHER)
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