// File: com/tubesmobile/purrytify/ui/screens/soundcapsuledetail/TopSongsScreen.kt
package com.tubesmobile.purrytify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.data.model.MonthlySoundCapsuleData
import com.tubesmobile.purrytify.data.model.SongData
import com.tubesmobile.purrytify.service.DataKeeper
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopSongsScreen(navController: NavHostController) {
    var capsuleData by remember { mutableStateOf<MonthlySoundCapsuleData?>(null) }

    LaunchedEffect(Unit) {
        capsuleData = DataKeeper.currentSelectedCapsule
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Top songs") },
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
                    text = "You played ",
                    fontSize = 20.sp,
                    color = Color.White
                )
                Text(
                    text = "${data.totalSongsPlayedThisMonth ?: 0} different songs",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFFF00), // Yellow for songs as per image
                )
                Text(
                    text = "this month.",
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 24.dp)
                )


                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(data.topSongsList ?: emptyList()) {index, song ->
                        TopSongItem(song = song, rank = index + 1)
                    }
                }
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No song data available.", color = Color.White)
            }
        }
    }
}

@Composable
fun TopSongItem(song: SongData, rank: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "%02d".format(rank),
            fontSize = 16.sp,
            color = Color(0xFF757575),
            modifier = Modifier.width(30.dp)
        )
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(song.imageUrl)
                .crossfade(true)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .build(),
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp)) // Album art is usually square
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artists,
                fontSize = 14.sp,
                color = Color.LightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.plays} plays",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun TopSongsScreenPreview() {
    PurrytifyTheme {
        DataKeeper.currentSelectedCapsule = MonthlySoundCapsuleData(
            monthYear = "April 2025",
            totalSongsPlayedThisMonth = 203,
            topSongsList = listOf(
                SongData(1, "Starboy", "The Weeknd, Daft Punk", "https://i.scdn.co/image/ab67616d0000b273c05276696219639749f25088", 15),
                SongData(2, "Loose", "Daniel Caesar", "https://images.genius.com/6a8fac3cf1b03a233988398647661990.1000x1000x1.jpg", 12),
                SongData(3, "Nights", "Frank Ocean", "https://upload.wikimedia.org/wikipedia/en/a/a0/Blonde_-_Frank_Ocean.jpeg", 8),
                SongData(4, "Doomsday", "MF DOOM, Pebbles The Invisible Girl", "https://i.scdn.co/image/ab67616d0000b273daa5c409172d490928ea441a", 4)
            ),
            timeListenedMinutes = null, dailyAverageMinutes = null, topArtistName = null, topArtistImageUrl = null, totalArtistsListenedThisMonth = null, topArtistsList = null,
            topSongName = null, topSongImageUrl = null,
            dayStreakCount = null, dayStreakSongName = null, dayStreakSongArtist = null, dayStreakFullText = null, dayStreakDateRange = null, dayStreakImage = null,
            hasData = true
        )
        TopSongsScreen(navController = rememberNavController())
    }
}