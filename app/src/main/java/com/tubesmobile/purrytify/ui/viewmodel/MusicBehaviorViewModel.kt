package com.tubesmobile.purrytify.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.tubesmobile.purrytify.ui.screens.Song
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
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

    private val _queue = mutableStateListOf<Song>()
    val queue: List<Song> get() = _queue

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
                setOnCompletionListener {
                    playNext(context)
                }
                start()
                _duration.value = duration
                _isPlaying.value = true
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
        if (_queue.isNotEmpty()) {
            val nextFromQueue = _queue.removeAt(0)
            Log.d("QUEUE_DEBUG", "Playing from queue: ${nextFromQueue.title}")
            playSong(nextFromQueue, context)
        } else {
            Log.d("QUEUE_DEBUG", "Queue is empty, playing from playlist")
            playNextFromPlaylist(context)
        }
    }


    private fun playNextFromPlaylist(context: Context) {
        val list = _playlist
        if (list.isEmpty()) return

        when (_playbackMode.value) {
            PlaybackMode.REPEAT_ONE -> {
                _currentSong.value?.let {
                    Log.d("QUEUE_DEBUG", "REPEAT_ONE: Replaying ${it.title}")
                    playSong(it, context)
                }
            }

            PlaybackMode.SHUFFLE -> {
                val indices = list.indices - currentIndex
                if (indices.isNotEmpty()) {
                    currentIndex = indices.random()
                    Log.d("QUEUE_DEBUG", "SHUFFLE: Playing ${list[currentIndex].title}")
                    playSong(list[currentIndex], context)
                }
            }

            PlaybackMode.REPEAT -> {
                currentIndex = (currentIndex + 1) % list.size
                Log.d("QUEUE_DEBUG", "REPEAT: Playing ${list[currentIndex].title}")
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

    fun addToQueue(song: Song) {
        _queue.add(song)
        Log.d("QUEUE_DEBUG", "Added to queue: ${song.title}, new queue size: ${_queue.size}")
    }

    fun playNextFromQueue(context: Context){
        if(_queue.isNotEmpty()){
            val nextSong = _queue.removeAt(0)
            playSong(nextSong, context)
        }
        else{
            playNext(context)
        }
    }

    fun playOrQueueNext(context: Context) {
        if (_queue.isNotEmpty()) {
            val nextSong = _queue.removeAt(0)
            Log.d("QUEUE", "Playing from queue: ${nextSong.title}")
            playSong(nextSong, context)
        } else {
            Log.d("QUEUE", "Queue empty, fallback to playlist next")
            playNext(context)
        }
    }


    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}