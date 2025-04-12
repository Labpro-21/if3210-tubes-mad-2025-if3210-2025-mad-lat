package com.tubesmobile.purrytify.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MusicDbViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao = AppDatabase.Companion.getDatabase(application).songDao()
    private val appContext = application.applicationContext

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


    // Improved artwork extraction method with better error handling
    fun extractAndSaveArtwork(context: Context, uri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        try {
            Log.d("ArtworkFix", "Trying to extract artwork from: $uri")
            retriever.setDataSource(context, uri)

            val embeddedArt = try {
                retriever.embeddedPicture
            } catch (e: Exception) {
                Log.d("ArtworkFix", "No embedded artwork found: ${e.message}")
                null
            }

            if (embeddedArt != null && embeddedArt.isNotEmpty()) {
                val filename = "artwork_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, filename)
                file.writeBytes(embeddedArt)
                Log.d("ArtworkFix", "Saved embedded artwork to: ${file.absolutePath}")
                return file.absolutePath
            }

            return null
        } catch (e: Exception) {
            Log.d("ArtworkFix", "Couldn't extract artwork: ${e.message}")
            return null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
            }
        }
    }



    fun saveArtworkFromUri(context: Context, artworkUri: Uri): String? {
        try {
            Log.d("ArtworkFix", "Saving artwork from: $artworkUri")

            val inputStream = context.contentResolver.openInputStream(artworkUri)
                ?: return null

            val filename = "artwork_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, filename)

            // Copy the file contents
            FileOutputStream(file).use { outputStream ->
                inputStream.use { input ->
                    input.copyTo(outputStream)
                }
            }

            Log.d("ArtworkFix", "Saved artwork to: ${file.absolutePath}")
            return file.absolutePath
        } catch (e: Exception) {
            Log.e("ArtworkFix", "Failed to save artwork: ${e.message}")
            return null
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
        onSuccess: () -> Unit,
        onExists: () -> Unit
    ) {
        viewModelScope.launch {
            val existsForUser = songDao.isSongExistsForUser(song.title, song.artist, DataKeeper.email.toString())
            val exists = songDao.isSongExists(song.title, song.artist)

            if (existsForUser) {
                onExists()
            } else {
                if (exists) {
                    val songId = songDao.getSongId(song.title, song.artist)
                    val registerUploader = SongUploader(
                        uploaderEmail = DataKeeper.email.toString(),
                        songId = songId
                    )
                    songDao.registerUserToSong(registerUploader.uploaderEmail, registerUploader.songId)
                } else {
                    var savedArtworkPath = ""
                    // If user selected an artwork, try to save it directly
                    if (song.artworkUri.isNotEmpty()) {
                        try {
                            val artworkUri = Uri.parse(song.artworkUri)
                            savedArtworkPath = saveArtworkFromUri(context, artworkUri) ?: ""
                            Log.d("ArtworkFix", "Saved selected artwork: $savedArtworkPath")
                        } catch (e: Exception) {
                            Log.e("ArtworkFix", "Failed to save selected artwork: ${e.message}")
                        }
                    }

                    // If no artwork saved yet, try to extract from the audio file
                    if (savedArtworkPath.isEmpty()) {
                        try {
                            val audioUri = Uri.parse(song.uri)
                            savedArtworkPath = extractAndSaveArtwork(context, audioUri) ?: ""
                            Log.d("ArtworkFix", "Extracted audio artwork: $savedArtworkPath")
                        } catch (e: Exception) {
                            Log.e("ArtworkFix", "Failed to extract audio artwork: ${e.message}")
                        }
                    }

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
                onSuccess()
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

    // Toggle like status
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

    fun updateSong(
        originalSong: Song,
        newTitle: String,
        newArtist: String,
        newArtworkUri: Uri?,
        onSuccess: () -> Unit,
        onExists: (String) -> Unit
    ) {
        viewModelScope.launch {
            if (originalSong.id == null) return@launch

            val trimmedTitle = newTitle.trim()
            val trimmedArtist = newArtist.trim()

            // Check for duplicates (excluding the current song)
            val exists = songDao.isSongExistsForUserExcludingId(
                trimmedTitle,
                trimmedArtist,
                DataKeeper.email.toString(),
                originalSong.id
            )

            if (exists) {
                onExists("A song with the title '$trimmedTitle' and artist '$trimmedArtist' already exists in your library.")
            } else {
                val existingEntity = songDao.getSongById(originalSong.id)
                if (existingEntity != null) {
                    var updatedArtworkUri = existingEntity.artworkUri

                    if (newArtworkUri != null) {
                        Log.d("ArtworkFix", "Processing new artwork: $newArtworkUri")

                        // Delete old artwork if it exists
                        if (!existingEntity.artworkUri.isNullOrEmpty()) {
                            try {
                                val oldFile = File(existingEntity.artworkUri)
                                if (oldFile.exists()) {
                                    oldFile.delete()
                                    Log.d("ArtworkFix", "Deleted old artwork: ${existingEntity.artworkUri}")
                                }
                            } catch (e: Exception) {
                                Log.e("ArtworkFix", "Error deleting old artwork: ${e.message}")
                            }
                        }

                        // Save new artwork - using application context here
                        try {
                            val appContext = getApplication<Application>().applicationContext
                            val savedPath = saveArtworkFromUri(appContext, newArtworkUri)
                            if (savedPath != null) {
                                updatedArtworkUri = savedPath
                                Log.d("ArtworkFix", "Saved new artwork: $savedPath")
                            }
                        } catch (e: Exception) {
                            Log.e("ArtworkFix", "Failed to save new artwork: ${e.message}")
                        }
                    }

                    val updatedEntity = existingEntity.copy(
                        title = trimmedTitle,
                        artist = trimmedArtist,
                        artworkUri = updatedArtworkUri
                    )
                    songDao.updateSongEntity(updatedEntity)
                    onSuccess()
                } else {
                    onExists("Error: Could not find the song to update.")
                }
            }
        }
    }

    fun deleteSong(song: Song, onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (song.id == null) return@launch

            val userEmail = DataKeeper.email.toString()
            val songId = song.id

            // 1. Remove the link between the user and the song
            songDao.deleteUserSongLink(userEmail, songId)

            // 2. Remove like status for this user and song (if exists)
            songDao.deleteLikedSongLink(userEmail, songId)

            // 3. Remove timestamp for this user and song (if exists)
            songDao.deleteTimestampForUserSong(userEmail, songId)

            // 4. Check if any other user is linked to this song
            val remainingUsers = songDao.countUsersForSong(songId)

            // 5. If no other users are linked, delete the actual song record and its artwork
            if (remainingUsers == 0) {
                if (song.artworkUri.isNotEmpty()) {
                    try {
                        val artworkFile = File(song.artworkUri)
                        if (artworkFile.exists()) {
                            artworkFile.delete()
                            Log.d("DeleteSong", "Deleted artwork file: ${song.artworkUri}")
                        }
                    } catch (e: Exception) {
                        Log.e("DeleteSong", "Error deleting artwork file: ${song.artworkUri}", e)
                    }
                }
                songDao.deleteSongById(songId)
                Log.d("DeleteSong", "Deleted song record with ID: $songId")
            } else {
                Log.d("DeleteSong", "Unlinked user $userEmail from song $songId. $remainingUsers users remaining.")
            }

            onDeleted()
        }
    }
}