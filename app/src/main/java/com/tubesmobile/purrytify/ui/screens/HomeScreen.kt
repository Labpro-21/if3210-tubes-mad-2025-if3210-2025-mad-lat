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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.data.model.ApiSong
import com.tubesmobile.purrytify.data.model.ProfileResponse
import com.tubesmobile.purrytify.service.DataKeeper
import com.tubesmobile.purrytify.ui.components.BottomPlayerBar
import com.tubesmobile.purrytify.ui.components.NetworkOfflineScreen
import com.tubesmobile.purrytify.ui.components.Screen
import com.tubesmobile.purrytify.ui.components.SharedBottomNavigationBar
import com.tubesmobile.purrytify.ui.theme.LocalNetworkStatus
import com.tubesmobile.purrytify.ui.viewmodel.LoginViewModel
import com.tubesmobile.purrytify.ui.viewmodel.MusicBehaviorViewModel
import com.tubesmobile.purrytify.ui.viewmodel.ProfileViewModel
import com.tubesmobile.purrytify.ui.viewmodel.ProfileViewModel.ProfileState
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import com.tubesmobile.purrytify.viewmodel.OnlineSongsViewModel

@Composable
fun HomeScreen(
    profileViewModel: ProfileViewModel = viewModel(),
    navController: NavHostController,
    musicBehaviorViewModel: MusicBehaviorViewModel,
    loginViewModel: LoginViewModel
) {
    val userName by loginViewModel.userName.collectAsState()
    val isConnected by LocalNetworkStatus.current.collectAsState()
    val currentScreen = remember { mutableStateOf(Screen.HOME) }
    val context = LocalContext.current
    val musicDbViewModel: MusicDbViewModel = viewModel()
    val onlineSongsViewModel: OnlineSongsViewModel = viewModel()
    val songsList by musicDbViewModel.allSongs.collectAsState(initial = emptyList())
    val songsTimestamp by musicDbViewModel.songsTimestamp.collectAsState(initial = emptyList())
    val currentSong by musicBehaviorViewModel.currentSong.collectAsState()
    val onlineGlobalSongs by onlineSongsViewModel.onlineGlobalSongs.collectAsState()
    val onlineCountrySongs by onlineSongsViewModel.onlineCountrySongs.collectAsState()
    val isLoadingSongs by musicDbViewModel.isLoadingSongs.collectAsState()
    val songsError by musicDbViewModel.songsError.collectAsState()
    val isLoadingOnlineSongs by onlineSongsViewModel.isLoading.collectAsState()
    val onlineSongsError by onlineSongsViewModel.error.collectAsState()
    val profileState by profileViewModel.profile.collectAsState()
    val baseUrl = "http://34.101.226.132:3000"
    var dynamicProfilePhotoUrl by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(key1 = isConnected, key2 = profileState) {
        if (isConnected && profileState is ProfileState.Loading) {
            Log.d("HomeScreen", "Attempting to load profile.")
            profileViewModel.loadProfile()
        }
    }

    LaunchedEffect(key1 = profileState) {
        if (profileState is ProfileState.Success) {
            val profile = (profileState as ProfileState.Success).profile
            if (!profile.profilePhoto.isNullOrEmpty()) {
                val sanitizedPhoto = sanitizeFileName(profile.profilePhoto)
                dynamicProfilePhotoUrl = "$baseUrl/uploads/profile-picture/$sanitizedPhoto"
                Log.d("HomeScreen", "Profile photo URL set: $dynamicProfilePhotoUrl")
            } else {
                dynamicProfilePhotoUrl = null
                Log.d("HomeScreen", "No profile photo found in profile data.")
            }
        } else if (profileState is ProfileState.Error || profileState is ProfileState.SessionExpired) {
            dynamicProfilePhotoUrl = null
            Log.d("HomeScreen", "Profile loading error or session expired, clearing photo URL.")
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                if (currentSong != null) {
                    BottomPlayerBar(
                        musicBehaviorViewModel = musicBehaviorViewModel,
                        navController = navController,
                        fromScreen = Screen.LIBRARY,
                        isFromApiSong = currentSong?.artworkUri?.startsWith("http") == true
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

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(dynamicProfilePhotoUrl)
                        .crossfade(true)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .build(),
                    contentDescription = "Profile Photo",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { navController.navigate("profile") },
                    contentScale = ContentScale.Crop
                )
            }

            Text(
                text = "Charts",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when {
                isLoadingOnlineSongs -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                onlineSongsError != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = onlineSongsError ?: "Error loading charts",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyRow(
                        modifier = Modifier.padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            ChartItem(
                                title = "Top 50",
                                subtitle = "GLOBAL",
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                onClick = { navController.navigate("top50/global") }
                            )
                        }
                        item {
                            ChartItem(
                                title = "Top 10",
                                subtitle = "${DataKeeper.location}",
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                onClick = { navController.navigate("top50/country") }
                            )
                        }
                    }
                }
            }

            Text(
                text = "New songs",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when {
                isLoadingSongs -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                songsError != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = songsError ?: "Error loading new songs",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                newSongs.isEmpty() -> {
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
                }
                else -> {
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
                                    navController.navigate("music/${Screen.HOME.name}/false/-1")
                                },
                                musicBehaviorViewModel = musicBehaviorViewModel
                            )
                        }
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

            when {
                isLoadingSongs -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                songsError != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = songsError ?: "Error loading recently played songs",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                recentlyPlayedSongs.isEmpty() -> {
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
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentlyPlayedSongs) { song ->
                            RecentlyPlayedItem(
                                song = song,
                                onClick = { selectedSong ->
                                    if (selectedSong.uri != currentSong?.uri) {
                                        musicBehaviorViewModel.playSong(selectedSong, context)
                                    }
                                    musicDbViewModel.updateSongTimestamp(selectedSong)
                                    navController.navigate("music/${Screen.LIBRARY.name}/false/-1")
                                },
                                musicBehaviorViewModel = musicBehaviorViewModel
                            )
                        }
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
fun ChartItem(
    title: String,
    subtitle: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable { onClick() }
            .background(color, RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
        Text(
            text = "",
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun parseDurationToMillis(duration: String): Long {
    val parts = duration.split(":")
    val minutes = parts[0].toLongOrNull() ?: 0L
    val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    return (minutes * 60 + seconds) * 1000
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
                    if (song.artworkUri == "Metadata") {
                        retriever.setDataSource(context, Uri.parse(song.uri))
                        val art = retriever.embeddedPicture
                        if (art != null) {
                            val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                            imageBitmapState.value = bitmap.asImageBitmap()
                        }
                    } else if (!song.artworkUri.isNullOrEmpty()) {
                        val fileBitmap = BitmapFactory.decodeFile(song.artworkUri)
                        if (fileBitmap != null) {
                            imageBitmapState.value = fileBitmap.asImageBitmap()
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
                    if (song.artworkUri == "Metadata") {
                        retriever.setDataSource(context, Uri.parse(song.uri))
                        val art = retriever.embeddedPicture
                        if (art != null) {
                            val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                            imageBitmapState.value = bitmap.asImageBitmap()
                        }
                    } else if (!song.artworkUri.isNullOrEmpty()) {
                        val fileBitmap = BitmapFactory.decodeFile(song.artworkUri)
                        if (fileBitmap != null) {
                            imageBitmapState.value = fileBitmap.asImageBitmap()
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
                    .background(MaterialTheme.colorScheme.surface)
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
    val artworkUri: String
)

data class SongTimestamp(
    val userEmail: String,
    val songId: Int,
    val lastPlayedTimestamp: Long? = null
)
