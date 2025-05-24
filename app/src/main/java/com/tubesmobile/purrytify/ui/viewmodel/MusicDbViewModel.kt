package com.tubesmobile.purrytify.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
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
import java.net.URL
import java.util.regex.Pattern

class MusicDbViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao = AppDatabase.getDatabase(application).songDao()
    private val appContext = application.applicationContext

    val allSongs: Flow<List<Song>> =
        songDao.getSongsByUser(sanitizeText(DataKeeper.email ?: "")).map { entities ->
            entities.map { entity ->
                Song(
                    id = entity.id,
                    title = sanitizeText(entity.title),
                    artist = sanitizeText(entity.artist),
                    duration = entity.duration,
                    uri = entity.uri,
                    artworkUri = entity.artworkUri ?: ""
                )
            }
        }

    val songsTimestamp: Flow<List<SongTimestamp>> =
        songDao.getSongsTimestampByEmail(sanitizeText(DataKeeper.email ?: "")).map { entities ->
            entities.map { entity ->
                SongTimestamp(
                    userEmail = sanitizeText(entity.userEmail),
                    songId = entity.songId,
                    lastPlayedTimestamp = entity.lastPlayedTimestamp,
                )
            }
        }

    fun extractAndSaveArtwork(context: Context, uri: Uri): String? {
        if (!isValidUri(uri, context.contentResolver)) {
            return null
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val embeddedArt = try {
                retriever.embeddedPicture
            } catch (e: Exception) {
                null
            }
            if (embeddedArt != null && embeddedArt.size <= 5 * 1024 * 1024) {
                val filename = "artwork_${System.currentTimeMillis()}_${sanitizeFileName(uri.lastPathSegment ?: "artwork")}.jpg"
                val file = File(context.filesDir, filename)
                file.writeBytes(embeddedArt)
                return file.absolutePath
            }
            return null
        } catch (e: Exception) {
            return null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Handle silently
            }
        }
    }

    fun saveArtworkFromUri(context: Context, artworkUri: Uri): String? {
        if (!isValidUri(artworkUri, context.contentResolver)) {
            return null
        }
        try {
            val inputStream = context.contentResolver.openInputStream(artworkUri) ?: return null
            val filename = "artwork_${System.currentTimeMillis()}_${sanitizeFileName(artworkUri.lastPathSegment ?: "artwork")}.jpg"
            val file = File(context.filesDir, filename)
            if (file.length() > 5 * 1024 * 1024) {
                inputStream.close()
                return null
            }
            FileOutputStream(file).use { outputStream ->
                inputStream.use { input ->
                    input.copyTo(outputStream)
                }
            }
            return file.absolutePath
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun downloadFile(urlString: String, destination: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection()
                connection.connect()
                val inputStream = connection.getInputStream()
                FileOutputStream(destination).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                inputStream.close()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun checkAndInsertOnlineSong(
        context: Context,
        song: Song,
        onSuccess: (Song) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isValidSong(song) || !isValidEmail(DataKeeper.email ?: "")) {
            onError("Invalid song data or email")
            return
        }
        viewModelScope.launch {
            val sanitizedEmail = sanitizeText(DataKeeper.email ?: "")
            val sanitizedTitle = sanitizeText(song.title)
            val sanitizedArtist = sanitizeText(song.artist)
            val existsForUser = songDao.isSongExistsForUser(
                sanitizedTitle,
                sanitizedArtist,
                sanitizedEmail
            )
            if (existsForUser) {
                onError("Song with title '$sanitizedTitle' and artist '$sanitizedArtist' already exists in your library")
                return@launch
            }

            val exists = songDao.isSongExists(sanitizedTitle, sanitizedArtist)
            if (exists) {
                val songId = songDao.getSongId(sanitizedTitle, sanitizedArtist)
                songDao.registerUserToSong(sanitizedEmail, songId)
                val updatedSong = song.copy(id = songId)
                onSuccess(updatedSong)
                return@launch
            }

            // Create Purrytify directory in internal storage
            val purrytifyDir = File(context.filesDir, "Purrytify")
            if (!purrytifyDir.exists()) {
                purrytifyDir.mkdirs()
            }

            // Download audio file
            var savedAudioPath = ""
            val audioFileName = "song_${System.currentTimeMillis()}_${sanitizeFileName(song.uri.substringAfterLast("/"))}"
            val audioFile = File(purrytifyDir, audioFileName)
            if (song.uri.startsWith("http")) {
                val success = downloadFile(song.uri, audioFile)
                if (!success || !audioFile.exists()) {
                    onError("Failed to download audio file")
                    return@launch
                }
                savedAudioPath = audioFile.absolutePath
            } else {
                onError("Invalid audio URI: must be a remote URL")
                return@launch
            }

            // Download artwork file
            var savedArtworkPath = ""
            if (song.artworkUri.isNotEmpty() && song.artworkUri.startsWith("http")) {
                val artworkFileName = "artwork_${System.currentTimeMillis()}_${sanitizeFileName(song.artworkUri.substringAfterLast("/"))}"
                val artworkFile = File(purrytifyDir, artworkFileName)
                val success = downloadFile(song.artworkUri, artworkFile)
                if (success && artworkFile.exists() && artworkFile.length() <= 5 * 1024 * 1024) {
                    savedArtworkPath = artworkFile.absolutePath
                }
            }

            // Fallback to extract artwork from audio file if artwork download fails
            if (savedArtworkPath.isEmpty()) {
                val audioUri = Uri.fromFile(audioFile)
                savedArtworkPath = extractAndSaveArtwork(context, audioUri) ?: ""
            }

            Log.d("kocokmeong", "song path nya $savedAudioPath dan artwork path nya $savedArtworkPath")
            val entity = SongEntity(
                title = sanitizedTitle,
                artist = sanitizedArtist,
                duration = song.duration,
                uri = savedAudioPath,
                artworkUri = savedArtworkPath
            )
            Log.d("kocokmeong", "entity yg disimpan $entity")
            val newId = songDao.insertSong(entity).toInt()
            songDao.registerUserToSong(sanitizedEmail, newId)

            val updatedSong = song.copy(id = newId, uri = savedAudioPath, artworkUri = savedArtworkPath)
            onSuccess(updatedSong)
        }
    }

    fun insertSong(song: Song, userEmail: String) {
        if (!isValidSong(song) || !isValidEmail(userEmail)) {
            return
        }
        viewModelScope.launch {
            val entity = SongEntity(
                title = sanitizeText(song.title),
                artist = sanitizeText(song.artist),
                duration = song.duration,
                uri = song.uri,
                artworkUri = song.artworkUri
            )
            val newId = songDao.insertSong(entity).toInt()
            songDao.registerUserToSong(sanitizeText(userEmail), newId)
        }
    }

    fun checkAndInsertSong(
        context: Context,
        song: Song,
        onSuccess: () -> Unit,
        onExists: () -> Unit
    ) {
        if (!isValidSong(song) || !isValidEmail(DataKeeper.email ?: "")) {
            return
        }
        viewModelScope.launch {
            val sanitizedEmail = sanitizeText(DataKeeper.email ?: "")
            val sanitizedTitle = sanitizeText(song.title)
            val sanitizedArtist = sanitizeText(song.artist)
            val existsForUser = songDao.isSongExistsForUser(
                sanitizedTitle,
                sanitizedArtist,
                sanitizedEmail
            )
            val exists = songDao.isSongExists(sanitizedTitle, sanitizedArtist)
            if (existsForUser) {
                onExists()
            } else {
                if (exists) {
                    val songId = songDao.getSongId(sanitizedTitle, sanitizedArtist)
                    songDao.registerUserToSong(sanitizedEmail, songId)
                    onSuccess()
                } else {
                    var savedArtworkPath = ""
                    if (song.artworkUri.isNotEmpty()) {
                        val artworkUri = Uri.parse(song.artworkUri)
                        savedArtworkPath = saveArtworkFromUri(context, artworkUri) ?: ""
                    }
                    if (savedArtworkPath.isEmpty()) {
                        val audioUri = Uri.parse(song.uri)
                        savedArtworkPath = extractAndSaveArtwork(context, audioUri) ?: ""
                    }
                    val entity = SongEntity(
                        title = sanitizedTitle,
                        artist = sanitizedArtist,
                        duration = song.duration,
                        uri = song.uri,
                        artworkUri = savedArtworkPath
                    )
                    val newId = songDao.insertSong(entity).toInt()
                    songDao.registerUserToSong(sanitizedEmail, newId)
                    onSuccess()
                }
            }
        }
    }

    fun updateSongTimestamp(song: Song) {
        if (song.id == null || !isValidEmail(DataKeeper.email ?: "")) {
            return
        }
        viewModelScope.launch {
            val sanitizedEmail = sanitizeText(DataKeeper.email ?: "")
            val timestampExists = songDao.isTimestampExistsForEmail(sanitizedEmail, song.id)
            val currentTime = System.currentTimeMillis()
            val songTimestamp = SongPlayTimestamp(
                userEmail = sanitizedEmail,
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
        songDao.getSongsByUser(sanitizeText(DataKeeper.email ?: "")).map { entities ->
            entities.filter { entity ->
                songDao.isSongLiked(sanitizeText(DataKeeper.email ?: ""), entity.id)
            }.map { entity ->
                Song(
                    id = entity.id,
                    title = sanitizeText(entity.title),
                    artist = sanitizeText(entity.artist),
                    duration = entity.duration,
                    uri = entity.uri,
                    artworkUri = entity.artworkUri ?: ""
                )
            }
        }

    suspend fun isSongLiked(songId: Int): Boolean {
        return songDao.isSongLiked(sanitizeText(DataKeeper.email ?: ""), songId)
    }

    fun toggleSongLike(song: Song) {
        if (song.id == null || !isValidEmail(DataKeeper.email ?: "")) {
            return
        }
        viewModelScope.launch {
            val sanitizedEmail = sanitizeText(DataKeeper.email ?: "")
            val isLiked = songDao.isSongLiked(sanitizedEmail, song.id)
            val crossRef = LikedSongCrossRef(sanitizedEmail, song.id)
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
            val sanitizedTitle = sanitizeText(newTitle.trim())
            val sanitizedArtist = sanitizeText(newArtist.trim())
            val exists = songDao.isSongExistsForUserExcludingId(
                sanitizedTitle,
                sanitizedArtist,
                sanitizeText(DataKeeper.email ?: ""),
                originalSong.id
            )
            if (exists) {
                onExists("A song with the title '$sanitizedTitle' and artist '$sanitizedArtist' already exists in your library.")
            } else {
                val existingEntity = songDao.getSongById(originalSong.id)
                if (existingEntity != null) {
                    var updatedArtworkUri = existingEntity.artworkUri
                    if (newArtworkUri != null && isValidUri(newArtworkUri, appContext.contentResolver)) {
                        if (!existingEntity.artworkUri.isNullOrEmpty()) {
                            try {
                                val oldFile = File(existingEntity.artworkUri)
                                if (oldFile.exists()) {
                                    oldFile.delete()
                                }
                            } catch (e: Exception) {
                                // Handle silently
                            }
                        }
                        val savedPath = saveArtworkFromUri(appContext, newArtworkUri)
                        if (savedPath != null) {
                            updatedArtworkUri = savedPath
                        }
                    }
                    val updatedEntity = existingEntity.copy(
                        title = sanitizedTitle,
                        artist = sanitizedArtist,
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
            val sanitizedEmail = sanitizeText(DataKeeper.email ?: "")
            val songId = song.id
            songDao.deleteUserSongLink(sanitizedEmail, songId)
            songDao.deleteLikedSongLink(sanitizedEmail, songId)
            songDao.deleteTimestampForUserSong(sanitizedEmail, songId)
            val remainingUsers = songDao.countUsersForSong(songId)
            if (remainingUsers == 0) {
                if (song.artworkUri.isNotEmpty()) {
                    try {
                        val artworkFile = File(song.artworkUri)
                        if (artworkFile.exists()) {
                            artworkFile.delete()
                        }
                    } catch (e: Exception) {
                        // Handle silently
                    }
                }
                songDao.deleteSongById(songId)
            }
            onDeleted()
        }
    }

    private fun isValidUri(uri: Uri, contentResolver: ContentResolver): Boolean {
        return try {
            val scheme = uri.scheme
            if (scheme != ContentResolver.SCHEME_CONTENT && scheme != ContentResolver.SCHEME_FILE) {
                return false
            }
            contentResolver.openInputStream(uri)?.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidSong(song: Song): Boolean {
        return song.uri.isNotBlank() && song.title.isNotBlank() && song.artist.isNotBlank()
    }

    private fun isValidEmail(email: String): Boolean {
        val emailPattern = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
            Pattern.CASE_INSENSITIVE
        )
        return emailPattern.matcher(email.trim()).matches()
    }

    private fun sanitizeText(text: String): String {
        val maxLength = 100
        val safeText = text.replace(Regex("[<>\"&]"), "")
        return if (safeText.length > maxLength) safeText.substring(0, maxLength) else safeText
    }

    private fun sanitizeFileName(fileName: String): String {
        val maxLength = 100
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "")
        return if (safeName.length > maxLength) safeName.substring(0, maxLength) else safeName
    }
}
