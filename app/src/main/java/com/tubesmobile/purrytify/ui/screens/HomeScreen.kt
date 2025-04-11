package com.tubesmobile.purrytify.ui.screens

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
import androidx.compose.ui.tooling.preview.Preview
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tubesmobile.purrytify.ui.viewmodel.LoginViewModel

@Composable
fun HomeScreen(navController: NavHostController, musicBehaviorViewModel: MusicBehaviorViewModel, loginViewModel: LoginViewModel) {
    loginViewModel.userEmail.collectAsState()
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
                if (currentSong != null){
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
            Text(
                text = "New songs",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (newSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
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
                            }
                        )
                    }
                }
            }

            Text(
                text = "Recently played",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (recentlyPlayedSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
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
                            onClick = { selectedSong ->
                                musicDbViewModel.updateSongTimestamp(selectedSong)
                                musicBehaviorViewModel.playSong(selectedSong, context)
                                navController.navigate("music/${Screen.HOME.name}")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NewSongItem(song: Song, onClick: (Song) -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick(song) },
        horizontalAlignment = Alignment.Start
    ) {
        val context = LocalContext.current
        val imageBitmapState = remember { mutableStateOf<ImageBitmap?>(null) }
        val imageBitmap = imageBitmapState.value

        LaunchedEffect(song?.artworkUri) {
            imageBitmapState.value = null
            val retriever = MediaMetadataRetriever()
            try {
                if (song?.artworkUri == "Metadata") {
                    retriever.setDataSource(context, Uri.parse(song.uri))
                    val art = retriever.embeddedPicture
                    if (art != null) {
                        val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                        imageBitmapState.value = bitmap.asImageBitmap()
                    }
                } else if (!song?.artworkUri.isNullOrEmpty()) {
                    val fileBitmap = BitmapFactory.decodeFile(song?.artworkUri)
                    if (fileBitmap != null) {
                        imageBitmapState.value = fileBitmap.asImageBitmap()
                    }
                }
            } catch (_: Exception) {
                imageBitmapState.value = null
            } finally {
                retriever.release()
            }
        }
        if (imageBitmap != null){
            Image(
                bitmap = imageBitmap!!,
                contentDescription = song.title,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
        else{
            Image(
                painter = painterResource(id =  R.drawable.ic_launcher_foreground),
                contentDescription = song.title,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = song.title,
            color = MaterialTheme.colorScheme.onBackground,
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
fun RecentlyPlayedItem(song: Song, onClick: (Song) -> Unit) {
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

        LaunchedEffect(song?.artworkUri) {
            imageBitmapState.value = null
            val retriever = MediaMetadataRetriever()
            try {
                if (song?.artworkUri == "Metadata") {
                    retriever.setDataSource(context, Uri.parse(song.uri))
                    val art = retriever.embeddedPicture
                    if (art != null) {
                        val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                        imageBitmapState.value = bitmap.asImageBitmap()
                    }
                } else if (!song?.artworkUri.isNullOrEmpty()) {
                    val fileBitmap = BitmapFactory.decodeFile(song?.artworkUri)
                    if (fileBitmap != null) {
                        imageBitmapState.value = fileBitmap.asImageBitmap()
                    }
                }
            } catch (_: Exception) {
                imageBitmapState.value = null
            } finally {
                retriever.release()
            }
        }
        if (imageBitmap != null){
            Image(
                bitmap = imageBitmap!!,
                contentDescription = song.title,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
        else{
            Image(
                painter = painterResource(id =  R.drawable.ic_launcher_foreground),
                contentDescription = song.title,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }

        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = song.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
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

//@Preview(showBackground = true)
//@Composable
//fun HomeScreenPreview() {
//    val navController = rememberNavController()
//    val previewViewModel: MusicBehaviorViewModel = viewModel()
//
//    PurrytifyTheme {
//        HomeScreen(navController, previewViewModel)
//    }
//}