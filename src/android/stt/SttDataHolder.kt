package tk.mallumo.cordova.kplug.stt

data class SttDataHolder(
    var preferOffline: Boolean = false,
    var maxResults: Int = 10,
    var autoContinue: Boolean = true,
    var enableStartStopSound: Boolean = true
)
