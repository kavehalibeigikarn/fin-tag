package ir.kaveh.fintag

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transactions")
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,               // "IN" = واریز ، "OUT" = برداشت
    val amount: Long,               // همیشه به ریال ذخیره می‌شود
    val balance: Long? = null,
    val note: String = "",
    val tags: String = "",          // جداشده با |
    val sender: String = "",
    val rawText: String = "",
    val time: Long,
    val reviewed: Boolean = false
) {
    fun tagList(): List<String> = tags.split("|").filter { it.isNotBlank() }
    fun isIn(): Boolean = type == "IN"
}

@Entity(tableName = "tags")
data class Tag(@PrimaryKey val name: String, val useCount: Int = 0)

@Dao
interface AppDao {
    @Insert suspend fun insertTxn(t: Txn): Long
    @Update suspend fun updateTxn(t: Txn)
    @Delete suspend fun deleteTxn(t: Txn)

    @Query("SELECT * FROM transactions ORDER BY time DESC")
    fun observeTxns(): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTxn(id: Long): Txn?

    @Query("SELECT COUNT(*) FROM transactions WHERE rawText = :raw AND time > :since")
    suspend fun recentDup(raw: String, since: Long): Int

    @Query("SELECT * FROM tags ORDER BY useCount DESC, name ASC")
    fun observeTags(): Flow<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(t: Tag): Long

    @Query("UPDATE tags SET useCount = useCount + 1 WHERE name = :name")
    suspend fun bumpTag(name: String)

    @Query("DELETE FROM tags WHERE name = :name")
    suspend fun deleteTag(name: String)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTxnById(id: Long)
}

@Database(entities = [Txn::class, Tag::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        private val DEFAULT_TAGS = listOf(
            "خوراک", "حمل‌ونقل", "قبوض", "خرید", "اجاره", "حقوق",
            "سلامت", "سرگرمی", "آموزش", "کارمزد", "سایر"
        )

        @Volatile private var instance: AppDb? = null

        fun get(ctx: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "fintag.db")
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_TAGS.forEach {
                            db.execSQL("INSERT OR IGNORE INTO tags (name, useCount) VALUES ('$it', 0)")
                        }
                    }
                })
                .build().also { instance = it }
        }
    }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("fintag", Context.MODE_PRIVATE)
    fun toman(c: Context) = sp(c).getBoolean("toman", true)
    fun setToman(c: Context, v: Boolean) = sp(c).edit().putBoolean("toman", v).apply()
    fun popup(c: Context) = sp(c).getBoolean("popup", true)
    fun setPopup(c: Context, v: Boolean) = sp(c).edit().putBoolean("popup", v).apply()
}
