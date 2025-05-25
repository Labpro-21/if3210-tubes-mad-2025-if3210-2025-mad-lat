package com.tubesmobile.purrytify.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.Log
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.ui.components.SharedBottomNavigationBar
import com.tubesmobile.purrytify.ui.components.Screen
import com.tubesmobile.purrytify.ui.components.BottomPlayerBar
import com.tubesmobile.purrytify.ui.components.NetworkOfflineScreen
import com.tubesmobile.purrytify.ui.components.SwipeableUpload
import com.tubesmobile.purrytify.service.MusicPlaybackService
import com.tubesmobile.purrytify.service.PlaybackMode
import com.tubesmobile.purrytify.ui.theme.LocalNetworkStatus
import com.tubesmobile.purrytify.ui.viewmodel.LoginViewModel
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import com.tubesmobile.purrytify.viewmodel.OnlineSongsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.compose.currentBackStackEntryAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(
    navController: NavHostController,
    loginViewModel: LoginViewModel
) {
    val context = LocalContext.current
    var musicService by remember { mutableStateOf<MusicPlaybackService?>(null) }
    var isBound by remember { mutableStateOf(false) }
    val musicDbViewModel: MusicDbViewModel = viewModel()
    val onlineSongsViewModel: OnlineSongsViewModel = viewModel()
    val songsList by musicDbViewModel.allSongs.collectAsState(initial = emptyList())
    val likedSongsList by musicDbViewModel.likedSongs.collectAsState(initial = emptyList())
    val currentSong by musicService?.currentSong?.collectAsState() ?: remember { mutableStateOf(null) }
    var searchQuery by remember { mutableStateOf("") }
    val playbackMode by musicService?.playbackMode?.collectAsState() ?: remember { mutableStateOf(PlaybackMode.REPEAT) }
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedTab by musicService?.selectedTab?.collectAsState() ?: remember { mutableStateOf("All Songs") }
    val scope = rememberCoroutineScope()
    val isConnected by LocalNetworkStatus.current.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var songToEdit by remember { mutableStateOf<Song?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    var showAddPopup by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentScreen = remember(currentRoute) {
        when (currentRoute) {
            "home" -> Screen.HOME
            "library" -> Screen.LIBRARY
            "profile" -> Screen.PROFILE
            else -> Screen.LIBRARY
        }
    }

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as MusicPlaybackService.MusicPlaybackBinder
                musicService = binder.getService()
                isBound = true
                musicService?.initializeAudioRouting()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                musicService = null
                isBound = false
            }
        }
    }

    DisposableEffect(Unit) {
        val intent = Intent(context, MusicPlaybackService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        context.startService(intent)

        onDispose {
            if (isBound) {
                context.unbindService(connection)
                isBound = false
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val commonParams = MainLibraryContentParams(
        musicService = musicService,
        musicDbViewModel = musicDbViewModel,
        onlineSongsViewModel = onlineSongsViewModel,
        songsList = songsList,
        likedSongsList = likedSongsList,
        currentSong = currentSong,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        playbackMode = playbackMode,
        selectedTab = selectedTab,
        onSetSelectedTab = { musicService?.setSelectedTab(it) },
        isConnected = isConnected,
        onCyclePlaybackMode = { musicService?.cyclePlaybackMode() },
        onSongClick = { selectedSong ->
            if (selectedSong.uri != currentSong?.uri) {
                musicService?.setPlaylist(if (selectedTab == "All Songs") songsList else likedSongsList)
                musicService?.playSong(selectedSong, musicDbViewModel, onlineSongsViewModel)
            }
            musicDbViewModel.updateSongTimestamp(selectedSong)
            navController.navigate("music/${Screen.LIBRARY.name}/false/-1")
        },
        onAddToQueue = { musicService?.addToQueue(it) },
        onEditRequest = {
            songToEdit = it
            showEditDialog = true
        },
        onDeleteRequest = {
            songToDelete = it
            showDeleteConfirmDialog = true
        },
        onShowAddPopup = { showAddPopup = true },
        navController = navController
    )

    if (isLandscape) {
        LandscapeLibraryScreenLayout(
            currentScreen = currentScreen,
            navController = navController,
            snackbarHostState = snackbarHostState,
            currentSong = currentSong,
            musicService = musicService,
            musicDbViewModel = musicDbViewModel,
            params = commonParams
        )
    } else {
        PortraitLibraryScreenLayout(
            currentScreen = currentScreen,
            navController = navController,
            snackbarHostState = snackbarHostState,
            currentSong = currentSong,
            musicService = musicService,
            musicDbViewModel = musicDbViewModel,
            params = commonParams
        )
    }

    if (showEditDialog && songToEdit != null) {
        SwipeableUpload(
            onDismiss = { showEditDialog = false; songToEdit = null },
            existingSong = songToEdit,
            onEditSong = { originalSong, newTitle, newArtist, newArtworkUri, onSuccess, onExists ->
                musicDbViewModel.updateSong(
                    originalSong,
                    newTitle,
                    newArtist,
                    newArtworkUri,
                    onSuccess = {
                        onSuccess()
                        scope.launch { snackbarHostState.showSnackbar("Song updated successfully") }
                    },
                    onExists = { errorMessage ->
                        onExists(errorMessage)
                    }
                )
            }
        )
    }

    if (showDeleteConfirmDialog && songToDelete != null) {
        DeleteConfirmationDialog(
            song = songToDelete!!,
            onDismiss = { showDeleteConfirmDialog = false; songToDelete = null },
            onConfirm = { song ->
                musicDbViewModel.deleteSong(song) {
                    showDeleteConfirmDialog = false
                    songToDelete = null
                    scope.launch { snackbarHostState.showSnackbar("${song.title} deleted") }
                    if (currentSong?.id == song.id && musicService != null) {
                        if (musicService!!.hasNextSong()) {
                            musicService!!.playNext(musicDbViewModel, onlineSongsViewModel)
                        } else {
                            musicService!!.stopPlayback(musicDbViewModel)
                        }
                    }
                }
            }
        )
    }

    if (showAddPopup) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    showAddPopup = false
                }
        )
        SwipeableUpload(
            onDismiss = { showAddPopup = false },
            onAddSong = { newSong, onExists ->
                musicDbViewModel.checkAndInsertSong(
                    context,
                    newSong,
                    onSuccess = {
                        showAddPopup = false
                        scope.launch { snackbarHostState.showSnackbar("Song added successfully") }
                    },
                    onExists = {
                        onExists()
                    }
                )
            },
            onEditSong = { originalSong, newTitle, newArtist, newArtworkUri, onSuccess, onExists ->
                musicDbViewModel.updateSong(
                    originalSong,
                    newTitle,
                    newArtist,
                    newArtworkUri,
                    onSuccess = {
                        onSuccess()
                        scope.launch { snackbarHostState.showSnackbar("Song updated successfully") }
                    },
                    onExists = { errorMessage ->
                        onExists(errorMessage)
                    }
                )
            }
        )

    }
}

