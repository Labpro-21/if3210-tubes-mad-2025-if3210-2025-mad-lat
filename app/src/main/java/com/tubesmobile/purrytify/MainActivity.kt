package com.tubesmobile.purrytify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tubesmobile.purrytify.ui.screens.LoginScreen
import com.tubesmobile.purrytify.ui.screens.HomeScreen
import com.tubesmobile.purrytify.ui.screens.MusicLibraryScreen
import com.tubesmobile.purrytify.ui.screens.MusicScreen
import com.tubesmobile.purrytify.ui.screens.ProfileScreen
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme
import com.tubesmobile.purrytify.ui.components.Screen
import com.tubesmobile.purrytify.ui.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {
    private val musicViewModel by viewModels<MusicViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PurrytifyTheme {
                PurrytifyNavHost(musicViewModel)
            }
        }
    }
}

@Composable
fun PurrytifyNavHost(musicViewModel: MusicViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") {
            LoginScreen(navController = navController)
        }
        composable("home") {
            HomeScreen(navController = navController, musicViewModel = musicViewModel)
        }
        composable("library") {
            MusicLibraryScreen(navController = navController, musicViewModel = musicViewModel)
        }
        composable("profile") {
            ProfileScreen(navController = navController)
        }
        composable("music/{sourceScreen}") {
            val sourceScreen = it.arguments?.getString("sourceScreen")?.let { name ->
                Screen.valueOf(name)
            } ?: Screen.HOME

            MusicScreen(
                navController = navController,
                sourceScreen = sourceScreen,
                musicViewModel = musicViewModel
            )
        }
    }
}