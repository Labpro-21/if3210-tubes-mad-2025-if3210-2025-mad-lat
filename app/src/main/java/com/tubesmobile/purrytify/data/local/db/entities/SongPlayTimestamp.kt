package com.tubesmobile.purrytify.data.local.db.entities;

import androidx.room.Entity

@Entity(tableName = "songs_timestamp", primaryKeys = ["userEmail", "songId"])
data class SongPlayTimestamp(
    val userEmail: String,
    val songId: Int,
    val lastPlayedTimestamp: Long? = null
)