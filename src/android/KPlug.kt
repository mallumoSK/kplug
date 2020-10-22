package tk.mallumo.cordova.kplug

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.os.bundleOf
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


@ExperimentalCoroutinesApi
open class KPlug : CordovaPlugin() {


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
                "stt" -> {
                    registerSttPluginCallback(callbackContext)
                    validatePermission(callbackContext, "android.permission.RECORD_AUDIO") {
                        sttStart(args?.getJSONObject(0)?.fromJson() ?: SttDataHolder())
                    }
                    true
                }
                "sttStop" -> {
                    callbackContext?.success()
                    validatePermission(callbackContext, "android.permission.RECORD_AUDIO") {
                        ServiceSTT.stop()
                    }
                    true
                }
                "sttStopForce" -> {
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
                "scheduleCancel" -> {
                    val key = args?.getString(0)
                    if (key.isNullOrEmpty()) {
                        callbackContext?.error("invalid key of scheduled task")
                    } else {
                        SchedulerWorker.cancelTask(cordova.context.applicationContext, key)
                        callbackContext?.success()
                    }
                    true
                }
                "schedule" -> {
                    Log.e("schedule:", args?.getJSONObject(0)?.toString()?.toJson() ?: "nope")

                    val data = args?.getJSONObject(0).fromJson<SchedulerDataHolder>()
                    SchedulerWorker.schedule(cordova.context.applicationContext, data)
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

data class SttDataHolder(var preferOffline: Boolean = false, var maxResults: Int = 10)

class SchedulerWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    companion object {
        fun cancelTask(ctx: Context, id: String) {
            WorkManager.getInstance(ctx).cancelUniqueWork(id)
        }

        fun schedule(ctx: Context, data: SchedulerDataHolder) {
            WorkManager.getInstance(ctx)
                .enqueueUniqueWork(
                    data.id,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequest.Builder(SchedulerWorker::class.java)
                        .apply {
                            if (data.time > System.currentTimeMillis() - 5 * 60 * 1000) {
                                Log.e("delay", (data.time - System.currentTimeMillis()).toString())
                                setInitialDelay(
                                    data.time - System.currentTimeMillis(),
                                    TimeUnit.MILLISECONDS
                                )
                            } else {
                                Log.e("delay", "now")
                            }
                        }
                        .setInputData(
                            Data.Builder()
                                .putString("json", data.toJson())
                                .build()
                        )
                        .build()
                )
        }
    }


    override fun doWork(): Result {
        val data = inputData.getString("json")!!.fromJson<SchedulerDataHolder>()
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(manager, data.channel, data.channelImportance)

        var defaults = 0
        if (data.sound) defaults = defaults or NotificationCompat.DEFAULT_SOUND
        if (data.led) defaults = defaults or NotificationCompat.DEFAULT_LIGHTS
        if (data.vibrate) defaults = defaults or NotificationCompat.DEFAULT_VIBRATE

        val builder = NotificationCompat.Builder(applicationContext, data.channel)
        builder.apply {
            setTicker(data.title)
            setContentTitle(data.title)
            setContentText(data.subtitle)
            setContentIntent(data.contentAction.createPI(applicationContext))
            setSmallIcon(data.getImageRes(applicationContext))
            setDefaults(defaults)
            setPriority(data.priority)
            setAutoCancel(true)
            if (data.color.isNotEmpty()) {
                setColorized(true)
                color = Color.parseColor(data.color)
            }
            data.actions.forEach {
                addAction(
                    NotificationCompat.Action.Builder(
                        it.getImageRes(applicationContext),
                        it.title,
                        it.createPI(applicationContext)
                    )
                        .build()
                )
            }
        }
        manager.notify(atomicRequestCode.getAndIncrement(), builder.build())
        return Result.success()
    }

    @SuppressLint("WrongConstant")
    private fun createChannel(
        manager: NotificationManager,
        channel: String,
        channelImportance: Int
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nChannel = manager.getNotificationChannel(channel)
            val recreateChannel =
                if (nChannel == null || nChannel.importance != channelImportance) {
                    if (nChannel != null) manager.deleteNotificationChannel(channel)
                    true
                } else false

            if (recreateChannel) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        channel,
                        channel,
                        channelImportance
                    )
                )
            }
        }
    }

}

private val atomicRequestCode = AtomicInteger(100)

