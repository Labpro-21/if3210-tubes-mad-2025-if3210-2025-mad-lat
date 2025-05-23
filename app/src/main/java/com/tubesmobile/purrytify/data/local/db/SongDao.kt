package com.tubesmobile.purrytify.data.local.db

import android.R
import android.util.Log
import androidx.room.*
import com.tubesmobile.purrytify.data.local.db.entities.SongEntity
import com.tubesmobile.purrytify.data.local.db.entities.LikedSongCrossRef
import com.tubesmobile.purrytify.data.local.db.entities.SongPlayLogEntity
import com.tubesmobile.purrytify.data.local.db.entities.SongPlayTimestamp
import com.tubesmobile.purrytify.data.model.ArtistPlayStats
import com.tubesmobile.purrytify.data.model.SongPlayStats
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT EXISTS(SELECT 1 FROM songs AS s JOIN song_uploader AS su WHERE s.title = :title AND s.artist = :artist AND su.uploaderEmail = :userEmail AND s.id = su.songId)")
    suspend fun isSongExistsForUser(title: String, artist: String, userEmail: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM songs WHERE title = :title AND artist = :artist)")
    suspend fun isSongExists(title: String, artist: String): Boolean

    @Query("INSERT INTO song_uploader (uploaderEmail, songId) VALUES (:uploader, :songId)")
    suspend fun registerUserToSong(uploader: String, songId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun likeSong(crossRef: LikedSongCrossRef)

    @Delete
    suspend fun unlikeSong(crossRef: LikedSongCrossRef)

    @Query("""
        SELECT s.* FROM songs AS s
        JOIN song_uploader AS su ON s.id = su.songId
        WHERE su.uploaderEmail = :userEmail
    """)
    fun getSongsByUser(userEmail: String): Flow<List<SongEntity>>


    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN liked_songs ON songs.id = liked_songs.songId
        WHERE liked_songs.userEmail = :userEmail
    """)
    fun getLikedSongs(userEmail: String): Flow<List<SongEntity>>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM liked_songs WHERE userEmail = :userEmail AND songId = :songId
        )
    """)
    suspend fun isSongLiked(userEmail: String, songId: Int): Boolean

    @Query("SELECT id FROM songs WHERE title = :title AND artist = :artist")
    suspend fun getSongId(title: String, artist: String): Int

    @Query("SELECT songs.* FROM songs WHERE id = :id")
    suspend fun getSongById(id: Int): SongEntity

    @Query("SELECT songs_timestamp.* FROM songs_timestamp WHERE userEmail = :userEmail")
    fun getSongsTimestampByEmail(userEmail: String): Flow<List<SongPlayTimestamp>>

    @Query("SELECT EXISTS(SELECT 1 FROM songs_timestamp WHERE userEmail = :userEmail AND songId = :id)")
    suspend fun isTimestampExistsForEmail(userEmail: String, id: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimestamp(timestamp: SongPlayTimestamp)

    @Update
    suspend fun updateTimestamp(timestamp: SongPlayTimestamp)

    @Query("SELECT EXISTS(SELECT 1 FROM songs AS s JOIN song_uploader AS su WHERE s.title = :title AND s.artist = :artist AND su.uploaderEmail = :userEmail AND s.id = su.songId AND s.id != :excludeSongId)") // Added excludeSongId
    suspend fun isSongExistsForUserExcludingId(title: String, artist: String, userEmail: String, excludeSongId: Int): Boolean // New function for edit check

    @Update
    suspend fun updateSongEntity(song: SongEntity) // For updating song details

    @Query("DELETE FROM song_uploader WHERE uploaderEmail = :userEmail AND songId = :songId")
    suspend fun deleteUserSongLink(userEmail: String, songId: Int)

    @Query("DELETE FROM liked_songs WHERE userEmail = :userEmail AND songId = :songId")
    suspend fun deleteLikedSongLink(userEmail: String, songId: Int)

    @Query("DELETE FROM songs_timestamp WHERE userEmail = :userEmail AND songId = :songId")
    suspend fun deleteTimestampForUserSong(userEmail: String, songId: Int)

    @Query("SELECT COUNT(*) FROM song_uploader WHERE songId = :songId")
    suspend fun countUsersForSong(songId: Int): Int // Check how many users have this song

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSongById(songId: Int) // Delete the actual song if no users are left

    // Song PLay Log

    @Insert
    suspend fun insertSongPlayLog(playLog: SongPlayLogEntity): Long

    @Query("""
    SELECT * FROM song_play_log
    WHERE userEmail = :userEmail 
    AND playedAtTimestamp >= :startTimeMillis 
    AND playedAtTimestamp < :endTimeMillis
    AND isLocal = 1 
""")
    fun getPlayLogsForUserInMonth(userEmail: String, startTimeMillis: Long, endTimeMillis: Long): Flow<List<SongPlayLogEntity>>

    @Query("""
    SELECT * FROM song_play_log
    WHERE userEmail = :userEmail AND songId = :songId AND isLocal = 1
    ORDER BY playedAtTimestamp ASC
    """)
    fun getPlayLogsForSongByUser(userEmail: String, songId: Int): Flow<List<SongPlayLogEntity>>

    @Query("""
    SELECT s.artist, COUNT(spl.songId) as playCount, SUM(spl.durationListenedMillis) as totalDuration
    FROM song_play_log spl
    JOIN songs s ON spl.songId = s.id
    WHERE spl.userEmail = :userEmail 
    AND spl.playedAtTimestamp >= :startTimeMillis 
    AND spl.playedAtTimestamp < :endTimeMillis
    AND spl.isLocal = 1
    GROUP BY s.artist
    ORDER BY playCount DESC 
    """)
    fun getTopArtistsInMonthByPlayCount(userEmail: String, startTimeMillis: Long, endTimeMillis: Long): Flow<List<ArtistPlayStats>>

    @Query("""
    SELECT spl.songId, s.title, s.artist, s.artworkUri, s.uri as songUri, s.duration as songDuration, 
           COUNT(spl.songId) as playCount, SUM(spl.durationListenedMillis) as totalDuration
    FROM song_play_log spl
    JOIN songs s ON spl.songId = s.id
    WHERE spl.userEmail = :userEmail 
    AND spl.playedAtTimestamp >= :startTimeMillis 
    AND spl.playedAtTimestamp < :endTimeMillis
    AND spl.isLocal = 1
    GROUP BY spl.songId
    ORDER BY playCount DESC
    """)
    fun getTopSongsInMonthByPlayCount(userEmail: String, startTimeMillis: Long, endTimeMillis: Long): Flow<List<SongPlayStats>>
}
