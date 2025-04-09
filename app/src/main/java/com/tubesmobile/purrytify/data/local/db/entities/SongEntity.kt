package com.tubesmobile.purrytify.data.local.db.entities
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val uri: String,
    val duration: Long,
    val lastPlayedTimestamp: Long? = 69
)