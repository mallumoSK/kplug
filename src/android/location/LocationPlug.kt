package tk.mallumo.cordova.kplug.location

import android.Manifest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONObject
import tk.mallumo.cordova.kplug.fromJson
import tk.mallumo.cordova.kplug.toJson


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
                pendingCallback?.sendPluginResult(PluginResult(PluginResult.Status.OK, JSONObject(it.toJson())).apply {
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
                "start" -> {
                    val json = args?.getString(0) ?: "{}"
                    if (hasLocationPermission()) {
                        if (LocationService.start(cordova.context, json.fromJson())) {
                            callbackContext?.success()
                        } else {
                            callbackContext?.error("Location service already running")
                        }
                    } else {
                        callbackContext?.error("""
                            Location service require permissions:
                            android.permission.ACCESS_FINE_LOCATION
                            android.permission.ACCESS_COARSE_LOCATION
                        """.trimIndent())
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
                    val items = LocationDatabase.get(cordova.context).query(offset, limit, identifier)
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

    private fun hasLocationPermission(): Boolean {
        return cordova.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                && cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }


}