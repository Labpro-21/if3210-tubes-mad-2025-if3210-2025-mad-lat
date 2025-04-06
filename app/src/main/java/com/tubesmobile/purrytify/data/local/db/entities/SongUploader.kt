package com.tubesmobile.purrytify.data.local.db.entities

import androidx.room.Entity

@Entity(tableName = "song_uploader")
data class SongUploader(
    val uploaderEmail: String,
    val songId: Int
)