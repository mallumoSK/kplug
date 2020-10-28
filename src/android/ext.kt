package tk.mallumo.cordova.kplug

import com.google.gson.Gson
import org.json.JSONObject


inline fun <reified T> JSONObject?.fromJson(): T {
    this ?: throw Exception("Invalid json object data")
    return toString().fromJson()
}

inline fun <reified T> String.fromJson(): T {
    return Gson().fromJson<T>(this, T::class.java)
}

fun Any.toJson(): String {
    return Gson().toJson(this)
}


