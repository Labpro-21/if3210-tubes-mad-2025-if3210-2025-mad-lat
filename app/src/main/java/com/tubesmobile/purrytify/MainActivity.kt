package com.tubesmobile.purrytify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tubesmobile.purrytify.data.local.TokenManager
import com.tubesmobile.purrytify.service.PermissionManager
import com.tubesmobile.purrytify.service.TokenVerificationService
import com.tubesmobile.purrytify.ui.screens.HomeScreen
import com.tubesmobile.purrytify.ui.screens.LoginScreen
import com.tubesmobile.purrytify.ui.screens.MusicLibraryScreen
import com.tubesmobile.purrytify.ui.screens.MusicScreen
import com.tubesmobile.purrytify.ui.screens.ProfileScreen
import com.tubesmobile.purrytify.ui.theme.LocalNetworkStatus
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme
import com.tubesmobile.purrytify.ui.components.Screen
import com.tubesmobile.purrytify.ui.viewmodel.LibraryViewModel
import com.tubesmobile.purrytify.ui.viewmodel.LoginViewModel
import com.tubesmobile.purrytify.ui.viewmodel.MusicBehaviorViewModel
import com.tubesmobile.purrytify.ui.viewmodel.NetworkViewModel
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel
import kotlin.math.log

class MainActivity : ComponentActivity() {
    private val musicBehaviorViewModel by viewModels<MusicBehaviorViewModel>()
    private val musicDbViewModel by viewModels<MusicDbViewModel>()
    private val networkViewModel by viewModels<NetworkViewModel>()
    private val loginViewModel by viewModels<LoginViewModel>()
    private val libraryViewModel by viewModels<LibraryViewModel>()
    private lateinit var tokenManager: TokenManager

    // Token expiration receiver
    private val tokenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TokenVerificationService.ACTION_LOGOUT) {
                // Force logout and restart app to login screen
                tokenManager.clearTokens()
                val intent = Intent(this@MainActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        loginViewModel.fetchUserEmail()
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(applicationContext)

        if (!PermissionManager.hasAudioPermission(this)) {
            PermissionManager.requestAudioPermission(this)
        }

        // Register for token expiration broadcasts
        LocalBroadcastManager.getInstance(this).registerReceiver(
            tokenReceiver,
            IntentFilter(TokenVerificationService.ACTION_LOGOUT)
        )

        startService(Intent(this, TokenVerificationService::class.java))

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
                        libraryViewModel = libraryViewModel
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        // Unregister receiver when activity is destroyed
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tokenReceiver)
        super.onDestroy()
    }
}

@Composable
fun TokenExpirationHandler(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == TokenVerificationService.ACTION_LOGOUT) {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver,
            IntentFilter(TokenVerificationService.ACTION_LOGOUT)
        )

        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        }
    }
}

@Composable
fun PurrytifyNavHost(
    musicBehaviorViewModel: MusicBehaviorViewModel,
    musicDbViewModel: MusicDbViewModel,
    loginViewModel: LoginViewModel,
    libraryViewModel: LibraryViewModel,
    isLoggedIn: Boolean
) {
    val navController = rememberNavController()

    // Add this line to handle token expiration within the composable hierarchy
    TokenExpirationHandler(navController)

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) "home" else "login"
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
                loginViewModel = loginViewModel,
                libraryViewModel = libraryViewModel
            )
        }
        composable("profile") {
            ProfileScreen(navController = navController,
                loginViewModel = loginViewModel)
        }
        composable("music/{sourceScreen}") {
            val sourceScreen = it.arguments?.getString("sourceScreen")?.let { name ->
                Screen.valueOf(name)
            } ?: Screen.HOME

            MusicScreen(
                navController = navController,
                sourceScreen = sourceScreen,
                musicBehaviorViewModel = musicBehaviorViewModel,
                musicDbViewModel = musicDbViewModel
            )
        }
    }
}