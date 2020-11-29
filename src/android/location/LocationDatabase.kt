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
        sqlite.execSQL("""
CREATE TABLE IF NOT EXISTS "LOCATION" (
ID INTEGER PRIMARY KEY AUTOINCREMENT,
JSON TEXT NOT NULL DEFAULT (''),
IDENTIFIER TEXT NOT NULL DEFAULT (''))
""")
    }

    fun insert(locationResponse: LocationResponse, identifier: String) {
        sqlite.insert("LOCATION", null,
                contentValuesOf(
                        "JSON" to locationResponse.toJson(),
                        "IDENTIFIER" to identifier))
    }

    fun clear(identifier:String){
        sqlite.delete("LOCATION", "IDENTIFIER = '$identifier'", null)
    }

    fun query(offset: Int, limit: Int, identifier: String): List<LocationResponse> {
        val cursor = sqlite.rawQuery("""
SELECT JSON 
FROM LOCATION 
WHERE IDENTIFIER = '$identifier' 
LIMIT $limit OFFSET $offset
""", null)

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