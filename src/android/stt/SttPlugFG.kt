package tk.mallumo.cordova.kplug.stt

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Html
import android.text.Spanned
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
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
import tk.mallumo.cordova.kplug.BottomDialog
import tk.mallumo.cordova.kplug.dp
import tk.mallumo.cordova.kplug.fromJson
import tk.mallumo.cordova.kplug.toJson


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
    private val buttonColor = MutableStateFlow("#C7C7C7")
    private val animColor = MutableStateFlow("#EEEEEE")
    private val animScale = MutableStateFlow(0F)

    //    private val textExtra = MutableStateFlow("")
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
            instance?.title?.value = value
        }

        fun color(buttonColor: String, animColor: String) {
            instance?.buttonColor?.value = buttonColor
            instance?.animColor?.value = animColor
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
        activity.runOnUiThread {

            val bottomDialog = object : BottomDialog(activity) {

                override fun peekHeight(): Int = context.dp(260)

                override fun onDismiss() {
                    sendInfo(
                            ServiceSTT.RecognitionInfo(
                                    ServiceSTT.RecognitionInfo.State.RESULT_FINAL,
                                    listOf(recognized),
                                    recognized
                            )
                    )
                    closeDialog()
                }

                override fun getContentView(): View {
                    return LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                dp(260)
                        )
                        setBackgroundColor(Color.WHITE)
                        dp(20).also { dp20 ->
                            setPadding(0, 0, 0, dp20)
                        }
                        titleTextView {
                            flow(title) { text = it.asHtml() }
                        }

                        addView(FrameLayout(context).apply {
                            dp(120).also {
                                layoutParams = ViewGroup.LayoutParams(it, it)
                            }
                            gravity = Gravity.CENTER_HORIZONTAL

                            animView {
                                flow(animColor) {
                                    background = GradientDrawable().apply {
                                        shape = GradientDrawable.OVAL
                                        color = try {
                                            ColorStateList.valueOf(Color.parseColor(it))
                                        } catch (e: Exception) {
                                            ColorStateList.valueOf(Color.YELLOW)
                                        }
                                    }
                                }
                                flow(animScale) {
                                    val factor = if (it <= 0) 1F
                                    else {
                                        ((minOf(it, 12F) / 0.12F) / 200F) + 1F
                                    }
                                    scaleX = factor
                                    scaleY = factor

                                }
                            }
                            cancelButton {
                                flow(buttonColor) {
                                    background = GradientDrawable().apply {
                                        shape = GradientDrawable.OVAL
                                        color = try {
                                            ColorStateList.valueOf(Color.parseColor(it))
                                        } catch (e: Exception) {
                                            ColorStateList.valueOf(Color.GREEN)
                                        }

                                    }
                                }
                                setOnClickListener {
                                    sendInfo(
                                            ServiceSTT.RecognitionInfo(
                                                    ServiceSTT.RecognitionInfo.State.BUTTON,
                                                    listOf(recognized),
                                                    recognized
                                            )
                                    )
                                    sendInfo(
                                            ServiceSTT.RecognitionInfo(
                                                    ServiceSTT.RecognitionInfo.State.RESULT_FINAL,
                                                    listOf(recognized),
                                                    recognized
                                            )
                                    )
                                    closeDialog()
                                }
                            }
                        })
                    }
                }
            }

            bottomDialog.show()
            flow(closeDialog) { if (it) bottomDialog.dismiss() }
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
//        title.value = "Initializing"
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
//                        title.value = "Počúvam"
                        sendInfo(ServiceSTT.RecognitionInfo(ServiceSTT.RecognitionInfo.State.READY))
                    }

                    override fun onBeginningOfSpeech() {
                    }
                    var rmsCounter = 0
                    override fun onRmsChanged(rmsdB: Float) {
                        rmsCounter+=1
                        if(rmsCounter>3){
                            animScale.value = rmsdB
                            rmsCounter= 0
                        }

                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                    }

                    override fun onEndOfSpeech() {

                    }

                    override fun onError(error: Int) {
                        if (!isClosing && sttDataHolder.autoContinue && error in arrayOf(
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

                        if (sttDataHolder.autoContinue && !isClosing) {
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
                        recognized = out
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


    private fun FrameLayout.cancelButton(body: ImageView.() -> Unit = {}) {
        addView(ImageView(context).apply {
            dp(80).also {
                layoutParams = FrameLayout.LayoutParams(it, it).apply {
                    gravity = Gravity.CENTER
                }
            }
            setImageResource(android.R.drawable.ic_btn_speak_now)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            body(this)
        })
    }

    private fun FrameLayout.animView(body: View.() -> Unit = {}) {
        addView(ImageView(context).apply {
            dp(80).also {
                layoutParams = FrameLayout.LayoutParams(it, it).apply {
                    gravity = Gravity.CENTER
                }
                pivotX = it.toFloat() / 2F
                pivotY = it.toFloat() / 2F
            }
            body(this)
        })
    }


    private fun LinearLayout.titleTextView(body: TextView.() -> Unit = {}) {
        addView(NestedScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(120)
            )
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT)
                dp(20).also { dp20 ->
                    setPadding(dp20, dp20, dp20, 0)
                }
                body(this)
            })
        })

    }
}


private fun String.asHtml(): Spanned =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(this, Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(this)
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
                        SttDialog.text(args?.getString(0) ?: "")
                    }
                    true
                }
                "color" -> {
                    if (!SttDialog.isActive()) {
                        callbackContext?.error("Is not active")
                    } else {
                        SttDialog.color(
                                args?.getString(0) ?: "#C7C7C7",
                                args?.getString(1) ?: "#EEEEEE"
                        )
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





