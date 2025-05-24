package com.tubesmobile.purrytify.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.core.content.edit
import com.tubesmobile.purrytify.data.api.RetrofitClient.apiService
import com.tubesmobile.purrytify.data.model.RefreshTokenRequest

class TokenManager(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        "secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"
    }

    fun saveToken(token: String, refreshToken: String) {
        sharedPreferences.edit {
            putString(KEY_TOKEN, token)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
        }
    }

    fun getToken(): String? = sharedPreferences.getString(KEY_TOKEN, null)

    fun getRefreshToken(): String? = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)

    fun getTokenTimestamp(): Long = sharedPreferences.getLong(KEY_TOKEN_TIMESTAMP, 0)

    fun clearTokens() {
        sharedPreferences.edit { clear() }
    }

    suspend fun refreshToken(): Result<Boolean> {
        val refreshToken = getRefreshToken()
            ?: return Result.failure(Exception("No refresh token available"))

        return try {
            val response = apiService.refreshToken(RefreshTokenRequest(refreshToken))
            if (response.isSuccessful) {
                response.body()?.let {
                    saveToken(it.accessToken, it.refreshToken)
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
        val token = getToken() ?: return Result.failure(Exception("No token available"))

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
}