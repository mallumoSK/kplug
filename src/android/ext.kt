package tk.mallumo.cordova.kplug

import android.content.Context
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
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


fun Context.dp(value: Int) = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    value.toFloat(),
    resources.displayMetrics
).toInt()

fun View.dp(value: Int) = context.dp(value)

val Context.dm: DisplayMetrics get() = resources.displayMetrics