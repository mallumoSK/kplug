package tk.mallumo.cordova.kplug.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONObject
import tk.mallumo.cordova.kplug.fromJson
import tk.mallumo.cordova.kplug.toJson

data class LocationInfoState(
    val gps: Boolean,
    val network: Boolean,
    val passive: Boolean
)

@ExperimentalCoroutinesApi
open class LocationPlug : CordovaPlugin() {

    private var pendingCallback: CallbackContext? = null
    private lateinit var scope: CoroutineScope

    private fun registerPluginCallback(callbackContext: CallbackContext?) {
        pendingCallback = callbackContext
        pendingCallback?.sendPluginResult(PluginResult(PluginResult.Status.OK).apply {
            keepCallback = true
        })
        if (::scope.isInitialized) {
            scope.runCatching {
                cancel()
            }
        }
        scope = MainScope()
        scope.launch(Dispatchers.Main) {
            LocationService.lastLocation.collect {
                pendingCallback?.sendPluginResult(
                    PluginResult(
                        PluginResult.Status.OK,
                        JSONObject(it.toJson())
                    ).apply {
                        keepCallback = true
                    })
            }
        }
    }

    private fun unregisterPluginCallback() {
        pendingCallback?.sendPluginResult(PluginResult(PluginResult.Status.OK).apply {
            keepCallback = false
        })
        pendingCallback = null
        if (::scope.isInitialized) {
            scope.runCatching {
                cancel()
            }
        }
    }

    override fun execute(
        action: String?,
        args: JSONArray?,
        callbackContext: CallbackContext?
    ): Boolean {
        try {
            return when (action) {
                "isEnabled" -> {
                    callbackContext?.success(JSONObject(locationInfoState().toJson()))
                    true
                }
                "enable" -> {
                    val priority = when (args?.getInt(0) ?: 0) {
                        0 -> com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
                        1 -> com.google.android.gms.location.LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
                        2 -> com.google.android.gms.location.LocationRequest.PRIORITY_LOW_POWER
                        else -> com.google.android.gms.location.LocationRequest.PRIORITY_NO_POWER

                    }
                    enableGPS(priority, callbackContext)
                    true
                }
                "start" -> {
                    val json = args?.getString(0) ?: "{}"
                    if (hasLocationPermission()) {
                        if (LocationService.start(cordova.context, json.fromJson())) {
                            callbackContext?.success()
                        } else {
                            callbackContext?.error("Location service already running")
                        }
                    } else {
                        callbackContext?.error(
                            """
                            Location service require permissions:
                            android.permission.ACCESS_FINE_LOCATION
                            android.permission.ACCESS_COARSE_LOCATION
                        """.trimIndent()
                        )
                    }
                    true
                }
                "callback" -> {
                    registerPluginCallback(callbackContext)
                    true
                }
                "stop" -> {
                    LocationService.stop()
                    unregisterPluginCallback()
                    callbackContext?.success()
                    true
                }
                "last" -> {
                    LocationDatabase.get(cordova.context)
                        .last()
                        .also { item ->
                            if (item != null) {
                                callbackContext?.success(JSONObject(item.toJson()))
                            } else {
                                callbackContext?.error("NO GPS POSITION")
                            }
                        }
                    true
                }
                "query" -> {
                    val identifier = args?.getString(0) ?: ""
                    val offset = args?.getInt(1) ?: 0
                    val limit = args?.getInt(2) ?: 1000
                    val items =
                        LocationDatabase.get(cordova.context).query(offset, limit, identifier)
                    callbackContext?.success(JSONArray(items.toJson()))
                    true
                }
                "clear" -> {
                    val identifier = args?.getString(0) ?: ""
                    LocationDatabase.get(cordova.context).clear(identifier)
                    callbackContext?.success()
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            callbackContext?.error(e.message)
            return false
        }
    }

    private fun locationInfoState(): LocationInfoState {
        val manager =
            cordova.context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val state = LocationInfoState(
            gps = manager.isProviderEnabled(LocationManager.GPS_PROVIDER),
            network = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER),
            passive = manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)
        )
        return state
    }

    private var gpsEnableCallback: CallbackContext? = null

    private fun enableGPS(priority: Int, callbackContext: CallbackContext?) {
        val settings = LocationServices.getSettingsClient(cordova.context)
        gpsEnableCallback = callbackContext
        gpsEnableCallback?.sendPluginResult(PluginResult(PluginResult.Status.OK).apply {
            keepCallback = true
        })
        val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
            this.priority = priority
            interval = 10 * 1000
            fastestInterval = 2 * 1000
        }

        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .build()

        settings.checkLocationSettings(settingsRequest)
            .addOnSuccessListener {
                gpsEnableCallback?.sendPluginResult(
                    PluginResult(
                        PluginResult.Status.OK,
                        JSONObject(locationInfoState().toJson())
                    ).apply {
                        keepCallback = false
                    })
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(cordova.activity, 8746)
                    } catch (e: Exception) {
                        gpsEnableCallback?.sendPluginResult(
                            PluginResult(
                                PluginResult.Status.ERROR,
                                e.message
                            ).apply {
                                keepCallback = false
                            })
                    }
                } else {
                    gpsEnableCallback?.sendPluginResult(
                        PluginResult(
                            PluginResult.Status.ERROR,
                            exception.message
                        ).apply {
                            keepCallback = false
                        })
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        when (requestCode) {
            8746 -> {
                gpsEnableCallback?.sendPluginResult(
                    PluginResult(
                        PluginResult.Status.OK,
                        JSONObject(locationInfoState().toJson())
                    ).apply {
                        keepCallback = false
                    })
                gpsEnableCallback = null
            }
            else -> super.onActivityResult(requestCode, resultCode, intent)
        }

    }

    private fun hasLocationPermission(): Boolean {
        return cordova.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                && cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}