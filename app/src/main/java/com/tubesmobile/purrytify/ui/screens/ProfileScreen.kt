package com.tubesmobile.purrytify.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import com.tubesmobile.purrytify.ui.viewmodel.ProfileViewModel
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import java.util.regex.Pattern

@Composable
fun ProfileScreen(navController: NavHostController, loginViewModel: LoginViewModel) {
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
                        onLogout = {
                            viewModel.logout()
                            loginViewModel.resetLoginState()
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

                ProfileViewModel.ProfileState.Idle -> TODO()
            }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 51.dp)
                .background(MaterialTheme.colorScheme.primaryContainer)
        )

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

        Spacer(modifier = Modifier.height(16.dp))

        // LOGOUT BUTTON
        OutlinedButton(
            onClick = onLogout,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(0.5f)
        ) {
            Text(
                text = "Logout",
                fontSize = 16.sp
            )
        }

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
