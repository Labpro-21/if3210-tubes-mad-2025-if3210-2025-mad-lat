package com.tubesmobile.purrytify.viewmodel

import android.app.Application
import android.content.ContentResolver
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
import java.util.regex.Pattern

class MusicDbViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao = AppDatabase.getDatabase(application).songDao()

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
        return try {
            retriever.setDataSource(context, uri)
            val art = retriever.embeddedPicture
            if (art != null && art.size <= 5 * 1024 * 1024) { 
                val filename = "artwork_${System.currentTimeMillis()}_${sanitizeFileName(uri.lastPathSegment ?: "artwork")}.jpg"
                val file = File(context.filesDir, filename)
                file.writeBytes(art)
                file.absolutePath
            } else {
                null
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
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
        onExists: () -> Unit
    ) {
        if (!isValidSong(song) || !isValidEmail(DataKeeper.email ?: "")) {
            return
        }

        viewModelScope.launch {
            val sanitizedEmail = sanitizeText(DataKeeper.email ?: "")
            val existsForUser = songDao.isSongExistsForUser(
                sanitizeText(song.title),
                sanitizeText(song.artist),
                sanitizedEmail
            )
            val exists = songDao.isSongExists(sanitizeText(song.title), sanitizeText(song.artist))

            if (existsForUser) {
                onExists()
            } else if (exists) {
                val songId = songDao.getSongId(sanitizeText(song.title), sanitizeText(song.artist))
                val registerUploader = SongUploader(
                    uploaderEmail = sanitizedEmail,
                    songId = songId
                )
                songDao.registerUserToSong(registerUploader.uploaderEmail, registerUploader.songId)
            } else {
                val savedArtworkPath = extractAndSaveArtwork(context, Uri.parse(song.uri)) ?: ""

                val entity = SongEntity(
                    title = sanitizeText(song.title),
                    artist = sanitizeText(song.artist),
                    duration = song.duration,
                    uri = song.uri,
                    artworkUri = savedArtworkPath
                )
                val newId = songDao.insertSong(entity).toInt()
                songDao.registerUserToSong(sanitizedEmail, newId)
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