data class MainLibraryContentParams(
    val musicService: MusicPlaybackService?,
    val musicDbViewModel: MusicDbViewModel,
    val onlineSongsViewModel: OnlineSongsViewModel,
    val songsList: List<Song>,
    val likedSongsList: List<Song>,
    val currentSong: Song?,
    val searchQuery: String,
    val onSearchQueryChange: (String) -> Unit,
    val playbackMode: PlaybackMode,
    val selectedTab: String,
    val onSetSelectedTab: (String) -> Unit,
    val isConnected: Boolean,
    val onCyclePlaybackMode: () -> Unit,
    val onSongClick: (Song) -> Unit,
    val onAddToQueue: (Song) -> Unit,
    val onEditRequest: (Song) -> Unit,
    val onDeleteRequest: (Song) -> Unit,
    val onShowAddPopup: () -> Unit,
    val navController: NavHostController
)

@Composable
fun PortraitLibraryScreenLayout(
    currentScreen: Screen,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    currentSong: Song?,
    musicService: MusicPlaybackService?,
    musicDbViewModel: MusicDbViewModel,
    params: MainLibraryContentParams
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                if (params.currentSong != null && params.musicService != null) {
                    BottomPlayerBar(
                        musicService = params.musicService,
                        musicDbViewModel = params.musicDbViewModel,
                        navController = params.navController,
                        fromScreen = Screen.LIBRARY,
                        isFromApiSong = params.currentSong.artworkUri.startsWith("http")
                    )
                }
                SharedBottomNavigationBar(
                    currentScreen = currentScreen,
                    onNavigate = { screen ->
                        if (currentScreen != screen) {
                            when (screen) {
                                Screen.HOME -> navController.navigate("home") { popUpTo("home") { inclusive = true } }
                                Screen.LIBRARY -> {}
                                Screen.PROFILE -> navController.navigate("profile")
                                Screen.MUSIC -> {}
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        MainLibraryContent(
            modifier = Modifier.padding(innerPadding),
            params = params
        )
    }
}

@Composable
fun LandscapeLibraryScreenLayout(
    currentScreen: Screen,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    currentSong: Song?,
    musicService: MusicPlaybackService?,
    musicDbViewModel: MusicDbViewModel,
    params: MainLibraryContentParams
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (params.currentSong != null && params.musicService != null) {
                BottomPlayerBar(
                    musicService = params.musicService,
                    musicDbViewModel = params.musicDbViewModel,
                    navController = params.navController,
                    fromScreen = Screen.LIBRARY,
                    isFromApiSong = params.currentSong.artworkUri.startsWith("http")
                )
            }
        }
    ) { innerPadding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LibrarySideNavigationBar(
                currentScreen = currentScreen,
                onNavigate = { screen ->
                    if (currentScreen != screen) {
                        when (screen) {
                            Screen.HOME -> navController.navigate("home") { popUpTo("home") { inclusive = true } }
                            Screen.LIBRARY -> {}
                            Screen.PROFILE -> navController.navigate("profile")
                            Screen.MUSIC -> {}
                        }
                    }
                }
            )
            MainLibraryContent(
                modifier = Modifier.weight(1f),
                params = params
            )
        }
    }
}

