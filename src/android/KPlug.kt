package tk.mallumo.cordova.kplug

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.os.bundleOf
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


@ExperimentalCoroutinesApi
open class KPlug : CordovaPlugin() {

    override fun execute(
        action: String?,
        args: JSONArray?,
        callbackContext: CallbackContext?
    ): Boolean {
        try {
            return when (action) {
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



}


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



