package com.tubesmobile.purrytify.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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

// DATA CLASS TO REPRESENT AN ALBUM
data class Album(
    val title: String,
    val artist: String,
    val imageResId: Int
)

// SAMPLE DATA
val albums = listOf(
    Album("STARBOY", "The Weeknd, Daft Punk", R.drawable.ic_launcher_foreground),
    Album("Here Comes The Sun - Remaster...", "The Beatles", R.drawable.ic_launcher_foreground),
    Album("MIDNIGHT PRETENDERS", "Tomoko Aran", R.drawable.ic_launcher_foreground),
    Album("VIOLENT CRIMES", "Kanye West", R.drawable.ic_launcher_foreground),
    Album("DENIAL IS A RIVER", "Doechii", R.drawable.ic_launcher_foreground),
    Album("Doomsday", "MF DOOM, Pebbles The Invisible Girl", R.drawable.ic_launcher_foreground)
)

@Composable
fun MusicLibraryScreen(navController: NavHostController) {
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
                    }
                }
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
                items(albums) { album ->
                    AlbumItem(album = album)
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
fun AlbumItem(album: Album) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = album.imageResId),
            contentDescription = album.title,
            modifier = Modifier
                .size(56.dp)
        )

        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text = album.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = album.artist,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun BottomNavigationBar() {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        NavigationBarItem(
            selected = false,
            onClick = { /* Handle Home click */ },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_home_inactive),
                    contentDescription = "Home",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            label = {
                Text(
                    text = "Home",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.surface
            )
        )
        NavigationBarItem(
            selected = true,
            onClick = { /* Handle Library click */ },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_library_active),
                    contentDescription = "Your Library",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            },
            label = {
                Text(
                    text = "Your Library",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.surface
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = { /* Handle Profile click */ },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_profile_inactive),
                    contentDescription = "Profile",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            label = {
                Text(
                    text = "Profile",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MusicLibraryScreenPreview() {
    val navController = rememberNavController()
    PurrytifyTheme {
        MusicLibraryScreen(navController)
    }
}
