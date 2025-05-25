package com.tubesmobile.purrytify.ui.screens

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.service.AudioDevice
import com.tubesmobile.purrytify.service.MusicPlaybackService
import com.tubesmobile.purrytify.ui.components.Screen
import com.tubesmobile.purrytify.ui.components.SharedBottomNavigationBar
import com.tubesmobile.purrytify.ui.components.SwipeableAudioDeviceDialog
import com.tubesmobile.purrytify.util.generateQRCode
import com.tubesmobile.purrytify.util.saveBitmapToCache
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import com.tubesmobile.purrytify.viewmodel.OnlineSongsViewModel
import kotlinx.coroutines.launch
import android.content.res.Configuration as AndroidConfiguration

data class MusicScreenEventHandlers(
    val onPlayPauseToggle: () -> Unit,
    val onNextSong: () -> Unit,
    val onPreviousSong: () -> Unit,
    val onSeek: (Int) -> Unit,
    val onToggleLike: () -> Unit,
    val onShare: () -> Unit,
    val onDownload: () -> Unit,
    val onAudioDeviceSettingsToggle: () -> Unit
)

@Composable
fun MusicScreen(
    navController: NavHostController,
    sourceScreen: Screen,
    musicService: MusicPlaybackService?,
    musicDbViewModel: MusicDbViewModel,
    isFromApiSong: Boolean = false,
    songId: Int = -1,
    onlineSongsViewModel: OnlineSongsViewModel
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentSong by musicService?.currentSong?.collectAsState() ?: remember { mutableStateOf(null) }
    val isPlaying by musicService?.isPlaying?.collectAsState() ?: remember { mutableStateOf(false) }
    val position by musicService?.currentPosition?.collectAsState() ?: remember { mutableStateOf(0) }
    val duration by musicService?.duration?.collectAsState() ?: remember { mutableStateOf(0) }
    val audioError by musicService?.audioError?.collectAsState() ?: remember { mutableStateOf(null) }
    val audioDevices by musicService?.audioDevices?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val currentAudioDevice by musicService?.currentAudioDevice?.collectAsState() ?: remember { mutableStateOf(null) }

    var isLikedState by remember { mutableStateOf(false) }
    var showAudioDeviceDialog by remember { mutableStateOf(false) }
    var showShareDialogState by remember { mutableStateOf(false) }

    val song = currentSong

    LaunchedEffect(songId, song) {
        if (songId != -1 && song?.id != songId && musicService != null) {
            onlineSongsViewModel.loadSongById(songId) { apiSong ->
                if (apiSong != null) {
                    val loadedSong = Song(
                        id = apiSong.id,
                        title = apiSong.title,
                        artist = apiSong.artist,
                        duration = parseDurationToMillis(apiSong.duration),
                        uri = apiSong.url,
                        artworkUri = apiSong.artwork
                    )
                    musicService.playSong(loadedSong, musicDbViewModel, onlineSongsViewModel)
                    musicDbViewModel.updateSongTimestamp(loadedSong)
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("Song with ID $songId not found")
                    }
                }
            }
        }
    }

    LaunchedEffect(song?.id) {
        song?.id?.let { currentSongId ->
            isLikedState = musicDbViewModel.isSongLiked(currentSongId)
        }
    }

    LaunchedEffect(Unit) {
        musicService?.initializeAudioRouting()
    }

    LaunchedEffect(audioError) {
        audioError?.let { error ->
            scope.launch {
                snackbarHostState.showSnackbar(error)
                musicService?.clearAudioError()
            }
        }
    }

    val eventHandlers = MusicScreenEventHandlers(
        onPlayPauseToggle = { musicService?.togglePlayPause(musicDbViewModel) },
        onNextSong = { musicService?.playNext(musicDbViewModel, onlineSongsViewModel) },
        onPreviousSong = { musicService?.playPrevious(musicDbViewModel, onlineSongsViewModel) },
        onSeek = { newPosition -> musicService?.seekTo(newPosition) },
        onToggleLike = {
            song?.let { currentSongValue ->
                musicDbViewModel.toggleSongLike(currentSongValue)
                isLikedState = !isLikedState
            }
        },
        onShare = { showShareDialogState = true },
        onDownload = {
            song?.let { songToDownload ->
                val songToSave = Song(
                    id = null,
                    title = songToDownload.title,
                    artist = songToDownload.artist,
                    duration = songToDownload.duration,
                    uri = songToDownload.uri,
                    artworkUri = songToDownload.artworkUri
                )
                musicDbViewModel.checkAndInsertOnlineSong(
                    context,
                    songToSave,
                    onSuccess = { savedSong ->
                        scope.launch { snackbarHostState.showSnackbar("Song added successfully") }
                    },
                    onError = { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                )
            }
        },
        onAudioDeviceSettingsToggle = { showAudioDeviceDialog = true }
    )

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == AndroidConfiguration.ORIENTATION_LANDSCAPE

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (!isLandscape) {
                SharedBottomNavigationBar(
                    currentScreen = sourceScreen,
                    onNavigate = { screen ->
                        when (screen) {
                            Screen.HOME -> navController.navigate("home") { popUpTo("home") { inclusive = false } }
                            Screen.LIBRARY -> navController.navigate("library") { popUpTo("library") { inclusive = false } }
                            Screen.PROFILE -> navController.navigate("profile") { popUpTo("profile") { inclusive = false } }
                            Screen.MUSIC -> {}
                        }
                    },
                )
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFBD1E01),
                        Color(0xFF893552),
                        Color(0xFF53062B),
                        Color(0xFF04061D)
                    )
                )
            )

        if (isLandscape) {
            LandscapeMusicScreenContent(
                modifier = screenModifier,
                navController = navController,
                song = song,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                isLiked = isLikedState,
                isFromApiSong = isFromApiSong,
                currentAudioDevice = currentAudioDevice,
                eventHandlers = eventHandlers,
                context = context
            )
        } else {
            PortraitMusicScreenContent(
                modifier = screenModifier,
                navController = navController,
                song = song,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                isLiked = isLikedState,
                isFromApiSong = isFromApiSong,
                currentAudioDevice = currentAudioDevice,
                eventHandlers = eventHandlers,
                context = context
            )
        }
    }

    if (showAudioDeviceDialog) {
        SwipeableAudioDeviceDialog(
            devices = audioDevices,
            currentDevice = currentAudioDevice,
            onDismiss = { showAudioDeviceDialog = false },
            onDeviceSelected = { device ->
                musicService?.selectAudioDevice(device)
                showAudioDeviceDialog = false
            }
        )
    }

    if (showShareDialogState) {
        ShareDialog(
            songId = song?.id,
            context = context,
            onDismiss = { showShareDialogState = false }
        )
    }
}

