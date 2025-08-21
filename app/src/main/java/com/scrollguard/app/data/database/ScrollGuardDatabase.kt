package com.scrollguard.app.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.scrollguard.app.data.dao.ContentDao
import com.scrollguard.app.data.dao.PreferencesDao
import com.scrollguard.app.data.dao.SessionDao
import com.scrollguard.app.data.model.ContentAnalysis
import com.scrollguard.app.data.model.ContentTypeConverter
import com.scrollguard.app.data.model.DailySummary
import com.scrollguard.app.data.model.FilterSession
import com.scrollguard.app.data.model.PreferencesTypeConverter
import com.scrollguard.app.data.model.UserPreferences

/**
 * Main Room database for ScrollGuard.
 * Contains all entities and provides access to DAOs.
 */
@Database(
    entities = [
        ContentAnalysis::class,
        UserPreferences::class,
        FilterSession::class,
        DailySummary::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(
    ContentTypeConverter::class,
    PreferencesTypeConverter::class
)
abstract class ScrollGuardDatabase : RoomDatabase() {

    abstract fun contentDao(): ContentDao
    abstract fun preferencesDao(): PreferencesDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val DATABASE_NAME = "scrollguard_database"
        
        @Volatile
        private var INSTANCE: ScrollGuardDatabase? = null

        fun getInstance(context: Context): ScrollGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScrollGuardDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2)
                .addCallback(DatabaseCallback())
                .build()
                
                INSTANCE = instance
                instance
            }
        }

        /**
         * Migration from version 1 to 2 (example for future use)
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Example migration - add new column
                // database.execSQL("ALTER TABLE content_analysis ADD COLUMN new_field TEXT")
            }
        }

        /**
         * Database callback for initialization
         */
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Database created for the first time
                // You can perform initial setup here
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Database opened
                // Enable foreign key constraints
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        /**
         * Clear all data from the database
         */
        suspend fun clearAllData(database: ScrollGuardDatabase) {
            database.runInTransaction {
                database.clearAllTables()
            }
        }

        /**
         * Get database size in bytes
         */
        fun getDatabaseSize(context: Context): Long {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            return if (dbFile.exists()) dbFile.length() else 0L
        }

        /**
         * Export database to file (for backup)
         */
        fun exportDatabase(context: Context, targetPath: String): Boolean {
            return try {
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                val targetFile = java.io.File(targetPath)
                
                if (dbFile.exists()) {
                    dbFile.copyTo(targetFile, overwrite = true)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Import database from file (for restore)
         */
        fun importDatabase(context: Context, sourcePath: String): Boolean {
            return try {
                val sourceFile = java.io.File(sourcePath)
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                
                if (sourceFile.exists()) {
                    // Close current database instance
                    INSTANCE?.close()
                    INSTANCE = null
                    
                    sourceFile.copyTo(dbFile, overwrite = true)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }
}