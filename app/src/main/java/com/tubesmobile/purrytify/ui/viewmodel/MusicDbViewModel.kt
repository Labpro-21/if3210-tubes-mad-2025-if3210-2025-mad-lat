package com.tubesmobile.purrytify.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tubesmobile.purrytify.data.local.db.AppDatabase
import com.tubesmobile.purrytify.data.local.db.entities.LikedSongCrossRef
import com.tubesmobile.purrytify.data.local.db.entities.SongEntity
import com.tubesmobile.purrytify.data.local.db.entities.SongPlayTimestamp
import com.tubesmobile.purrytify.data.local.db.entities.SongUploader
import com.tubesmobile.purrytify.ui.screens.Song
import com.tubesmobile.purrytify.service.DataKeeper
import com.tubesmobile.purrytify.ui.screens.SongTimestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class MusicDbViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao = AppDatabase.Companion.getDatabase(application).songDao()

    val allSongs: Flow<List<Song>> =
        songDao.getSongsByUser(DataKeeper.email.toString()).map { entities ->
            entities.map { entity ->
                Song(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    duration = entity.duration,
                    uri = entity.uri,
                    artworkUri = entity.artworkUri ?: ""
                )
            }
        }

    val songsTimestamp: Flow<List<SongTimestamp>> =
        songDao.getSongsTimestampByEmail(DataKeeper.email.toString()).map { entities ->
            entities.map { entity ->
                SongTimestamp(
                    userEmail = entity.userEmail,
                    songId = entity.songId,
                    lastPlayedTimestamp = entity.lastPlayedTimestamp,
                )
            }
        }

    fun extractAndSaveArtwork(context: Context, uri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val art = retriever.embeddedPicture
            if (art != null) {
                val filename = "artwork_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, filename)
                file.writeBytes(art)
                file.absolutePath
            } else null
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }


    fun insertSong(song: Song, userEmail: String){
        viewModelScope.launch {
            val entity = SongEntity(
                title = song.title,
                artist = song.artist,
                duration = song.duration,
                uri = song.uri,
                artworkUri = song.artworkUri
            )

            val newId = songDao.insertSong(entity).toInt()
            songDao.registerUserToSong(userEmail, newId)
        }
    }

    fun checkAndInsertSong(
        context: Context,
        song: Song,
        onExists: () -> Unit
    ) {
        viewModelScope.launch {
            val existsForUser = songDao.isSongExistsForUser(song.title, song.artist,
                DataKeeper.email.toString()
            )
            val exists = songDao.isSongExists(song.title, song.artist)

            if (existsForUser) {
                onExists()
            } else if (exists) {
                val songId = songDao.getSongId(song.title, song.artist)
                val registerUploader = SongUploader(
                    uploaderEmail = DataKeeper.email.toString(),
                    songId = songId
                )
                songDao.registerUserToSong(registerUploader.uploaderEmail, registerUploader.songId)
            } else {
                val savedArtworkPath = extractAndSaveArtwork(context, Uri.parse(song.uri)) ?: ""

                val entity = SongEntity(
                    title = song.title,
                    artist = song.artist,
                    duration = song.duration,
                    uri = song.uri,
                    artworkUri = savedArtworkPath
                )
                val newId = songDao.insertSong(entity).toInt()
                songDao.registerUserToSong(DataKeeper.email.toString(), newId)
            }
        }
    }

    fun updateSongTimestamp(song: Song) {
        viewModelScope.launch {
            val timestampExists = songDao.isTimestampExistsForEmail(DataKeeper.email.toString(), song.id ?: return@launch)
            val currentTime = System.currentTimeMillis()
            val songTimestamp = SongPlayTimestamp(
                userEmail = DataKeeper.email.toString(),
                songId = song.id,
                lastPlayedTimestamp = currentTime
            )
            if (timestampExists) {
                songDao.updateTimestamp(songTimestamp)
            } else {
                songDao.insertTimestamp(songTimestamp)
            }
        }
    }

    val likedSongs: Flow<List<Song>> =
        songDao.getSongsByUser(DataKeeper.email.toString()).map { entities ->
            entities.filter { entity ->
                songDao.isSongLiked(DataKeeper.email.toString(), entity.id)
            }.map { entity ->
                Song(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    duration = entity.duration,
                    uri = entity.uri,
                    artworkUri = entity.artworkUri ?: ""
                )
            }
        }

    suspend fun isSongLiked(songId: Int): Boolean {
        return songDao.isSongLiked(DataKeeper.email.toString(), songId)
    }

    // New: Toggle like status
    fun toggleSongLike(song: Song) {
        viewModelScope.launch {
            if (song.id == null) return@launch
            val isLiked = songDao.isSongLiked(DataKeeper.email.toString(), song.id)
            val crossRef = LikedSongCrossRef(DataKeeper.email.toString(), song.id)

            if (isLiked) {
                songDao.unlikeSong(crossRef)
            } else {
                songDao.likeSong(crossRef)
            }
        }
    }
}