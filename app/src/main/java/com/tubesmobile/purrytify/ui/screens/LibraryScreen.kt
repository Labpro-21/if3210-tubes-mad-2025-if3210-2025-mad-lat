package com.tubesmobile.purrytify.ui.screens

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme
import com.tubesmobile.purrytify.ui.components.SharedBottomNavigationBar
import com.tubesmobile.purrytify.ui.components.Screen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tubesmobile.purrytify.ui.components.BottomPlayerBar
import com.tubesmobile.purrytify.ui.components.SwipeableUpload
import com.tubesmobile.purrytify.ui.viewmodel.LoginViewModel
import com.tubesmobile.purrytify.ui.viewmodel.MusicBehaviorViewModel
import com.tubesmobile.purrytify.ui.viewmodel.PlaybackMode
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(
    navController: NavHostController,
    musicBehaviorViewModel: MusicBehaviorViewModel,
    loginViewModel: LoginViewModel
) {
    val currentScreen = remember { mutableStateOf(Screen.LIBRARY) }
    var showPopup by remember { mutableStateOf(false) }
    val musicDbViewModel: MusicDbViewModel = viewModel()
    val songsList by musicDbViewModel.allSongs.collectAsState(initial = emptyList())
    val likedSongsList by musicDbViewModel.likedSongs.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val currentSong by musicBehaviorViewModel.currentSong.collectAsState()
    var selectedTab by remember { mutableStateOf("All Songs") }
    var searchQuery by remember { mutableStateOf("") }
    val playbackMode by musicBehaviorViewModel.playbackMode.collectAsState()

    Scaffold(
        bottomBar = {
            Column {
                if (currentSong != null) {
                    BottomPlayerBar(
                        musicBehaviorViewModel = musicBehaviorViewModel,
                        navController = navController,
                        fromScreen = Screen.HOME
                    )
                }

                SharedBottomNavigationBar(
                    currentScreen = currentScreen.value,
                    onNavigate = { screen ->
                        currentScreen.value = screen
                        when (screen) {
                            Screen.HOME -> navController.navigate("home")
                            Screen.LIBRARY -> {}
                            Screen.PROFILE -> navController.navigate("profile")
                            Screen.MUSIC -> {}
                        }
                    },
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
                    .padding(start = 16.dp, end = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Library",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showPopup = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = "Add",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabButton(
                    text = "All",
                    isSelected = selectedTab == "All Songs",
                    onClick = { selectedTab = "All Songs" }
                )
                Spacer(modifier = Modifier.width(5.dp))
                TabButton(
                    text = "Liked",
                    isSelected = selectedTab == "Liked Songs",
                    onClick = { selectedTab = "Liked Songs" }
                )

                Spacer(modifier = Modifier.weight(1f)) // Dorong ke kanan

                IconButton(
                    onClick = { musicBehaviorViewModel.cyclePlaybackMode() },
                    modifier = Modifier
                        .size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            id = when (playbackMode) {
                                PlaybackMode.REPEAT -> R.drawable.ic_repeat
                                PlaybackMode.REPEAT_ONE -> R.drawable.ic_repeatone
                                PlaybackMode.SHUFFLE -> R.drawable.ic_shuffle
                            }
                        ),
                        contentDescription = "Playback Mode",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = {
                    Text("Search songs...")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Icon"
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            )

            val songsToDisplay = if (selectedTab == "All Songs") songsList else likedSongsList
            val filteredSongs = songsToDisplay.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
            }

            if (filteredSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) {
                            if (selectedTab == "All Songs")
                                "Your library is empty. Tap + to add a song!"
                            else
                                "You haven't liked any songs yet."
                        } else {
                            "No songs found."
                        },
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    items(filteredSongs) { song ->
                        SongItem(
                            song = song,
                            isPlaying = song.uri == currentSong?.uri,
                            onClick = { selectedSong ->
                                if (selectedSong.uri != currentSong?.uri) {
                                    musicBehaviorViewModel.setPlaylist(songsToDisplay)
                                    musicBehaviorViewModel.playSong(selectedSong, context)
                                }
                                musicDbViewModel.updateSongTimestamp(selectedSong)
                                navController.navigate("music/${Screen.LIBRARY.name}")
                            },
                            onAddToQueue = { musicBehaviorViewModel.addToQueue(it) }

                        )
                    }
                }
            }
        }
        if (showPopup) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        showPopup = false
                    }
            )

            SwipeableUpload(
                onDismiss = { showPopup = false },
                onAddSong = { song, onExists ->
                    musicDbViewModel.checkAndInsertSong(
                        context,
                        song,
                        onExists
                    )
                    showPopup = false
                }
            )
        }
    }
}


@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .height(36.dp)
            .wrapContentWidth()
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SongItem(song: Song, isPlaying: Boolean, onClick: (Song) -> Unit, onAddToQueue: (Song) -> Unit) {
    val context = LocalContext.current
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(song.artworkUri) {
        val file = File(song.artworkUri)
        if (file.exists()) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            imageBitmap = bitmap?.asImageBitmap()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(song) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            imageBitmap != null -> {
                Image(
                    bitmap = imageBitmap!!,
                    contentDescription = song.title,
                    modifier = Modifier.size(56.dp)
                )
            }

            song.artworkUri.isEmpty() -> {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = song.title,
                    modifier = Modifier.size(56.dp)
                )
            }

            else -> {
                val resId = song.artworkUri.toIntOrNull() ?: R.drawable.ic_launcher_foreground
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = song.title,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text = song.title,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
        }

        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Add to Queue") },
                    onClick = {
                        onAddToQueue(song)
                        expanded = false
                    }
                )
            }
        }
    }
}


//@Preview(showBackground = true)
//@Composable
//fun MusicLibraryScreenPreview() {
//    val navController = rememberNavController()
//    val previewViewModel: MusicBehaviorViewModel = viewModel()
//
//    PurrytifyTheme {
//        MusicLibraryScreen(navController, previewViewModel)
//    }
//}