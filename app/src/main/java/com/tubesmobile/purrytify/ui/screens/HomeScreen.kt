package com.tubesmobile.purrytify.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubesmobile.purrytify.R

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "New songs",
            color = MaterialTheme.colorScheme.onBackground, 
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            modifier = Modifier.padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(newSongs) { song ->
                NewSongItem(song = song)
            }
        }

        Text(
            text = "Recently played",
            color = MaterialTheme.colorScheme.onBackground, 
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(recentlyPlayed) { song ->
                RecentlyPlayedItem(song = song)
            }
        }

        BottomPlayerBar()
    }
}

@Composable
fun NewSongItem(song: Song) {
    Column(
        modifier = Modifier.width(120.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Image(
            painter = painterResource(id = song.imageRes),
            contentDescription = song.title,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = song.title,
            color = MaterialTheme.colorScheme.onBackground, 
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = song.artist,
            color = MaterialTheme.colorScheme.onSurfaceVariant, 
            fontSize = 12.sp
        )
    }
}

@Composable
fun RecentlyPlayedItem(song: Song) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = song.imageRes),
            contentDescription = song.title,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = song.title,
                color = MaterialTheme.colorScheme.onBackground, 
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = song.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant, 
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun BottomPlayerBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant) 
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "Now Playing",
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Starboy",
                color = MaterialTheme.colorScheme.onSurface, 
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "The Weeknd, Da...",
                color = MaterialTheme.colorScheme.onSurfaceVariant, 
                fontSize = 12.sp
            )
        }
        IconButton(onClick = { /* Play/Pause action */ }) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_media_play),
                contentDescription = "Play/Pause",
                tint = MaterialTheme.colorScheme.onSurface 
            )
        }
    }
}

data class Song(val title: String, val artist: String, val imageRes: Int)

val newSongs = listOf(
    Song("Starboy", "The Weeknd, Da...", R.drawable.ic_launcher_foreground),
    Song("Here Comes T...", "The Beatles", R.drawable.ic_launcher_foreground),
    Song("Midnight Pret...", "Tomoko Aran", R.drawable.ic_launcher_foreground),
    Song("Violent", "Kanye", R.drawable.ic_launcher_foreground)
)

val recentlyPlayed = listOf(
    Song("Jazz is for ordinary people", "berlioz", R.drawable.ic_launcher_foreground),
    Song("Loose", "Daniel Caesar", R.drawable.ic_launcher_foreground),
    Song("Nights", "Frank Ocean", R.drawable.ic_launcher_foreground),
    Song("Kiss of Life", "Sade", R.drawable.ic_launcher_foreground),
    Song("Best Interest", "Tyler, The Creator", R.drawable.ic_launcher_foreground)
)
