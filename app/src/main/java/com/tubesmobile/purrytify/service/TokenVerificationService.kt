package com.tubesmobile.purrytify.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tubesmobile.purrytify.data.api.RetrofitClient
import com.tubesmobile.purrytify.data.local.TokenManager
import com.tubesmobile.purrytify.data.repository.UserRepository
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class TokenVerificationService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var isRunning = false

    private lateinit var userRepository: UserRepository
    private lateinit var tokenManager: TokenManager

    // 4 minutes and 15 seconds in milliseconds (255 seconds)
    private val AUTO_REFRESH_INTERVAL_MS = TimeUnit.SECONDS.toMillis(255)

    // Keep a shorter verification interval for regular checks (30 seconds)
    private val VERIFICATION_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30)

    // Track when the token was last refreshed
    private var lastRefreshTime = 0L

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(applicationContext)
        userRepository = UserRepository(RetrofitClient.apiService, tokenManager)
        lastRefreshTime = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            scope.launch {
                startTokenVerification()
            }
        }
        return START_STICKY
    }

    private suspend fun startTokenVerification() {
        while (isRunning) {
            if (tokenManager.getToken() != null) {
                val currentTime = System.currentTimeMillis()

                val shouldAutoRefresh = (currentTime - lastRefreshTime) >= AUTO_REFRESH_INTERVAL_MS

                if (shouldAutoRefresh) {
                    Log.d("TokenService", "Auto refresh interval reached (4m15s), refreshing token...")
                    refreshToken()
                } else {
                    verifyToken()
                }
            }
            delay(VERIFICATION_INTERVAL_MS)
        }
    }

    private suspend fun verifyToken() {
        try {
            Log.d("TokenService", "Verifying token with server...")
            val verifyResult = userRepository.verifyToken()

            if (!verifyResult.isSuccess) {
                Log.d("TokenService", "Token verification failed, attempting refresh")
                refreshToken()
            } else {
                Log.d("TokenService", "Token is valid")
            }
        } catch (e: Exception) {
            Log.e("TokenService", "Error during token verification", e)
        }
    }

    private suspend fun refreshToken() {
        try {
            val refreshResult = userRepository.refreshToken()

            if (!refreshResult.isSuccess) {
                Log.e("TokenService", "Token refresh failed")
                // Force logout on token refresh failure
//                tokenManager.clearTokens()
//                val intent = Intent(ACTION_LOGOUT)
//                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            } else {
                Log.d("TokenService", "Token refreshed successfully")
                // Update last refresh time
                lastRefreshTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e("TokenService", "Error during token refresh", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        job.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_LOGOUT = "com.tubesmobile.purrytify.ACTION_LOGOUT"
    }
}