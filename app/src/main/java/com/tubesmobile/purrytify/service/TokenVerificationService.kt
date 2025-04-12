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

    // Check interval - 30 seconds
    private val CHECK_INTERVAL_MS = 30 * 1000L

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(applicationContext)
        userRepository = UserRepository(RetrofitClient.apiService, tokenManager)
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
                verifyAndRefreshToken()
            }
            delay(CHECK_INTERVAL_MS)
        }
    }

    private suspend fun verifyAndRefreshToken() {
        try {
            Log.d("TokenService", "Verifying token with server...")
            val verifyResult = userRepository.verifyToken()

            if (!verifyResult.isSuccess) {
                Log.d("TokenService", "Token verification failed, attempting refresh")
                val refreshResult = userRepository.refreshToken()

                if (!refreshResult.isSuccess) {
                    Log.e("TokenService", "Token refresh failed")
                    // Force logout on token refresh failure
                    tokenManager.clearTokens()
                    val intent = Intent(ACTION_LOGOUT)
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                } else {
                    Log.d("TokenService", "Token refreshed successfully")
                }
            } else {
                Log.d("TokenService", "Token is valid")
            }
        } catch (e: Exception) {
            Log.e("TokenService", "Error during token verification", e)
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