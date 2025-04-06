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