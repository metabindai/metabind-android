package ai.metabind.data.home.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RecentItem::class], version = 2)
abstract class MetabindDatabase : RoomDatabase() {
    abstract fun recentItemDao(): RecentsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ${RoomConstants.RECENTS_TABLE_NAME} ADD COLUMN name TEXT DEFAULT NULL")
            }
        }
    }
}