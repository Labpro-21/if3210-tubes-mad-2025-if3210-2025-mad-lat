package com.tubesmobile.purrytify.data.local.db.entities

import androidx.room.Entity

@Entity(tableName = "liked_songs")
data class LikedSongCrossRef(
    val userEmail: String,
    val songId: Int
)