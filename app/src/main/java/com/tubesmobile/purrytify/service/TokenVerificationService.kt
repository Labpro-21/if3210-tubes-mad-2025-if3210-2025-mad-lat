package com.tubesmobile.purrytify.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tubesmobile.purrytify.data.api.RetrofitClient
import com.tubesmobile.purrytify.data.local.TokenManager
import com.tubesmobile.purrytify.data.repository.UserRepository
import kotlinx.coroutines.*

class TokenVerificationService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var userRepository: UserRepository
    private lateinit var tokenManager: TokenManager

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(applicationContext)
        userRepository = UserRepository(RetrofitClient.apiService, tokenManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            while (true) {
                checkToken()
                delay(60000) // Check every minute
            }
        }
        return START_STICKY
    }

    private suspend fun checkToken() {
        val result = userRepository.verifyToken()
        if (result.isFailure) {
            // Token might be expired, try to refresh
            val refreshResult = userRepository.refreshToken()
            if (refreshResult.isFailure) {
                // Refresh failed, logout user
                tokenManager.clearTokens()

                // Send broadcast to notify activities
                val intent = Intent(ACTION_LOGOUT)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_LOGOUT = "com.tubesmobile.purrytify.ACTION_LOGOUT"
    }
}