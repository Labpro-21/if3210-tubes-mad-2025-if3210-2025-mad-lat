package com.tubesmobile.purrytify.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "song_play_log",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["songId"]), Index(value = ["userEmail"]), Index(value = ["playedAtTimestamp"])]
)
data class SongPlayLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Int = 0,
    val songId: Int,
    val userEmail: String,
    val playedAtTimestamp: Long,
    val durationListenedMillis: Long,
    val isLocal: Boolean
)