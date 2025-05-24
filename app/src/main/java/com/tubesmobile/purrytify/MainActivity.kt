package com.tubesmobile.purrytify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tubesmobile.purrytify.data.local.TokenManager
import com.tubesmobile.purrytify.service.PermissionManager
import com.tubesmobile.purrytify.service.TokenVerificationService
import com.tubesmobile.purrytify.ui.components.Screen
import com.tubesmobile.purrytify.ui.screens.HomeScreen
import com.tubesmobile.purrytify.ui.screens.LoginScreen
import com.tubesmobile.purrytify.ui.screens.MusicLibraryScreen
import com.tubesmobile.purrytify.ui.screens.MusicScreen
import com.tubesmobile.purrytify.ui.screens.ProfileScreen
import com.tubesmobile.purrytify.ui.screens.Song
import com.tubesmobile.purrytify.ui.screens.Top50Screen
import com.tubesmobile.purrytify.ui.theme.LocalNetworkStatus
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme
import com.tubesmobile.purrytify.ui.viewmodel.LoginViewModel
import com.tubesmobile.purrytify.ui.viewmodel.MusicBehaviorViewModel
import com.tubesmobile.purrytify.ui.viewmodel.NetworkViewModel
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import com.tubesmobile.purrytify.viewmodel.OnlineSongsViewModel

class MainActivity : ComponentActivity() {
    private val musicBehaviorViewModel by viewModels<MusicBehaviorViewModel>()
    private val musicDbViewModel by viewModels<MusicDbViewModel>()
    private val networkViewModel by viewModels<NetworkViewModel>()
    private val loginViewModel by viewModels<LoginViewModel>()
    private val onlineSongsViewModel by viewModels<OnlineSongsViewModel>()
    private lateinit var tokenManager: TokenManager

    private val tokenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TokenVerificationService.ACTION_LOGOUT) {
                try {
                    tokenManager.clearTokens()
                    val intent = Intent(this@MainActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error handling logout: ${e.message}", e)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        try {
            loginViewModel.fetchUserEmail()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error fetching user email: ${e.message}", e)
        }

        super.onCreate(savedInstanceState)

        try {
            tokenManager = TokenManager(applicationContext)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to initialize TokenManager: ${e.message}", e)
            try {
                applicationContext.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()
                tokenManager = TokenManager(applicationContext)
            } catch (retryException: Exception) {
                Log.e("MainActivity", "Retry TokenManager initialization failed: ${retryException.message}", retryException)
                setContent {
                    PurrytifyTheme {
                        ErrorScreen(errorMessage = "Failed to initialize secure storage")
                    }
                }
                return
            }
        }

        if (!PermissionManager.hasAudioPermission(this)) {
            PermissionManager.requestAudioPermission(this)
        }

        try {
            LocalBroadcastManager.getInstance(this).registerReceiver(
                tokenReceiver,
                IntentFilter(TokenVerificationService.ACTION_LOGOUT)
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "Error registering token receiver: ${e.message}", e)
        }

        try {
            startService(Intent(this, TokenVerificationService::class.java))
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting TokenVerificationService: ${e.message}", e)
        }

        enableEdgeToEdge()
        setContent {
            PurrytifyTheme {
                CompositionLocalProvider(
                    LocalNetworkStatus provides networkViewModel.isConnected
                ) {
                    PurrytifyNavHost(
                        musicBehaviorViewModel = musicBehaviorViewModel,
                        loginViewModel = loginViewModel,
                        isLoggedIn = tokenManager.getToken() != null,
                        musicDbViewModel = musicDbViewModel,
                        onlineSongsViewModel = onlineSongsViewModel,
                        deepLinkIntent = intent
                    )
                }
            }
        }

        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "purrytify" && uri.host == "song") {
                val songId = uri.pathSegments.firstOrNull()?.toIntOrNull()
                if (songId != null) {
                    Log.d("MainActivity", "Handling deep link for song ID: $songId")
                    onlineSongsViewModel.loadSongById(songId) { song ->
                        if (song != null) {
                            val songData = Song(
                                id = song.id,
                                title = song.title,
                                artist = song.artist,
                                duration = parseDurationToMillis(song.duration),
                                uri = song.url,
                                artworkUri = song.artwork
                            )
                            musicBehaviorViewModel.playSong(songData, applicationContext)
                            musicDbViewModel.updateSongTimestamp(songData)
                        } else {
                            Log.e("MainActivity", "Song with ID $songId not found")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(tokenReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering token receiver: ${e.message}", e)
        }
        super.onDestroy()
    }
}

@Composable
fun ErrorScreen(errorMessage: String) {
    Text(
        text = "Error: $errorMessage",
        color = Color.Red,
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize()
    )
}

@Composable
fun TokenExpirationHandler(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == TokenVerificationService.ACTION_LOGOUT) {
                    try {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    } catch (e: Exception) {
                        Log.e("TokenExpirationHandler", "Error navigating to login: ${e.message}", e)
                    }
                }
            }
        }

        try {
            LocalBroadcastManager.getInstance(context).registerReceiver(
                receiver,
                IntentFilter(TokenVerificationService.ACTION_LOGOUT)
            )
        } catch (e: Exception) {
            Log.e("TokenExpirationHandler", "Error registering receiver: ${e.message}", e)
        }

        onDispose {
            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e("TokenExpirationHandler", "Error unregistering receiver: ${e.message}", e)
            }
        }
    }
}

