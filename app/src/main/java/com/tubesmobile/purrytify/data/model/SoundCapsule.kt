package com.tubesmobile.purrytify.data.model

data class ArtistData(
    val rank: Int,
    val name: String,
    val imageUrl: String
)

data class SongData(
    val rank: Int,
    val title: String,
    val artists: String, // e.g., "The Weeknd, Daft Punk"
    val imageUrl: String,
    val plays: Int
)

data class MonthlySoundCapsuleData(
    val monthYear: String, // e.g., "April 2025"
    val timeListenedMinutes: Int?,
    val dailyAverageMinutes: Int?, // For TimeListenedScreen

    val topArtistName: String?,
    val topArtistImageUrl: String?,
    val totalArtistsListenedThisMonth: Int? = 0, // For TopArtistsScreen header
    val topArtistsList: List<ArtistData>? = null, // For TopArtistsScreen list

    val topSongName: String? = null,
    val topSongImageUrl: String? = null,
    val totalSongsPlayedThisMonth: Int? = 0, // For TopSongsScreen header
    val topSongsList: List<SongData>? = null, // For TopSongsScreen list

    val dayStreakCount: Int? = 0,
    val dayStreakSongName: String? = null, // e.g., "Loose"
    val dayStreakSongArtist: String? = null, // e.g., "Daniel Caesar"
    val dayStreakFullText: String? = null, // e.g., "You played Loose by Daniel Caesar day after day."
    val dayStreakDateRange: String? = null, // e.g., "Mar 21-25, 2025"
    val dayStreakImage: String? = null, // Main image for streak on capsule card

    val hasData: Boolean = timeListenedMinutes != null || topArtistName != null || topSongName != null || dayStreakCount != null
)