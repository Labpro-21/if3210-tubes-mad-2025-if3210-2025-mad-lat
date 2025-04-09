package com.tubesmobile.purrytify.data.local.db

import android.util.Log
import androidx.room.*
import com.tubesmobile.purrytify.data.local.db.entities.SongEntity
import com.tubesmobile.purrytify.data.local.db.entities.LikedSongCrossRef
import com.tubesmobile.purrytify.ui.screens.Song
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
    suspend fun getLikedSongs(userEmail: String): List<SongEntity>

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

    @Update
    suspend fun update(song: SongEntity)

    @Query("UPDATE songs SET lastPlayedTimestamp = :timestamp WHERE id = :songId")
    suspend fun updateLastPlayedTimestamp(songId: Int, timestamp: Long?)

    suspend fun updateCaller(song: Song) {
        this.updateLastPlayedTimestamp(this.getSongId(song.title, song.artist), song.lastPlayedTimestamp)
        Log.d("homescreen", "id nya ${this.getSongId(song.title, song.artist)}")
        Log.d("homescreen", "timestamp nya di fungsi caller ${song.lastPlayedTimestamp}")
        Log.d("homescreen", "ini song yg udah diambil dari database ${this.getSongById(this.getSongId(song.title, song.artist)).lastPlayedTimestamp}")
//        Log.d("homescreen", "udah masuk kesini")
//        val helper = SongEntity(
//            id = this.getSongId(song.title, song.artist),
//            title = song.title,
//            artist = song.artist,
//            artworkUri = song.artworkUri,
//            uri = song.uri,
//            duration = song.duration,
//            lastPlayedTimestamp = song.lastPlayedTimestamp
//        )
//        Log.d("homescreen", "kebawah sini juga udah")
//        Log.d("homescreen", "helpernya ${helper.lastPlayedTimestamp}")
//        this.update(helper)
    }
}
