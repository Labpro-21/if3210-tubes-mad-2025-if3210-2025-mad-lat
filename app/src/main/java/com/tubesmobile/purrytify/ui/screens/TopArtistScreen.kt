package com.tubesmobile.purrytify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.data.model.ArtistData
import com.tubesmobile.purrytify.data.model.MonthlySoundCapsuleData
import com.tubesmobile.purrytify.service.DataKeeper
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopArtistsScreen(navController: NavHostController) {
    var capsuleData by remember { mutableStateOf<MonthlySoundCapsuleData?>(null) }

    LaunchedEffect(Unit) {
        capsuleData = DataKeeper.currentSelectedCapsule
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Top artists") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212), // Match screen background
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        capsuleData?.let { data ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = data.monthYear,
                    fontSize = 16.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                Text(
                    text = "You listened to ",
                    fontSize = 20.sp,
                    color = Color.White
                )
                Text(
                    text = "${data.totalArtistsListenedThisMonth ?: 0} artists",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1DB954), // Accent color
                )
                Text(
                    text = "this month.",
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(data.topArtistsList ?: emptyList()) { index, artist ->
                        TopArtistItem(artist = artist, rank = index + 1)
                    }
                }
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No artist data available.", color = Color.White)
            }
        }
    }
}

@Composable
fun TopArtistItem(artist: ArtistData, rank: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "%02d".format(rank), // e.g., "01", "02"
            fontSize = 16.sp,
            color = Color(0xFF757575), // Spotify gray for rank
            modifier = Modifier.width(30.dp)
        )
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artist.imageUrl)
                .crossfade(true)
                .placeholder(R.drawable.ic_launcher_foreground) // Your placeholder
                .error(R.drawable.ic_launcher_foreground)
                .build(),
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = artist.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}


@Preview(showBackground = true)
@Composable
fun TopArtistsScreenPreview() {
    PurrytifyTheme {
        DataKeeper.currentSelectedCapsule = MonthlySoundCapsuleData(
            monthYear = "April 2025",
            totalArtistsListenedThisMonth = 137,
            topArtistsList = listOf(
                ArtistData(1, "Beatles", "https://e-cdns-images.dzcdn.net/images/artist/b290e6c703939503914620c25452a152/264x264-000000-80-0-0.jpg"),
                ArtistData(2, "The Weeknd", "https://i.scdn.co/image/ab676161000051748ae7f2aaa9817a704a87ea36"),
                ArtistData(3, "Kanye West", "https://i.scdn.co/image/ab67616100005174c0118f0a00a00aa761d5f507"),
                ArtistData(4, "Doechii", "https://i.scdn.co/image/ab67616100005174cf8a66352927a65606ccd6a4")
            ),
            timeListenedMinutes = null, dailyAverageMinutes = null, topArtistName = null, topArtistImageUrl = null,
            topSongName = null, topSongImageUrl = null, totalSongsPlayedThisMonth = null, topSongsList = null,
            dayStreakCount = null, dayStreakSongName = null, dayStreakSongArtist = null, dayStreakFullText = null, dayStreakDateRange = null, dayStreakImage = null,
            hasData = true
        )
        TopArtistsScreen(navController = rememberNavController())
    }
}