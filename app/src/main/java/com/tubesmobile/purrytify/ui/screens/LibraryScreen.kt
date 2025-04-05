package com.tubesmobile.purrytify.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme
import com.tubesmobile.purrytify.ui.components.SharedBottomNavigationBar
import com.tubesmobile.purrytify.ui.components.Screen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tubesmobile.purrytify.viewmodel.MusicViewModel

// SAMPLE DATA
val songs = listOf(
    Song("STARBOY", "The Weeknd, Daft Punk", R.drawable.ic_launcher_foreground),
    Song("Here Comes The Sun - Remaster...", "The Beatles", R.drawable.ic_launcher_foreground),
    Song("MIDNIGHT PRETENDERS", "Tomoko Aran", R.drawable.ic_launcher_foreground),
    Song("VIOLENT CRIMES", "Kanye West", R.drawable.ic_launcher_foreground),
    Song("DENIAL IS A RIVER", "Doechii", R.drawable.ic_launcher_foreground),
    Song("Doomsday", "MF DOOM, Pebbles The Invisible Girl", R.drawable.ic_launcher_foreground)
)

@Composable
fun MusicLibraryScreen(navController: NavHostController, musicViewModel: MusicViewModel) {
    val currentScreen = remember { mutableStateOf(Screen.LIBRARY) }

    Scaffold(
        bottomBar = {
            SharedBottomNavigationBar(
                currentScreen = currentScreen.value,
                onNavigate = { screen ->
                    currentScreen.value = screen
                    when (screen) {
                        Screen.HOME -> navController.navigate("home")
                        Screen.LIBRARY -> {}
                        Screen.PROFILE -> navController.navigate("profile")
                        Screen.MUSIC -> {}
                    }
                },
                transparent = false
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Library",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { /* Add action */ }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = "Add",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabButton(text = "All", isSelected = true)
                TabButton(text = "Liked", isSelected = false)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                items(songs) { song ->
                    SongItem(
                        song = song,
                        onClick = { selectedSong ->
                            musicViewModel.playSong(selectedSong)
                            navController.navigate("music/${Screen.LIBRARY.name}")
                        })
                }
            }
        }
    }
}

@Composable
fun TabButton(text: String, isSelected: Boolean) {
    Button(
        onClick = { /* Handle tab click */ },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .height(36.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SongItem(song: Song, onClick: (Song) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(song) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = song.imageRes),
            contentDescription = song.title,
            modifier = Modifier
                .size(56.dp)
        )

        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text = song.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = song.artist,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MusicLibraryScreenPreview() {
    val navController = rememberNavController()
    val previewViewModel: MusicViewModel = viewModel()

    PurrytifyTheme {
        MusicLibraryScreen(navController, previewViewModel)
    }
}
