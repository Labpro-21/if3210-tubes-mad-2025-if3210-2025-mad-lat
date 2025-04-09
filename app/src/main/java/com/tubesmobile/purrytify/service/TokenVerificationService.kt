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

class TokenVerificationService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var isRunning = false

    private lateinit var userRepository: UserRepository
    private lateinit var tokenManager: TokenManager

    // Fixed refresh interval - 4 minutes and 15 seconds
    private val REFRESH_INTERVAL_MS = 255 * 1000L

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(applicationContext)
        userRepository = UserRepository(RetrofitClient.apiService, tokenManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            scope.launch {
                scheduleTokenRefresh()
            }
        }
        return START_STICKY
    }

    private suspend fun scheduleTokenRefresh() {
        while (isRunning) {
            tokenManager.getToken()?.let {
                val currentTime = System.currentTimeMillis()
                val tokenTime = tokenManager.getTokenTimestamp()
                val elapsed = currentTime - tokenTime

                if (elapsed >= REFRESH_INTERVAL_MS) {
                    Log.d("TokenService", "Token is ${elapsed/1000}s old, refreshing now")
                    refreshToken()
                } else {
                    val waitTime = REFRESH_INTERVAL_MS - elapsed
                    Log.d("TokenService", "Token obtained ${elapsed/1000}s ago. Waiting ${waitTime/1000}s before refresh")
                    delay(waitTime)
                }
            }
        }
    }

    private suspend fun refreshToken() {
        try {
            Log.d("TokenService", "Attempting to refresh token")
            val refreshResult = userRepository.refreshToken()

            if (!refreshResult.isSuccess) {
                val error = refreshResult.exceptionOrNull()?.message ?: "Unknown error"
                Log.e("TokenService", "Failed to refresh token: $error")

                if (error.contains("403")) {
                    // Force logout on 403 Forbidden (expired refresh token)
                    Log.e("TokenService", "Refresh token expired, logging out")
                    tokenManager.clearTokens()
                    val intent = Intent(ACTION_LOGOUT)
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                }
            } else {
                Log.d("TokenService", "Token refreshed successfully")
            }
        } catch (e: Exception) {
            Log.e("TokenService", "Error refreshing token", e)
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