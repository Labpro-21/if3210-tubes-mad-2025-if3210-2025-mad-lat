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
import com.tubesmobile.purrytify.ui.viewmodel.SoundCapsuleViewModel

@Composable
fun ProfileScreen(navController: NavHostController, loginViewModel: LoginViewModel, musicService: MusicPlaybackService?, musicDbViewModel: MusicDbViewModel) {
    val currentScreen = remember { mutableStateOf(Screen.PROFILE) }
    val viewModel: ProfileViewModel = viewModel()
    val musicDbViewModel: MusicDbViewModel = viewModel()
    val soundCapsuleViewModel: SoundCapsuleViewModel = viewModel()
    val profileState by viewModel.profile.collectAsState()
    val isConnected by LocalNetworkStatus.current.collectAsState()
    val songsList by musicDbViewModel.allSongs.collectAsState(initial = emptyList())
    val likedSongsList by musicDbViewModel.likedSongs.collectAsState(initial = emptyList())
    val songsTimestamp by musicDbViewModel.songsTimestamp.collectAsState(initial = emptyList())

    val monthlyCapsules by soundCapsuleViewModel.monthlyCapsules.collectAsState()
    val isLoadingCapsules by soundCapsuleViewModel.isLoading.collectAsState()

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

    LaunchedEffect(DataKeeper.email) {
        if (DataKeeper.email != null) {
            soundCapsuleViewModel.loadSoundCapsuleData()
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
                        monthlyCapsulesFromVM = monthlyCapsules,
                        isLoadingCapsules = isLoadingCapsules,
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
    navController: NavHostController,
    monthlyCapsulesFromVM: List<MonthlySoundCapsuleData>,
    isLoadingCapsules: Boolean,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val baseUrl = "http://34.101.226.132:3000"
    val sanitizedPhoto = sanitizeFileName(profile.profilePhoto)
    val profilePhotoUrl = "$baseUrl/uploads/profile-picture/$sanitizedPhoto"
    var expanded by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    var currentCapsuleIndex by remember { mutableStateOf(0) }

    LaunchedEffect(monthlyCapsulesFromVM) {
        if (monthlyCapsulesFromVM.isNotEmpty() && currentCapsuleIndex >= monthlyCapsulesFromVM.size) {
            currentCapsuleIndex = 0
        } else if (monthlyCapsulesFromVM.isEmpty()) {
            currentCapsuleIndex = 0
        }
    }

    val currentCapsuleToDisplay = if (monthlyCapsulesFromVM.isNotEmpty() && currentCapsuleIndex < monthlyCapsulesFromVM.size) {
        monthlyCapsulesFromVM[currentCapsuleIndex]
    } else {
        if (!isLoadingCapsules && monthlyCapsulesFromVM.isEmpty()) {
            MonthlySoundCapsuleData(
                monthYear = "No Data", hasData = false, timeListenedMinutes = null, dailyAverageMinutes = null, topArtistName = null, topArtistImageUrl = null, totalArtistsListenedThisMonth = null, topArtistsList = null, topSongName = null, topSongImageUrl = null, totalSongsPlayedThisMonth = null, topSongsList = null, dayStreakCount = null, dayStreakSongName = null, dayStreakSongArtist = null, dayStreakFullText = null, dayStreakDateRange = null, dayStreakImage = null
            )
        } else null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(50.dp))

            AsyncImage(
                model = ImageRequest.Builder(context).data(profilePhotoUrl).crossfade(true)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground).build(),
                contentDescription = "Profile Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(100.dp).clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(sanitizeText(profile.username), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(sanitizeText(profile.email), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Text(sanitizeText(profile.location), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem("SONGS", DataKeeper.songsAmount.toString())
                StatItem("LIKED", DataKeeper.likesAmount.toString())
                StatItem("LISTENED", DataKeeper.listenedAmount.toString())
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Your Sound Capsule",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoadingCapsules) {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
            } else if (monthlyCapsulesFromVM.isEmpty()) {
                Text(
                    "No sound capsule data available yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            else if (currentCapsuleToDisplay != null) {
                Row( // Month Navigation
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (currentCapsuleIndex > 0) currentCapsuleIndex-- },
                        enabled = currentCapsuleIndex > 0
                    ) {
                        Icon(Icons.Filled.ArrowBackIosNew, "Previous Month",
                            tint = if (currentCapsuleIndex > 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                    }
                    Text(currentCapsuleToDisplay.monthYear, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    IconButton(
                        onClick = { if (currentCapsuleIndex < monthlyCapsulesFromVM.size - 1) currentCapsuleIndex++ },
                        enabled = currentCapsuleIndex < monthlyCapsulesFromVM.size - 1
                    ) {
                        Icon(Icons.Filled.ArrowForwardIos, "Next Month",
                            tint = if (currentCapsuleIndex < monthlyCapsulesFromVM.size - 1) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                    }
                }

                MonthlySoundCapsuleCard(
                    capsuleData = currentCapsuleToDisplay,
                    onTimeListenedClick = {
                        if (currentCapsuleToDisplay.hasData) {
                            DataKeeper.currentSelectedCapsule = currentCapsuleToDisplay
                            navController.navigate("timeListenedDetail")
                        }
                    },
                    onTopArtistClick = {
                        if (currentCapsuleToDisplay.hasData) {
                            DataKeeper.currentSelectedCapsule = currentCapsuleToDisplay
                            navController.navigate("topArtistsDetail")
                        }
                    },
                    onTopSongClick = {
                        if (currentCapsuleToDisplay.hasData) {
                            DataKeeper.currentSelectedCapsule = currentCapsuleToDisplay
                            navController.navigate("topSongsDetail")
                        }
                    },
                    onShareClick = { /* TODO: Implement share */ },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                Text(
                    "Sound capsule data is currently unavailable.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 8.dp)) {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, "Menu", tint = MaterialTheme.colorScheme.onBackground)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Edit Profile") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { expanded = false; showEditProfileDialog = true }
                )
                DropdownMenuItem(
                    text = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { expanded = false; onLogout() }
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