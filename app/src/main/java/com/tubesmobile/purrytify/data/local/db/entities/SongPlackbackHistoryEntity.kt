package com.tubesmobile.purrytify.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "song_playback_history",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userEmail", "playedAtMonthYear"]), Index(value = ["songId"])]
)

data class SongPlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userEmail: String,
    val songId: Int,
    val songTitle: String,
    val songArtist: String,
    val startTimestampMs: Long,
    var durationListenedMs: Long,
    val playedAtMonthYear: String // format "YYYY-MM"
)