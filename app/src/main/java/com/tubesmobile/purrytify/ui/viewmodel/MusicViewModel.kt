package com.tubesmobile.purrytify.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.tubesmobile.purrytify.ui.screens.Song
import android.media.MediaPlayer
import android.net.Uri

class MusicViewModel : ViewModel() {
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private var mediaPlayer: MediaPlayer? = null

    fun playSong(song: Song, context: Context) {
        _currentSong.value = song
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.parse(song.uri))
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}