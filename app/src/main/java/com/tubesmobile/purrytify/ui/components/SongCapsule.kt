package com.tubesmobile.purrytify.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tubesmobile.purrytify.R // Your R file
import com.tubesmobile.purrytify.data.model.MonthlySoundCapsuleData
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme // Your Theme

@Composable
fun MonthlySoundCapsuleCard(
    capsuleData: MonthlySoundCapsuleData,
    onTimeListenedClick: () -> Unit,
    onTopArtistClick: () -> Unit,
    onTopSongClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF181818) // Darker card background
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween, // Month Year and Share
                verticalAlignment = Alignment.CenterVertically
            ) {
                // This month year text is now outside, above the card in ProfileScreen
                // So we might not need it here, or adjust its placement.
                // For now, let's assume it's handled by the parent ProfileScreen.

                Spacer(modifier = Modifier.weight(1f)) // Pushes share icon to the right

                IconButton(onClick = onShareClick) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share Capsule",
                        tint = Color(0xFFB3B3B3)
                    )
                }
            }
            //Spacer(modifier = Modifier.height(8.dp)) // Add space if monthYear was here

            if (!capsuleData.hasData) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp), // More padding for "No data"
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No data available",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFB3B3B3)
                    )
                }
            } else {
                // Time Listened
                capsuleData.timeListenedMinutes?.let { minutes ->
                    SoundCapsuleInfoRow(
                        label = "Time listened",
                        value = "$minutes minutes",
                        onClick = onTimeListenedClick,
                        valueColor = Color(0xFF1DB954) // Spotify Green
                    )
                }

                // Top Artist & Top Song in a Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    capsuleData.topArtistName?.let { artistName ->
                        SoundCapsuleImageInfoItem(
                            label = "Top artist",
                            value = artistName,
                            imageUrl = capsuleData.topArtistImageUrl,
                            onClick = onTopArtistClick,
                            modifier = Modifier.weight(1f)
                        )
                    } ?: Spacer(modifier = Modifier.weight(1f)) // Keep layout if one is null

                    capsuleData.topSongName?.let { songName ->
                        SoundCapsuleImageInfoItem(
                            label = "Top song",
                            value = songName,
                            imageUrl = capsuleData.topSongImageUrl,
                            onClick = onTopSongClick,
                            modifier = Modifier.weight(1f)
                        )
                    } ?: Spacer(modifier = Modifier.weight(1f)) // Keep layout if one is null
                }
                Spacer(modifier = Modifier.height(12.dp))


                // Day Streak (Main Image Card)
                capsuleData.dayStreakCount?.let { _ ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f) // Or a fixed height
                            .clickable { /* Potentially navigate to streak details or play song */ },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray) // Placeholder bg
                    ) {
                        Box(contentAlignment = Alignment.BottomStart) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(capsuleData.dayStreakImage ?: R.drawable.ic_launcher_background) // Placeholder if null
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Day Streak Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Overlayed text for streak
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.5f)) // Semi-transparent scrim
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "You had a ${capsuleData.dayStreakCount}-day streak",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                capsuleData.dayStreakFullText?.let {
                                    Text(
                                        text = it,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                                capsuleData.dayStreakDateRange?.let {
                                    Text(
                                        text = it,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SoundCapsuleInfoRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    valueColor: Color = Color.White, // Default color for value
    labelColor: Color = Color(0xFFB3B3B3)
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF282828)) // Slightly lighter than card
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = label, fontSize = 13.sp, color = labelColor)
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Navigate",
                tint = labelColor
            )
        }
    }
}

@Composable
fun SoundCapsuleImageInfoItem(
    label: String,
    value: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color = Color(0xFFB3B3B3)
) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF282828))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl ?: R.drawable.ic_launcher_foreground) // Use a generic placeholder
                    .crossfade(true)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .build(),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp) // Slightly larger image
                    .clip(RoundedCornerShape(4.dp)) // Or CircleShape if preferred for artists
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) { // Allow text to take available space
                Text(text = label, fontSize = 13.sp, color = labelColor)
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2 // Prevent overly long text from breaking layout
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Navigate",
                tint = labelColor,
                modifier = Modifier.padding(start = 4.dp) // Add some space before chevron
            )
        }
    }
}


@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewMonthlySoundCapsuleCardWithData() {
    PurrytifyTheme {
        MonthlySoundCapsuleCard(
            capsuleData = MonthlySoundCapsuleData(
                monthYear = "April 2025",
                timeListenedMinutes = 862,
                dailyAverageMinutes = 33,
                topArtistName = "The Beatles",
                topArtistImageUrl = "https://e-cdns-images.dzcdn.net/images/artist/b290e6c703939503914620c25452a152/264x264-000000-80-0-0.jpg",
                topSongName = "Starboy",
                topSongImageUrl = "https://i.scdn.co/image/ab67616d0000b273c05276696219639749f25088",
                dayStreakCount = 5,
                dayStreakFullText = "You played Loose by Daniel Caesar day after day. You were on fire",
                dayStreakDateRange = "Mar 21-25, 2025",
                dayStreakImage = "https://images.genius.com/6a8fac3cf1b03a233988398647661990.1000x1000x1.jpg",
                hasData = true
            ),
            onTimeListenedClick = {},
            onTopArtistClick = {},
            onTopSongClick = {},
            onShareClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewMonthlySoundCapsuleCardNoData() {
    PurrytifyTheme {
        MonthlySoundCapsuleCard(
            capsuleData = MonthlySoundCapsuleData(
                monthYear = "May 2025",
                timeListenedMinutes = null, dailyAverageMinutes = null,
                topArtistName = null, topArtistImageUrl = null, totalArtistsListenedThisMonth = null, topArtistsList = null,
                topSongName = null, topSongImageUrl = null, totalSongsPlayedThisMonth = null, topSongsList = null,
                dayStreakCount = null, dayStreakSongName = null, dayStreakSongArtist = null, dayStreakFullText = null, dayStreakDateRange = null, dayStreakImage = null,
                hasData = false
            ),
            onTimeListenedClick = {},
            onTopArtistClick = {},
            onTopSongClick = {},
            onShareClick = {}
        )
    }
}