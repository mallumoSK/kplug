package tk.mallumo.cordova.kplug.location

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tk.mallumo.http.http

class LocationUploader(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {

        private val syncMutex = Mutex()

        @JvmStatic
        fun exec(context: Context) {
            WorkManager.getInstance(context)
                    .enqueue(OneTimeWorkRequest
                            .Builder(LocationUploader::class.java)
                            .setConstraints(Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build())
                            .build())
        }
    }


    override suspend fun doWork(): Result {
        syncMutex.withLock {
            val database = LocationDatabase.get(applicationContext)
            val dataToUpload = LocationDatabase.get(applicationContext).uploadDataQuery()
            if (dataToUpload != null) {
                withContext(Dispatchers.IO) {
                    if (makeRequest(dataToUpload.second)) {
                        database.uploadComplete(dataToUpload.first)
                    }
                }
            }
        }
        return Result.success()
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun makeRequest(url: String): Boolean =
        http.post<String>(url, mapOf<String, String>()).let {
            it.exception?.printStackTrace()
            it.code == 200
        }
}