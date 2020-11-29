package tk.mallumo.cordova.kplug.stt

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Html
import android.text.Spanned
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.widget.NestedScrollView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
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

        fun resetText() {
            instance?.resetText()
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
                ServiceSTT.RecognitionInfo.State.DESTROYED
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
                    return RelativeLayout(activity).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(260)
                        )
                        contentLayout {
                            titleTextView {
                                flow(title) { text = it.asHtml() }
                            }
                            actionFrame {
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
                                commitButton {
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
                            }
                        }
                        cancelButton {
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
                        }
                    }

                }
            }

            bottomDialog.show()
            flow(closeDialog) {
                if (it) {
                    bottomDialog.dismiss()
                    soundStart()
                }
            }
        }
        callbackContext?.sendPluginResult(PluginResult(PluginResult.Status.OK).apply {
            keepCallback = true
        })
        initRecognition()
        soundStop()
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

    private val soundStreamType = AudioManager.STREAM_NOTIFICATION

    fun soundStart() {
        if (sttDataHolder.enableStartStopSound) return

        val app = activity.applicationContext
        GlobalScope.launch(Dispatchers.Main) {
            delay(1000)
            val manager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
//            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
//                if (manager.isStreamMute(soundStreamType)) {
//                    manager.adjustStreamVolume(soundStreamType, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_VIBRATE)
//                }
//            } else {
            @Suppress("DEPRECATION")
            manager.setStreamMute(soundStreamType, false)
//            }
        }

    }

    fun soundStop() {
        if (sttDataHolder.enableStartStopSound) return

        val manager =
            activity.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
//            Log.e("soundStop 1", manager.isStreamMute(soundStreamType).toString())
//            if (!manager.isStreamMute(soundStreamType)) {
//
//                manager.adjustStreamVolume(soundStreamType, AudioManager.ADJUST_MUTE, AudioManager.FLAG_VIBRATE)
//            }
//        } else {
        @Suppress("DEPRECATION")
        manager.setStreamMute(soundStreamType, true)
//        }
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

    private fun LinearLayout.actionFrame(body: FrameLayout.() -> Unit) {
        addView(FrameLayout(context).apply {
            dp(120).also {
                layoutParams = ViewGroup.LayoutParams(it, it)
            }
            gravity = Gravity.CENTER_HORIZONTAL
            body(this)
        })
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
                        rmsCounter += 1
                        if (rmsCounter > 3) {
                            animScale.value = rmsdB
                            rmsCounter = 0
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
                        val out = extractResultsRecognition(results)

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

                    private fun extractResultsRecognition(results: Bundle?): String {
                        return (results?.getStringArrayList("results_recognition")?.first() ?: "")
                            .let { "$cachedResult $it".trim() }
                            .let {
                                when {
                                    trimIndexCachedResultText == 0 -> it
                                    trimIndexCachedResultText <= it.length -> {
                                        it.substring(trimIndexCachedResultText, it.length)
                                    }
                                    else -> ""

                                }
                            }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val out = extractResultsRecognition(partialResults)
                        recognized = out
                        sendInfo(
                            ServiceSTT.RecognitionInfo(
                                ServiceSTT.RecognitionInfo.State.RESULT_PARTIAL,
                                listOf(out),
                                extractPartialResultsRecognition(partialResults)
                            )
                        )
                    }

                    private fun extractPartialResultsRecognition(partialResults: Bundle?): String {
                        return partialResults
                            ?.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")
                            ?.firstOrNull() ?: ""
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {
                    }

                })
            }
        }
    }

    var trimIndexCachedResultText = 0

    private fun resetText() {
        trimIndexCachedResultText =
            if (cachedResult.isNotEmpty()) cachedResult.length - 1
            else 0
        recognized = ""
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


    private fun RelativeLayout.contentLayout(body: LinearLayout.() -> Unit) {
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
            dp(20).also { dp20 ->
                setPadding(0, 0, 0, dp20)
            }
            body(this)
        })
    }
}

private fun RelativeLayout.cancelButton(body: ImageView.() -> Unit) {
    addView(
        ImageView(
            ContextThemeWrapper(
                context,
                android.R.style.Widget_Material_Button_Borderless
            )
        ).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE)
            }
            dp(8).also {
                setPadding(it, it, it, it)
            }
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(Color.GRAY)
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            body(this)
        })
}

private fun FrameLayout.commitButton(body: ImageView.() -> Unit = {}) {
    addView(ImageView(context).apply {
        dp(65).also {
            layoutParams = FrameLayout.LayoutParams(it, it).apply {
                gravity = Gravity.CENTER
            }
        }
        scaleType = ImageView.ScaleType.CENTER_INSIDE
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
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_HORIZONTAL
            dp(20).also { dp20 ->
                setPadding(dp20, (dp20.toFloat() * 1.5F).toInt(), dp20, 0)
            }
            body(this)
        })
    })

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
            return if (!cordova.hasPermission("android.permission.RECORD_AUDIO")) {
                callbackContext?.error("require permission: android.permission.RECORD_AUDIO")
                true
            } else when (action) {
                "start" -> {
                    if (SttDialog.isActive()) {
                        callbackContext?.error("Is already Active")
                    } else {
                        SttDialog(
                            activity = cordova.activity,
                            callbackContext = callbackContext,
                            sttDataHolder = args?.getJSONObject(0)
                                ?.fromJson()
                                ?: SttDataHolder()
                        ).show()
                    }
                    true
                }
                "resetText" -> {
                    SttDialog.resetText()
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

    override fun onStop() {
        SttDialog.dismiss()
        super.onStop()
    }
}