@Composable
fun AlbumArtDisplay(song: Song?, modifier: Modifier = Modifier, context: Context) {
    val imageBitmapState = remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(song?.artworkUri, song?.uri) {
        imageBitmapState.value = null
        if (song?.artworkUri?.isNotEmpty() == true) {
            if (song.artworkUri.startsWith("http")) {
            } else if (song.artworkUri == "Metadata" && song.uri.isNotBlank()) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(song.uri))
                    retriever.embeddedPicture?.let {
                        imageBitmapState.value = BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    Log.e("MusicScreen", "Error loading artwork from metadata", e)
                } finally {
                    retriever.release()
                }
            } else {
                try {
                    val fileBitmap = BitmapFactory.decodeFile(song.artworkUri)
                    if (fileBitmap != null) {
                        imageBitmapState.value = fileBitmap.asImageBitmap()
                    }
                } catch (e: Exception) {
                    Log.e("MusicScreen", "Error loading artwork from file", e)
                }
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            song?.artworkUri?.startsWith("http") == true -> {
                AsyncImage(
                    model = song.artworkUri,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.ic_launcher_foreground),
                    error = painterResource(id = R.drawable.ic_launcher_foreground)
                )
            }
            imageBitmapState.value != null -> {
                Image(
                    bitmap = imageBitmapState.value!!,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun PortraitMusicScreenContent(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    song: Song?,
    isPlaying: Boolean,
    position: Int,
    duration: Int,
    isLiked: Boolean,
    isFromApiSong: Boolean,
    currentAudioDevice: AudioDevice?,
    eventHandlers: MusicScreenEventHandlers,
    context: Context
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_caret),
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { navController.popBackStack() }
            )
        }

        Spacer(modifier = Modifier.height(64.dp))

        AlbumArtDisplay(
            song = song,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            context = context
        )


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song?.title ?: "",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song?.artist ?: "",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFromApiSong) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_share),
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = eventHandlers.onShare)
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_download),
                        contentDescription = "Download",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = eventHandlers.onDownload)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = if (isLiked) R.drawable.ic_liked else R.drawable.ic_heart),
                        contentDescription = "Like",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = eventHandlers.onToggleLike)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            Slider(
                value = if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = { newValue ->
                    val newPosition = (newValue * duration).toInt()
                    eventHandlers.onSeek(newPosition)
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatMillis(position), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Text(text = formatMillis(duration), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_previous),
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier
                    .size(26.dp)
                    .clickable(onClick = eventHandlers.onPreviousSong)
            )
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clickable(onClick = eventHandlers.onPlayPauseToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(84.dp)
                )
            }
            Icon(
                painter = painterResource(id = R.drawable.ic_skip),
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier
                    .size(26.dp)
                    .clickable(onClick = eventHandlers.onNextSong)
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_add),
                contentDescription = "Select Audio Output",
                tint = Color.White,
                modifier = Modifier
                    .size(26.dp)
                    .clickable(onClick = eventHandlers.onAudioDeviceSettingsToggle)
            )
        }

        Text(
            text = "Playing on ${currentAudioDevice?.name ?: "Internal Speaker"}",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun LandscapeMusicScreenContent(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    song: Song?,
    isPlaying: Boolean,
    position: Int,
    duration: Int,
    isLiked: Boolean,
    isFromApiSong: Boolean,
    currentAudioDevice: AudioDevice?,
    eventHandlers: MusicScreenEventHandlers,
    context: Context
) {
    Column(modifier = modifier.padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_caret),
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { navController.popBackStack() }
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtDisplay(
                song = song,
                modifier = Modifier
                    .fillMaxHeight(0.7f)
                    .aspectRatio(1f)
                    .padding(end = 24.dp),
                context = context
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = song?.title ?: "",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song?.artist ?: "",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isFromApiSong) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_share),
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp).clickable(onClick = eventHandlers.onShare)
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.ic_download),
                                contentDescription = "Download",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp).clickable(onClick = eventHandlers.onDownload)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = if (isLiked) R.drawable.ic_liked else R.drawable.ic_heart),
                                contentDescription = "Like",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp).clickable(onClick = eventHandlers.onToggleLike)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = if (duration > 0) position.toFloat() / duration else 0f,
                        onValueChange = { newValue -> eventHandlers.onSeek((newValue * duration).toInt()) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = formatMillis(position), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        Text(text = formatMillis(duration), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_previous),
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp).clickable(onClick = eventHandlers.onPreviousSong)
                    )
                    Box(
                        modifier = Modifier.size(70.dp).clickable(onClick = eventHandlers.onPlayPauseToggle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = if (isPlaying) R.drawable.pause else R.drawable.play),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip),
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp).clickable(onClick = eventHandlers.onNextSong)
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = "Select Audio Output",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp).clickable(onClick = eventHandlers.onAudioDeviceSettingsToggle)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Playing on ${currentAudioDevice?.name ?: "Internal Speaker"}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
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

fun formatMillis(millis: Int): String {
    val minutes = millis / 1000 / 60
    val seconds = (millis / 1000) % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun parseDurationToMillis(duration: String): Long {
    val parts = duration.split(":")
    val minutes = parts[0].toLongOrNull() ?: 0L
    val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    return (minutes * 60 + seconds) * 1000
}
