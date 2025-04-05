package com.tubesmobile.purrytify.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.tubesmobile.purrytify.R

enum class Screen {
    HOME,
    LIBRARY,
    PROFILE,
    MUSIC
}

@Composable
fun SharedBottomNavigationBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    transparent: Boolean
) {
    NavigationBar(
        containerColor = if (!transparent)
                            MaterialTheme.colorScheme.surface
                         else
                            Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationBarItem(
            selected = currentScreen == Screen.HOME,
            onClick = { onNavigate(Screen.HOME) },
            icon =
                {
                Icon(
                    painter = painterResource(
                        id = if (currentScreen == Screen.HOME)
                            R.drawable.ic_home_active
                        else
                            R.drawable.ic_home_inactive
                    ),
                    contentDescription = "Home",
                    tint = if (currentScreen == Screen.HOME)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            },
            label = {
                Text(
                    text = "Home",
                    color = if (currentScreen == Screen.HOME)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (currentScreen == Screen.HOME)
                        FontWeight.Bold
                    else
                        FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent
            )
        )

        NavigationBarItem(
            selected = currentScreen == Screen.LIBRARY,
            onClick = { onNavigate(Screen.LIBRARY) },
            icon = {
                Icon(
                    painter = painterResource(
                        id = if (currentScreen == Screen.LIBRARY)
                            R.drawable.ic_library_active
                        else
                            R.drawable.ic_library_inactive
                    ),
                    contentDescription = "Your Library",
                    tint = if (currentScreen == Screen.LIBRARY)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            },
            label = {
                Text(
                    text = "Your Library",
                    color = if (currentScreen == Screen.LIBRARY)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (currentScreen == Screen.LIBRARY)
                        FontWeight.Bold
                    else
                        FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent

            )
        )

        NavigationBarItem(
            selected = currentScreen == Screen.PROFILE,
            onClick = { onNavigate(Screen.PROFILE) },
            icon = {
                Icon(
                    painter = painterResource(
                        id = if (currentScreen == Screen.PROFILE)
                            R.drawable.ic_profile_active
                        else
                            R.drawable.ic_profile_inactive
                    ),
                    contentDescription = "Profile",
                    tint = if (currentScreen == Screen.PROFILE)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            },
            label = {
                Text(
                    text = "Profile",
                    color = if (currentScreen == Screen.PROFILE)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (currentScreen == Screen.PROFILE)
                        FontWeight.Bold
                    else
                        FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}