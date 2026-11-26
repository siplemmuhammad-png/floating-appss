package com.floatingai.assistant
import android.content.Context
import androidx.room.*

@Entity data class Msg(@PrimaryKey(autoGenerate = true) val id: Long = 0, val q: String, val a: String, val time: Long = System.currentTimeMillis())
@Dao interface MsgDao {
    @Query("SELECT * FROM Msg ORDER BY id DESC") suspend fun all(): List<Msg>
    @Insert suspend fun add(m: Msg)
    @Query("DELETE FROM Msg") suspend fun clear()
}
@Database(entities = [Msg::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): MsgDao
    companion object {
        @Volatile private var i: Db? = null
        fun get(c: Context): Db = i ?: synchronized(this) { i ?: Room.databaseBuilder(c.applicationContext, Db::class.java, "h.db").build().also { i = it } }
    }
}
class Prefs(c: Context) {
    private val p = c.getSharedPreferences("s", 0)
    var key: String get() = p.getString("key", "")!!; set(v) { p.edit().putString("key", v).apply() }
    var lang: String get() = p.getString("lang", "id")!!; set(v) { p.edit().putString("lang", v).apply() }
    var alpha: Float get() = p.getFloat("alpha", 0.95f); set(v) { p.edit().putFloat("alpha", v).apply() }
    var cbt: Boolean get() = p.getBoolean("cbt", false); set(v) { p.edit().putBoolean("cbt", v).apply() }
    var dark: Boolean get() = p.getBoolean("dark", true); set(v) { p.edit().putBoolean("dark", v).apply() }
}
