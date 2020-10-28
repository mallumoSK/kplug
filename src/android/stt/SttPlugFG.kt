package tk.mallumo.cordova.kplug.stt

import android.R
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.Px
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONObject
import tk.mallumo.cordova.kplug.fromJson
import tk.mallumo.cordova.kplug.toJson


private fun View.sp(value: Int) = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_SP,
    value.toFloat(),
    context.resources.displayMetrics
)

private fun View.dp(value: Int) = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    value.toFloat(),
    context.resources.displayMetrics
).toInt()

@Px
private fun View.px(value: Int) = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_PX,
    value.toFloat(),
    context.resources.displayMetrics
).toInt()

@ExperimentalCoroutinesApi
class SttDialog(
    val activity: Activity,
    val callbackContext: CallbackContext?,
    val sttDataHolder: SttDataHolder
) {
    private val dialogScope = MainScope()


    private lateinit var recognizer: SpeechRecognizer

    private var cachedResult = ""
    private var recognized = ""
    private var isClosing = false

    private val title = MutableStateFlow("")
    private val textExtra = MutableStateFlow("")
    private val closeDialog = MutableStateFlow(false)

    private fun <T> flow(flow: Flow<T>, body: (T) -> Unit) {
        dialogScope.launch(Dispatchers.Main) {
            flow.collect {
                activity.runOnUiThread {
                    body(it)
                }
            }
        }
    }

    companion object {
        private var instance: SttDialog? = null
        fun isActive() = instance != null
        fun dismiss() {
            instance?.closeDialog()
        }

        fun text(value: String) {
            instance?.textExtra?.value = value
        }
    }

    private fun closeDialog() {
        isClosing = true
        sttDataHolder.autoContinue = false
        kotlin.runCatching { recognizer.stopListening() }
        kotlin.runCatching { recognizer.cancel() }
        kotlin.runCatching { recognizer.destroy() }
        closeDialog.value = true
        instance = null

        sendInfo(
            ServiceSTT.RecognitionInfo(
                ServiceSTT.RecognitionInfo.State.DESTROYED,
            )
        )
    }

    fun show(): SttDialog {
        instance = this
        title.value = "Initializing"
        activity.runOnUiThread {
            val themeWrapper =  ContextThemeWrapper(
                activity,
                R.style.Theme_DeviceDefault_Light_DialogWhenLarge
            )
            val dialog = AlertDialog.Builder(themeWrapper)
                .setCancelable(false)
                .setView(
                    LinearLayout(activity).apply {

                        orientation = LinearLayout.VERTICAL
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        dp(20).also { dp20 ->
//                            setPadding(0, 0, 0, dp20)
                        }
                        addView(TextView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            gravity = Gravity.CENTER_HORIZONTAL
                            textSize = sp(18)
                            dp(20).also { dp20 ->
                                setPadding(dp20, dp20, dp20, dp20)
                            }
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(Color.BLACK)

                            flow(title) { text = it }
                        })
                        addView(ImageView(context).apply {
                            dp(80).also {
                                layoutParams = ViewGroup.LayoutParams(it, it)
                            }
                            dp(8).also {
                                setPadding(it, it, it, it)
                            }
                            gravity = Gravity.CENTER_HORIZONTAL
                            background = GradientDrawable().apply {
                                color = ColorStateList.valueOf(Color.BLUE)
                                shape = GradientDrawable.OVAL
                            }
                            setImageResource(R.drawable.ic_btn_speak_now)
                            imageTintList = ColorStateList.valueOf(Color.WHITE)
                            setOnClickListener {
                                sendInfo(
                                    ServiceSTT.RecognitionInfo(
                                        ServiceSTT.RecognitionInfo.State.RESULT_FINAL,
                                        listOf(recognized),
                                        recognized
                                    )
                                )
                                closeDialog()
                            }
                        })
                        addView(TextView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            gravity = Gravity.CENTER_HORIZONTAL
                            textSize = sp(12)
                            dp(20).also { dp20 ->
                                setPadding(dp20/2, dp20, dp20/2, dp20)
                            }
                            setTextColor(Color.BLACK)

                            flow(textExtra) { text = it }
                        })
                    })
                .show()

            flow(closeDialog) { if (it) dialog.dismiss() }
        }
        callbackContext?.sendPluginResult(PluginResult(PluginResult.Status.OK).apply {
            keepCallback = true
        })
        initRecognition()
        startRecognization()
        return this
    }

    private fun reinitDialog() {
        kotlin.runCatching { recognizer.stopListening() }
        kotlin.runCatching { recognizer.cancel() }
        kotlin.runCatching { recognizer.destroy() }
        initRecognition()
        startRecognization()
    }

    private fun startRecognization() {
        title.value = "Initializing"
        if (!isClosing) activity.runOnUiThread {
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, sttDataHolder.preferOffline)
                }
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sk-SK")
            })
        }
    }

    private fun initRecognition() {
        if (!isClosing) activity.runOnUiThread {
            recognizer = SpeechRecognizer.createSpeechRecognizer(activity).apply {
                setRecognitionListener(object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        title.value = "Počúvam"
                        sendInfo(ServiceSTT.RecognitionInfo(ServiceSTT.RecognitionInfo.State.READY))
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
                        if (isClosing == false && sttDataHolder.autoContinue && error in arrayOf(
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            )
                        ) {
                            reinitDialog()
                        } else {
                            sendInfo(
                                ServiceSTT.RecognitionInfo(
                                    ServiceSTT.RecognitionInfo.State.ERROR,
                                    error = error
                                )
                            )
                            closeDialog()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val out =
                            (results?.getStringArrayList("results_recognition")?.first() ?: "")
                                .let { "$cachedResult $it".trim() }

                        val state =
                            if (sttDataHolder.autoContinue) ServiceSTT.RecognitionInfo.State.RESULT_PARTIAL
                            else ServiceSTT.RecognitionInfo.State.RESULT_FINAL


                        sendInfo(
                            ServiceSTT.RecognitionInfo(
                                state,
                                listOf(out),
                                out
                            )
                        )

                        if (sttDataHolder.autoContinue && isClosing == false) {
                            cachedResult = out
                            reinitDialog()
                        } else {
                            closeDialog()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val out =
                            (partialResults?.getStringArrayList("results_recognition")?.first()
                                ?: "")
                                .let { "$cachedResult $it".trim() }
                        recognized =out
                        sendInfo(
                            ServiceSTT.RecognitionInfo(
                                ServiceSTT.RecognitionInfo.State.RESULT_PARTIAL,
                                listOf(out),
                                partialResults
                                    ?.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")
                                    ?.firstOrNull() ?: ""
                            )
                        )
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {
                    }

                })
            }
        }
    }

    private fun sendInfo(recognitionInfo: ServiceSTT.RecognitionInfo) {
        callbackContext?.sendPluginResult(
            PluginResult(
                PluginResult.Status.OK,
                JSONObject(recognitionInfo.toJson())
            ).apply {
                keepCallback = recognitionInfo.state != ServiceSTT.RecognitionInfo.State.DESTROYED
            })
    }


}


