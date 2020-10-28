package tk.mallumo.cordova.kplug.stt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect


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
