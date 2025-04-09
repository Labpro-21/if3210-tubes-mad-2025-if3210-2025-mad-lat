package com.tubesmobile.purrytify

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tubesmobile.purrytify.data.local.TokenManager
import com.tubesmobile.purrytify.service.PermissionManager
import com.tubesmobile.purrytify.service.TokenVerificationService
import com.tubesmobile.purrytify.ui.screens.LoginScreen
import com.tubesmobile.purrytify.ui.screens.HomeScreen
import com.tubesmobile.purrytify.ui.screens.MusicLibraryScreen
import com.tubesmobile.purrytify.ui.screens.MusicScreen
import com.tubesmobile.purrytify.ui.screens.ProfileScreen
import com.tubesmobile.purrytify.ui.theme.LocalNetworkStatus
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme
import com.tubesmobile.purrytify.ui.components.Screen
import com.tubesmobile.purrytify.ui.viewmodel.MusicViewModel
import com.tubesmobile.purrytify.ui.viewmodel.NetworkViewModel

class MainActivity : ComponentActivity() {
    private val musicViewModel by viewModels<MusicViewModel>()
    private val networkViewModel by viewModels<NetworkViewModel>()
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(applicationContext)

        if (!PermissionManager.hasAudioPermission(this)) {
            PermissionManager.requestAudioPermission(this)
        }

        startService(Intent(this, TokenVerificationService::class.java))

        enableEdgeToEdge()
        setContent {
            PurrytifyTheme {
                CompositionLocalProvider(
                    LocalNetworkStatus provides networkViewModel.isConnected
                ) {
                    PurrytifyNavHost(
                        musicViewModel,
                        isLoggedIn = tokenManager.getToken() != null)
                }
            }
        }
    }
}

@Composable
fun PurrytifyNavHost(musicViewModel: MusicViewModel, isLoggedIn: Boolean) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) "home" else "login"
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