@Composable
fun PurrytifyNavHost(
    musicBehaviorViewModel: MusicBehaviorViewModel,
    musicDbViewModel: MusicDbViewModel,
    loginViewModel: LoginViewModel,
    isLoggedIn: Boolean,
    onlineSongsViewModel: OnlineSongsViewModel,
    deepLinkIntent: Intent?
) {
    val navController = rememberNavController()
    val startDestination = if (isLoggedIn) "home" else "login"

    TokenExpirationHandler(navController)

    LaunchedEffect(deepLinkIntent) {
        deepLinkIntent?.data?.let { uri ->
            if (uri.scheme == "purrytify" && uri.host == "song") {
                val songId = uri.pathSegments.firstOrNull()?.toIntOrNull()
                if (songId != null) {
                    navController.navigate("music/${Screen.HOME.name}/true/$songId") {
                        popUpTo(startDestination) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("login") {
            LoginScreen(navController = navController, loginViewModel = loginViewModel)
        }
        composable("home") {
            HomeScreen(
                navController = navController,
                musicBehaviorViewModel = musicBehaviorViewModel,
                loginViewModel = loginViewModel
            )
        }
        composable("library") {
            MusicLibraryScreen(
                navController = navController,
                musicBehaviorViewModel = musicBehaviorViewModel,
                loginViewModel = loginViewModel
            )
        }
        composable("profile") {
            ProfileScreen(
                navController = navController,
                loginViewModel = loginViewModel,
                musicBehaviorViewModel = musicBehaviorViewModel
            )
        }
        composable(
            route = "music/{sourceScreen}/{isFromApiSong}/{songId}",
            arguments = listOf(
                navArgument("sourceScreen") { type = NavType.StringType },
                navArgument("isFromApiSong") { type = NavType.BoolType },
                navArgument("songId") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val sourceScreen = backStackEntry.arguments?.getString("sourceScreen")?.let { name ->
                Screen.valueOf(name)
            } ?: Screen.HOME
            val isFromApiSong = backStackEntry.arguments?.getBoolean("isFromApiSong") ?: false
            val songId = backStackEntry.arguments?.getInt("songId") ?: -1

            MusicScreen(
                navController = navController,
                sourceScreen = sourceScreen,
                musicBehaviorViewModel = musicBehaviorViewModel,
                musicDbViewModel = musicDbViewModel,
                isFromApiSong = isFromApiSong,
                songId = songId,
                onlineSongsViewModel = onlineSongsViewModel
            )
        }
        composable("top50/global") {
            Top50Screen(
                navController = navController,
                musicBehaviorViewModel = musicBehaviorViewModel,
                type = "global"
            )
        }
        composable("top50/country") {
            Top50Screen(
                navController = navController,
                musicBehaviorViewModel = musicBehaviorViewModel,
                type = "country"
            )
        }
    }
}

private fun parseDurationToMillis(duration: String): Long {
    val parts = duration.split(":")
    val minutes = parts[0].toLongOrNull() ?: 0L
    val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    return (minutes * 60 + seconds) * 1000
}
