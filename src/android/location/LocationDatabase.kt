package tk.mallumo.cordova.kplug.location

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import com.google.gson.Gson
import tk.mallumo.cordova.kplug.toJson
import java.io.Closeable

class LocationDatabase private constructor(context: Context) : Closeable {

    private val dbFile = context.getDatabasePath("KPluginLocation.sqlite")
    private val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)

    companion object {

        private var instance: LocationDatabase? = null

        fun get(context: Context): LocationDatabase {
            if (instance == null) {
                instance = LocationDatabase(context.applicationContext)
            }
            return instance!!
        }
    }

    init {
        sqlite.execSQL(
            """
CREATE TABLE IF NOT EXISTS "LOCATION" (
ID INTEGER PRIMARY KEY AUTOINCREMENT,
JSON TEXT NOT NULL DEFAULT (''),
IDENTIFIER TEXT NOT NULL DEFAULT (''),
UPLOAD_COMPLETE INTEGER NOT NULL DEFAULT (0),
URL TEXT NOT NULL DEFAULT (''),
DATA_PREFIX TEXT NOT NULL DEFAULT (''))
"""
        )

        sqlite.runCatching {
            execSQL("""ALTER TABLE "LOCATION" ADD COLUMN UPLOAD_COMPLETE INTEGER DEFAULT 0""")
            execSQL("""ALTER TABLE "LOCATION" ADD COLUMN URL TEXT DEFAULT ''""")
            execSQL("""ALTER TABLE "LOCATION" ADD COLUMN DATA_PREFIX TEXT DEFAULT ''""")
            execSQL("""UPDATE "LOCATION" SET UPLOAD_COMPLETE = 1 WHERE 1=1""")
        }
    }


    fun uploadComplete(id: Long) {
        sqlite.execSQL(
            """
UPDATE "LOCATION" SET UPLOAD_COMPLETE = 1 WHERE ID <=$id
        """
        )
    }

    fun insert(
        locationResponse: LocationResponse,
        identifier: String,
        url: String,
        dataPrefix: String
    ) {
        sqlite.insert(
            "LOCATION", null,
            contentValuesOf(
                "JSON" to locationResponse.toJson(),
                "IDENTIFIER" to identifier,
                "URL" to url,
                "DATA_PREFIX" to dataPrefix,
                "UPLOAD_COMPLETE" to (if (url.isEmpty()) 1 else 0)
            )
        )
    }

    fun clear(identifier: String) {
        sqlite.delete("LOCATION", "IDENTIFIER = '$identifier'", null)
    }

    fun uploadDataQuery(): Pair<Long, String>? {
        return sqlite.rawQuery(
            """
SELECT ID, JSON, URL, DATA_PREFIX  
FROM LOCATION 
WHERE UPLOAD_COMPLETE = 0
ORDER BY ID ASC
        """, null
        ).use {
            if (it.count > 0) {
                val gson = Gson()
                val locationData = buildString {
                    while (it.moveToNext()) {
                        val item = gson.fromJson(it.getString(1), LocationResponse::class.java)
                        val prefix = it.getString(3)
                        append(item.buildUploadLink(prefix))
                        append("$")
                    }
                }

                it.moveToLast()

                val maxID = it.getLong(0)
                val url = "${it.getString(2)}$locationData"
                maxID to url
            } else {
                null
            }

        }
    }

    fun query(offset: Int, limit: Int, identifier: String): List<LocationResponse> {
        return queryExec(
            """
SELECT JSON 
FROM LOCATION 
WHERE IDENTIFIER = '$identifier' 
LIMIT $limit OFFSET $offset
"""
        )
    }

    fun last(): LocationResponse? {
        return queryExec(
            """
SELECT JSON 
FROM LOCATION
ORDER BY ID DESC
LIMIT 1
"""
        ).firstOrNull()
    }

    private fun queryExec(sql: String): List<LocationResponse> {
        val cursor = sqlite.rawQuery(sql, null)
        val response = arrayListOf<LocationResponse>()
        val gson = Gson()
        while (cursor.moveToNext()) {
            response.add(gson.fromJson(cursor.getString(0), LocationResponse::class.java))
        }
        cursor.close()
        return response
    }

    override fun close() {
        sqlite.runCatching {
            close()
        }
        instance = null
    }


}