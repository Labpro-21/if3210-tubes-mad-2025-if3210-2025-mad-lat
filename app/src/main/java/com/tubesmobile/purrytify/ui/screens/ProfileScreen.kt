package com.tubesmobile.purrytify.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme
import com.tubesmobile.purrytify.ui.components.SharedBottomNavigationBar
import com.tubesmobile.purrytify.ui.components.Screen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.tubesmobile.purrytify.data.model.ProfileResponse
import com.tubesmobile.purrytify.service.DataKeeper
import com.tubesmobile.purrytify.ui.components.NetworkOfflineScreen
import com.tubesmobile.purrytify.ui.components.ProfileData
import com.tubesmobile.purrytify.ui.components.SwipeableProfileEditDialog
import com.tubesmobile.purrytify.service.MusicPlaybackService
import com.tubesmobile.purrytify.ui.theme.LocalNetworkStatus
import com.tubesmobile.purrytify.ui.viewmodel.LoginViewModel
import com.tubesmobile.purrytify.ui.viewmodel.ProfileViewModel
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import androidx.compose.foundation.pager.rememberPagerState
import java.util.regex.Pattern
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.tubesmobile.purrytify.data.model.ArtistData
import com.tubesmobile.purrytify.data.model.MonthlySoundCapsuleData
import com.tubesmobile.purrytify.data.model.SongData
import com.tubesmobile.purrytify.ui.components.MonthlySoundCapsuleCard

