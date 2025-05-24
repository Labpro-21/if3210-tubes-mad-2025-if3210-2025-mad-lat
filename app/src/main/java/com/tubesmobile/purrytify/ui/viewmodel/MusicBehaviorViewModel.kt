package com.tubesmobile.purrytify.ui.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tubesmobile.purrytify.data.local.db.AppDatabase
import com.tubesmobile.purrytify.data.local.db.entities.SongPlaybackHistoryEntity
import com.tubesmobile.purrytify.service.DataKeeper
import com.tubesmobile.purrytify.ui.screens.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class PlaybackMode {
    REPEAT,
    REPEAT_ONE,
    SHUFFLE
}

class MusicBehaviorViewModel(application: Application) : AndroidViewModel(application) {
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

    private val playbackHistoryDao = AppDatabase.getDatabase(application).songPlaybackHistoryDao()

    // Variabel untuk melacak sesi pemutaran saat ini
    private var currentPlaybackSessionId: Long? = null
    private var currentSessionSong: Song? = null
    private var currentSessionStartTimeMs: Long? = null

    // Format untuk bulan-tahun
    private val monthYearFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    fun playSong(song: Song, context: Context) {
        if (!isValidSong(song)) {
            return
        }

        finalizeCurrentPlaybackSession()

        val uri = Uri.parse(song.uri)
        if (!isValidUri(uri, context.contentResolver)) {
            return
        }

        _currentSong.value = song
        currentSessionSong = song
        currentIndex = _playlist.indexOfFirst { it.uri == song.uri }
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepareAsync()
                setOnPreparedListener {
                    start()
                    _duration.value = duration
                    _isPlaying.value = true
                    startNewPlaybackSession(song)
                }
                setOnCompletionListener {
                    finalizeCurrentPlaybackSession()
                    playNext(context)
                }
                setOnErrorListener { _, what, extra ->
                    _isPlaying.value = false
                    finalizeCurrentPlaybackSession()
                    true
                }
            }
            startUpdatingProgress()
        } catch (e: SecurityException) {
            finalizeCurrentPlaybackSession()
            Log.e("MusicBehaviorVM", "Error playing song: ${song.title}", e)
        }
    }

    private fun startNewPlaybackSession(song: Song) {
        viewModelScope.launch {
            val userEmail = DataKeeper.email ?: return@launch
            if (song.id == null) { // Pastikan song.id tidak null
                Log.e("MusicBehaviorVM", "Cannot start session, song ID is null for ${song.title}")
                return@launch
            }

            currentSessionStartTimeMs = System.currentTimeMillis()
            val playedAtMonthYear = monthYearFormat.format(Calendar.getInstance().time)

            val newEvent = SongPlaybackHistoryEntity(
                userEmail = userEmail,
                songId = song.id,
                songTitle = song.title,
                songArtist = song.artist,
                startTimestampMs = currentSessionStartTimeMs!!,
                durationListenedMs = 0,
                playedAtMonthYear = playedAtMonthYear
            )
            currentPlaybackSessionId = playbackHistoryDao.insertPlaybackEvent(newEvent)
            Log.d("MusicBehaviorVM", "Started playback session for ${song.title}, session ID: $currentPlaybackSessionId")
        }
    }

    private fun finalizeCurrentPlaybackSession() {
        viewModelScope.launch {
            val sessionId = currentPlaybackSessionId ?: return@launch
            val startTime = currentSessionStartTimeMs ?: return@launch
            val song = currentSessionSong ?: return@launch
            val userEmail = DataKeeper.email ?: return@launch
            if (song.id == null) return@launch


            val durationListened = System.currentTimeMillis() - startTime
            if (durationListened > 5000) {
                val playedAtMonthYear = monthYearFormat.format(Calendar.getInstance().time)
                val eventToUpdate = SongPlaybackHistoryEntity(
                    id = sessionId,
                    userEmail = userEmail,
                    songId = song.id,
                    songTitle = song.title,
                    songArtist = song.artist,
                    startTimestampMs = startTime,
                    durationListenedMs = durationListened,
                    playedAtMonthYear = playedAtMonthYear
                )
                playbackHistoryDao.updatePlaybackEvent(eventToUpdate)
                Log.d("MusicBehaviorVM", "Finalized playback session for ${song.title}, duration: $durationListened ms")
            } else {
                Log.d("MusicBehaviorVM", "Playback session for ${song.title} too short ($durationListened ms), not significantly updated.")
            }

            currentPlaybackSessionId = null
            currentSessionStartTimeMs = null
            currentSessionSong = null
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.pause()
                    _isPlaying.value = false
                    finalizeCurrentPlaybackSession()
                } else {
                    it.start()
                    _isPlaying.value = true
                    if (currentPlaybackSessionId == null && _currentSong.value != null) {
                        startNewPlaybackSession(_currentSong.value!!)
                    }
                }
            } catch (e: IllegalStateException) {
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
            }
        }
    }

    fun setPlaylist(songs: List<Song>) {
        _playlist.clear()
        _playlist.addAll(songs.filter { isValidSong(it) })
    }

    fun playNext(context: Context) {
        finalizeCurrentPlaybackSession()
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
        finalizeCurrentPlaybackSession()
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
        finalizeCurrentPlaybackSession()
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

    public override fun onCleared() {
        finalizeCurrentPlaybackSession()
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
            val scheme = uri.scheme?.lowercase()
            when (scheme) {
                ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE -> {
                    contentResolver.openInputStream(uri)?.close()
                    true
                }
                "http", "https" -> true
                null -> {
                    val file = File(uri.toString())
                    file.exists()
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidSong(song: Song): Boolean {
        return song.uri.isNotBlank() && song.title.isNotBlank() && song.artist.isNotBlank()
    }
}
