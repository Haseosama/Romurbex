package com.romurbex.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LocationEntity::class, PhotoEntity::class, ListVisibilityEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class RomurbexDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun photoDao(): PhotoDao
    abstract fun listVisibilityDao(): ListVisibilityDao

    companion object {
        @Volatile private var instance: RomurbexDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS list_visibility (name TEXT NOT NULL PRIMARY KEY, isVisible INTEGER NOT NULL)",
                )
            }
        }

        fun get(context: Context): RomurbexDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RomurbexDatabase::class.java,
                    "romurbex.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
