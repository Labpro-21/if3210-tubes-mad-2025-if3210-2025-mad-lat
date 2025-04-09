package com.tubesmobile.purrytify.data.repository

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
        val token = tokenManager.getToken() ?: return Result.failure(Exception("No token available"))

        return try {
            val response = apiService.getProfile("Bearer $token")
            if (response.isSuccessful) {
                response.body()?.let {
                    return Result.success(it)
                }
                return Result.failure(Exception("Empty response"))
            } else if (response.code() == 403 || response.code() == 401) {
                // Token expired, try to refresh
                val refreshResult = refreshToken()
                if (refreshResult.isSuccess) {
                    // Retry with new token
                    return getProfile()
                } else {
                    // Force logout
                    tokenManager.clearTokens()
                    return Result.failure(Exception("Token expired and refresh failed - please login again"))
                }
            } else {
                return Result.failure(Exception("Failed to get profile: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error fetching profile", e)
            return Result.failure(e)
        }
    }

    suspend fun refreshToken(): Result<Boolean> {
        val refreshToken = tokenManager.getRefreshToken()
            ?: return Result.failure(Exception("No refresh token available"))

        return try {
            val response = apiService.refreshToken(RefreshTokenRequest(refreshToken))
            if (response.isSuccessful) {
                response.body()?.let {
                    tokenManager.saveToken(it.accessToken, it.refreshToken)
                    return Result.success(true)
                }
                android.util.Log.e("UserRepository", "Empty response while refreshing token")
                return Result.failure(Exception("Empty response"))
            } else {
                android.util.Log.e("UserRepository", "Failed to refresh token: ${response.code()}")
                return Result.failure(Exception("Failed to refresh token: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error refreshing token", e)
            return Result.failure(e)
        }
    }

    suspend fun verifyToken(): Result<Boolean> {
        val token = tokenManager.getToken() ?: return Result.failure(Exception("No token available"))

        return try {
            val response = apiService.verifyToken("Bearer $token")
            if (response.isSuccessful) {
                Result.success(response.isSuccessful)
            }
            else {
                Result.failure(Exception("Token verification failed with code: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        tokenManager.clearTokens()
    }
}