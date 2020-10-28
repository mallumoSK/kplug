package tk.mallumo.cordova.kplug.stt

import android.content.pm.PackageManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONObject
import tk.mallumo.cordova.kplug.fromJson
import tk.mallumo.cordova.kplug.toJson
import java.util.concurrent.atomic.AtomicInteger


@ExperimentalCoroutinesApi
open class SttPlugBG : CordovaPlugin() {


    private fun registerSttPluginCallback(callbackContext: CallbackContext?) {
        sttPendingCallback = callbackContext
        sttPendingCallback?.sendPluginResult(PluginResult(PluginResult.Status.OK).apply {
            keepCallback = true
        })
    }

    override fun execute(
        action: String?,
        args: JSONArray?,
        callbackContext: CallbackContext?
    ): Boolean {
        try {
            return when (action) {
                "start" -> {
                    registerSttPluginCallback(callbackContext)
                    validatePermission(callbackContext, "android.permission.RECORD_AUDIO") {
                        sttStart(args?.getJSONObject(0)?.fromJson() ?: SttDataHolder())
                    }
                    true
                }
                "stop" -> {
                    callbackContext?.success()
                    validatePermission(callbackContext, "android.permission.RECORD_AUDIO") {
                        ServiceSTT.stop()
                    }
                    true
                }
                "stopForce" -> {
                    callbackContext?.success()
                    validatePermission(callbackContext, "android.permission.RECORD_AUDIO") {
                        ServiceSTT.stop(true)
                    }
                    true
                }
                "greet" -> {
                    callbackContext!!.success("WORKS : ${args?.get(0)}")
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            callbackContext?.error(e.message)
            return false
        }
    }


    private val permissionRC = 12548

    private var cordovaTask: (() -> Unit) = { }
    private var cordovaTaskCallback: CallbackContext? = null

    @Suppress("SameParameterValue")
    private fun validatePermission(
        cordovaCallback: CallbackContext?,
        vararg permissions: String,
        task: () -> Unit
    ) {
        if (permissions.all { cordova.hasPermission(it) }) {
            task.invoke()
        } else {
            cordovaTask = task
            cordovaTaskCallback = cordovaCallback
            cordova.requestPermissions(this, permissionRC, permissions)
        }
    }

    override fun onRequestPermissionResult(
        requestCode: Int,
        permissions: Array<out String>?,
        grantResults: IntArray?
    ) {
        if (requestCode == permissionRC) {
            if (grantResults?.all { it == PackageManager.PERMISSION_GRANTED } == true) {
                cordovaTask()
                cordovaTask = {}
                cordovaTaskCallback = null
            } else {
                cordovaTaskCallback?.sendPluginResult(
                    PluginResult(PluginResult.Status.ERROR)
                )
                cordovaTask = {}
                cordovaTaskCallback = null
            }
        } else {
            super.onRequestPermissionResult(requestCode, permissions, grantResults)
        }
    }


    private fun sttStart(holder: SttDataHolder) {
        ServiceSTT.start(cordova.context, holder.maxResults, holder.preferOffline)
        sttJob?.cancel()
        sttJob = GlobalScope.launch(Dispatchers.Main) {
            ServiceSTT.serviceState.collect {
                if (it.state != ServiceSTT.RecognitionInfo.State.IDLE) {
                    val isRunning = it.state != ServiceSTT.RecognitionInfo.State.DESTROYED
                    val result =
                        PluginResult(PluginResult.Status.OK, JSONObject(it.toJson())).apply {
                            keepCallback = isRunning
                        }
                    sttPendingCallback?.sendPluginResult(result)
                    if (!isRunning) {
                        sttPendingCallback = null
                        launch {
                            sttJob?.cancel()
                            sttJob = null
                        }
                        ServiceSTT.serviceState.value =
                            ServiceSTT.RecognitionInfo(ServiceSTT.RecognitionInfo.State.IDLE)
                    }
                }

            }
        }
    }

    private var sttPendingCallback: CallbackContext? = null
    private var sttJob: Job? = null

    override fun onStop() {
        sttJob?.cancel()
        sttJob = null
        val result = PluginResult(
            PluginResult.Status.OK,
            JSONObject(ServiceSTT.RecognitionInfo(ServiceSTT.RecognitionInfo.State.IDLE).toJson())
        ).apply {
            keepCallback = false
        }
        sttPendingCallback?.sendPluginResult(result)
        sttPendingCallback = null
        super.onStop()
    }
}






