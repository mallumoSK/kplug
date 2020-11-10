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
            return if(!cordova.hasPermission("android.permission.RECORD_AUDIO")){
                callbackContext?.error("require permission: android.permission.RECORD_AUDIO")
                true
            } else  return when (action) {
                "start" -> {
                    registerSttPluginCallback(callbackContext)
                    sttStart(args?.getJSONObject(0)?.fromJson() ?: SttDataHolder())
                    true
                }
                "stop" -> {
                    callbackContext?.success()
                    ServiceSTT.stop()
                    true
                }
                "stopForce" -> {
                    callbackContext?.success()
                    ServiceSTT.stop(true)
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






