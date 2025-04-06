package com.tubesmobile.purrytify.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tubesmobile.purrytify.data.local.db.SongDao
import com.tubesmobile.purrytify.data.local.db.entities.SongEntity
import androidx.room.Room
import android.content.Context

@Database(entities = [SongEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "purrytify_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}