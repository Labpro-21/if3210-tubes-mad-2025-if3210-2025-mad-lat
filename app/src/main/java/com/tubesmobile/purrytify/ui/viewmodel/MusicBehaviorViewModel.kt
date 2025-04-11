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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PlaybackMode {
    REPEAT,
    REPEAT_ONE,
    SHUFFLE
}

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
    val playlist: List<Song> get() = _playlist

    private val _playbackMode = MutableStateFlow(PlaybackMode.REPEAT)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode


    private var currentIndex = -1

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle


    private var mediaPlayer: MediaPlayer? = null
    private var updateJob: Job? = null

    fun playSong(song: Song, context: Context) {
        _currentSong.value = song
        currentIndex = _playlist.indexOfFirst { it.uri == song.uri }
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.parse(song.uri))
                prepare()
                start()
                _duration.value = duration
                _isPlaying.value = true
                setOnCompletionListener {
                    playNext(context)
                }
            }
            startUpdatingProgress()
        } catch (e: Exception) {
            e.printStackTrace()
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
        _playlist.clear()
        _playlist.addAll(songs)
    }

    fun playNext(context: Context) {
        val list = _playlist
        if (list.isEmpty()) return

        when (_playbackMode.value) {
            PlaybackMode.REPEAT_ONE -> {
                _currentSong.value?.let {
                    playSong(it, context)
                }
            }

            PlaybackMode.SHUFFLE -> {
                val indices = list.indices - currentIndex
                if (indices.isNotEmpty()) {
                    currentIndex = indices.random()
                    playSong(list[currentIndex], context)
                }
            }

            PlaybackMode.REPEAT -> {
                currentIndex = (currentIndex + 1) % list.size
                playSong(list[currentIndex], context)
            }
        }
    }

    fun playPrevious(context: Context) {
        val list = _playlist
        if (list.isEmpty()) return

        currentIndex = if (_isShuffle.value) {
            (list.indices - currentIndex).random()
        } else {
            if (currentIndex - 1 < 0) list.size - 1 else currentIndex - 1
        }

        playSong(list[currentIndex], context)
    }

    fun cyclePlaybackMode() {
        _playbackMode.value = when (_playbackMode.value) {
            PlaybackMode.REPEAT -> PlaybackMode.REPEAT_ONE
            PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> PlaybackMode.REPEAT
        }
    }


    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}