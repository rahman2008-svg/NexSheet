package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.SpreadsheetDao
import com.example.data.entity.SpreadsheetFile
import com.example.data.entity.SpreadsheetContent
import com.example.data.entity.SpreadsheetVersion

@Database(
    entities = [
        SpreadsheetFile::class,
        SpreadsheetContent::class,
        SpreadsheetVersion::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spreadsheetDao(): SpreadsheetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nexsheet_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
