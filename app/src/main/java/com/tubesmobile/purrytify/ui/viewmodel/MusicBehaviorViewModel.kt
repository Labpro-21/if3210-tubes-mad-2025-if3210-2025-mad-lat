package com.tubesmobile.purrytify.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tubesmobile.purrytify.ui.screens.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException

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

    private val _selectedTab = MutableStateFlow("All Songs")
    val selectedTab: StateFlow<String> = _selectedTab

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
        val uri = Uri.parse(song.uri)
        if (!isValidUri(uri, context.contentResolver)) {
            return
        }

        _currentSong.value = song
        currentIndex = _playlist.indexOfFirst { it.uri == song.uri }
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                setOnCompletionListener {
                    playNext(context)
                }
                start()
                _duration.value = duration
                _isPlaying.value = true
            }
            startUpdatingProgress()
        } catch (e: SecurityException) {
            // Handle silently for production
        } catch (e: IOException) {
            // Handle silently for production
        } catch (e: Exception) {
            // Handle silently for production
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.pause()
                    _isPlaying.value = false
                } else {
                    it.start()
                    _isPlaying.value = true
                }
            } catch (e: IllegalStateException) {
                // Handle silently for production
            }
        }
    }

    private fun startUpdatingProgress() {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            while (true) {
                mediaPlayer?.let {
                    try {
                        _currentPosition.value = it.currentPosition
                    } catch (e: IllegalStateException) {
                        // Handle silently for production
                    }
                }
                delay(1000)
            }
        }
    }

    fun setSelectedTab(tab: String) {
        _selectedTab.value = tab
    }

    fun seekTo(position: Int) {
        mediaPlayer?.let {
            val validPosition = position.coerceIn(0, it.duration)
            try {
                it.seekTo(validPosition)
                _currentPosition.value = validPosition
            } catch (e: IllegalStateException) {
                // Handle silently for production
            }
        }
    }

    fun setPlaylist(songs: List<Song>) {
        _playlist.clear()
        _playlist.addAll(songs.filter { isValidSong(it) })
    }

    fun playNext(context: Context) {
        if (_queue.isNotEmpty()) {
            val nextFromQueue = _queue.removeAt(0)
            playSong(nextFromQueue, context)
        } else {
            playNextFromPlaylist(context)
        }
    }

    private fun playNextFromPlaylist(context: Context) {
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
        if (isValidSong(song)) {
            _queue.add(song)
        }
    }

    fun playNextFromQueue(context: Context) {
        if (_queue.isNotEmpty()) {
            val nextSong = _queue.removeAt(0)
            playSong(nextSong, context)
        } else {
            playNext(context)
        }
    }

    fun playOrQueueNext(context: Context) {
        if (_queue.isNotEmpty()) {
            val nextSong = _queue.removeAt(0)
            playSong(nextSong, context)
        } else {
            playNext(context)
        }
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
        if (_playlist.isEmpty()) {
            return false
        }
        return when (_playbackMode.value) {
            PlaybackMode.REPEAT -> true
            PlaybackMode.SHUFFLE -> _playlist.size > 1
            PlaybackMode.REPEAT_ONE -> _playlist.size > 1
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
        updateJob?.cancel()
        _currentSong.value = null
        _playlist.clear()
        _queue.clear()
        _currentPosition.value = 0
        _duration.value = 0
        _isPlaying.value = false
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
}