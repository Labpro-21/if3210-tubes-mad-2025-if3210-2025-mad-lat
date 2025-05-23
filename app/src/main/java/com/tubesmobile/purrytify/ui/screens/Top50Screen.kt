package com.tubesmobile.purrytify.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.data.model.ApiSong
import com.tubesmobile.purrytify.service.DataKeeper
import com.tubesmobile.purrytify.ui.components.BottomPlayerBar
import com.tubesmobile.purrytify.ui.components.Screen
import com.tubesmobile.purrytify.ui.components.SharedBottomNavigationBar
import com.tubesmobile.purrytify.service.MusicPlaybackService
import com.tubesmobile.purrytify.util.generateQRCode
import com.tubesmobile.purrytify.util.saveBitmapToCache
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import com.tubesmobile.purrytify.viewmodel.OnlineSongsViewModel
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Top50Screen(
    navController: NavHostController,
    musicService: MusicPlaybackService?,
    type: String // "global" or "country"
) {
    val context = LocalContext.current
    val musicDbViewModel: MusicDbViewModel = viewModel()
    val onlineSongsViewModel: OnlineSongsViewModel = viewModel()
    val currentScreen = Screen.HOME
    val currentSong by musicService?.currentSong?.collectAsState() ?: remember { mutableStateOf(null) }
    val onlineGlobalSongs by onlineSongsViewModel.onlineGlobalSongs.collectAsState()
    val onlineCountrySongs by onlineSongsViewModel.onlineCountrySongs.collectAsState()
    val isLoadingOnlineSongs by onlineSongsViewModel.isLoading.collectAsState()
    val onlineSongsError by onlineSongsViewModel.error.collectAsState()

    val songs = if (type == "global") onlineGlobalSongs else onlineCountrySongs
    val title = if (type == "global") "Top 50 Global" else "Top 10 ${DataKeeper.location}"

    // State for search query
    var searchQuery by remember { mutableStateOf("") }

    // Filtered songs based on search query
    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter { song ->
                song.title.contains(searchQuery, ignoreCase = true) ||
                        song.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                if (currentSong != null && musicService != null) {
                    BottomPlayerBar(
                        musicService = musicService,
                        musicDbViewModel = musicDbViewModel,
                        navController = navController,
                        fromScreen = Screen.HOME,
                        isFromApiSong = currentSong?.artworkUri?.startsWith("http") == true
                    )
                }
                SharedBottomNavigationBar(
                    currentScreen = currentScreen,
                    onNavigate = { screen ->
                        when (screen) {
                            Screen.HOME -> navController.navigate("home") {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
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
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                placeholder = {
                    Text(
                        text = "Search songs or artists",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Icon",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { /* Handle search action if needed */ }),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    cursorColor = MaterialTheme.colorScheme.primary
                )
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
                            text = onlineSongsError ?: "Error loading top songs",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                filteredSongs.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "No top songs available" else "No songs found",
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
                        items(filteredSongs) { apiSong ->
                            TopSongItem(
                                apiSong = apiSong,
                                onClick = {
                                    val song = Song(
                                        id = apiSong.id,
                                        title = apiSong.title,
                                        artist = apiSong.artist,
                                        duration = parseDurationToMillis(apiSong.duration),
                                        uri = apiSong.url,
                                        artworkUri = apiSong.artwork
                                    )
                                    musicDbViewModel.updateSongTimestamp(song)
                                    musicService?.playSong(song, musicDbViewModel)
                                    navController.navigate("music/${Screen.HOME.name}/true/-1")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopSongItem(apiSong: ApiSong, onClick: () -> Unit) {
    val context = LocalContext.current
    var showShareDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = apiSong.artwork,
                contentDescription = apiSong.title,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
                placeholder = painterResource(id = R.drawable.ic_launcher_foreground),
                error = painterResource(id = R.drawable.ic_launcher_foreground)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = apiSong.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = apiSong.artist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_share),
            contentDescription = "Share",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .size(24.dp)
                .clickable { showShareDialog = true }
        )
    }

    if (showShareDialog) {
        ShareDialog(
            songId = apiSong.id,
            context = context,
            onDismiss = { showShareDialog = false }
        )
    }
}

@Composable
fun ShareDialog(
    songId: Int?,
    context: Context,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Song") },
        text = {
            Column {
                Text(
                    text = "Share as URL",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            songId?.let { id ->
                                val shareUrl = "purrytify://song/$id"
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Song URL"))
                            }
                            onDismiss()
                        }
                        .padding(vertical = 8.dp),
                    fontSize = 16.sp
                )
                Text(
                    text = "Share as QR Code",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            songId?.let { id ->
                                val shareUrl = "purrytify://song/$id"
                                val qrBitmap = generateQRCode(shareUrl, 512, 512)
                                if (qrBitmap != null) {
                                    val uri = saveBitmapToCache(context, qrBitmap, "song_qr_$id.png")
                                    if (uri != null) {
                                        val shareIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            type = "image/png"
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Song QR Code"))
                                    }
                                }
                                onDismiss()
                            }
                        }
                        .padding(vertical = 8.dp),
                    fontSize = 16.sp
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun parseDurationToMillis(duration: String): Long {
    val parts = duration.split(":")
    val minutes = parts[0].toLongOrNull() ?: 0L
    val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    return (minutes * 60 + seconds) * 1000
}