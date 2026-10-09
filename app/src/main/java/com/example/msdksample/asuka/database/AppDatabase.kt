package com.example.msdksample.asuka.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.msdksample.asuka.database.daos.TaskDao
import com.example.msdksample.asuka.database.daos.AnalysisDao
import com.example.msdksample.asuka.database.entities.TaskEntity
import com.example.msdksample.asuka.database.entities.AnalysisEntity

@Database(
    entities = [TaskEntity::class, AnalysisEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun analysisDao(): AnalysisDao

    companion object {
        private const val DATABASE_NAME = "inspection_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
