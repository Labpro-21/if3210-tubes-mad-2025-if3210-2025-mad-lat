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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.ui.components.Screen
import com.tubesmobile.purrytify.ui.components.SharedBottomNavigationBar
import com.tubesmobile.purrytify.ui.components.SwipeableAudioDeviceDialog
import com.tubesmobile.purrytify.ui.viewmodel.AudioDevice
import com.tubesmobile.purrytify.ui.viewmodel.MusicBehaviorViewModel
import com.tubesmobile.purrytify.util.generateQRCode
import com.tubesmobile.purrytify.util.saveBitmapToCache
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import com.tubesmobile.purrytify.viewmodel.OnlineSongsViewModel
import kotlinx.coroutines.launch

@Composable
fun MusicScreen(
    navController: NavHostController,
    sourceScreen: Screen,
    musicBehaviorViewModel: MusicBehaviorViewModel,
    musicDbViewModel: MusicDbViewModel,
    isFromApiSong: Boolean = false,
    songId: Int = -1,
    onlineSongsViewModel: OnlineSongsViewModel
) {
    var showPopup by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentSong by musicBehaviorViewModel.currentSong.collectAsState()
    val isPlaying by musicBehaviorViewModel.isPlaying.collectAsState()
    val position by musicBehaviorViewModel.currentPosition.collectAsState()
    val duration by musicBehaviorViewModel.duration.collectAsState()
    val audioError by musicBehaviorViewModel.audioError.collectAsState()
    val audioDevices by musicBehaviorViewModel.audioDevices.collectAsState()
    val currentAudioDevice by musicBehaviorViewModel.currentAudioDevice.collectAsState()
    var isLiked by remember { mutableStateOf(false) }
    var speakerIconPosition by remember { mutableStateOf(Offset(0f, 0f)) }
    val context = LocalContext.current
    val song = currentSong

    LaunchedEffect(songId, song) {
        if (songId != -1 && song?.id != songId) {
            onlineSongsViewModel.loadSongById(songId) { apiSong ->
                if (apiSong != null) {
                    val song = Song(
                        id = apiSong.id,
                        title = apiSong.title,
                        artist = apiSong.artist,
                        duration = parseDurationToMillis(apiSong.duration),
                        uri = apiSong.url,
                        artworkUri = apiSong.artwork
                    )
                    musicBehaviorViewModel.playSong(song, context)
                    musicDbViewModel.updateSongTimestamp(song)
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("Song with ID $songId not found")
                    }
                }
            }
        }
    }

    LaunchedEffect(song?.id) {
        song?.id?.let { songId ->
            isLiked = musicDbViewModel.isSongLiked(songId)
        }
    }

    LaunchedEffect(Unit) {
        musicBehaviorViewModel.initializeAudioRouting(context)
    }

    LaunchedEffect(audioError) {
        audioError?.let { error ->
            scope.launch {
                snackbarHostState.showSnackbar(error)
                musicBehaviorViewModel.clearAudioError()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            SharedBottomNavigationBar(
                currentScreen = sourceScreen,
                onNavigate = { screen ->
                    when (screen) {
                        Screen.HOME -> navController.navigate("home") {
                            popUpTo("home") { inclusive = false }
                        }
                        Screen.LIBRARY -> navController.navigate("library") {
                            popUpTo("library") { inclusive = false }
                        }
                        Screen.PROFILE -> navController.navigate("profile") {
                            popUpTo("profile") { inclusive = false }
                        }
                        Screen.MUSIC -> {}
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
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
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                val imageBitmapState = remember { mutableStateOf<ImageBitmap?>(null) }
                val imageBitmap = imageBitmapState.value

                LaunchedEffect(song?.artworkUri) {
                    imageBitmapState.value = null
                    if (song?.artworkUri?.isNotEmpty() == true && song.artworkUri != "Metadata" && !song.artworkUri.startsWith("http")) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            val fileBitmap = BitmapFactory.decodeFile(song.artworkUri)
                            if (fileBitmap != null) {
                                imageBitmapState.value = fileBitmap.asImageBitmap()
                            }
                        } catch (e: SecurityException) {
                            Log.e("MusicScreen", "Security exception accessing URI", e)
                        } catch (e: Exception) {
                            Log.e("MusicScreen", "Error loading artwork", e)
                        } finally {
                            retriever.release()
                        }
                    }
                }

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
                    imageBitmap != null -> {
                        Image(
                            bitmap = imageBitmap,
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFromApiSong) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_share),
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { showShareDialog = true }
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_download),
                            contentDescription = "Download",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable {
                                    song?.let { song ->
                                        val songToSave = Song(
                                            id = null,
                                            title = song.title,
                                            artist = song.artist,
                                            duration = song.duration,
                                            uri = song.uri,
                                            artworkUri = song.artworkUri
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
                                }
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = if (isLiked) R.drawable.ic_liked else R.drawable.ic_heart),
                            contentDescription = "Like",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable {
                                    song?.let {
                                        musicDbViewModel.toggleSongLike(it)
                                        isLiked = !isLiked
                                    }
                                }
                        )
                    }
                }
            }

            if (showShareDialog) {
                ShareDialog(
                    songId = song?.id,
                    context = context,
                    onDismiss = { showShareDialog = false }
                )
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
                        musicBehaviorViewModel.seekTo(newPosition)
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
                    Text(
                        text = formatMillis(position),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatMillis(duration),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
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
                        .clickable {
                            musicBehaviorViewModel.playPrevious(context)
                        }
                )

                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clickable { musicBehaviorViewModel.togglePlayPause() },
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
                        .clickable {
                            musicBehaviorViewModel.playNext(context)
                        }
                )

                Box {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = "Select Audio Output",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { showPopup = true }
                            .onGloballyPositioned { coordinates ->
                                speakerIconPosition = coordinates.positionInRoot()
                                Log.d("MusicScreen", "Speaker icon position: $speakerIconPosition")
                            }
                    )
                    if (showPopup) {
                        SwipeableAudioDeviceDialog (
                            devices = audioDevices,
                            currentDevice = currentAudioDevice,
                            onDismiss = { showPopup = false },
                            onDeviceSelected = { device ->
                                musicBehaviorViewModel.selectAudioDevice(device, context)
                                showPopup = false
                            }
                        )
                    }
                }
            }

            Text(
                text = "Playing on ${currentAudioDevice?.name ?: "Internal Speaker"}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun DeviceDialog(
    devices: List<AudioDevice>,
    currentDevice: AudioDevice?,
    iconPosition: Offset,
    onDeviceSelected: (AudioDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val dialogWidth = 180.dp
    val dialogHeight = if (devices.size <= 1) 48.dp else (devices.size * 36).dp
    val offsetX = with(density) { iconPosition.x.toDp() - dialogWidth / 2 }
    val offsetY = with(density) { iconPosition.y.toDp() - dialogHeight - 12.dp }

    Popup(
        onDismissRequest = onDismiss,
        offset = androidx.compose.ui.unit.IntOffset(
            x = with(density) { offsetX.roundToPx() },
            y = with(density) { offsetY.roundToPx() }
        )
    ) {
        Box(
            modifier = Modifier
                .width(dialogWidth)
                .height(dialogHeight)
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                if (devices.size <= 1) {
                    Text(
                        text = "This Device",
                        color = Color(0xFF00FF00),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    devices.forEach { device ->
                        Text(
                            text = device.name,
                            color = if (device == currentDevice) Color(0xFF00FF00) else Color.Black,
                            fontSize = 14.sp,
                            fontWeight = if (device == currentDevice) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDeviceSelected(device)
                                }
                                .padding(vertical = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

//@Composable
//fun ShareDialog(
//    songId: Int?,
//    context: Context,
//    onDismiss: () -> Unit
//) {
//    AlertDialog(
//        onDismissRequest = onDismiss,
//        title = { Text("Share Song") },
//        text = {
//            Column {
//                Text(
//                    text = "Share as URL",
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable {
//                            songId?.let { id ->
//                                val shareUrl = "purrytify://song/$id"
//                                Log.d("kocokmeong", shareUrl)
//                                val shareIntent = Intent().apply {
//                                    action = Intent.ACTION_SEND
//                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
//                                    type = "text/plain"
//                                }
//                                context.startActivity(Intent.createChooser(shareIntent, "Share Song URL"))
//                            }
//                            onDismiss()
//                        }
//                        .padding(vertical = 8.dp),
//                    fontSize = 16.sp
//                )
//                Text(
//                    text = "Share as QR Code",
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable {
//                            songId?.let { id ->
//                                val shareUrl = "purrytify://song/$id"
//                                val qrBitmap = generateQRCode(shareUrl, 512, 512)
//                                if (qrBitmap != null) {
//                                    val uri = saveBitmapToCache(context, qrBitmap, "song_qr_$id.png")
//                                    if (uri != null) {
//                                        val shareIntent = Intent().apply {
//                                            action = Intent.ACTION_SEND
//                                            putExtra(Intent.EXTRA_STREAM, uri)
//                                            type = "image/png"
//                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                                        }
//                                        context.startActivity(Intent.createChooser(shareIntent, "Share Song QR Code"))
//                                    }
//                                }
//                                onDismiss()
//                            }
//                        }
//                        .padding(vertical = 8.dp),
//                    fontSize = 16.sp
//                )
//            }
//        },
//        confirmButton = {},
//        dismissButton = {
//            TextButton(onClick = onDismiss) {
//                Text("Cancel")
//            }
//        }
//    )
//}

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