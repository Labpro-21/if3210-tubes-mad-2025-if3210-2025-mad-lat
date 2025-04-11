package com.tubesmobile.purrytify.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.tubesmobile.purrytify.ui.screens.Song
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.viewModelScope
import com.tubesmobile.purrytify.ui.screens.PlaybackMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MusicBehaviorViewModel : ViewModel() {
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration

    private val _playlist = mutableStateListOf<Song>()
//    val playlist: List<Song> get() = _playlist

    private val _playbackMode = MutableStateFlow(PlaybackMode.REPEAT)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode


//    private var currentIndex = -1

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle


    private var mediaPlayer: MediaPlayer? = null
    private var updateJob: Job? = null
    private var originalPlaylist: List<Song> = emptyList()
    private var currentPlaylist: List<Song> = emptyList()
    private var currentIndex: Int = -1

    fun playSong(song: Song, context: Context) {
        viewModelScope.launch {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.parse(song.uri))
                prepare()
                start()
                _isPlaying.value = true
                _duration.value = duration
                currentIndex = currentPlaylist.indexOf(song)
                _currentSong.value = song

                setOnCompletionListener {
                    when (_playbackMode.value) {
                        PlaybackMode.REPEAT -> playNext(context)
                        PlaybackMode.REPEAT_ONE -> {
                            seekTo(0)
                            start()
                        }
                        PlaybackMode.SHUFFLE -> playNext(context)
                    }
                }
            }

            while (isPlaying.value) {
                _currentPosition.value = mediaPlayer?.currentPosition ?: 0
                kotlinx.coroutines.delay(1000)
            }
        }
    }


    fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying){
                it.pause()
                _isPlaying.value = false
            }
            else{
                it.start()
                _isPlaying.value = true
            }
        }
    }

    private fun startUpdatingProgress() {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            while (true) {
                mediaPlayer?.let {
                    _currentPosition.value= it.currentPosition
                }
                delay(1000)
            }
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }


    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _currentPosition.value = position
    }


    fun setPlaylist(songs: List<Song>) {
        originalPlaylist = songs
        currentPlaylist = when (_playbackMode.value) {
            PlaybackMode.SHUFFLE -> songs.shuffled()
            else -> songs
        }
        currentIndex = currentPlaylist.indexOf(_currentSong.value).takeIf { it >= 0 } ?: -1
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        _playbackMode.value = mode
        when (mode) {
            PlaybackMode.SHUFFLE -> {
                currentPlaylist = originalPlaylist.shuffled()
                currentIndex = currentPlaylist.indexOf(_currentSong.value)
            }
            PlaybackMode.REPEAT, PlaybackMode.REPEAT_ONE -> {
                currentPlaylist = originalPlaylist
                currentIndex = currentPlaylist.indexOf(_currentSong.value)
            }
        }
    }

    fun playNext(context: Context) {
        if (currentPlaylist.isEmpty() || currentIndex < 0) {
            // If playlist is empty or index is invalid, try to start with the first song
            if (currentPlaylist.isNotEmpty()) {
                currentIndex = 0
                playSong(currentPlaylist[currentIndex], context)
            }
            return
        }
        currentIndex = if (currentIndex < currentPlaylist.size - 1) {
            currentIndex + 1
        } else {
            if (_playbackMode.value == PlaybackMode.SHUFFLE) {
                currentPlaylist = originalPlaylist.shuffled()
                0
            } else {
                0
            }
        }
        playSong(currentPlaylist[currentIndex], context)
    }

    fun playPrevious(context: Context) {
        if (currentPlaylist.isEmpty() || currentIndex < 0) {
            // If playlist is empty or index is invalid, try to start with the last song
            if (currentPlaylist.isNotEmpty()) {
                currentIndex = currentPlaylist.size - 1
                playSong(currentPlaylist[currentIndex], context)
            }
            return
        }
        currentIndex = if (currentIndex > 0) {
            currentIndex - 1
        } else {
            currentPlaylist.size - 1
        }
        playSong(currentPlaylist[currentIndex], context)
    }


    override fun onCleared() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onCleared()
    }
}