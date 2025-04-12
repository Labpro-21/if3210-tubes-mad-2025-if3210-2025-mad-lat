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
                if (list.size > 1) {
                    val newIndex = (list.indices - currentIndex).random()
                    currentIndex = newIndex
                }
                Log.d("QUEUE_DEBUG", "SHUFFLE: Playing ${list[currentIndex].title}")
                playSong(list[currentIndex], context)
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

        when (_playbackMode.value) {
            PlaybackMode.REPEAT_ONE -> {
                _currentSong.value?.let {
                    playSong(it, context)
                }
            }

            PlaybackMode.SHUFFLE -> {
                if (list.size > 1) {
                    val newIndex = (list.indices - currentIndex).random()
                    currentIndex = newIndex
                }
                playSong(list[currentIndex], context)
            }

            PlaybackMode.REPEAT -> {
                currentIndex = if (currentIndex <= 0) list.size - 1 else currentIndex - 1
                playSong(list[currentIndex], context)
            }
        }
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

    fun playNextFromQueue(context: Context) {
        playNext(context) // This already handles queue priority correctly
    }

    fun playOrQueueNext(context: Context) {
        playNext(context) // This already handles queue priority correctly
    }

    fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            reset()
        }

        _isPlaying.value = false
    }

    fun hasNextSong(): Boolean {
        if (_queue.isNotEmpty()) {
            return true
        }

        // If no songs in queue, check if there are more songs in the playlist
        if (_playlist.isEmpty()) {
            return false
        }

        // If in REPEAT mode, there's always a next song as long as playlist has songs
        if (_playbackMode.value == PlaybackMode.REPEAT) {
            return _playlist.size > 1
        }

        // If in SHUFFLE mode, there's a next song if playlist has more than one song
        if (_playbackMode.value == PlaybackMode.SHUFFLE) {
            return _playlist.size > 1
        }

        // If in REPEAT_ONE mode, there's technically no "next" song (it just repeats current)
        // But we'll return true if there are other songs in the playlist
        if (_playbackMode.value == PlaybackMode.REPEAT_ONE) {
            return _playlist.size > 1
        }

        // If current index is valid and not at the end of playlist
        return currentIndex >= 0 && currentIndex < _playlist.size - 1
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}