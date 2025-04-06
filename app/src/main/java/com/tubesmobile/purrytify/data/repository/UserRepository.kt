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
            } else if (response.code() == 403) {
                // Token expired, try to refresh
                val refreshResult = refreshToken()
                if (refreshResult.isSuccess) {
                    // Retry with new token
                    return getProfile()
                }
                Result.failure(Exception("Token expired and refresh failed"))
            } else {
                Result.failure(Exception("Failed to get profile: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshToken(): Result<Boolean> {
        val refreshToken = tokenManager.getRefreshToken()
            ?: return Result.failure(Exception("No refresh token available"))

        // Get new refresh token from server
        return try {
            val response = apiService.refreshToken(RefreshTokenRequest(refreshToken))
            if (response.isSuccessful) {
                response.body()?.let {
                    tokenManager.saveToken(it.token, refreshToken)
                    return Result.success(true)
                }
                return Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception("Failed to refresh token: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyToken(): Result<Boolean> {
        val token = tokenManager.getToken() ?: return Result.failure(Exception("No token available"))

        return try {
            val response = apiService.verifyToken("Bearer $token")
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        tokenManager.clearTokens()
    }
}