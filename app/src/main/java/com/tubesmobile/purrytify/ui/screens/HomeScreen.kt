package com.tubesmobile.purrytify.ui.screens

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.ui.components.BottomPlayerBar
import com.tubesmobile.purrytify.ui.components.Screen
import com.tubesmobile.purrytify.ui.components.SharedBottomNavigationBar
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme
import com.tubesmobile.purrytify.ui.viewmodel.MusicBehaviorViewModel
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.tubesmobile.purrytify.ui.components.NetworkOfflineScreen
import com.tubesmobile.purrytify.ui.theme.LocalNetworkStatus
import com.tubesmobile.purrytify.ui.viewmodel.LoginViewModel
import java.io.File

@Composable
fun HomeScreen(
    navController: NavHostController,
    musicBehaviorViewModel: MusicBehaviorViewModel,
    loginViewModel: LoginViewModel
) {
    val userName by loginViewModel.userName.collectAsState()
    val isConnected by LocalNetworkStatus.current.collectAsState()
    val currentScreen = remember { mutableStateOf(Screen.HOME) }
    val context = LocalContext.current
    val musicDbViewModel: MusicDbViewModel = viewModel()
    val songsList by musicDbViewModel.allSongs.collectAsState(initial = emptyList())
    val songsTimestamp by musicDbViewModel.songsTimestamp.collectAsState(initial = emptyList())
    val currentSong by musicBehaviorViewModel.currentSong.collectAsState()

    val newSongs = remember(songsList, songsTimestamp) {
        val timestampMap = songsTimestamp.associateBy { it.songId }
        songsList
            .filter { it.id !in timestampMap }
            .sortedByDescending { it.id }
    }
    val recentlyPlayedSongs = remember(songsList, songsTimestamp) {
        val timestampMap = songsTimestamp.associateBy { it.songId }
        songsList
            .filter { it.id in timestampMap }
            .sortedByDescending { timestampMap[it.id]?.lastPlayedTimestamp ?: 0L }
            .take(5)
    }

    Scaffold(
        bottomBar = {
            Column {
                if (currentSong != null) {
                    BottomPlayerBar(
                        musicBehaviorViewModel = musicBehaviorViewModel,
                        navController = navController,
                        fromScreen = Screen.LIBRARY
                    )
                }
                SharedBottomNavigationBar(
                    currentScreen = currentScreen.value,
                    onNavigate = { screen ->
                        currentScreen.value = screen
                        when (screen) {
                            Screen.HOME -> {}
                            Screen.LIBRARY -> navController.navigate("library")
                            Screen.PROFILE -> navController.navigate("profile")
                            Screen.MUSIC -> {}
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildAnnotatedString {
                            append("Welcome,\n")
                            if (!userName.isNullOrEmpty()) {
                                withStyle(style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )) {
                                    append(userName)
                                }
                            }
                        },
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 36.sp
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Purrytify Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .padding(start = 16.dp)
                )
            }

            Text(
                text = "New songs",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (newSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No new songs available",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(newSongs) { song ->
                        NewSongItem(
                            song = song,
                            onClick = { selectedSong ->
                                musicDbViewModel.updateSongTimestamp(selectedSong)
                                musicBehaviorViewModel.playSong(selectedSong, context)
                                navController.navigate("music/${Screen.HOME.name}")
                            },
                            musicBehaviorViewModel = musicBehaviorViewModel
                        )
                    }
                }
            }

            Text(
                text = "Recently played",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (recentlyPlayedSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recently played songs",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recentlyPlayedSongs) { song ->
                        RecentlyPlayedItem(
                            song = song,
                            onClick = {selectedSong ->
                                if (selectedSong.uri != currentSong?.uri) {
                                musicBehaviorViewModel.playSong(selectedSong, context)
                            }
                                musicDbViewModel.updateSongTimestamp(selectedSong)
                                navController.navigate("music/${Screen.LIBRARY.name}")
                            },
                            musicBehaviorViewModel = musicBehaviorViewModel
                        )
                    }
                }
            }
            if (!isConnected) {
                NetworkOfflineScreen(24)
            }
        }
    }
}

