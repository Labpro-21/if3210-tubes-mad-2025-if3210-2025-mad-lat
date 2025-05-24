package com.tubesmobile.purrytify.data.repository

import android.util.Log
import com.tubesmobile.purrytify.data.api.ApiService
import com.tubesmobile.purrytify.data.local.TokenManager
import com.tubesmobile.purrytify.data.model.*

class UserRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun login(email: String, password: String): Result<Boolean> {
        return try {
            val response = apiService.login(LoginRequest(email, password))
            if (response.isSuccessful) {
                response.body()?.let {
                    tokenManager.saveToken(it.accessToken, it.refreshToken)
                    return Result.success(true)
                }
                return Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception("Login failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProfile(): Result<ProfileResponse> {
        val token = tokenManager.getToken() ?: return Result.failure(Exception("Token expired"))

        return try {
            val response = apiService.getProfile("Bearer $token")
            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d("UserRepository", "getProfilesuccess")
                    return Result.success(it)
                }
                return Result.failure(Exception("Empty response"))
            } else if (response.code() == 403 || response.code() == 401) {
                // Token expired, try to refresh
                val refreshResult = tokenManager.refreshToken()
                if (refreshResult.isSuccess) {
                    // Retry with new token
                    return getProfile()
                } else {
                    // Force logout
                    tokenManager.clearTokens()
                    return Result.failure(Exception("Token expired"))
                }
            } else {
                return Result.failure(Exception("Failed to get profile: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error fetching profile", e)
            return Result.failure(e)
        }
    }

    fun logout() {
        tokenManager.clearTokens()
    }
}