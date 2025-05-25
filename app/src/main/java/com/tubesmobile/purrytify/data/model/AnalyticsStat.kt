package com.tubesmobile.purrytify.data.model

data class ArtistPlayStats(
    val artist: String,
    val playCount: Int,
    val totalDuration: Long
)

data class SongPlayStats(
    val songId: Int,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val songUri: String,
    val songDuration: Long,
    val playCount: Int,
    val totalDuration: Long
)