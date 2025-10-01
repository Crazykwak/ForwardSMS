package com.odinu.forwardsms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Filter::class, FilterHistory::class],
    version = 2,
    exportSchema = false
)
abstract class FilterDatabase : RoomDatabase() {
    abstract fun filterDao(): FilterDao

    companion object {
        @Volatile
        private var INSTANCE: FilterDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `filter_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `filterId` INTEGER NOT NULL,
                        `filterKeyword` TEXT NOT NULL,
                        `smsMessage` TEXT NOT NULL,
                        `sender` TEXT NOT NULL,
                        `webhookUrl` TEXT NOT NULL,
                        `httpMethod` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `success` INTEGER NOT NULL,
                        `responseCode` INTEGER,
                        `errorMessage` TEXT
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): FilterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FilterDatabase::class.java,
                    "filter_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}