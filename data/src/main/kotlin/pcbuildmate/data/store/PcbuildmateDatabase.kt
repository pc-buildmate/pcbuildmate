package pcbuildmate.data.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BuildEntity::class, RevisionEntity::class, DraftEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PcbuildmateDatabase : RoomDatabase() {
    abstract fun buildDao(): BuildDao
    abstract fun draftDao(): DraftDao

    companion object {
        @Volatile private var instance: PcbuildmateDatabase? = null

        fun get(context: Context): PcbuildmateDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PcbuildmateDatabase::class.java,
                    "pcbuildmate.db",
                ).allowMainThreadQueries().build().also { instance = it }
            }
    }
}