@Composable
fun LibrarySideNavigationBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(200.dp) // Standard width for side nav
            .background(MaterialTheme.colorScheme.background) // Match screen background
            .padding(vertical = 24.dp, horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(32.dp)) // Space for OS elements like time

        LibrarySideNavItem(
            text = "Home",
            icon = Icons.Filled.Home,
            isSelected = currentScreen == Screen.HOME,
            onClick = { onNavigate(Screen.HOME) }
        )
        Spacer(Modifier.height(16.dp))
        LibrarySideNavItem(
            text = "Your Library",
            icon = Icons.Filled.LibraryMusic,
            isSelected = currentScreen == Screen.LIBRARY,
            onClick = { onNavigate(Screen.LIBRARY) }
        )
        Spacer(Modifier.height(16.dp))
        LibrarySideNavItem(
            text = "Profile",
            icon = Icons.Filled.AccountCircle,
            isSelected = currentScreen == Screen.PROFILE,
            onClick = { onNavigate(Screen.PROFILE) }
        )
    }
}

@Composable
fun LibrarySideNavItem(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLibraryContent(
    modifier: Modifier = Modifier,
    params: MainLibraryContentParams
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Library",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = params.onShowAddPopup) {
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
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabButton(
                text = "All",
                isSelected = params.selectedTab == "All Songs",
                onClick = { params.onSetSelectedTab("All Songs") }
            )
            Spacer(modifier = Modifier.width(5.dp))
            TabButton(
                text = "Liked",
                isSelected = params.selectedTab == "Liked Songs",
                onClick = { params.onSetSelectedTab("Liked Songs") }
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = params.onCyclePlaybackMode,
                modifier = Modifier
                    .size(36.dp)
                    .padding(end = 8.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = when (params.playbackMode) {
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

        OutlinedTextField(
            value = params.searchQuery,
            onValueChange = params.onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            placeholder = { Text("Search songs...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search Icon") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        )

        val songsToDisplay = if (params.selectedTab == "All Songs") params.songsList else params.likedSongsList
        val filteredSongs = songsToDisplay.filter {
            it.title.contains(params.searchQuery, ignoreCase = true) ||
                    it.artist.contains(params.searchQuery, ignoreCase = true)
        }

        if (filteredSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 32.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (params.searchQuery.isEmpty()) {
                        if (params.selectedTab == "All Songs") "Your library is empty. Tap + to add a song!"
                        else "You haven't liked any songs yet."
                    } else "No songs found.",
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
            ) {
                items(filteredSongs, key = { it.id ?: it.uri }) { song ->
                    SongItem(
                        song = song,
                        isPlaying = song.uri == params.currentSong?.uri,
                        onClick = params.onSongClick,
                        onAddToQueue = params.onAddToQueue,
                        onEditRequest = params.onEditRequest,
                        onDeleteRequest = params.onDeleteRequest
                    )
                }
            }
        }
        if (!params.isConnected) {
            NetworkOfflineScreen(24)
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
fun SongItem(
    song: Song,
    isPlaying: Boolean,
    onClick: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onEditRequest: (Song) -> Unit,
    onDeleteRequest: (Song) -> Unit
) {
    val context = LocalContext.current
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(song.artworkUri) {
        imageBitmap = null // Reset for new song
        if (song.artworkUri.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(song.artworkUri)
                    if (file.exists()) {
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 2
                        }
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                        if (bitmap != null) {
                            withContext(Dispatchers.Main) {
                                imageBitmap = bitmap.asImageBitmap()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SongItem", "Error loading artwork: ${e.message}", e)
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(song) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap!!,
                    contentDescription = "Artwork for ${song.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "No artwork",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Add to Queue") },
                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                    onClick = {
                        onAddToQueue(song)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        onEditRequest(song)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                    onClick = {
                        onDeleteRequest(song)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    song: Song,
    onDismiss: () -> Unit,
    onConfirm: (Song) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Song") },
        text = { Text("Are you sure you want to delete '${song.title}' by ${song.artist}? This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = { onConfirm(song) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun isSafeFilePath(path: String): Boolean {
    return !path.contains("..") && !path.startsWith("/") && path.isNotBlank()
}