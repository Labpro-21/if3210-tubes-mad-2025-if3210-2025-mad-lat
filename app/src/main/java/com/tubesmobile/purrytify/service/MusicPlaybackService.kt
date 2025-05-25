package com.tubesmobile.purrytify.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tubesmobile.purrytify.MainActivity
import com.tubesmobile.purrytify.data.local.db.entities.SongPlayLogEntity
import com.tubesmobile.purrytify.ui.screens.Song
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import android.annotation.SuppressLint
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.os.Bundle
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.session.MediaButtonReceiver
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.data.model.ApiSong
import com.tubesmobile.purrytify.viewmodel.OnlineSongsViewModel
import kotlinx.coroutines.*
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

enum class PlaybackMode {
    REPEAT,
    REPEAT_ONE,
    SHUFFLE
}

data class AudioDevice(
    val name: String,
    val id: Int,
    val type: Int,
    val isConnected: Boolean
)

class MusicPlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {
    private val binder = MusicPlaybackBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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

    private val _playlist = mutableListOf<Song>()
    val playlist: List<Song> get() = _playlist

    private val _queue = mutableListOf<Song>()
    val queue: List<Song> get() = _queue

    private var currentSessionListenedDurationMillis: Long = 0L
    private var lastPlayTimestamp: Long = 0L
    private var currentSongStartTimeMillis: Long = 0L
    private val MIN_PLAY_DURATION_FOR_LOG_MS = 1000

    private val _playbackMode = MutableStateFlow(PlaybackMode.REPEAT)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode

    private val _audioDevices = MutableStateFlow<List<AudioDevice>>(emptyList())
    val audioDevices: StateFlow<List<AudioDevice>> = _audioDevices

    private val _currentAudioDevice = MutableStateFlow<AudioDevice?>(null)
    val currentAudioDevice: StateFlow<AudioDevice?> = _currentAudioDevice

    private val _audioError = MutableStateFlow<String?>(null)
    val audioError: StateFlow<String?> = _audioError

    private var currentIndex = -1
    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle

    private var userSelectedDeviceId: Int? = null

    private var mediaPlayer: MediaPlayer? = null
    private var updateJob: Job? = null
    private var audioManager: AudioManager? = null
    @SuppressLint("NewApi")
    private var audioFocusRequest: AudioFocusRequest? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isEmulator = Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator")
    private var isForegroundService = false
    private lateinit var notificationManager: NotificationManager
    private var currentIndexInPlaylist: Int = -1

    private var mediaSession: MediaSessionCompat? = null
    private var artworkBitmapCache: Bitmap? = null
    private var progressUpdateJob: Job? = null

    private val audioDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return
            if (isEmulator) {
                Log.d("MusicPlaybackService", "Skipping receiver on emulator")
                return
            }
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    updateAudioDevices()
                    if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                        setAudioOutputToSpeaker()
                        _audioError.value = "Bluetooth device disconnected. Switched to internal speaker."
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "MusicPlaybackSrv"
        const val NOTIFICATION_ID = 777 // ID unik untuk notifikasi
        const val CHANNEL_ID = "purrytify_music_playback_channel_v1" // ID unik untuk channel

        const val ACTION_PLAY_NEW_PLAYLIST = "com.tubesmobile.purrytify.ACTION_PLAY_NEW_PLAYLIST"
        const val ACTION_PLAY_SONG_AT_INDEX = "com.tubesmobile.purrytify.ACTION_PLAY_SONG_AT_INDEX"
        const val ACTION_SEEK_TO = "com.tubesmobile.purrytify.ACTION_SEEK_TO"

        const val EXTRA_PLAYLIST = "com.tubesmobile.purrytify.EXTRA_PLAYLIST"
        const val EXTRA_SONG_INDEX = "com.tubesmobile.purrytify.EXTRA_SONG_INDEX"
        const val EXTRA_SEEK_POSITION = "com.tubesmobile.purrytify.EXTRA_SEEK_POSITION"
        const val ACTION_STOP_SERVICE = "com.tubesmobile.purrytify.ACTION_STOP_SERVICE"

