package nl.medictimer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalTime

class Store(context: Context) : SQLiteOpenHelper(context, "medictimer.db", null, 1), java.io.Closeable {
    override fun close() { super.close() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, timestamp INTEGER NOT NULL,
            first REAL, second REAL, pulse INTEGER, label TEXT NOT NULL,
            meal_id INTEGER, alerted INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("CREATE INDEX entries_time ON entries(timestamp)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun all(): List<Entry> = readableDatabase.rawQuery("SELECT * FROM entries ORDER BY timestamp DESC, id DESC", null).use { c ->
        buildList {
            while (c.moveToNext()) add(Entry(
                id = c.getLong(0), kind = Kind.valueOf(c.getString(1)), time = c.getLong(2),
                first = if (c.isNull(3)) null else c.getDouble(3),
                second = if (c.isNull(4)) null else c.getDouble(4),
                pulse = if (c.isNull(5)) null else c.getInt(5), label = c.getString(6),
                mealId = if (c.isNull(7)) null else c.getLong(7), alerted = c.getInt(8) == 1,
            ))
        }
    }
    fun save(entry: Entry): Long {
        val values = ContentValues().apply {
            put("kind", entry.kind.name); put("timestamp", entry.time)
            put("first", entry.first); put("second", entry.second); put("pulse", entry.pulse)
            put("label", entry.label); put("meal_id", entry.mealId); put("alerted", if (entry.alerted) 1 else 0)
        }
        if (entry.id == 0L) return writableDatabase.insertOrThrow("entries", null, values)
        writableDatabase.update("entries", values, "id=?", arrayOf(entry.id.toString()))
        return entry.id
    }
    fun delete(id: Long) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("entries", "id=?", arrayOf(id.toString()))
            writableDatabase.execSQL("UPDATE entries SET meal_id=NULL WHERE meal_id=?", arrayOf(id))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
    fun markAlerted(id: Long) {
        writableDatabase.execSQL("UPDATE entries SET alerted=1 WHERE id=?", arrayOf(id))
    }
}

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("schedule", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    fun times(kind: Kind): List<LocalTime> {
        val defaults = if (kind == Kind.MEDICATION) listOf("08:00", "13:00", "20:00") else listOf("08:00", "20:00")
        return defaults.mapIndexed { index, default -> LocalTime.parse(prefs.getString("${kind.name}$index", default)) }
    }
    fun setTime(kind: Kind, index: Int, time: LocalTime) {
        prefs.edit().putString("${kind.name}$index", time.toString()).apply()
    }
}
