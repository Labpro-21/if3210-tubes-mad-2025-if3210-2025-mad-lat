package com.tubesmobile.purrytify.ui.screens

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.ui.components.Screen
import com.tubesmobile.purrytify.ui.components.SharedBottomNavigationBar
import com.tubesmobile.purrytify.ui.viewmodel.MusicViewModel

@Composable
fun MusicScreen(
    navController: NavHostController,
    sourceScreen: Screen,
    musicViewModel: MusicViewModel
) {
    // Collect the current song from the ViewModel
    val currentSong by musicViewModel.currentSong.collectAsState()

    val song = currentSong

    val gradientColors = listOf(
        Color(0xFFBD1E01),
        Color(0xFF893552),
        Color(0xFF53062B),
        Color(0xFF04061D)
    )

    Scaffold(
        bottomBar = {
            SharedBottomNavigationBar(
                currentScreen = sourceScreen,
                onNavigate = { screen ->
                    when (screen) {
                        Screen.HOME -> navController.navigate("home") {
                            popUpTo("home") { inclusive = false }
                        }
                        Screen.LIBRARY -> navController.navigate("library") {
                            popUpTo("library") { inclusive = false }
                        }
                        Screen.PROFILE -> navController.navigate("profile") {
                            popUpTo("profile") { inclusive = false }
                        }
                        Screen.MUSIC -> {}
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(colors = gradientColors)
                )
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            // Top bar with back button and menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    // Back icon
                    painter = painterResource(id = R.drawable.ic_caret),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { navController.popBackStack() }
                )

                Icon(
                    // Menu icon
                    painter = painterResource(id = R.drawable.ic_more),
                    contentDescription = "More options",
                    tint = Color.White,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { /* Show menu */ }
                )
            }

            Spacer(modifier = Modifier.height(64.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
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

                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap!!,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.starboy), // fallback
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

            }

            // Song title and artist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = song?.title ?: "Starboy",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song?.artist ?: "The Weeknd, Daft Punk",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 16.sp
                    )
                }

                Icon(
                    // Heart/like icon
                    painter = painterResource(id = R.drawable.ic_heart),
                    contentDescription = "Like",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { /* Toggle like */ }
                )
            }

            // Progress slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                Slider(
                    value = 0.4f, // Current progress
                    onValueChange = { /* Update progress */ },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "1:44",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "3:50",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }

            // Playback controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    // Previous icon
                    painter = painterResource(id = R.drawable.ic_previous),
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable { /* Previous track */ }
                )

                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clickable { /* Play/Pause */ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        // Play icon
                        painter = painterResource(id = R.drawable.play),
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(84.dp)
                    )
                }

                Icon(
                    // Next icon
                    painter = painterResource(id = R.drawable.ic_skip),
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable { /* Next track */ }
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MusicScreenPreview() {
    val dummyNavController = rememberNavController()
    val previewViewModel: MusicViewModel = viewModel()
    val context = LocalContext.current

     previewViewModel.playSong(Song("Preview Song", "Preview Artist", 300, "",  R.drawable.ic_launcher_foreground.toString()), context)

    MusicScreen(
        navController = dummyNavController,
        sourceScreen = Screen.HOME,
        musicViewModel = previewViewModel
    )
}