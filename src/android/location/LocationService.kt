package tk.mallumo.cordova.kplug.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import tk.mallumo.cordova.kplug.toJson

@SuppressLint("MissingPermission")
class LocationService : Service() {

    private var manager: LocationManager? = null
    private var request: LocationRequest? = null
    private lateinit var stopScope: CoroutineScope

    private val locationListener by lazy {
        LocationListenerCallback(this) {
            consumeLocation(it)
        }
    }

    companion object {
        private val _lastLocation =
            MutableStateFlow(LocationResponse(state = LocationResponse.State.IDLE))
        val lastLocation: Flow<LocationResponse> get() = _lastLocation

        private val _isRunning = MutableStateFlow(false)
        val isRunning: Flow<Boolean> get() = _isRunning

        fun start(context: Context, request: LocationRequest): Boolean {
            if (_isRunning.value) return false
            _isRunning.value = true
            Intent(context, LocationService::class.java).also {
                it.putExtra("json", request.toJson())

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(it)
                } else {
                    context.startService(it)
                }
            }
            return true
        }

        fun stop() {
            _isRunning.value = false
            _lastLocation.value = LocationResponse(state = LocationResponse.State.IDLE)
        }
    }

    override fun onCreate() {
        super.onCreate()
        manager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

//        arrayOf(
//                manager?.getLastKnownLocation(LocationManager.GPS_PROVIDER),
//                manager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER),
//                manager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER))
//                .filterNotNull()
//                .maxByOrNull { it.time }
//                ?.also {
//                    _lastLocation.value = it.ofLocationResponse(LocationResponse.State.LAST_LOCATION)
//                }

    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = intent?.getStringExtra("json")?.let {
            Gson().fromJson(it, LocationRequest::class.java)
        }
        registerNotification()
        if (request != null) {
            start(request)
        } else {
            stopSelf()
        }
        return START_STICKY

    }

    private fun consumeLocation(location: LocationResponse) {
        location.identifier = request?.identifier ?: ""
        _lastLocation.value = location
        saveLocation(location)
        if (!request?.url.isNullOrEmpty()) {
            Log.e("UPLOAD", "SCHEDULE")
            LocationUploader.exec(this)
        }
    }


    private fun saveLocation(location: LocationResponse) {
        request?.also {
            LocationDatabase.get(context = applicationContext)
                .insert(location, it.identifier, it.url, it.dataPrefix)
        }

    }

    private fun registerNotification() {
        startForeground(5544785, createNotification())
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, getChannelID()).apply {
            setSmallIcon(android.R.drawable.ic_menu_mylocation)
            priority = NotificationCompat.PRIORITY_MIN
            setAutoCancel(false)
            setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            setOngoing(true)
            setVisibility(NotificationCompat.VISIBILITY_SECRET)
            "Snímanie GPS pozície".also {
                setTicker(it)
                setContentTitle(it)
            }

        }.build()
    }

    private fun getChannelID(): String {
        val id = "GPS"
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

    private fun start(request: LocationRequest) {
        this.request = request
        manager?.requestLocationUpdates(
            request.minTimeMS,
            request.minDistanceM.toFloat(),
            request.criteria,
            locationListener,
            mainLooper
        )

        stopScope = MainScope()
        stopScope.launch(Dispatchers.Main) {
            isRunning.collect {
                if (!it) {
                    release()
                }
            }
        }
    }


    private fun release() {
        stopScope.cancel()
        manager?.runCatching {
            removeUpdates(locationListener)
        }
        manager = null
        stopForeground(true)
        stopSelf()
    }
}