class KPluginBroadcast : BroadcastReceiver() {

    companion object {
        fun getPI(
            ctx: Context,
            activityPck: String,
            activityClass: String,
            broadcastKey: String,
            params: Map<String, String>
        ): PendingIntent {
            return PendingIntent.getBroadcast(
                ctx,
                atomicRequestCode.getAndIncrement(),
                Intent(ctx, KPluginBroadcast::class.java).apply {
                    putExtra("__pck", activityPck)
                    putExtra("__class", activityClass)
                    putExtra("__broadcast", broadcastKey)
                    putExtra("params", bundleOf(*params.map { it.key to it.value }.toTypedArray()))
                },
                PendingIntent.FLAG_CANCEL_CURRENT
            )
        }
    }

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context?, intent: Intent?) {

        Log.e("onReceive", "onReceiveonReceiveonReceiveonReceive")
        if (intent == null || context == null) return

        val _package = intent.getStringExtra("__pck") ?: ""
        val _class = intent.getStringExtra("__class") ?: ""
        val _broadcast = intent.getStringExtra("__broadcast") ?: "kplug-default"
        val params = intent.getBundleExtra("params")

        val activityIntent = if (_package.isNotEmpty() && _class.isNotEmpty()) {
            Intent.makeMainActivity(ComponentName(_package, _class))
        } else {
            Intent.makeMainActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.component)
        }
        val broadcastIntent = Intent(_broadcast)
        activityIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        params?.keySet()?.forEach {
            activityIntent.putExtra(it, params.getString(it) ?: "")
            broadcastIntent.putExtra(it, params.getString(it) ?: "")
        }

        LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent)

        context.startActivity(activityIntent)

    }

}

data class SchedulerDataHolder(
    var id: String = System.currentTimeMillis().toString(),
    var channel: String = "default",
    var channelImportance: Int = 3,//NotificationManager.IMPORTANCE_DEFAULT,
    var priority: Int = NotificationCompat.PRIORITY_MAX,
    var img: String = "",
    var title: String = "",
    var subtitle: String = "",

    var color: String = "",
    var vibrate: Boolean = true,
    var sound: Boolean = true,
    var led: Boolean = true,
    var time: Long = System.currentTimeMillis(),

    var contentAction: Action = Action(),
    var actions: List<Action> = listOf()
) {
    fun getImageRes(ctx: Context): Int {
        val parts = img.split(".")
        return try {
            ctx.resources.getIdentifier(parts[1], parts[0], ctx.packageName).also {
                if (it < 1) throw Exception("invalid identifier")
            }
        } catch (e: Exception) {
            Log.e("notification icon", "Invalid notification icon id : $img")
            android.R.drawable.stat_notify_chat
        }
    }

    data class Action(
        var activityPck: String = "",
        var activityClass: String = "",
        var broadcastKey: String = "kplug-default",
        var img: String = "",
        var title: String = "action",
        var params: Map<String, String> = mapOf()
    ) {

        fun createPI(ctx: Context): PendingIntent? {

            return KPluginBroadcast.getPI(ctx, activityPck, activityClass, broadcastKey, params)
        }

        fun getImageRes(ctx: Context): Int {
            val parts = img.split(".")
            return try {
                ctx.resources.getIdentifier(parts[1], parts[0], ctx.packageName).also {
                    if (it < 1) throw Exception("invalid identifier")
                }
            } catch (e: Exception) {
                Log.e("notification icon", "Invalid notification icon id : $img")
                android.R.drawable.stat_notify_chat
            }
        }
    }
}


@OptIn(ExperimentalCoroutinesApi::class)
class ServiceSTT : Service() {

    private lateinit var recognizer: SpeechRecognizer

    override fun onBind(intent: Intent?): IBinder? = null

    enum class InternalState {
        IDLE,
        ACTIVE,
        FINISHING,
        FINISHED
    }

