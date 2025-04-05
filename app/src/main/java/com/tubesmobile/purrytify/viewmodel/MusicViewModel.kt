package com.tubesmobile.purrytify.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.tubesmobile.purrytify.ui.screens.Song

class MusicViewModel : ViewModel() {
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    fun playSong(song: Song) {
        _currentSong.value = song
    }
}