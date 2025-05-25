// File: com/tubesmobile/purrytify/ui/screens/soundcapsuledetail/TimeListenedScreen.kt
package com.tubesmobile.purrytify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.tubesmobile.purrytify.data.model.MonthlySoundCapsuleData
import com.tubesmobile.purrytify.service.DataKeeper // If using DataKeeper
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeListenedScreen(navController: NavHostController) {
    // Retrieve the selected capsule data.
    // For simplicity, using DataKeeper. For robust solution, pass ID via nav args and fetch.
    var capsuleData by remember { mutableStateOf<MonthlySoundCapsuleData?>(null) }

    LaunchedEffect(Unit) {
        capsuleData = DataKeeper.currentSelectedCapsule
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Time listened") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, // Or a darker color
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = Color(0xFF121212) // Dark background consistent with images
    ) { paddingValues ->
        capsuleData?.let { data ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.Start // Align text to start
            ) {
                Text(
                    text = data.monthYear,
                    fontSize = 16.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "You listened to music for",
                    fontSize = 20.sp,
                    color = Color.White,
                )
                Text(
                    text = "${data.timeListenedMinutes ?: 0} minutes",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1DB954), // Spotify Green
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "this month.",
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Text(
                    text = "Daily average: ${data.dailyAverageMinutes ?: 0} min",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Placeholder for Daily Chart
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp) // Example height
                        .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Daily Chart", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("(Chart will be implemented here)", fontSize = 14.sp, color = Color.Gray)
                    }
                }
                // Axis labels for the chart (conceptual)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("minutes", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp /* to align with Y axis */))
                    Text("day", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(end = 4.dp /* to align with X axis */))
                }
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No data available for this month.", color = Color.White)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TimeListenedScreenPreview() {
    PurrytifyTheme {
        // Mock DataKeeper for preview
        DataKeeper.currentSelectedCapsule = MonthlySoundCapsuleData(
            monthYear = "April 2025",
            timeListenedMinutes = 862,
            dailyAverageMinutes = 33,
            topArtistName = null, topArtistImageUrl = null, totalArtistsListenedThisMonth = null, topArtistsList = null,
            topSongName = null, topSongImageUrl = null, totalSongsPlayedThisMonth = null, topSongsList = null,
            dayStreakCount = null, dayStreakSongName = null, dayStreakSongArtist = null, dayStreakFullText = null, dayStreakDateRange = null, dayStreakImage = null,
            hasData = true
        )
        TimeListenedScreen(navController = rememberNavController())
    }
}