@Composable
fun NewSongItem(song: Song, onClick: (Song) -> Unit, musicBehaviorViewModel: MusicBehaviorViewModel) {
    val currentSong by musicBehaviorViewModel.currentSong.collectAsState()
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable { onClick(song) },
        horizontalAlignment = Alignment.Start
    ) {
        val context = LocalContext.current
        val imageBitmapState = remember { mutableStateOf<ImageBitmap?>(null) }
        val imageBitmap = imageBitmapState.value

        LaunchedEffect(song.artworkUri) {
            imageBitmapState.value = null
            if (song.artworkUri.isNotEmpty()) {
                val retriever = MediaMetadataRetriever()
                try {
                    val uri = Uri.parse(song.uri)
                    if (!isValidUri(uri, context.contentResolver)) {
                        return@LaunchedEffect
                    }

                    if (song.artworkUri == "Metadata") {
                        retriever.setDataSource(context, uri)
                        val art = retriever.embeddedPicture
                        if (art != null && art.size <= 5 * 1024 * 1024) { // batas ukuran 5mb
                            val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                            imageBitmapState.value = bitmap?.asImageBitmap()
                        }
                    } else {
                        val file = File(song.artworkUri)
                        if (file.exists() && isSafeFilePath(file.absolutePath) && file.length() <= 5 * 1024 * 1024) {
                            val fileBitmap = BitmapFactory.decodeFile(song.artworkUri)
                            imageBitmapState.value = fileBitmap?.asImageBitmap()
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e("NewSongItem", "Security exception accessing URI", e)
                } catch (e: Exception) {
                    Log.e("NewSongItem", "Error loading artwork", e)
                } finally {
                    retriever.release()
                }
            }
        }

        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = song.title,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = song.title,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = song.title,
            color = if (song.id == currentSong?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = song.artist,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
fun RecentlyPlayedItem(song: Song, onClick: (Song) -> Unit, musicBehaviorViewModel: MusicBehaviorViewModel) {
    val currentSong by musicBehaviorViewModel.currentSong.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(song) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val imageBitmapState = remember { mutableStateOf<ImageBitmap?>(null) }
        val imageBitmap = imageBitmapState.value

        LaunchedEffect(song.artworkUri) {
            imageBitmapState.value = null
            if (song.artworkUri.isNotEmpty()) {
                val retriever = MediaMetadataRetriever()
                try {
                    val uri = Uri.parse(song.uri)
                    if (!isValidUri(uri, context.contentResolver)) {
                        return@LaunchedEffect
                    }

                    if (song.artworkUri == "Metadata") {
                        retriever.setDataSource(context, uri)
                        val art = retriever.embeddedPicture
                        if (art != null && art.size <= 5 * 1024 * 1024) { // batas ukuran 5mb
                            val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                            imageBitmapState.value = bitmap?.asImageBitmap()
                        }
                    } else {
                        val file = File(song.artworkUri)
                        if (file.exists() && isSafeFilePath(file.absolutePath) && file.length() <= 5 * 1024 * 1024) {
                            val fileBitmap = BitmapFactory.decodeFile(song.artworkUri)
                            imageBitmapState.value = fileBitmap?.asImageBitmap()
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e("RecentlyPlayedItem", "Security exception accessing URI", e)
                } catch (e: Exception) {
                    Log.e("RecentlyPlayedItem", "Error loading artwork", e)
                } finally {
                    retriever.release()
                }
            }
        }

        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = song.title,
                modifier = Modifier
                    .size(60.dp)
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = song.title,
                modifier = Modifier
                    .size(60.dp)
                    .background(MaterialTheme.colorScheme.surface),

            )
        }

        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = song.title,
                color = if (song.id == currentSong?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = song.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

private fun isValidUri(uri: Uri, contentResolver: ContentResolver): Boolean {
    return try {
        val scheme = uri.scheme
        if (scheme != ContentResolver.SCHEME_CONTENT && scheme != ContentResolver.SCHEME_FILE) {
            return false
        }
        // cek apakah uri dapat diakses
        contentResolver.openInputStream(uri)?.close()
        true
    } catch (e: Exception) {
        false
    }
}

private fun isSafeFilePath(path: String): Boolean {
    return !path.contains("..") && !path.startsWith("/") && path.isNotBlank()
}

data class Song(
    val id: Int? = null,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: String,
    val artworkUri: String,
)

data class SongTimestamp(
    val userEmail: String,
    val songId: Int,
    val lastPlayedTimestamp: Long? = null
)
