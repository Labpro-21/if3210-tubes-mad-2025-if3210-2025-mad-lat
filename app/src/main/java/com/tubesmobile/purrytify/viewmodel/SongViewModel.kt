package com.tubesmobile.purrytify.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tubesmobile.purrytify.data.local.db.AppDatabase
import com.tubesmobile.purrytify.data.local.db.entities.SongEntity
import com.tubesmobile.purrytify.ui.screens.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SongViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao = AppDatabase.Companion.getDatabase(application).songDao()

    val allSongs: Flow<List<Song>> =
        songDao.getSongsByUser("13522126@std.stei.itb.ac.id").map { entities ->
            entities.map { entity ->
                Song(
                    title = entity.title,
                    artist = entity.artist,
                    duration = entity.duration,
                    uri = entity.uri,
                    artworkUri = entity.artworkUri ?: ""
                )
            }
        }

    fun insertSong(song: Song){
        viewModelScope.launch {
            val entity = SongEntity(
                title = song.title,
                artist = song.artist,
                duration = song.duration,
                uri = song.uri,
                artworkUri = song.artworkUri
            )
            songDao.insertSong(entity)
        }
    }
}