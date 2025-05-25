package com.tubesmobile.purrytify.ui.screens

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.zxing.integration.android.IntentIntegrator
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.data.model.ApiSong
import com.tubesmobile.purrytify.service.DataKeeper
import com.tubesmobile.purrytify.service.MusicPlaybackService
import com.tubesmobile.purrytify.ui.components.BottomPlayerBar
import com.tubesmobile.purrytify.ui.components.NetworkOfflineScreen
import com.tubesmobile.purrytify.ui.components.Screen
import com.tubesmobile.purrytify.ui.components.SharedBottomNavigationBar
import com.tubesmobile.purrytify.ui.theme.LocalNetworkStatus
import com.tubesmobile.purrytify.ui.viewmodel.LoginViewModel
import com.tubesmobile.purrytify.ui.viewmodel.ProfileViewModel
import com.tubesmobile.purrytify.ui.viewmodel.ProfileViewModel.ProfileState
import com.tubesmobile.purrytify.ui.viewmodel.QrScanViewModel
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import com.tubesmobile.purrytify.viewmodel.OnlineSongsViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import com.tubesmobile.purrytify.data.model.ApiSong
import kotlinx.parcelize.Parcelize

@Composable
fun HomeScreen(
    profileViewModel: ProfileViewModel = viewModel(),
    navController: NavHostController,
    musicService: MusicPlaybackService?,
    loginViewModel: LoginViewModel,
    qrScanViewModel: QrScanViewModel = viewModel()
) {
    val userName by loginViewModel.userName.collectAsState()
    val isConnected by LocalNetworkStatus.current.collectAsState()
    val context = LocalContext.current
    val musicDbViewModel: MusicDbViewModel = viewModel()
    val onlineSongsViewModel: OnlineSongsViewModel = viewModel()
    val songsListState by musicDbViewModel.allSongs.collectAsState(initial = emptyList())
    val songsTimestampState by musicDbViewModel.songsTimestamp.collectAsState(initial = emptyList())
    val currentSong by musicService?.currentSong?.collectAsState() ?: remember { mutableStateOf(null) }
    val onlineGlobalSongsApi by onlineSongsViewModel.onlineGlobalSongs.collectAsState()
    val onlineCountrySongsApi by onlineSongsViewModel.onlineCountrySongs.collectAsState()
    val isLoadingSongs by musicDbViewModel.isLoadingSongs.collectAsState()
    val songsError by musicDbViewModel.songsError.collectAsState()
    val isLoadingOnlineSongs by onlineSongsViewModel.isLoading.collectAsState()
    val onlineSongsError by onlineSongsViewModel.error.collectAsState()
    val profileState by profileViewModel.profile.collectAsState()
    val scanResult by qrScanViewModel.scanResult.collectAsState()
    val baseUrl = "http://34.101.226.132:3000"
    var dynamicProfilePhotoUrl by remember { mutableStateOf<String?>(null) }
    val showPermissionDeniedDialog = remember { mutableStateOf(false) }
    val likedSongs by musicDbViewModel.likedSongs.collectAsState(initial = emptyList())

    var recommendedBasedOnLikes by remember { mutableStateOf<List<Song>>(emptyList()) }
    var topGlobalRecommendations by remember { mutableStateOf<List<Song>>(emptyList()) }
    var topCountryRecommendations by remember { mutableStateOf<List<Song>>(emptyList()) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentScreen = remember(currentRoute) {
        when (currentRoute) {
            "home" -> Screen.HOME
            "library" -> Screen.LIBRARY
            "profile" -> Screen.PROFILE
            else -> Screen.HOME
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            IntentIntegrator(context as androidx.activity.ComponentActivity)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setPrompt("Scan a QR code")
                .setCameraId(0)
                .setBeepEnabled(true)
                .setBarcodeImageEnabled(true)
                .initiateScan()
        } else {
            showPermissionDeniedDialog.value = true
        }
    }

    val qrScanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val scanResultData = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        if (scanResultData != null && scanResultData.contents != null) {
            qrScanViewModel.setScanResult(scanResultData.contents)
        } else {
            qrScanViewModel.setScanResult(null)
        }
    }

    LaunchedEffect(likedSongs, songsListState, onlineGlobalSongsApi, onlineCountrySongsApi) {
        val mappedOnlineGlobalSongs = onlineGlobalSongsApi.map { it.toSong() }
        val mappedOnlineCountrySongs = onlineCountrySongsApi.map { it.toSong() }

        if (likedSongs.isNotEmpty()) {
            val likedArtists = likedSongs.map { it.artist.lowercase() }.distinct().toSet()
            val allAvailableSongs = (songsListState + mappedOnlineGlobalSongs + mappedOnlineCountrySongs)
                .distinctBy { it.uri }
            recommendedBasedOnLikes = allAvailableSongs.filter { song ->
                val songArtistLower = song.artist.lowercase()
                !likedSongs.any { likedSong -> likedSong.uri == song.uri } &&
                        (likedArtists.contains(songArtistLower))
            }.shuffled().take(10)
        } else {
            recommendedBasedOnLikes = mappedOnlineGlobalSongs.shuffled().take(5)
        }
        topGlobalRecommendations = mappedOnlineGlobalSongs.shuffled().take(10)
        topCountryRecommendations = mappedOnlineCountrySongs.shuffled().take(10)
    }

    LaunchedEffect(scanResult) {
        scanResult?.let { deepLink ->
            val (songId, isValid) = qrScanViewModel.parseDeepLink(deepLink)
            if (isValid && songId != null) {
                navController.navigate("music/${Screen.HOME.name}/true/$songId") {
                    popUpTo("home") { inclusive = false }
                    launchSingleTop = true
                }
            }
            qrScanViewModel.clearScanResult()
        }
    }

    val newSongs = remember(songsListState, songsTimestampState) {
        val timestampMap = songsTimestampState.associateBy { it.songId }
        songsListState
            .filter { it.id !in timestampMap }
            .sortedByDescending { it.id }
    }
    val recentlyPlayedSongs = remember(songsListState, songsTimestampState) {
        val timestampMap = songsTimestampState.associateBy { it.songId }
        songsListState
            .filter { it.id in timestampMap }
            .sortedByDescending { timestampMap[it.id]?.lastPlayedTimestamp ?: 0L }
            .take(5)
    }

    LaunchedEffect(key1 = isConnected, key2 = profileState) {
        if (isConnected && profileState is ProfileState.Loading) {
            profileViewModel.loadProfile()
        }
    }

    LaunchedEffect(key1 = profileState) {
        if (profileState is ProfileState.Success) {
            val profile = (profileState as ProfileState.Success).profile
            if (!profile.profilePhoto.isNullOrEmpty()) {
                val sanitizedPhoto = sanitizeFileName(profile.profilePhoto)
                dynamicProfilePhotoUrl = "$baseUrl/uploads/profile-picture/$sanitizedPhoto"
            } else {
                dynamicProfilePhotoUrl = null
            }
        } else if (profileState is ProfileState.Error || profileState is ProfileState.SessionExpired) {
            dynamicProfilePhotoUrl = null
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        LandscapeHomeScreenLayout(
            userName = userName,
            dynamicProfilePhotoUrl = dynamicProfilePhotoUrl,
            isConnected = isConnected,
            navController = navController,
            musicService = musicService,
            musicDbViewModel = musicDbViewModel,
            onlineSongsViewModel = onlineSongsViewModel,
            recommendedBasedOnLikes = recommendedBasedOnLikes,
            topGlobalRecommendations = topGlobalRecommendations,
            topCountryRecommendations = topCountryRecommendations,
            newSongs = newSongs,
            recentlyPlayedSongs = recentlyPlayedSongs,
            isLoadingSongs = isLoadingSongs,
            songsError = songsError,
            isLoadingOnlineSongs = isLoadingOnlineSongs,
            onlineSongsError = onlineSongsError,
            context = context,
            cameraPermissionLauncher = cameraPermissionLauncher,
            showPermissionDeniedDialog = showPermissionDeniedDialog,
            currentSong = currentSong,
            currentScreen = currentScreen
        )
    } else {
        PortraitHomeScreenLayout(
            userName = userName,
            dynamicProfilePhotoUrl = dynamicProfilePhotoUrl,
            isConnected = isConnected,
            navController = navController,
            musicService = musicService,
            musicDbViewModel = musicDbViewModel,
            onlineSongsViewModel = onlineSongsViewModel,
            recommendedBasedOnLikes = recommendedBasedOnLikes,
            topGlobalRecommendations = topGlobalRecommendations,
            topCountryRecommendations = topCountryRecommendations,
            newSongs = newSongs,
            recentlyPlayedSongs = recentlyPlayedSongs,
            isLoadingSongs = isLoadingSongs,
            songsError = songsError,
            isLoadingOnlineSongs = isLoadingOnlineSongs,
            onlineSongsError = onlineSongsError,
            context = context,
            cameraPermissionLauncher = cameraPermissionLauncher,
            showPermissionDeniedDialog = showPermissionDeniedDialog,
            currentSong = currentSong,
            currentScreen = currentScreen
        )
    }

    if (showPermissionDeniedDialog.value) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog.value = false },
            title = { Text("Camera Permission Denied") },
            text = { Text("This feature requires camera access to scan QR codes. Please enable camera permission in your device settings.") },
            confirmButton = {
                TextButton(onClick = { showPermissionDeniedDialog.value = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun PortraitHomeScreenLayout(
    userName: String?,
    dynamicProfilePhotoUrl: String?,
    isConnected: Boolean,
    navController: NavHostController,
    musicService: MusicPlaybackService?,
    musicDbViewModel: MusicDbViewModel,
    onlineSongsViewModel: OnlineSongsViewModel,
    recommendedBasedOnLikes: List<Song>,
    topGlobalRecommendations: List<Song>,
    topCountryRecommendations: List<Song>,
    newSongs: List<Song>,
    recentlyPlayedSongs: List<Song>,
    isLoadingSongs: Boolean,
    songsError: String?,
    isLoadingOnlineSongs: Boolean,
    onlineSongsError: String?,
    context: android.content.Context,
    cameraPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    showPermissionDeniedDialog: MutableState<Boolean>,
    currentSong: Song?,
    currentScreen: Screen
) {
    Scaffold(
        bottomBar = {
            Column {
                if (currentSong != null && musicService != null) {
                    BottomPlayerBar(
                        musicService = musicService,
                        musicDbViewModel = musicDbViewModel,
                        navController = navController,
                        fromScreen = currentScreen,
                        isFromApiSong = currentSong.artworkUri.startsWith("http")
                    )
                }
                SharedBottomNavigationBar(
                    currentScreen = currentScreen,
                    onNavigate = { screen ->
                        if (currentScreen != screen) {
                            when (screen) {
                                Screen.HOME -> navController.navigate("home") { popUpTo("home") { inclusive = true } }
                                Screen.LIBRARY -> navController.navigate("library")
                                Screen.PROFILE -> navController.navigate("profile")
                                Screen.MUSIC -> {}
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        MainContent(
            modifier = Modifier.padding(innerPadding),
            isLandscape = false,
            userName, dynamicProfilePhotoUrl, isConnected, navController, musicService, musicDbViewModel,
            onlineSongsViewModel, recommendedBasedOnLikes, topGlobalRecommendations, topCountryRecommendations,
            newSongs, recentlyPlayedSongs, isLoadingSongs, songsError, isLoadingOnlineSongs, onlineSongsError,
            context, cameraPermissionLauncher, showPermissionDeniedDialog
        )
    }
}

@Composable
fun LandscapeHomeScreenLayout(
    userName: String?,
    dynamicProfilePhotoUrl: String?,
    isConnected: Boolean,
    navController: NavHostController,
    musicService: MusicPlaybackService?,
    musicDbViewModel: MusicDbViewModel,
    onlineSongsViewModel: OnlineSongsViewModel,
    recommendedBasedOnLikes: List<Song>,
    topGlobalRecommendations: List<Song>,
    topCountryRecommendations: List<Song>,
    newSongs: List<Song>,
    recentlyPlayedSongs: List<Song>,
    isLoadingSongs: Boolean,
    songsError: String?,
    isLoadingOnlineSongs: Boolean,
    onlineSongsError: String?,
    context: android.content.Context,
    cameraPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    showPermissionDeniedDialog: MutableState<Boolean>,
    currentSong: Song?,
    currentScreen: Screen
) {
    Scaffold(
        bottomBar = {
            if (currentSong != null && musicService != null) {
                BottomPlayerBar(
                    musicService = musicService,
                    musicDbViewModel = musicDbViewModel,
                    navController = navController,
                    fromScreen = currentScreen,
                    isFromApiSong = currentSong.artworkUri.startsWith("http")
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
            SideNavigationBar(
                currentScreen = currentScreen,
                onNavigate = { screen ->
                    if (currentScreen != screen) {
                        when (screen) {
                            Screen.HOME -> navController.navigate("home") { popUpTo("home") { inclusive = true } }
                            Screen.LIBRARY -> navController.navigate("library")
                            Screen.PROFILE -> navController.navigate("profile")
                            Screen.MUSIC -> {}
                        }
                    }
                }
            )
            MainContent(
                modifier = Modifier.weight(1f),
                isLandscape = true,
                userName, dynamicProfilePhotoUrl, isConnected, navController, musicService, musicDbViewModel,
                onlineSongsViewModel, recommendedBasedOnLikes, topGlobalRecommendations, topCountryRecommendations,
                newSongs, recentlyPlayedSongs, isLoadingSongs, songsError, isLoadingOnlineSongs, onlineSongsError,
                context, cameraPermissionLauncher, showPermissionDeniedDialog
            )
        }
    }
}

@Composable
fun SideNavigationBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(200.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 24.dp, horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        SideNavItem(
            text = "Home",
            icon = Icons.Filled.Home,
            isSelected = currentScreen == Screen.HOME,
            onClick = { onNavigate(Screen.HOME) }
        )
        Spacer(Modifier.height(16.dp))
        SideNavItem(
            text = "Your Library",
            icon = Icons.Filled.LibraryMusic,
            isSelected = currentScreen == Screen.LIBRARY,
            onClick = { onNavigate(Screen.LIBRARY) }
        )
        Spacer(Modifier.height(16.dp))
        SideNavItem(
            text = "Profile",
            icon = Icons.Filled.AccountCircle,
            isSelected = currentScreen == Screen.PROFILE,
            onClick = { onNavigate(Screen.PROFILE) }
        )
    }
}

@Composable
fun SideNavItem(
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
            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp
        )
    }
}

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    isLandscape: Boolean,
    userName: String?,
    dynamicProfilePhotoUrl: String?,
    isConnected: Boolean,
    navController: NavHostController,
    musicService: MusicPlaybackService?,
    musicDbViewModel: MusicDbViewModel,
    onlineSongsViewModel: OnlineSongsViewModel,
    recommendedBasedOnLikes: List<Song>,
    topGlobalRecommendations: List<Song>,
    topCountryRecommendations: List<Song>,
    newSongs: List<Song>,
    recentlyPlayedSongs: List<Song>,
    isLoadingSongs: Boolean,
    songsError: String?,
    isLoadingOnlineSongs: Boolean,
    onlineSongsError: String?,
    context: android.content.Context,
    cameraPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    showPermissionDeniedDialog: MutableState<Boolean>
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
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

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Charts",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        IntentIntegrator(context as androidx.activity.ComponentActivity)
                            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                            .setPrompt("Scan a QR code")
                            .setCameraId(0)
                            .setBeepEnabled(true)
                            .setBarcodeImageEnabled(true)
                            .initiateScan()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = "Scan QR Code",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

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
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ChartItem(
                            title = "Top 50",
                            subtitle = "GLOBAL",
                            drawableResId = R.drawable.global,
                            onClick = { navController.navigate("top50/global") }
                        )
                    }
                    item {
                        val locationSubtitle = DataKeeper.location?.takeIf { it.isNotBlank() } ?: "Country"
                        ChartItem(
                            title = "Top 10",
                            subtitle = locationSubtitle.uppercase(),
                            drawableResId = R.drawable.country,
                            onClick = { navController.navigate("top50/country") }
                        )
                    }
                }
            }
        }

        if (recommendedBasedOnLikes.isNotEmpty()) {
            Text(
                text = "Based on Your Likes",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(recommendedBasedOnLikes, key = { song -> "liked_${song.id}_${song.uri}" }) { song ->
                    RecommendedSongItem(song = song, onClick = { selectedSong ->
                        musicService?.playSong(selectedSong, musicDbViewModel, onlineSongsViewModel)
                        val isApi = selectedSong.uri.startsWith("http")
                        navController.navigate("music/${Screen.HOME.name}/${isApi}/${selectedSong.id ?: -1}")
                    })
                }
            }
        }

        if (topGlobalRecommendations.isNotEmpty()) {
            Text(
                text = "Top Global Mix",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(topGlobalRecommendations, key = { song -> "global_${song.id}_${song.uri}" }) { song ->
                    RecommendedSongItem(song = song, onClick = { selectedSong ->
                        musicService?.playSong(selectedSong, musicDbViewModel, onlineSongsViewModel)
                        navController.navigate("music/${Screen.HOME.name}/true/${selectedSong.id ?: -1}")
                    })
                }
            }
        }
        if (topCountryRecommendations.isNotEmpty() && !isLandscape) {
            Text(
                text = "Top Country Mix",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(topCountryRecommendations, key = { song -> "country_${song.id}_${song.uri}" }) { song ->
                    RecommendedSongItem(song = song, onClick = { selectedSong ->
                        musicService?.playSong(selectedSong, musicDbViewModel, onlineSongsViewModel)
                        navController.navigate("music/${Screen.HOME.name}/true/${selectedSong.id ?: -1}")
                    })
                }
            }
        }


        Text(
            text = "New songs",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
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
                    items(newSongs, key = { song -> "new_${song.id}_${song.uri}" }) { song ->
                        NewSongItem(
                            song = song,
                            onClick = { selectedSong ->
                                musicDbViewModel.updateSongTimestamp(selectedSong)
                                musicService?.playSong(selectedSong, musicDbViewModel, onlineSongsViewModel)
                                navController.navigate("music/${Screen.HOME.name}/false/-1")
                            },
                            musicService = musicService
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

        val recentlyPlayedModifier = if (isLandscape) {
            Modifier.heightIn(max = 375.dp)
        } else {
            Modifier.heightIn(max = 375.dp)
        }

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
                    modifier = recentlyPlayedModifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recentlyPlayedSongs, key = { song -> "recent_${song.id}_${song.uri}" }) { song ->
                        RecentlyPlayedItem(
                            song = song,
                            onClick = { selectedSong ->
                                if (selectedSong.uri != musicService?.currentSong?.value?.uri) {
                                    musicService?.playSong(selectedSong, musicDbViewModel, onlineSongsViewModel)
                                }
                                musicDbViewModel.updateSongTimestamp(selectedSong)
                                navController.navigate("music/${Screen.LIBRARY.name}/false/-1")
                            },
                            musicService = musicService
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


@Composable
fun ChartItem(
    title: String,
    subtitle: String,
    imagePath: String? = null,
    @DrawableRes drawableResId: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageData: Any = drawableResId
        ?: imagePath?.takeIf { it.isNotBlank() }
        ?: R.drawable.ic_launcher_foreground

    Column(
        modifier = modifier
            .width(150.dp)
            .clickable(onClick = onClick)
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageData)
                .crossfade(true)
                .build(),
            contentDescription = "Chart: $title - $subtitle",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle.uppercase(),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

private fun parseDurationToMillis(duration: String): Long {
    val parts = duration.split(":")
    val minutes = parts[0].toLongOrNull() ?: 0L
    val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    return (minutes * 60 + seconds) * 1000
}

fun ApiSong.toSong(): Song {
    return Song(
        id = this.id,
        title = this.title,
        artist = this.artist ?: "Unknown Artist",
        duration = parseDurationToMillis(this.duration),
        uri = this.url,
        artworkUri = this.artwork ?: ""
    )
}

@Composable
fun NewSongItem(song: Song, onClick: (Song) -> Unit, musicService: MusicPlaybackService?) {
    val currentSong by musicService?.currentSong?.collectAsState() ?: remember { mutableStateOf(null) }
    val context = LocalContext.current
    val imageBitmap = remember(song.artworkUri, song.uri) {
        if (song.artworkUri.isNotEmpty()) {
            val retriever = MediaMetadataRetriever()
            try {
                when {
                    song.artworkUri == "Metadata" && song.uri.isNotBlank() -> {
                        retriever.setDataSource(context, Uri.parse(song.uri))
                        retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                    }
                    song.artworkUri.startsWith("http") -> null
                    else -> BitmapFactory.decodeFile(song.artworkUri)?.asImageBitmap()
                }
            } catch (e: Exception) { null }
            finally { retriever.release() }
        } else null
    }

    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable { onClick(song) },
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(song.artworkUri.ifEmpty { R.drawable.ic_launcher_foreground })
                        .error(R.drawable.ic_launcher_foreground)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .crossfade(true)
                        .build(),
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = song.title,
            color = if (song.id == currentSong?.id && song.uri == currentSong?.uri) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
fun RecentlyPlayedItem(song: Song, onClick: (Song) -> Unit, musicService: MusicPlaybackService?) {
    val currentSong by musicService?.currentSong?.collectAsState() ?: remember { mutableStateOf(null) }
    val context = LocalContext.current
    val imageBitmap = remember(song.artworkUri, song.uri) {
        if (song.artworkUri.isNotEmpty()) {
            val retriever = MediaMetadataRetriever()
            try {
                when {
                    song.artworkUri == "Metadata" && song.uri.isNotBlank() -> {
                        retriever.setDataSource(context, Uri.parse(song.uri))
                        retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                    }
                    song.artworkUri.startsWith("http") -> null
                    else -> BitmapFactory.decodeFile(song.artworkUri)?.asImageBitmap()
                }
            } catch (e: Exception) { null }
            finally { retriever.release() }
        } else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(song) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(song.artworkUri.ifEmpty { R.drawable.ic_launcher_foreground })
                        .error(R.drawable.ic_launcher_foreground)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .crossfade(true)
                        .build(),
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (song.id == currentSong?.id && song.uri == currentSong?.uri) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
fun RecommendedSongItem(
    song: Song,
    onClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .width(110.dp)
            .clickable { onClick(song) }
            .padding(bottom = 8.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(song.artworkUri.ifEmpty { R.drawable.ic_launcher_foreground })
                .error(R.drawable.ic_launcher_foreground)
                .placeholder(R.drawable.ic_launcher_foreground)
                .crossfade(true)
                .build(),
            contentDescription = song.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

@Parcelize
data class Song(
    val id: Int? = null,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: String,
    val artworkUri: String
) : Parcelable

data class SongTimestamp(
    val userEmail: String,
    val songId: Int,
    val lastPlayedTimestamp: Long? = null
)
