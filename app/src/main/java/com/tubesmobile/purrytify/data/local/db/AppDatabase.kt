package com.tubesmobile.purrytify.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tubesmobile.purrytify.data.local.db.SongDao
import com.tubesmobile.purrytify.data.local.db.entities.SongEntity
import com.tubesmobile.purrytify.data.local.db.entities.SongUploader
import com.tubesmobile.purrytify.data.local.db.entities.LikedSongCrossRef
import androidx.room.Room
import android.content.Context
import com.tubesmobile.purrytify.data.local.db.entities.SongPlayTimestamp
import com.tubesmobile.purrytify.data.local.db.entities.SongPlaybackHistoryEntity

@Database(entities = [SongEntity::class, LikedSongCrossRef::class, SongUploader::class, SongPlayTimestamp::class, SongPlaybackHistoryEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun songPlaybackHistoryDao(): SongPlaybackHistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "purrytify_database"
                )   .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}