@ExperimentalCoroutinesApi
open class SttPlugFG : CordovaPlugin() {

    override fun execute(
        action: String?,
        args: JSONArray?,
        callbackContext: CallbackContext?
    ): Boolean {
        try {
            return when (action) {
                "start" -> {
                    if (SttDialog.isActive()) {
                        callbackContext?.error("Is already Active")
                    } else {
                        validatePermission(callbackContext, "android.permission.RECORD_AUDIO") {
                            SttDialog(
                                activity = cordova.activity,
                                callbackContext = callbackContext,
                                sttDataHolder = args?.getJSONObject(0)
                                    ?.fromJson()
                                    ?: SttDataHolder()
                            ).show()
                        }
                    }
                    true
                }
                "stop" -> {
                    dismiss(callbackContext)
                    true
                }
                "text" -> {
                    if (!SttDialog.isActive()) {
                        callbackContext?.error("Is not active")
                    } else {
                        SttDialog.text(args?.getString(0)?:"")
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

    private fun dismiss(callbackContext: CallbackContext?) {
        if (!SttDialog.isActive()) {
            callbackContext?.error("Is not active")
        } else {
            SttDialog.dismiss()
            callbackContext?.success()
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

    override fun onStop() {
        SttDialog.dismiss()
        super.onStop()
    }
}



