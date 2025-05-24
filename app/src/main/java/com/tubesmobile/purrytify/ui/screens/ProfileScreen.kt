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
import com.tubesmobile.purrytify.ui.theme.LocalNetworkStatus
import com.tubesmobile.purrytify.ui.viewmodel.LoginViewModel
import com.tubesmobile.purrytify.ui.viewmodel.MusicBehaviorViewModel
import com.tubesmobile.purrytify.ui.viewmodel.ProfileViewModel
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import java.util.regex.Pattern
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.tubesmobile.purrytify.ui.viewmodel.MonthlyAnalytics
import com.tubesmobile.purrytify.ui.viewmodel.SoundCapsuleViewModel
import java.util.concurrent.TimeUnit
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun ProfileScreen(navController: NavHostController, loginViewModel: LoginViewModel, musicBehaviorViewModel: MusicBehaviorViewModel) {
    val currentScreen = remember { mutableStateOf(Screen.PROFILE) }
    val viewModel: ProfileViewModel = viewModel()
    val musicDbViewModel: MusicDbViewModel = viewModel()
    val profileState by viewModel.profile.collectAsState()
    val isConnected by LocalNetworkStatus.current.collectAsState()
    val songsList by musicDbViewModel.allSongs.collectAsState(initial = emptyList())
    val likedSongsList by musicDbViewModel.likedSongs.collectAsState(initial = emptyList())
    val songsTimestamp by musicDbViewModel.songsTimestamp.collectAsState(initial = emptyList())
    val soundCapsuleViewModel: SoundCapsuleViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(LocalContext.current.applicationContext as Application)
    )
    val monthlyAnalytics by soundCapsuleViewModel.monthlyAnalytics.collectAsState()
    val context = LocalContext.current

    val newSongs = remember(songsList, songsTimestamp) {
        val timestampMap = songsTimestamp.associateBy { it.songId }
        songsList
            .filter { it.id != null && it.id !in timestampMap }
            .sortedByDescending { it.id }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(context, "Storage permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Storage permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            when (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                PackageManager.PERMISSION_GRANTED -> { /* Permission already granted */ }
                else -> { requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
            }
        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            if (!isConnected) {
                NetworkOfflineScreen(24)
            }

            when (profileState) {
                is ProfileViewModel.ProfileState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
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
                        onLogout = {
                            viewModel.logout()
                            loginViewModel.resetLoginState()
                            musicBehaviorViewModel.onCleared()
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
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SoundCapsuleSection(
                analytics = monthlyAnalytics,
                onPreviousMonth = { soundCapsuleViewModel.changeMonth(-1) },
                onNextMonth = { soundCapsuleViewModel.changeMonth(1) },
                onExportCSV = {
                    val currentMonthYear = soundCapsuleViewModel.selectedMonthYear.value
                    soundCapsuleViewModel.exportAnalyticsToCSV(context, currentMonthYear) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
                onExportPDF = {
                    val currentMonthYear = soundCapsuleViewModel.selectedMonthYear.value
                    soundCapsuleViewModel.exportAnalyticsToPDF(context, currentMonthYear) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
                musicBehaviorViewModel = musicBehaviorViewModel
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ProfileContent(
    profile: ProfileResponse,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val baseUrl = "http://34.101.226.132:3000"
    val sanitizedPhoto = sanitizeFileName(profile.profilePhoto)
    val profilePhotoUrl = "$baseUrl/uploads/profile-picture/$sanitizedPhoto"
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(51.dp))

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(profilePhotoUrl)
                    .crossfade(true)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .allowHardware(false)
                    .build(),
                contentDescription = "Profile Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // USERNAME
            Text(
                text = sanitizeText(profile.username),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // EMAIL
            Text(
                text = sanitizeText(profile.email),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            // LOCATION
            Text(
                text = sanitizeText(profile.location),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // STATS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "SONGS", value = DataKeeper.songsAmount.toString())
                StatItem(label = "LIKED", value = DataKeeper.likesAmount.toString())
                StatItem(label = "LISTENED", value = DataKeeper.listenedAmount.toString())
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit Profile") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "Logout",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 15.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        onLogout()
                        expanded = false
                    }
                )
            }
        }
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

private fun sanitizeFileName(fileName: String): String {
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

@Composable
fun SoundCapsuleSection(
    analytics: MonthlyAnalytics?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onExportCSV: () -> Unit,
    onExportPDF: () -> Unit,
    musicBehaviorViewModel: MusicBehaviorViewModel
) {
    val context = LocalContext.current
    val currentSongIsPlaying by musicBehaviorViewModel.isPlaying.collectAsState()
    val currentSongPositionMs by musicBehaviorViewModel.currentPosition.collectAsState()

    var displayTimeListenedMinutes = analytics?.totalTimeListenedMinutes ?: 0L
    val currentMonthYearInternal = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Calendar.getInstance().time)

    if (analytics?.monthYearDisplay == SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Calendar.getInstance().time) &&
        currentSongIsPlaying) {
        val additionalMinutesThisSession = TimeUnit.MILLISECONDS.toMinutes(currentSongPositionMs.toLong())
        displayTimeListenedMinutes += additionalMinutesThisSession
    }


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Your Sound Capsule",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row {
                IconButton(onClick = onExportCSV) {
                    Icon(Icons.Filled.Download, contentDescription = "Export to CSV", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onExportPDF) {
                    Icon(painterResource(id = R.drawable.ic_pdf_export), contentDescription = "Export to PDF", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) // Ganti dengan ikon PDF Anda
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Navigasi Bulan
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Previous Month", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = analytics?.monthYearDisplay ?: "Loading...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "Next Month", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (analytics == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (analytics.isEmpty) {
            Text(
                text = "No data available for this month.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            AnalyticsCard(
                label = "Time listened",
                value = "$displayTimeListenedMinutes minutes",
                backgroundColor = Color(0xFF2E7D32).copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnalyticsCard(
                    modifier = Modifier.weight(1f),
                    label = "Top artist",
                    value = analytics.topArtist ?: "-",
                    valueDetails = null,
                    iconResId = R.drawable.ic_artist,
                    backgroundColor = Color(0xFF0277BD).copy(alpha = 0.2f) // Warna biru lembut
                )
                AnalyticsCard(
                    modifier = Modifier.weight(1f),
                    label = "Top song",
                    value = analytics.topSongTitle ?: "-",
                    valueDetails = if(analytics.topSongArtist != null) "by ${analytics.topSongArtist}" else null,
                    iconResId = R.drawable.ic_song,
                    backgroundColor = Color(0xFFD84315).copy(alpha = 0.2f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Day Streak
            if (analytics.dayStreakCount >= 2 && analytics.dayStreakSongTitle != null) {
                AnalyticsCard(
                    label = "You had a ${analytics.dayStreakCount}-day streak",
                    value = "You played ${analytics.dayStreakSongTitle}",
                    valueDetails = if (analytics.dayStreakSongArtist != null) "by ${analytics.dayStreakSongArtist} day after day. You were on fire!" else "day after day. You were on fire!",
                    isStreak = true,
                    backgroundColor = Color(0xFFFFC107).copy(alpha = 0.2f)
                )
            } else {
                AnalyticsCard(
                    label = "Day Streak",
                    value = "No significant streak this month.",
                    backgroundColor = Color.Gray.copy(alpha = 0.1f)
                )
            }
        }
    }
}

@Composable
fun AnalyticsCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueDetails: String? = null,
    iconResId: Int? = null,
    isStreak: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = if (isStreak) Alignment.Start else Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                iconResId?.let {
                    Icon(
                        painter = painterResource(id = it),
                        contentDescription = label,
                        modifier = Modifier.size(36.dp).padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = if (isStreak) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (isStreak) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (valueDetails != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = valueDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}