        var currentSessionToken: MediaSessionCompat.Token? = null
            private set
    }

    inner class MusicPlaybackBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    fun onCleared() {
        mediaPlayer?.release()
        mediaPlayer = null
        updateJob?.cancel()
        _currentSong.value = null
        _playlist.clear()
        _queue.clear()
        _currentPosition.value = 0
        _duration.value = 0
        _isPlaying.value = false
        _audioDevices.value = emptyList()
        _currentAudioDevice.value = null
        _audioError.value = null
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Service is being created.")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initializeAudioRouting()
        initializeMediaPlayer()
        initializeMediaSession()
        createNotificationChannel()
    }

    private fun initializeMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener(this@MusicPlaybackService::onMediaPlayerPrepared)
            setOnCompletionListener(this@MusicPlaybackService::onMediaPlayerCompletion)
            setOnErrorListener(this@MusicPlaybackService::onMediaPlayerError)
        }
        Log.d(TAG, "MediaPlayer initialized.")
    }

    private fun onMediaPlayerPrepared(mp: MediaPlayer) {
        Log.d(TAG, "MediaPlayer Prepared. Duration: ${mp.duration}ms. Starting...")
        _duration.value = mp.duration
        mp.start()
        _isPlaying.value = true
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, mp.currentPosition.toLong())
        updateMediaSessionMetadata()
        Log.i(TAG, "onMediaPlayerPrepared: Calling startForegroundNotificationAndUpdate()")
        startForegroundNotificationAndUpdate()
        startProgressUpdater()
    }

    private fun onMediaPlayerCompletion(mp: MediaPlayer) {
        Log.d(TAG, "MediaPlayer onCompletion.")
        _isPlaying.value = false
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, mp.duration.toLong())
        progressUpdateJob?.cancel()
        mediaSession?.controller?.transportControls?.skipToNext()
    }

    private fun onMediaPlayerError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        Log.e(TAG, "MediaPlayer Error - What: $what, Extra: $extra")
        _isPlaying.value = false
        updatePlaybackState(PlaybackStateCompat.STATE_ERROR, mp?.currentPosition?.toLong() ?: 0L)
        updateNotification()
        return true
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action: ${intent?.action}")

        if (intent != null && Intent.ACTION_MEDIA_BUTTON == intent.action) {
            MediaButtonReceiver.handleIntent(mediaSession, intent)
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_PLAY_NEW_PLAYLIST -> {
                val parcelablePlaylist = intent.getParcelableArrayListExtra<Song>(EXTRA_PLAYLIST)
                if (!parcelablePlaylist.isNullOrEmpty()) {
                    _playlist.clear()
                    _playlist.addAll(parcelablePlaylist) // Simpan playlist ke service
                    currentIndexInPlaylist = intent.getIntExtra(EXTRA_SONG_INDEX, 0).coerceIn(0, _playlist.size - 1)
                    Log.d(TAG, "Playing new playlist, size: ${_playlist.size}, starting at index: $currentIndexInPlaylist")
                    playSongNow(_playlist[currentIndexInPlaylist])
                } else {
                    Log.w(TAG, "Received PLAY_NEW_PLAYLIST with empty or null playlist.")
                }
            }
            ACTION_PLAY_SONG_AT_INDEX -> {
                val index = intent.getIntExtra(EXTRA_SONG_INDEX, currentIndexInPlaylist)
                if (index >= 0 && index < _playlist.size) {
                    currentIndexInPlaylist = index
                    playSongNow(_playlist[currentIndexInPlaylist])
                } else {
                    Log.w(TAG, "Invalid index $index for ACTION_PLAY_SONG_AT_INDEX. Playlist size: ${_playlist.size}")
                }
            }
            ACTION_SEEK_TO -> {
                val pos = intent.getLongExtra(EXTRA_SEEK_POSITION, 0L)
                mediaSessionCallback.onSeekTo(pos)
            }
            ACTION_STOP_SERVICE -> {
                mediaSessionCallback.onStop()
            }
        }
        return START_NOT_STICKY
    }

    private fun playSongNow(song: Song) {
        Log.i(TAG, "playSongNow: Title='${song.title}', URI='${song.uri}'")
        if (!requestAudioFocus()) {
            Log.w(TAG, "Audio focus request failed for ${song.title}. Playback aborted.")
            _audioError.value = "Could not get audio focus to play music."
            return
        }

        _currentSong.value = song
        artworkBitmapCache = null // Reset cache artwork untuk lagu baru

        // Ambil artwork di background thread
        serviceScope.launch {
            artworkBitmapCache = getArtworkBitmapFromUri(song.artworkUri)
            updateMediaSessionMetadata()
            if (isForegroundService && mediaPlayer?.isPlaying == true) { // Hanya update notif jika sudah foreground dan playing
                updateNotification()
            } else if (mediaPlayer?.isPlaying == true) { // Jika playing tapi belum foreground
                startForegroundNotificationAndUpdate()
            }
        }

        try {
            mediaPlayer?.reset()
            val uri = Uri.parse(song.uri)
            when (uri.scheme?.lowercase()) {
                "http", "https" -> mediaPlayer?.setDataSource(song.uri)
                "content", "file" -> mediaPlayer?.setDataSource(applicationContext, uri)
                else -> {
                    if (File(song.uri).exists()) mediaPlayer?.setDataSource(song.uri)
                    else throw IOException("Unsupported URI scheme or invalid file path: ${song.uri}")
                }
            }
            mediaPlayer?.prepareAsync()
            updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING, 0L)
            Log.d(TAG, "MediaPlayer prepareAsync called for ${song.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting data source or preparing MediaPlayer for ${song.title}", e)
            _audioError.value = "Error playing song: ${e.localizedMessage}"
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR, 0L)
            _isPlaying.value = false
        }
    }

    private fun startProgressUpdater() {
        progressUpdateJob?.cancel()
        if (mediaPlayer == null) return // Jangan mulai jika media player tidak ada

        progressUpdateJob = serviceScope.launch {
            Log.d(TAG, "ProgressUpdater started.")
            while (isActive) { // Cek coroutine isActive
                try {
                    mediaPlayer?.let { mp ->
                        if (mp.isPlaying) { // Hanya update jika sedang playing
                            val currentPos = mp.currentPosition
                            _currentPosition.value = currentPos
                            if (mediaSession?.controller?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
                                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, currentPos.toLong())
                            }
                        }
                    }
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "MediaPlayer state error during progress update: ${e.message}. Stopping updater.")
                    break // Hentikan loop jika player dalam state tidak valid
                }
                delay(1000) // Update setiap detik
            }
            Log.d(TAG, "ProgressUpdater stopped.")
        }
    }

    private fun startForegroundNotificationAndUpdate() {
        val currentSongToDisplay = _currentSong.value // Ambil nilai saat ini
        Log.d(TAG, "startForegroundNotificationAndUpdate called. Current song: ${currentSongToDisplay?.title}, isForeground: $isForegroundService")
        if (currentSongToDisplay != null) {
            val notification = buildMusicNotification()
            if (!isForegroundService) {
                try {
                    startForeground(NOTIFICATION_ID, notification)
                    isForegroundService = true
                    Log.i(TAG, "Service successfully started in foreground with notification for: ${currentSongToDisplay.title}")
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL ERROR starting foreground service", e)
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID, notification)
                Log.d(TAG, "Foreground notification updated for: ${currentSongToDisplay.title}")
            }
        } else {
            Log.w(TAG, "Cannot start/update foreground notification: currentSong is null.")
            // Jika tidak ada lagu dan service sedang foreground, mungkin hentikan foreground mode
            if (isForegroundService) {
                stopForeground(true) // Hapus notifikasi jika tidak ada lagu
                isForegroundService = false
                Log.d(TAG, "No current song, stopping foreground state and removing notification.")
            }
        }
    }

    private fun updateNotification() {
        if (isForegroundService && _currentSong.value != null) { // Hanya update jika memang sudah foreground
            notificationManager.notify(NOTIFICATION_ID, buildMusicNotification())
            Log.d(TAG, "Notification updated (isForeground=true).")
        } else if (!isForegroundService && _currentSong.value != null && !_isPlaying.value) {
            Log.d(TAG, "Service not foreground or no current song, not updating notification via notify().")
        }
    }

    private fun updateMediaSessionMetadata() {
        val song = _currentSong.value
        Log.d(TAG, "Updating MediaSession metadata for song: ${song?.title}")
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song?.title ?: "No Title")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song?.artist ?: "No Artist")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer?.duration?.toLong() ?: song?.duration ?: 0L)

        artworkBitmapCache?.let {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
        } ?: run {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, getDefaultArtworkBitmap())
        }
        mediaSession?.setMetadata(metadataBuilder.build())
    }

    private fun updatePlaybackState(state: Int, positionMs: Long) {
        val playbackSpeed = if (state == PlaybackStateCompat.STATE_PLAYING) 1.0f else 0.0f
        val availableActions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                (if (state == PlaybackStateCompat.STATE_PLAYING) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY)

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(availableActions)
            .setState(state, positionMs, playbackSpeed, System.currentTimeMillis())
        mediaSession?.setPlaybackState(stateBuilder.build())
        Log.d(TAG, "PlaybackState updated to: ${stateToString(state)}, Position: $positionMs")

        if (state == PlaybackStateCompat.STATE_PLAYING) {
            if (progressUpdateJob == null || !progressUpdateJob!!.isActive) {
                startProgressUpdater()
            }
        } else {
            progressUpdateJob?.cancel()
        }
    }
    private fun stateToString(state: Int): String { // Helper untuk logging
        return when (state) {
            PlaybackStateCompat.STATE_NONE -> "NONE"
            PlaybackStateCompat.STATE_STOPPED -> "STOPPED"
            PlaybackStateCompat.STATE_PAUSED -> "PAUSED"
            PlaybackStateCompat.STATE_PLAYING -> "PLAYING"
            else -> "UNKNOWN_STATE ($state)"
        }
    }

    private fun buildMusicNotification(): Notification {
        val songToDisplay = _currentSong.value
        val isPlayingCurrently = _isPlaying.value

        val openPlayerIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = "SHOW_PLAYER_FROM_NOTIFICATION" // Action kustom
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("song_id_to_show", songToDisplay?.id ?: -1)
            putExtra("is_from_api_song_to_show", songToDisplay?.uri?.startsWith("http") ?: false)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(), // Request code unik untuk menghindari caching PendingIntent yang salah
            openPlayerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopActionPendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)

        val prevPendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        val playPausePendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)
        val nextPendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)

        val largeIconBitmap = artworkBitmapCache ?: getDefaultArtworkBitmap()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.mono)
            .setContentTitle(songToDisplay?.title ?: "Purrytify Music")
            .setContentText(songToDisplay?.artist ?: "Now Playing")
            .setSubText(if (songToDisplay != null) "Playing" else null) // Teks tambahan kecil
            .setLargeIcon(largeIconBitmap)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopActionPendingIntent) // Dipanggil saat notifikasi di-dismiss (swipe)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Agar muncul di lock screen
            .setOngoing(isPlayingCurrently) // Jika true, notifikasi tidak bisa di-swipe oleh pengguna
            .setSilent(true)

            // Menambahkan tombol aksi
            .addAction(R.drawable.ic_previous, "Previous", prevPendingIntent)
            .addAction(
                if (isPlayingCurrently) R.drawable.ic_pause else R.drawable.ic_add,
                if (isPlayingCurrently) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(R.drawable.ic_skip, "Next", nextPendingIntent)

            // Menerapkan MediaStyle
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken) // Hubungkan dengan MediaSession
                    .setShowActionsInCompactView(0, 1, 2) // Indeks: Previous, Play/Pause, Next
                    .setShowCancelButton(true) // Tampilkan tombol 'X' (close) bawaan sistem
                    .setCancelButtonIntent(stopActionPendingIntent) // Aksi untuk tombol 'X'
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Menambahkan progress bar untuk Android 12 (API 31) ke atas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val maxProgress = _duration.value
            val currentProgress = _currentPosition.value
            if (maxProgress > 0 && (isPlayingCurrently || _currentSong.value != null) ) { // Tampilkan jika ada lagu & durasi valid
                builder.setProgress(maxProgress, currentProgress, false)
            } else {
                builder.setProgress(0,0,false) // Sembunyikan jika tidak ada durasi/tidak playing
            }
        }
        Log.d(TAG, "Notification built. isPlaying: $isPlayingCurrently, Title: ${songToDisplay?.title}")
        return builder.build()
    }

    private suspend fun getArtworkBitmapFromUri(uriString: String?): Bitmap? {
        if (uriString.isNullOrEmpty()) {
            Log.d(TAG, "Artwork URI is null or empty.")
            return getDefaultArtworkBitmap()
        }
        return withContext(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            try {
                val uri = Uri.parse(uriString)
                Log.d(TAG, "Attempting to load artwork from URI: $uri, Scheme: ${uri.scheme}")
                when (uri.scheme?.lowercase()) {
                    "http", "https" -> {
                        val url = URL(uriString)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.doInput = true
                        connection.connectTimeout = 5000 // Timeout koneksi 5 detik
                        connection.readTimeout = 10000    // Timeout baca 10 detik
                        connection.connect()
                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                            val input: InputStream = connection.inputStream
                            bitmap = BitmapFactory.decodeStream(input)
                            input.close()
                            Log.d(TAG, "Artwork loaded from network: $uriString")
                        } else {
                            Log.w(TAG, "Network request for artwork failed with code: ${connection.responseCode} for $uriString")
                        }
                        connection.disconnect()
                    }
                    "content", "file" -> {
                        applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                            bitmap = BitmapFactory.decodeStream(stream)
                            Log.d(TAG, "Artwork loaded from local URI: $uriString")
                        } ?: Log.w(TAG, "Could not open InputStream for local URI: $uriString")
                    }
                    else -> Log.w(TAG, "Unsupported URI scheme for artwork: ${uri.scheme}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading artwork bitmap from URI: $uriString", e)
            }
            bitmap ?: getDefaultArtworkBitmap() // Kembalikan default jika gagal atau null
        }
    }

    @SuppressLint("NewApi")
    private fun requestAudioFocus(): Boolean {
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
            setAudioAttributes(AudioAttributes.Builder().run {
                setUsage(AudioAttributes.USAGE_MEDIA)
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                build()
            })
            setAcceptsDelayedFocusGain(true)
            setOnAudioFocusChangeListener(this@MusicPlaybackService)
            build()
        }
        audioFocusRequest = focusRequest
        val result = audioManager?.requestAudioFocus(focusRequest)

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "Audio focus granted.")
            return true
        }
        Log.w(TAG, "Audio focus denied.")
        return false
    }

    private fun abandonAudioFocus() {
        Log.d(TAG, "Attempting to abandon audio focus.")
        val listener: AudioManager.OnAudioFocusChangeListener = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager?.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(listener)
        }
        audioFocusRequest = null
    }

    override fun onAudioFocusChange(focusChange: Int) {
        Log.d(TAG, "onAudioFocusChange: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.i(TAG, "AudioFocus: LOSS. Triggering stop.")
                mediaSession?.controller?.transportControls?.stop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.i(TAG, "AudioFocus: LOSS_TRANSIENT. Triggering pause.")
                mediaSession?.controller?.transportControls?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.i(TAG, "AudioFocus: LOSS_TRANSIENT_CAN_DUCK. Lowering volume.")
                mediaPlayer?.setVolume(0.3f, 0.3f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "AudioFocus: GAIN. Restoring volume.")
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
        }
    }

    private fun initializeMediaSession() {
        val mediaButtonReceiver = ComponentName(applicationContext, MediaButtonReceiver::class.java)
        mediaSession = MediaSessionCompat(this, TAG, mediaButtonReceiver, null).apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(mediaSessionCallback) // Set callback untuk handle perintah media

            val sessionActivityIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val sessionActivityPendingIntent = PendingIntent.getActivity(
                applicationContext, 0, sessionActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setSessionActivity(sessionActivityPendingIntent)

            isActive = true // PENTING: Aktifkan session
            currentSessionToken = this.sessionToken // Simpan token untuk diakses UI/ViewModel
            Log.i(TAG, "MediaSession initialized and active. Token: $currentSessionToken")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Allows control of music playback from Purrytify"
                setShowBadge(false)
                setSound(null, null) // Tidak ada suara notifikasi default
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel '$CHANNEL_ID' created/updated.")
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Log.d(TAG, "MediaSessionCallback: onPlay")
            if (!requestAudioFocus()) {
                Log.w(TAG, "Audio focus not granted in onPlay.")
                return
            }
            if (!requestAudioFocus()) return // Minta audio focus sebelum play
            mediaPlayer?.start()
            _isPlaying.value = true
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, mediaPlayer?.currentPosition?.toLong() ?: 0L)
            Log.i(TAG, "MediaSessionCallback onPlay: Calling startForegroundNotificationAndUpdate()")
            startForegroundNotificationAndUpdate()
            startProgressUpdater()
        }

        override fun onPause() {
            Log.d(TAG, "MediaSessionCallback: onPause")
            mediaPlayer?.pause()
            _isPlaying.value = false
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, mediaPlayer?.currentPosition?.toLong() ?: 0L)
            updateNotification()
            stopForeground(false) // false = jangan hapus notifikasi, service tidak lagi high priority
            progressUpdateJob?.cancel()
        }

        override fun onStop() { // Dipanggil saat swipe notif atau tombol stop eksplisit
            Log.d(TAG, "MediaSessionCallback: onStop. Stopping service and playback.")
            stopPlaybackAndService()
        }

        private fun stopPlaybackAndService() {
            Log.i(TAG, "stopPlaybackAndService called: Stopping playback and service.")

            // 1. Hentikan dan reset MediaPlayer
            mediaPlayer?.let {
                try {
                    if (it.isPlaying) {
                        it.stop()
                        Log.d(TAG, "MediaPlayer stopped.")
                    }
                    it.reset() // Reset untuk melepaskan resource dan kembali ke state Idle
                    Log.d(TAG, "MediaPlayer reset.")
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "IllegalStateException during MediaPlayer stop/reset: ${e.message}")
                    // MediaPlayer mungkin sudah dalam state yang tidak valid, coba release saja
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during MediaPlayer stop/reset: ${e.message}")
                }
            }

            // 2. Update StateFlows internal Service
            _isPlaying.value = false
            _currentSong.value = null // Hapus informasi lagu saat ini
            _currentPosition.value = 0
            _duration.value = 0
            artworkBitmapCache = null // Hapus cache artwork

            // 3. Update MediaSession state ke STOPPED dan kosongkan metadata
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED, 0L)
            updateMediaSessionMetadata() // Ini akan mengirim metadata null atau default

            // 4. Batalkan job updater progress
            progressUpdateJob?.cancel()
            Log.d(TAG, "Progress updater job cancelled.")

            // 5. Lepaskan Audio Focus
            abandonAudioFocus()

            // 6. Hentikan Foreground Service dan hapus Notifikasi
            if (isForegroundService) {
                stopForeground(true) // 'true' untuk menghapus notifikasi
                isForegroundService = false
                Log.d(TAG, "Service stopped from foreground, notification removed.")
            }

            // 7. Hentikan Service itu sendiri
            Log.i(TAG, "Calling stopSelf() to terminate the service.")
            stopSelf() // Ini akan memicu onDestroy() jika tidak ada lagi start command atau binding
        }

        override fun onSkipToNext() {
            Log.d(TAG, "MediaSessionCallback: onSkipToNext")
            if (_playlist.isEmpty()) return
            val currentMode = _playbackMode.value // Ambil dari StateFlow
            when (currentMode) {
                PlaybackMode.REPEAT_ONE -> { /* Tetap mainkan lagu saat ini, atau update posisi ke 0 */
                    _currentSong.value?.let { playSongNow(it) }
                }
                PlaybackMode.SHUFFLE -> {
                    if (_playlist.size > 1) {
                        var nextIdx = currentIndexInPlaylist
                        while (nextIdx == currentIndexInPlaylist) { nextIdx = _playlist.indices.random() }
                        currentIndexInPlaylist = nextIdx
                    } else {
                        currentIndexInPlaylist = 0 // Jika hanya 1 lagu
                    }
                    playSongNow(_playlist[currentIndexInPlaylist])
                }
                PlaybackMode.REPEAT -> {
                    currentIndexInPlaylist = (currentIndexInPlaylist + 1) % _playlist.size
                    playSongNow(_playlist[currentIndexInPlaylist])
                }
            }
        }

        override fun onSkipToPrevious() {
            Log.d(TAG, "MediaSessionCallback: onSkipToPrevious")
            if (_playlist.isEmpty()) return
            // Implementasi logika previous
            val currentMode = _playbackMode.value
            when (currentMode) {
                PlaybackMode.REPEAT_ONE -> {
                    _currentSong.value?.let { playSongNow(it) }
                }
                PlaybackMode.SHUFFLE -> { // Prev di shuffle bisa jadi lagu random lain
                    if (_playlist.size > 1) {
                        var prevIdx = currentIndexInPlaylist
                        while (prevIdx == currentIndexInPlaylist) { prevIdx = _playlist.indices.random() }
                        currentIndexInPlaylist = prevIdx
                    } else {
                        currentIndexInPlaylist = 0
                    }
                    playSongNow(_playlist[currentIndexInPlaylist])
                }
                PlaybackMode.REPEAT -> {
                    currentIndexInPlaylist = if (currentIndexInPlaylist > 0) currentIndexInPlaylist - 1 else _playlist.size - 1
                    playSongNow(_playlist[currentIndexInPlaylist])
                }
            }
        }

        override fun onSeekTo(pos: Long) {
            Log.d(TAG, "MediaSessionCallback: onSeekTo $pos")
            mediaPlayer?.let { mp ->
                // 1. Perintahkan MediaPlayer untuk seek ke posisi baru
                val newPositionInt = pos.toInt().coerceIn(0, mp.duration) // Pastikan posisi valid
                mp.seekTo(newPositionInt)

                // 2. Update StateFlow _currentPosition internal
                _currentPosition.value = newPositionInt

                // 3. Tentukan status PlaybackStateCompat saat ini
                //    Operasi seekTo() pada MediaPlayer tidak mengubah status isPlaying-nya.
                //    Jadi, kita gunakan status _isPlaying.value yang seharusnya sudah benar.
                val currentStateCompat = if (_isPlaying.value) { // _isPlaying.value dari service
                    PlaybackStateCompat.STATE_PLAYING
                } else {
                    PlaybackStateCompat.STATE_PAUSED
                }

                // 4. Update PlaybackStateCompat dari MediaSession
                updatePlaybackState(currentStateCompat, newPositionInt.toLong())
                Log.d(TAG, "Seek complete. PlaybackState updated to: $currentStateCompat at $newPositionInt")
            }
        }

        override fun onPlayFromUri(uri: Uri?, extras: Bundle?) {
            uri?.let {
                val title = extras?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "Unknown Title"
                val artist = extras?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                // Ambil detail lain dari extras jika ada
                val songToPlay = Song(id = 0, title = title, artist = artist, duration = 0, uri = uri.toString(), artworkUri = "")

                _playlist.clear()
                _playlist.add(songToPlay)
                currentIndexInPlaylist = 0
                playSongNow(songToPlay)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        serviceJob.cancel()
        abandonAudioFocus()
        currentSessionToken = null
        mediaSession?.release()
        updateJob?.cancel()
        serviceScope.cancel()
        _currentSong.value = null
        _playlist.clear()
        _queue.clear()
        _currentPosition.value = 0
        _duration.value = 0
        _isPlaying.value = false
        _audioDevices.value = emptyList()
        _currentAudioDevice.value = null
        _audioError.value = null
        if (!isEmulator) {
            try {
                unregisterReceiver(audioDeviceReceiver)
            } catch (e: IllegalArgumentException) {
                Log.e("MusicPlaybackService", "Receiver not registered: ${e.message}")
            }
        }
    }

    internal fun initializeAudioRouting() {
        startMonitoringAudioOutput()
        Log.d("MusicPlaybackService", "initializeAudioRouting: isEmulator=$isEmulator")
        if (isEmulator) {
            _audioError.value = "Audio routing not supported on emulator. Using internal speaker."
            setAudioOutputToSpeaker()
            return
        }
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                _audioError.value = "Bluetooth is not supported on this device."
                setAudioOutputToSpeaker()
                return
            }
            updateAudioDevices()
            registerAudioDeviceReceiver()
        } catch (e: Exception) {
            _audioError.value = "Error initializing audio routing: ${e.message}"
            setAudioOutputToSpeaker()
        }
    }

    private fun registerAudioDeviceReceiver() {
        if (isEmulator) {
            Log.d("MusicPlaybackService", "Skipping receiver registration on emulator")
            return
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        try {
            ContextCompat.registerReceiver(this, audioDeviceReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } catch (e: Exception) {
            _audioError.value = "Failed to register Bluetooth receiver: ${e.message}"
        }
    }

    private fun startMonitoringAudioOutput() {
        serviceScope.launch {
            while (true) {
                val newDevice = getCurrentRoutedDevice()
                if (userSelectedDeviceId == null || newDevice?.id == userSelectedDeviceId) {
                    _currentAudioDevice.value = newDevice
                    Log.d("AudioRouting", "Detected audio switch to: ${newDevice?.name}")
                }
                delay(3000)
            }
        }
    }

    private fun getCurrentRoutedDevice(): AudioDevice? {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val routedDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val selected = routedDevices.find {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        }
        return if (selected != null) {
            AudioDevice(
                name = selected.productName?.toString() ?: "Bluetooth Device",
                id = selected.id,
                type = selected.type,
                isConnected = true
            )
        } else {
            AudioDevice("Internal Speaker", -1, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, true)
        }
    }

    fun updateAudioDevices() {
        Log.d("MusicPlaybackService", "updateAudioDevices: isEmulator=$isEmulator")
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = mutableListOf<AudioDevice>()

        devices.add(AudioDevice("Internal Speaker", -1, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, true))

        if (isEmulator) {
            _audioDevices.value = devices
            if (_currentAudioDevice.value == null) {
                _currentAudioDevice.value = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            }
            Log.d("MusicPlaybackService", "updateAudioDevices: devices=${_audioDevices.value}")
            return
        }

        try {
            val availableDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            availableDevices.forEach { device ->
                if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                    devices.add(AudioDevice(
                        name = device.productName?.toString() ?: "Unknown Device",
                        id = device.id,
                        type = device.type,
                        isConnected = true
                    ))
                }
            }
        } catch (e: Exception) {
            _audioError.value = "Error detecting audio devices: ${e.message}"
        }

        _audioDevices.value = devices

        val preferredId = mediaPlayer?.preferredDevice?.id
        val matchingDevice = devices.find { it.id == preferredId }

        _currentAudioDevice.value = matchingDevice ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

        Log.d("MusicPlaybackService", "updateAudioDevices: current=${_currentAudioDevice.value?.name}")
    }

    fun selectAudioDevice(device: AudioDevice) {
        if (isEmulator) {
            _audioError.value = "Device selection not supported on emulator."
            return
        }

        userSelectedDeviceId = device.id

        try {
            if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                setAudioOutputToSpeaker()
                return
            } else {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val availableDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val targetDevice = availableDevices.find { it.id == device.id }
                if (targetDevice != null) {
                    mediaPlayer?.setPreferredDevice(targetDevice)
                    _currentAudioDevice.value = device
                    _audioError.value = null
                } else {
                    _audioError.value = "Selected device not available"
                    setAudioOutputToSpeaker()
                }
            }
        } catch (e: Exception) {
            _audioError.value = "Error selecting audio device: ${e.message}"
            setAudioOutputToSpeaker()
        }
    }

    fun clearAudioError() {
        _audioError.value = null
    }

    private fun setAudioOutputToSpeaker() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            mediaPlayer?.setPreferredDevice(null)
            audioManager.isSpeakerphoneOn = true
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            _currentAudioDevice.value = _audioDevices.value.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        } catch (e: Exception) {
            _audioError.value = "Error setting speaker output: ${e.message}"
        }
    }

    fun playSong(song: Song, musicDbViewModel: MusicDbViewModel, onlineSongsViewModel: OnlineSongsViewModel) {
        Log.i(TAG, "Service command: playSong for '${song.title}'")

        // Validasi lagu
        if (!isValidSong(song)) {
            _audioError.value = "Invalid song data for '${song.title}'"
            Log.e(TAG, "playSong: Invalid song data for ${song.title}")
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR, 0L)
            updateNotification()
            return
        }

        // Validasi URI
        val uri = Uri.parse(song.uri)
        if (!isValidUri(uri)) {
            _audioError.value = "Invalid song URI for '${song.title}'"
            Log.e(TAG, "playSong: Invalid song URI ${song.uri}")
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR, 0L)
            updateNotification()
            return
        }

        // Log durasi lagu sebelumnya (jika ada)
        logCurrentSongPlayDuration(musicDbViewModel)

        // Update state lagu saat ini
        _currentSong.value = song
        currentSessionListenedDurationMillis = 0L
        lastPlayTimestamp = System.currentTimeMillis()
        currentIndex = _playlist.indexOfFirst { it.uri == song.uri }

        // Minta audio focus sebelum memulai playback
        if (!requestAudioFocus()) {
            Log.w(TAG, "Audio focus request failed for ${song.title}. Playback aborted.")
            _audioError.value = "Could not get audio focus."
            return
        }

        // Ambil artwork di background
        serviceScope.launch {
            artworkBitmapCache = getArtworkBitmapFromUri(song.artworkUri)
            updateMediaSessionMetadata() // Update metadata dengan artwork
        }

        try {
            // Release MediaPlayer lama
            Log.d(TAG, "Releasing old MediaPlayer instance if any.")
            mediaPlayer?.release()

            // Buat MediaPlayer baru
            Log.d(TAG, "Creating new MediaPlayer instance for ${song.title}")
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                // Set data source berdasarkan skema URI
                val scheme = uri.scheme?.lowercase()
                Log.d(TAG, "Setting MediaPlayer dataSource with URI: $uri, Scheme: $scheme")
                if (scheme == "http" || scheme == "https") {
                    setDataSource(song.uri)
                } else {
                    setDataSource(this@MusicPlaybackService, uri)
                }

                // Prepare async
                Log.d(TAG, "Calling prepareAsync for ${song.title}")
                prepareAsync()

                // Listener saat MediaPlayer siap
                setOnPreparedListener { mp ->
                    Log.i(TAG, "MediaPlayer prepared for ${song.title}. Duration: ${mp.duration}ms.")
                    currentSongStartTimeMillis = System.currentTimeMillis()
                    _duration.value = mp.duration
                    mp.start()
                    _isPlaying.value = true

                    // Update state dan metadata
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, 0L)
                    updateMediaSessionMetadata()

                    // Mulai notifikasi foreground
                    Log.i(TAG, "Calling startForegroundNotificationAndUpdate for ${song.title}")
                    startForegroundNotificationAndUpdate()

                    // Mulai update progress untuk UI dan notifikasi
                    startProgressUpdater()

                    // Set perangkat audio jika bukan emulator
                    if (!isEmulator) {
                        _currentAudioDevice.value?.let { device ->
                            if (device.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                val availableDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                                val targetDevice = availableDevices.find { it.id == device.id }
                                mp.setPreferredDevice(targetDevice)
                                Log.d(TAG, "Preferred device set (if any) for ${song.title}")
                            }
                        }
                    }

                    // Update timestamp di database
                    musicDbViewModel.updateSongTimestamp(song)
                    logCurrentSongPlayDuration(musicDbViewModel, isCompletion = false)
                }

                // Listener saat lagu selesai
                setOnCompletionListener {
                    Log.i(TAG, "MediaPlayer onCompletion for ${song.title}.")
                    updateAndLogDurationOnEvent(musicDbViewModel)
                    _isPlaying.value = false
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, _duration.value.toLong())
                    progressUpdateJob?.cancel()
                    playNext(musicDbViewModel, onlineSongsViewModel) // Panggil playNext untuk logika next
                }

                // Listener untuk error
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error for ${song.title}: code $what, extra $extra")
                    _isPlaying.value = false
                    _audioError.value = "Playback error on ${song.title}: code $what"
                    updatePlaybackState(PlaybackStateCompat.STATE_ERROR, mediaPlayer!!.currentPosition.toLong())
                    updateNotification()
                    true
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security error setting up MediaPlayer for ${song.title}", e)
            _audioError.value = "Security error: ${e.message}"
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR, 0L)
            updateNotification()
        } catch (e: IOException) {
            Log.e(TAG, "IO error setting up MediaPlayer for ${song.title}", e)
            _audioError.value = "IO error: ${e.message}"
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR, 0L)
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error setting up MediaPlayer for ${song.title}", e)
            _audioError.value = "Unexpected error: ${e.message}"
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR, 0L)
            updateNotification()
        }
    }

    private fun updateCurrentSessionListenedDuration() {
        if (_isPlaying.value && lastPlayTimestamp > 0) {
            val elapsed = System.currentTimeMillis() - lastPlayTimestamp
            currentSessionListenedDurationMillis += elapsed
            lastPlayTimestamp = System.currentTimeMillis()
        }
    }

    private fun updateAndLogDurationOnEvent(musicDbViewModel: MusicDbViewModel) {
        updateCurrentSessionListenedDuration()
        logCurrentSongPlayDuration(musicDbViewModel)
        currentSessionListenedDurationMillis = 0L
    }

    private fun logCurrentSongPlayDuration(musicDbViewModel: MusicDbViewModel, isCompletion: Boolean = false) {
        val songToLog = _currentSong.value
        val mediaPlayerInstance = mediaPlayer

        if (songToLog == null || songToLog.id == null || mediaPlayerInstance == null) {
            Log.d("MusicBehaviorVM", "Skipping log: song or id is null, or media player is not initialized")
            return
        }

        val finalDurationToLog = currentSessionListenedDurationMillis.coerceAtMost(songToLog.duration)

        if (finalDurationToLog >= MIN_PLAY_DURATION_FOR_LOG_MS) {
            serviceScope.launch(Dispatchers.IO) {
                val songExists = musicDbViewModel.isSongExists(songToLog.id)
                if (!songExists) {
                    Log.d("MusicPlaybackService", "Skipping log: song with id ${songToLog.id} does not exist in database")
                    return@launch
                }

                val userEmail = DataKeeper.email
                if (userEmail.isNullOrBlank()) {
                    Log.e("MusicPlaybackService", "Skipping log: User email is null or blank")
                    return@launch
                }

                val playLog = SongPlayLogEntity(
                    songId = songToLog.id,
                    userEmail = userEmail,
                    playedAtTimestamp = System.currentTimeMillis(),
                    durationListenedMillis = finalDurationToLog,
                    isLocal = true
                )
                musicDbViewModel.insertSongPlayLog(playLog)
                Log.d("MusicPlaybackService", "Logged play: ${songToLog.title}, Actual Duration Listened: $finalDurationToLog ms, Played At: ${playLog.playedAtTimestamp}")
            }
        } else {
            Log.d("MusicPlaybackService", "Skipping log for ${songToLog.title}: duration $finalDurationToLog ms < $MIN_PLAY_DURATION_FOR_LOG_MS ms")
        }
    }

    fun togglePlayPause(musicDbViewModel: MusicDbViewModel) {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    logCurrentSongPlayDuration(musicDbViewModel)
                    it.pause()
                    _isPlaying.value = false
                } else {
                    currentSongStartTimeMillis = System.currentTimeMillis() - it.currentPosition
                    it.start()
                    _isPlaying.value = true
                }
            } catch (e: IllegalStateException) {
                _audioError.value = "Playback state error: ${e.message}"
            }
        }
    }

    private fun startUpdatingProgress() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (true) {
                mediaPlayer?.let {
                    try {
                        _currentPosition.value = it.currentPosition
                    } catch (e: IllegalStateException) {
                        _audioError.value = "Progress update error: ${e.message}"
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
                _audioError.value = "Seek error: ${e.message}"
            }
        }
    }

    fun setPlaylist(songs: List<Song>) {
        _playlist.clear()
        _playlist.addAll(songs.filter { isValidSong(it) })
    }

    fun playNext(musicDbViewModel: MusicDbViewModel, onlineSongsViewModel: OnlineSongsViewModel) {
        updateAndLogDurationOnEvent(musicDbViewModel) // Log lagu saat ini sebelum pindah
        if (_queue.isNotEmpty()) {
            val nextFromQueue = _queue.removeAt(0)
            playSong(nextFromQueue, musicDbViewModel, onlineSongsViewModel)
        } else {
            playNextFromPlaylist(musicDbViewModel, onlineSongsViewModel)
        }
    }

    private fun playNextFromPlaylist(musicDbViewModel: MusicDbViewModel, onlineSongsViewModel: OnlineSongsViewModel) {
        val list = _playlist

        val currentSongId = _currentSong.value?.id
        var songList = onlineSongsViewModel.onlineGlobalSongs.value
        var currentSongIndex = songList.indexOfFirst { it.id == currentSongId }

        var nextSongIndex = -1
        var nextSong = ApiSong(0,"","","","","","",0,"","")

        if (currentSongIndex == -1) { // song not in global but in country
            songList = onlineSongsViewModel.onlineCountrySongs.value
            currentSongIndex = songList.indexOfFirst { it.id == currentSongId }
        }

        if (currentSongIndex != -1){ // song is in global or country (else skip this section)
            nextSongIndex = if (currentSongIndex + 1 >= songList.size) 0 else currentSongIndex + 1
            nextSong = songList[nextSongIndex]

            val song = Song(
                id = nextSong.id,
                title = nextSong.title,
                artist = nextSong.artist,
                uri = nextSong.url,
                duration = parseDurationToMillis(nextSong.duration),
                artworkUri = nextSong.artwork
            )
            currentIndex = _playlist.indexOfFirst { it.id == nextSong.id }
            if (currentIndex == -1) currentIndex = 0
            playSong(song, musicDbViewModel, onlineSongsViewModel)
            return
        }

        when (_playbackMode.value) {
            PlaybackMode.REPEAT_ONE -> {
                _currentSong.value?.let { playSong(it, musicDbViewModel, onlineSongsViewModel) }
            }
            PlaybackMode.SHUFFLE -> {
                val indices = list.indices - currentIndex
                if (indices.isNotEmpty()) {
                    currentIndex = indices.random()
                    playSong(list[currentIndex], musicDbViewModel, onlineSongsViewModel)
                }
            }
            PlaybackMode.REPEAT -> {
                currentIndex = (currentIndex + 1) % list.size
                playSong(list[currentIndex], musicDbViewModel, onlineSongsViewModel)
            }
        }
    }

    fun playPrevious(musicDbViewModel: MusicDbViewModel, onlineSongsViewModel: OnlineSongsViewModel) {
        updateAndLogDurationOnEvent(musicDbViewModel)

        val list = _playlist

        val currentSongId = _currentSong.value?.id
        var songList = onlineSongsViewModel.onlineGlobalSongs.value
        var currentSongIndex = songList.indexOfFirst { it.id == currentSongId }

        var prevSongIndex = -1
        var prevSong = ApiSong(0,"","","","","","",0,"","")

        if (currentSongIndex == -1) { // song not in global but in country
            songList = onlineSongsViewModel.onlineCountrySongs.value
            currentSongIndex = songList.indexOfFirst { it.id == currentSongId }
        }

        if (currentSongIndex != -1){ // song is in global or country (else skip this section)
            prevSongIndex = if (currentSongIndex <= 0) songList.size - 1 else currentSongIndex - 1
            prevSong = songList[prevSongIndex]

            val song = Song(
                id = prevSong.id,
                title = prevSong.title,
                artist = prevSong.artist,
                uri = prevSong.url,
                duration = parseDurationToMillis(prevSong.duration),
                artworkUri = prevSong.artwork
            )
            currentIndex = _playlist.indexOfFirst { it.id == prevSong.id }
            if (currentIndex == -1) currentIndex = 0
            playSong(song, musicDbViewModel, onlineSongsViewModel)
            return
        }

        when (_playbackMode.value) {
            PlaybackMode.REPEAT_ONE -> {
                _currentSong.value?.let { playSong(it, musicDbViewModel, onlineSongsViewModel) }
            }
            PlaybackMode.SHUFFLE -> {
                val indices = list.indices - currentIndex
                if (indices.isNotEmpty()) {
                    currentIndex = indices.random()
                    playSong(list[currentIndex], musicDbViewModel, onlineSongsViewModel)
                }
            }
            PlaybackMode.REPEAT -> {
                currentIndex = if (currentIndex <= 0) list.size - 1 else currentIndex - 1
                playSong(list[currentIndex], musicDbViewModel, onlineSongsViewModel)
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

    fun playNextFromQueue(musicDbViewModel: MusicDbViewModel, onlineSongsViewModel: OnlineSongsViewModel) {
        if (_queue.isNotEmpty()) {
            val nextSong = _queue.removeAt(0)
            playSong(nextSong, musicDbViewModel, onlineSongsViewModel)
        } else {
            playNext(musicDbViewModel, onlineSongsViewModel)
        }
    }

    fun playOrQueueNext(musicDbViewModel: MusicDbViewModel, onlineSongsViewModel: OnlineSongsViewModel) {
        if (_queue.isNotEmpty()) {
            val nextSong = _queue.removeAt(0)
            playSong(nextSong, musicDbViewModel, onlineSongsViewModel)
        } else {
            playNext(musicDbViewModel, onlineSongsViewModel)
        }
    }


    fun stopPlayback(musicDbViewModel: MusicDbViewModel) {
        logCurrentSongPlayDuration(musicDbViewModel)
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            reset()
        }
        _isPlaying.value = false
    }

    fun hasNextSong(): Boolean {
        if (_queue.isNotEmpty()) return true
        if (_playlist.isEmpty()) return false
        return when (_playbackMode.value) {
            PlaybackMode.REPEAT -> true
            PlaybackMode.SHUFFLE -> _playlist.size > 1
            PlaybackMode.REPEAT_ONE -> _playlist.size > 1
        }
    }

    private fun getDefaultArtworkBitmap(): Bitmap? {
        return try {
            BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_launcher_foreground) // GANTI INI
        } catch (e: Exception) {
            Log.e(TAG, "Error loading default artwork bitmap for notification", e)
            null
        }
    }

    private fun isValidUri(uri: Uri): Boolean {
        return try {
            val scheme = uri.scheme?.lowercase()
            when (scheme) {
                android.content.ContentResolver.SCHEME_CONTENT, android.content.ContentResolver.SCHEME_FILE -> {
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

private fun parseDurationToMillis(duration: String): Long {
    val parts = duration.split(":")
    val minutes = parts[0].toLongOrNull() ?: 0L
    val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    return (minutes * 60 + seconds) * 1000
}
