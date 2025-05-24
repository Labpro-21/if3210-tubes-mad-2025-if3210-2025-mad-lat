package com.tubesmobile.purrytify.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tubesmobile.purrytify.data.local.db.entities.SongPlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongPlaybackHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackEvent(event: SongPlaybackHistoryEntity): Long

    @Update
    suspend fun updatePlaybackEvent(event: SongPlaybackHistoryEntity)

    @Query("SELECT * FROM song_playback_history WHERE userEmail = :userEmail AND playedAtMonthYear = :monthYear ORDER BY startTimestampMs DESC")
    fun getPlaybackHistoryForMonth(userEmail: String, monthYear: String): Flow<List<SongPlaybackHistoryEntity>>

    @Query("DELETE FROM song_playback_history WHERE userEmail = :userEmail AND songId = :songId")
    suspend fun deleteHistoryForUserSong(userEmail: String, songId: Int)

    @Query("SELECT SUM(durationListenedMs) FROM song_playback_history WHERE userEmail = :userEmail AND playedAtMonthYear = :monthYear")
    fun getTotalListenTimeForMonth(userEmail: String, monthYear: String): Flow<Long?>

    @Query("SELECT songArtist, SUM(durationListenedMs) as totalDuration FROM song_playback_history WHERE userEmail = :userEmail AND playedAtMonthYear = :monthYear GROUP BY songArtist ORDER BY totalDuration DESC LIMIT 1")
    fun getTopArtistForMonth(userEmail: String, monthYear: String): Flow<TopArtistInfo?>

    @Query("SELECT songTitle, songArtist, SUM(durationListenedMs) as totalDuration FROM song_playback_history WHERE userEmail = :userEmail AND playedAtMonthYear = :monthYear GROUP BY songId ORDER BY totalDuration DESC LIMIT 1")
    fun getTopSongForMonth(userEmail: String, monthYear: String): Flow<TopSongInfo?>

    data class TopArtistInfo(val songArtist: String, val totalDuration: Long)
    data class TopSongInfo(val songTitle: String, val songArtist: String, val totalDuration: Long)

    // day streak
    @Query("SELECT DISTINCT startTimestampMs FROM song_playback_history WHERE userEmail = :userEmail AND songId = :songId AND playedAtMonthYear = :monthYear ORDER BY startTimestampMs ASC")
    fun getPlayTimestampsForSongInMonth(userEmail: String, songId: Int, monthYear: String): Flow<List<Long>>

    @Query("SELECT * FROM song_playback_history WHERE userEmail = :userEmail AND playedAtMonthYear = :monthYear")
    suspend fun getRawPlaybackHistoryForMonthSync(userEmail: String, monthYear: String): List<SongPlaybackHistoryEntity>
}