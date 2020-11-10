package tk.mallumo.cordova.kplug

import android.content.pm.PackageManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.PluginResult
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicInteger


@ExperimentalCoroutinesApi
open class Permission : CordovaPlugin() {

    private val permissionRC = AtomicInteger(652)

    private val tasks = arrayListOf<Pair<Int, (() -> Unit)>>()

    override fun execute(
            action: String?,
            args: JSONArray?,
            callbackContext: CallbackContext?
    ): Boolean {
        try {
            return when (action) {
                "enableSingle" -> {
                    val permission = args?.getString(0) ?: ""
                    if (permission.isEmpty() || isPermissionValid(permission)) {
                        callbackContext!!.success(JSONArray())
                    } else {
                        val code = permissionRC.getAndIncrement()
                        val permissions = arrayOf(permission)

                        permissionEnabling(callbackContext!!, code, permissions)
                    }
                    true
                }
                "enableMultiple" -> {
                    val permissions = args?.let { jArr ->
                        (0 until jArr.length())
                                .map { jArr.getString(it) }
                    }?.filterNot { it.isNullOrEmpty() }
                            ?.toTypedArray()
                            ?: arrayOf()

                    if (permissions.isEmpty() || permissions.all { isPermissionValid(it) }) {
                        callbackContext!!.success(JSONArray())
                    } else {
                        val code = permissionRC.getAndIncrement()
                        permissionEnabling(callbackContext!!,
                                code,
                                permissions.filterNot { isPermissionValid(it) }
                                        .toTypedArray())
                    }
                    true
                }
                "enableAll" -> {
                    val permissions = getPermissions()
                            .filterNot { it.second }
                            .map { it.first }
                            .toTypedArray()
                    if (permissions.isEmpty()) {
                        callbackContext!!.success(JSONArray())
                    } else {
                        val code = permissionRC.getAndIncrement()
                        permissionEnabling(callbackContext!!, code, permissions)
                    }
                    true
                }
                "listAll" -> {
                    callbackContext!!.success(JSONArray(getPermissions().toJson()))
                    true
                }
                "listEnabled" -> {
                    callbackContext!!.success(JSONArray(getPermissions()
                            .filter { it.second }
                            .map { it.first }
                            .toJson()))
                    true
                }
                "listDisabled" -> {
                    callbackContext!!.success(JSONArray(getPermissions()
                            .filterNot { it.second }
                            .map { it.first }
                            .toJson()))
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            callbackContext?.error(e.message)
            return false
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        tasks.firstOrNull { it.first == requestCode }
                ?.second
                ?.also { permissionTask ->
                    tasks.removeAll { it.first == requestCode }
                    permissionTask()
                }
                ?: super.onRequestPermissionResult(requestCode, permissions, grantResults)
    }

    private fun permissionEnabling(callbackContext: CallbackContext, code: Int, permissions: Array<String>) {
        callbackContext.sendPluginResult(PluginResult(PluginResult.Status.OK).apply {
            keepCallback = true
        })
        tasks.add(code to {
            val json = permissions.filterNot { isPermissionValid(it) }
            callbackContext.sendPluginResult(PluginResult(
                    PluginResult.Status.OK,
                    JSONArray(json)).apply {
                keepCallback = false
            })
        })
        cordova.requestPermissions(this, code, permissions)
    }

    private fun getPermissions(): List<Pair<String, Boolean>> {
        return cordova.activity.packageManager
                .getPackageInfo(cordova.activity.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .map { it to isPermissionValid(it) }
    }

    private fun isPermissionValid(permission: String): Boolean = cordova.hasPermission(permission)
}