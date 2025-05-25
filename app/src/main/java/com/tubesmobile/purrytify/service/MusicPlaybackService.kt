package com.tubesmobile.purrytify.service

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
import androidx.core.content.ContextCompat
import com.tubesmobile.purrytify.ui.screens.Song
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

class MusicPlaybackService : Service() {
    private val binder = MusicPlaybackBinder()
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
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isEmulator = Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator")

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

    inner class MusicPlaybackBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    public fun onCleared() {
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
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initializeAudioRouting()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
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

    fun playSong(song: Song) {
        if (!isValidSong(song)) {
            _audioError.value = "Invalid song data"
            return
        }

        val uri = Uri.parse(song.uri)
        if (!isValidUri(uri)) {
            _audioError.value = "Invalid song URI"
            return
        }

        _currentSong.value = song
        currentIndex = _playlist.indexOfFirst { it.uri == song.uri }
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                val scheme = uri.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    setDataSource(song.uri)
                } else {
                    setDataSource(this@MusicPlaybackService, uri)
                }
                prepareAsync()
                setOnPreparedListener {
                    start()
                    _duration.value = duration
                    _isPlaying.value = true
                    if (!isEmulator) {
                        _currentAudioDevice.value?.let { device ->
                            if (device.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                val availableDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                                val targetDevice = availableDevices.find { it.id == device.id }
                                setPreferredDevice(targetDevice)
                            }
                        }
                    }
                }
                setOnCompletionListener {
                    playNext()
                }
                setOnErrorListener { _, what, extra ->
                    _isPlaying.value = false
                    _audioError.value = "Playback error: code $what, extra $extra"
                    true
                }
            }
            startUpdatingProgress()
        } catch (e: SecurityException) {
            _audioError.value = "Security error: ${e.message}"
        } catch (e: IOException) {
            _audioError.value = "IO error: ${e.message}"
        } catch (e: Exception) {
            _audioError.value = "Unexpected error: ${e.message}"
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

    fun playNext() {
        if (_queue.isNotEmpty()) {
            val nextFromQueue = _queue.removeAt(0)
            playSong(nextFromQueue)
        } else {
            playNextFromPlaylist()
        }
    }

    private fun playNextFromPlaylist() {
        val list = _playlist
        if (list.isEmpty()) return

        when (_playbackMode.value) {
            PlaybackMode.REPEAT_ONE -> {
                _currentSong.value?.let { playSong(it) }
            }
            PlaybackMode.SHUFFLE -> {
                val indices = list.indices - currentIndex
                if (indices.isNotEmpty()) {
                    currentIndex = indices.random()
                    playSong(list[currentIndex])
                }
            }
            PlaybackMode.REPEAT -> {
                currentIndex = (currentIndex + 1) % list.size
                playSong(list[currentIndex])
            }
        }
    }

    fun playPrevious() {
        val list = _playlist
        if (list.isEmpty()) return

        when (_playbackMode.value) {
            PlaybackMode.REPEAT_ONE -> {
                _currentSong.value?.let { playSong(it) }
            }
            PlaybackMode.SHUFFLE -> {
                val indices = list.indices - currentIndex
                if (indices.isNotEmpty()) {
                    currentIndex = indices.random()
                    playSong(list[currentIndex])
                }
            }
            PlaybackMode.REPEAT -> {
                currentIndex = if (currentIndex <= 0) list.size - 1 else currentIndex - 1
                playSong(list[currentIndex])
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

    fun playNextFromQueue() {
        if (_queue.isNotEmpty()) {
            val nextSong = _queue.removeAt(0)
            playSong(nextSong)
        } else {
            playNext()
        }
    }

    fun playOrQueueNext() {
        if (_queue.isNotEmpty()) {
            val nextSong = _queue.removeAt(0)
            playSong(nextSong)
        } else {
            playNext()
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
        if (_queue.isNotEmpty()) return true
        if (_playlist.isEmpty()) return false
        return when (_playbackMode.value) {
            PlaybackMode.REPEAT -> true
            PlaybackMode.SHUFFLE -> _playlist.size > 1
            PlaybackMode.REPEAT_ONE -> _playlist.size > 1
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