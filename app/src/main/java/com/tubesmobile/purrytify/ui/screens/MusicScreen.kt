package com.tubesmobile.purrytify.ui.screens

import android.content.ContentResolver
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.ui.components.Screen
import com.tubesmobile.purrytify.ui.components.SharedBottomNavigationBar
import com.tubesmobile.purrytify.ui.viewmodel.MusicBehaviorViewModel
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import kotlinx.coroutines.launch

@Composable
fun MusicScreen(
    navController: NavHostController,
    sourceScreen: Screen,
    musicBehaviorViewModel: MusicBehaviorViewModel,
    musicDbViewModel: MusicDbViewModel,
    isFromApiSong: Boolean = false
) {
    var showPopup by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentSong by musicBehaviorViewModel.currentSong.collectAsState()
    val isPlaying by musicBehaviorViewModel.isPlaying.collectAsState()
    val position by musicBehaviorViewModel.currentPosition.collectAsState()
    val duration by musicBehaviorViewModel.duration.collectAsState()
    var isLiked by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val song = currentSong

    val gradientColors = listOf(
        Color(0xFFBD1E01),
        Color(0xFF893552),
        Color(0xFF53062B),
        Color(0xFF04061D)
    )

    LaunchedEffect(song?.id) {
        song?.id?.let { songId ->
            isLiked = musicDbViewModel.isSongLiked(songId)
        }
    }

    Scaffold(
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
                    brush = Brush.verticalGradient(colors = gradientColors)
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

                if (isFromApiSong) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_download),
                        contentDescription = "Download",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                song?.let { currentSong ->
                                    val songToSave = Song(
                                        id = null,
                                        title = currentSong.title,
                                        artist = currentSong.artist,
                                        duration = currentSong.duration,
                                        uri = currentSong.uri,
                                        artworkUri = currentSong.artworkUri
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
            }

            Spacer(modifier = Modifier.weight(1f))
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
