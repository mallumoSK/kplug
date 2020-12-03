package tk.mallumo.cordova.kplug.location

import android.location.Criteria

data class LocationRequest(
    /**
     * If you want upload locations to remote server by POST request, paste here url
     */
    val url: String = "",
    /**
     * If you uploading data, this prefix will be added on start
     */
    val dataPrefix: String = "",
    /**
     * Identifier will be forwarded with received location
     */
    val identifier: String = "",
    /**
     * minimal time between 2 location request in milliseconds
     */
    val minTimeMS: Long = 0L,
    /**
     * minimal distance between 2 location request in meters
     */
    val minDistanceM: Long = 0,
    /**
     * Indicates the desired horizontal accuracy (latitude and longitude)
     *
     * * 0 -> no accuracy
     * * 1 -> LOW (greater than 500 meters)
     * * 2 -> MEDIUM (between 100 and 500 meters)
     * * 3 -> HIGH (less than 100 meters)
     */
    var horizontalAccuracy: Int = 0,
    /**
     * Indicates the desired vertical accuracy (altitude)
     *
     * * 0 -> no accuracy
     * * 1 -> LOW (greater than 500 meters)
     * * 2 -> MEDIUM (between 100 and 500 meters)
     * * 3 -> HIGH (less than 100 meters)
     */
    val verticalAccuracy: Int = 0,
    /**
     * Indicates the desired speed accuracy
     *
     * * 0 -> no accuracy
     * * 1 -> LOW (greater than 500 meters)
     * * 3 -> HIGH (less than 100 meters)
     */
    val speedAccuracy: Int = 0,
    /**
     * Indicates the desired bearing accuracy
     *
     * * 0 -> no accuracy
     * * 1 -> LOW (greater than 500 meters)
     * * 3 -> HIGH (less than 100 meters)
     */
    val bearingAccuracy: Int = 0,
    /**
     * Indicates the desired maximum power level
     *
     * * 0 -> any
     * * 1 -> LOW  low power requirement
     * * 2 -> MEDIUM  medium power requirement
     * * 3 -> HIGH  high power requirement
     */
    val powerRequirement: Int = 0,
    /**
     * Indicates whether the provider must provide altitude information
     */
    val altitudeRequired: Boolean = false,
    /**
     * Indicates whether the provider must provide bearing information
     */
    val bearingRequired: Boolean = false,
    /**
     * Indicates whether the provider must provide speed information
     */
    val speedRequired: Boolean = false,
    /**
     * Indicates whether the provider is allowed to incur monetary cost
     */
    val costAllowed: Boolean = false
)

val LocationRequest.criteria: Criteria
    get() = Criteria().apply {
        this.horizontalAccuracy = horizontalAccuracy
        this.verticalAccuracy = verticalAccuracy
        this.speedAccuracy = speedAccuracy
        this.bearingAccuracy = bearingAccuracy
        this.powerRequirement = powerRequirement
        this.isAltitudeRequired = altitudeRequired
        this.isBearingRequired = bearingRequired
        this.isSpeedRequired = speedRequired
        this.isCostAllowed = costAllowed
    }