    companion object {

        val serviceState = MutableStateFlow(RecognitionInfo(RecognitionInfo.State.IDLE))

        private val internalState = MutableStateFlow(InternalState.IDLE)

        fun stop(force: Boolean = false) {
            internalState.value =
                if (force) InternalState.FINISHED
                else InternalState.FINISHING
        }

        fun start(context: Context, maxResults: Int, preferOffline: Boolean) {
            Intent(context, ServiceSTT::class.java).also {
                it.putExtra("maxResults", maxResults)
                it.putExtra("preferOffline", preferOffline)

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(it)
                } else {
                    context.startService(it)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        internalState.value = InternalState.ACTIVE
        showNotification()
        initStateCallback(
            intent?.getIntExtra("maxResults", 10) ?: 10,
            intent?.getBooleanExtra("preferOffline", false) ?: false
        )

        return START_STICKY
    }

    private var workerJob: Job? = null

    private fun initStateCallback(maxResults: Int, preferOffline: Boolean) {
        workerJob = GlobalScope.launch(Dispatchers.Main) {
            internalState.collect { state ->
                when (state) {
                    InternalState.ACTIVE -> initRecognition(maxResults, preferOffline)
                    InternalState.FINISHING -> recognizer.stopListening()
                    InternalState.FINISHED -> stopSelf()
                    else -> {
                    }
                }
            }
        }
    }


    private fun showNotification() {
        startForeground(5544685, createNotification())
    }


    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, getChannelID()).apply {
            setSmallIcon(android.R.drawable.ic_btn_speak_now)
            priority = NotificationCompat.PRIORITY_MIN
            "Počúvam".also {
                setTicker(it)
                setContentTitle(it)
            }

        }.build()
    }

    private fun getChannelID(): String {
        val id = "Speach to text"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)!!.also { manager ->
                if (manager.notificationChannels.none { it.id == id }) {
                    manager.createNotificationChannel(
                        NotificationChannel(
                            id,
                            id,
                            NotificationManager.IMPORTANCE_MIN
                        )
                    )
                }

            }
        }
        return id
    }


    private fun initRecognition(maxResults: Int, preferOffline: Boolean) {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    sendInfo(RecognitionInfo(RecognitionInfo.State.READY))
                }

                override fun onBeginningOfSpeech() {
                }

                override fun onRmsChanged(rmsdB: Float) {

                }

                override fun onBufferReceived(buffer: ByteArray?) {
                }

                override fun onEndOfSpeech() {
                }

                override fun onError(error: Int) {
                    sendInfo(RecognitionInfo(RecognitionInfo.State.ERROR, error = error))
                    internalState.value = InternalState.FINISHED
                }

                override fun onResults(results: Bundle?) {
                    sendInfo(
                        RecognitionInfo(
                            RecognitionInfo.State.RESULT_FINAL,
                            results?.getStringArrayList("results_recognition") ?: listOf(),
                            results?.getStringArrayList("results_recognition")?.firstOrNull() ?: ""
                        )
                    )
                    internalState.value = InternalState.FINISHED
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    sendInfo(
                        RecognitionInfo(
                            RecognitionInfo.State.RESULT_PARTIAL,
                            partialResults?.getStringArrayList("results_recognition") ?: listOf(),
                            partialResults?.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")
                                ?.firstOrNull() ?: ""
                        )
                    )
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                }

            })

            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                }
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, maxResults)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sk-SK")
            })
        }
    }


    data class RecognitionInfo(
        val state: State,
        val results: List<String> = listOf(),
        val unstable: String = "",
        val error: Int = 0
    ) {
        enum class State {
            IDLE,
            ERROR,
            READY,
            RESULT_PARTIAL,
            RESULT_FINAL,
            DESTROYED
        }
    }

    private fun sendInfo(info: RecognitionInfo) {
        serviceState.value = info
    }


    override fun onDestroy() {
        stopForeground(true)
        release()
        internalState.value = InternalState.IDLE
        workerJob?.cancel()
        sendInfo(RecognitionInfo(RecognitionInfo.State.DESTROYED))
        super.onDestroy()
    }

    private fun release() {
        if (::recognizer.isInitialized) {
            try {
                recognizer.stopListening()
            } catch (e: Exception) {
            }
            try {
                recognizer.cancel()
            } catch (e: Exception) {
            }
            try {
                recognizer.destroy()
            } catch (e: Exception) {
            }
        }
    }
}

inline fun <reified T> JSONObject?.fromJson(): T {
    this ?: throw Exception("Invalid json object data")
    return toString().fromJson()
}

inline fun <reified T> String.fromJson(): T {
    return Gson().fromJson<T>(this, T::class.java)
}

private fun Any.toJson(): String {
    return Gson().toJson(this)
}





