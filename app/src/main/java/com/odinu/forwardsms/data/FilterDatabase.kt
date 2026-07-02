package com.odinu.forwardsms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Filter::class, FilterHistory::class],
    version = 3,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add phoneNumber and filterType columns to filters table
                database.execSQL("""
                    ALTER TABLE `filters` ADD COLUMN `phoneNumber` TEXT NOT NULL DEFAULT ''
                """.trimIndent())
                database.execSQL("""
                    ALTER TABLE `filters` ADD COLUMN `filterType` TEXT NOT NULL DEFAULT 'KEYWORD'
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): FilterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FilterDatabase::class.java,
                    "filter_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
                INSTANCE = instance
                instance
            }
        }
    }
}