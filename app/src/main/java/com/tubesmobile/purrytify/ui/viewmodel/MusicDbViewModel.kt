package com.tubesmobile.purrytify.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tubesmobile.purrytify.data.api.RetrofitClient
import com.tubesmobile.purrytify.data.local.TokenManager
import kotlinx.coroutines.flow.first
import com.tubesmobile.purrytify.data.local.db.AppDatabase
import com.tubesmobile.purrytify.data.local.db.entities.SongEntity
import com.tubesmobile.purrytify.data.local.db.entities.SongPlayTimestamp
import com.tubesmobile.purrytify.data.local.db.entities.SongUploader
import com.tubesmobile.purrytify.data.repository.UserRepository
import com.tubesmobile.purrytify.ui.screens.Song
import com.tubesmobile.purrytify.service.EmailKeeper
import com.tubesmobile.purrytify.ui.screens.SongTimestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import java.io.File

class MusicDbViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao = AppDatabase.Companion.getDatabase(application).songDao()

    val allSongs: Flow<List<Song>> =
        songDao.getSongsByUser(EmailKeeper.email.toString()).map { entities ->
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
        songDao.getSongsTimestampByEmail(EmailKeeper.email.toString()).map { entities ->
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
                EmailKeeper.email.toString()
            )
            val exists = songDao.isSongExists(song.title, song.artist)

            if (existsForUser) {
                onExists()
            } else if (exists) {
                val songId = songDao.getSongId(song.title, song.artist)
                val registerUploader = SongUploader(
                    uploaderEmail = EmailKeeper.email.toString(),
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
                    artworkUri = savedArtworkPath // 👈 pakai path lokal
                )
                val newId = songDao.insertSong(entity).toInt()
                songDao.registerUserToSong(EmailKeeper.email.toString(), newId)
            }
        }
    }

    fun updateSongTimestamp(song: Song) {
        viewModelScope.launch {
            val timestampExists = songDao.isTimestampExistsForEmail(EmailKeeper.email.toString(), song.id ?: return@launch)
            val currentTime = System.currentTimeMillis()
            val songTimestamp = SongPlayTimestamp(
                userEmail = EmailKeeper.email.toString(),
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
}