@Composable
fun ProfileScreen(navController: NavHostController, loginViewModel: LoginViewModel, musicService: MusicPlaybackService?) {
    val currentScreen = remember { mutableStateOf(Screen.PROFILE) }
    val viewModel: ProfileViewModel = viewModel()
    val musicDbViewModel: MusicDbViewModel = viewModel()
    val profileState by viewModel.profile.collectAsState()
    val isConnected by LocalNetworkStatus.current.collectAsState()
    val songsList by musicDbViewModel.allSongs.collectAsState(initial = emptyList())
    val likedSongsList by musicDbViewModel.likedSongs.collectAsState(initial = emptyList())
    val songsTimestamp by musicDbViewModel.songsTimestamp.collectAsState(initial = emptyList())

    val newSongs = remember(songsList, songsTimestamp) {
        val timestampMap = songsTimestamp.associateBy { it.songId }
        songsList
            .filter { it.id != null && it.id !in timestampMap }
            .sortedByDescending { it.id }
    }

    DataKeeper.songsAmount = songsList.size
    DataKeeper.likesAmount = likedSongsList.size
    DataKeeper.listenedAmount = songsList.size - newSongs.size

    LaunchedEffect(key1 = Unit) {
        if (isConnected) {
            viewModel.loadProfile()
        }
    }

    Scaffold(
        bottomBar = {
            SharedBottomNavigationBar(
                currentScreen = currentScreen.value,
                onNavigate = { screen ->
                    currentScreen.value = screen
                    when (screen) {
                        Screen.HOME -> navController.navigate("home")
                        Screen.LIBRARY -> navController.navigate("library")
                        Screen.PROFILE -> {}
                        Screen.MUSIC -> {}
                    }
                },
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            if (!isConnected) {
                NetworkOfflineScreen(24)
            }

            when (profileState) {
                is ProfileViewModel.ProfileState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ProfileViewModel.ProfileState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_network_off),
                            contentDescription = "Network unavailable",
                            tint = Color.White,
                            modifier = Modifier
                                .size(128.dp)
                                .clickable { /* Show menu */ }
                        )
                        if (isConnected) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadProfile() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is ProfileViewModel.ProfileState.Success -> {
                    val profile = (profileState as ProfileViewModel.ProfileState.Success).profile
                    ProfileContent(
                        profile = profile,
                        viewModel = viewModel,
                        navController = navController,
                        onLogout = {
                            viewModel.logout()
                            loginViewModel.resetLoginState()
                            musicService?.onCleared()
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
                is ProfileViewModel.ProfileState.SessionExpired -> {
                    LaunchedEffect(Unit) {
                        loginViewModel.resetLoginState()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileContent(
    profile: ProfileResponse,
    viewModel: ProfileViewModel,
    navController: NavHostController, // Added NavController
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val baseUrl = "http://34.101.226.132:3000" // Consider moving to a config/constant file
    val sanitizedPhoto = sanitizeFileName(profile.profilePhoto)
    val profilePhotoUrl = "$baseUrl/uploads/profile-picture/$sanitizedPhoto"
    var expanded by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    // Dummy data for Monthly Sound Capsules
    val soundCapsules = remember {
        listOf(
            MonthlySoundCapsuleData( // May 2025 - No data
                monthYear = "May 2025",
                timeListenedMinutes = null, dailyAverageMinutes = null,
                topArtistName = null, topArtistImageUrl = null, totalArtistsListenedThisMonth = null, topArtistsList = null,
                topSongName = null, topSongImageUrl = null, totalSongsPlayedThisMonth = null, topSongsList = null,
                dayStreakCount = null, dayStreakSongName = null, dayStreakSongArtist = null, dayStreakFullText = null, dayStreakDateRange = null, dayStreakImage = null,
                hasData = false
            ),
            MonthlySoundCapsuleData( // April 2025 - With data
                monthYear = "April 2025",
                timeListenedMinutes = 862,
                dailyAverageMinutes = 33,
                topArtistName = "The Beatles",
                topArtistImageUrl = "https://e-cdns-images.dzcdn.net/images/artist/b290e6c703939503914620c25452a152/264x264-000000-80-0-0.jpg",
                totalArtistsListenedThisMonth = 137,
                topArtistsList = listOf(
                    ArtistData(1, "Beatles", "https://e-cdns-images.dzcdn.net/images/artist/b290e6c703939503914620c25452a152/264x264-000000-80-0-0.jpg"),
                    ArtistData(2, "The Weeknd", "https://i.scdn.co/image/ab676161000051748ae7f2aaa9817a704a87ea36"),
                    ArtistData(3, "Kanye West", "https://i.scdn.co/image/ab67616100005174c0118f0a00a00aa761d5f507"),
                    ArtistData(4, "Doechii", "https://i.scdn.co/image/ab67616100005174cf8a66352927a65606ccd6a4")
                ),
                topSongName = "Starboy",
                topSongImageUrl = "https://i.scdn.co/image/ab67616d0000b273c05276696219639749f25088",
                totalSongsPlayedThisMonth = 203,
                topSongsList = listOf(
                    SongData(1, "Starboy", "The Weeknd, Daft Punk", "https://i.scdn.co/image/ab67616d0000b273c05276696219639749f25088", 15),
                    SongData(2, "Loose", "Daniel Caesar", "https://images.genius.com/6a8fac3cf1b03a233988398647661990.1000x1000x1.jpg", 12),
                    SongData(3, "Nights", "Frank Ocean", "https://upload.wikimedia.org/wikipedia/en/a/a0/Blonde_-_Frank_Ocean.jpeg", 8),
                    SongData(4, "Doomsday", "MF DOOM, Pebbles The Invisible Girl", "https://i.scdn.co/image/ab67616d0000b273daa5c409172d490928ea441a", 4)
                ),
                dayStreakCount = 5,
                dayStreakSongName = "Loose",
                dayStreakSongArtist = "Daniel Caesar",
                dayStreakFullText = "You played Loose by Daniel Caesar day after day. You were on fire.",
                dayStreakDateRange = "Mar 21-25, 2025",
                dayStreakImage = "https://images.genius.com/6a8fac3cf1b03a233988398647661990.1000x1000x1.jpg", // Example image for streak
                hasData = true
            ),
            MonthlySoundCapsuleData( // March 2025 - With data
                monthYear = "March 2025",
                timeListenedMinutes = 601,
                dailyAverageMinutes = 25,
                topArtistName = "Doechii",
                topArtistImageUrl = "https://i.scdn.co/image/ab67616100005174cf8a66352927a65606ccd6a4",
                totalArtistsListenedThisMonth = 105,
                topArtistsList = listOf( /* ... add more artists ... */ ),
                topSongName = "Nights",
                topSongImageUrl = "https://upload.wikimedia.org/wikipedia/en/a/a0/Blonde_-_Frank_Ocean.jpeg",
                totalSongsPlayedThisMonth = 150,
                topSongsList = listOf( /* ... add more songs ... */ ),
                dayStreakCount = 3,
                dayStreakSongName = "Persuasive",
                dayStreakSongArtist = "Doechii",
                dayStreakFullText = "You vibed to Persuasive by Doechii for 3 days straight!",
                dayStreakDateRange = "Mar 10-12, 2025",
                dayStreakImage = "https://i.scdn.co/image/ab67616d0000b273103213779f756af07f16174c", // Example image
                hasData = true
            ),
            MonthlySoundCapsuleData( // Feb 2025 - No data
                monthYear = "February 2025",
                timeListenedMinutes = null, dailyAverageMinutes = null,
                topArtistName = null, topArtistImageUrl = null, totalArtistsListenedThisMonth = null, topArtistsList = null,
                topSongName = null, topSongImageUrl = null, totalSongsPlayedThisMonth = null, topSongsList = null,
                dayStreakCount = null, dayStreakSongName = null, dayStreakSongArtist = null, dayStreakFullText = null, dayStreakDateRange = null, dayStreakImage = null,
                hasData = false
            )
        ).sortedByDescending { it.monthYear } // Ensure latest month is first or handle sorting as needed
    }

    var currentCapsuleIndex by remember { mutableStateOf(0) }
    val currentCapsule = soundCapsules[currentCapsuleIndex]

    Box(modifier = Modifier.fillMaxSize()) { // For the MoreVert menu
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()) // Make the whole profile scrollable
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(50.dp)) // Space for status bar and top items

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(profilePhotoUrl)
                    .crossfade(true)
                    .placeholder(R.drawable.ic_launcher_foreground) // Your placeholder
                    .error(R.drawable.ic_launcher_foreground)       // Your error placeholder
                    .build(),
                contentDescription = "Profile Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(100.dp) // Slightly smaller as per design
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(sanitizeText(profile.username), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(sanitizeText(profile.email), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Text(sanitizeText(profile.location), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp), // More padding for stats
                horizontalArrangement = Arrangement.SpaceAround // Or SpaceEvenly
            ) {
                StatItem("SONGS", DataKeeper.songsAmount.toString())
                StatItem("LIKED", DataKeeper.likesAmount.toString())
                StatItem("LISTENED", DataKeeper.listenedAmount.toString()) // This is overall, monthly is in capsule
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sound Capsule Section
            Text(
                "Your Sound Capsule",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Month Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (currentCapsuleIndex > 0) currentCapsuleIndex-- },
                    enabled = currentCapsuleIndex > 0
                ) {
                    Icon(
                        Icons.Filled.ArrowBackIosNew,
                        contentDescription = "Previous Month",
                        tint = if (currentCapsuleIndex > 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    )
                }
                Text(
                    currentCapsule.monthYear,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(
                    onClick = { if (currentCapsuleIndex < soundCapsules.size - 1) currentCapsuleIndex++ },
                    enabled = currentCapsuleIndex < soundCapsules.size - 1
                ) {
                    Icon(
                        Icons.Filled.ArrowForwardIos,
                        contentDescription = "Next Month",
                        tint = if (currentCapsuleIndex < soundCapsules.size - 1) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    )
                }
            }

            // Display the current month's capsule
            MonthlySoundCapsuleCard(
                capsuleData = currentCapsule,
                onTimeListenedClick = {
                    // Store currentCapsule in a shared ViewModel or pass its ID/monthYear
                    DataKeeper.currentSelectedCapsule = currentCapsule // Example of using DataKeeper
                    navController.navigate("timeListenedDetail")
                },
                onTopArtistClick = {
                    DataKeeper.currentSelectedCapsule = currentCapsule
                    navController.navigate("topArtistsDetail")
                },
                onTopSongClick = {
                    DataKeeper.currentSelectedCapsule = currentCapsule
                    navController.navigate("topSongsDetail")
                },
                onShareClick = {
                    // TODO: Implement share functionality
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp)) // Space at the bottom
        }

        // MoreVert Menu (Top End)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 8.dp) // Adjusted padding
        ) {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onBackground)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Edit Profile") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = {
                        expanded = false
                        showEditProfileDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        expanded = false
                        onLogout()
                    }
                )
            }
        }
    }

    if (showEditProfileDialog) {
        val profileDataForDialog = ProfileData( // Ensure ProfileData is defined
            currentUsername = profile.username,
            currentLocation = profile.location,
            currentProfilePhotoUrl = if (profile.profilePhoto.isNotEmpty()) "$baseUrl/uploads/profile-picture/${sanitizeFileName(profile.profilePhoto)}" else null
        )
        SwipeableProfileEditDialog( // Ensure this composable is defined and handles its state
            onDismiss = { showEditProfileDialog = false },
            existingProfile = profileDataForDialog,
            onSaveProfile = { locationToSave, profilePhotoUriToSave, onSaveComplete, onError ->
                viewModel.updateProfile(
                    location = locationToSave,
                    profilePhotoUri = profilePhotoUriToSave,
                    onSuccess = {
                        viewModel.loadProfile() // Reload profile to see changes
                        onSaveComplete()
                    },
                    onFailure = { errorMsg ->
                        onError(errorMsg) // Pass error message to dialog
                    }
                )
            }
        )
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

fun sanitizeFileName(fileName: String): String {
    val maxLength = 100
    val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "")
    return if (safeName.length > maxLength) safeName.substring(0, maxLength) else safeName
}

private fun sanitizeText(text: String): String {
    val maxLength = 100
    val safeText = text.replace(Regex("[<>\"&]"), "")
    return if (safeText.length > maxLength) safeText.substring(0, maxLength) else safeText
}

private fun isValidEmail(email: String): Boolean {
    val emailPattern = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
        Pattern.CASE_INSENSITIVE
    )
    return emailPattern.matcher(email.trim()).matches()
}