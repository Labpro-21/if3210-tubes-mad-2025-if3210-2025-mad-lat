package com.tubesmobile.purrytify.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.tubesmobile.purrytify.data.api.RetrofitClient.apiService
import com.tubesmobile.purrytify.data.model.RefreshTokenRequest
import java.io.IOException
import java.security.GeneralSecurityException

class TokenManager(context: Context) {
    private val masterKeyAlias = try {
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    } catch (e: Exception) {
        Log.e("TokenManager", "Failed to create MasterKey: ${e.message}", e)
        throw IllegalStateException("Cannot create MasterKey", e)
    }

    private val sharedPreferences: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("TokenManager", "Failed to create EncryptedSharedPreferences: ${e.message}", e)
        when (e) {
            is GeneralSecurityException, is IOException -> {
                // Clear corrupted SharedPreferences data
                try {
                    context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .apply()
                    Log.d("TokenManager", "Cleared corrupted secure_prefs")
                    // Retry creating EncryptedSharedPreferences
                    EncryptedSharedPreferences.create(
                        "secure_prefs",
                        masterKeyAlias,
                        context,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (retryException: Exception) {
                    Log.e("TokenManager", "Retry failed: ${retryException.message}", retryException)
                    // Fallback to regular SharedPreferences (temporary for debugging)
                    context.getSharedPreferences("secure_prefs_fallback", Context.MODE_PRIVATE)
                }
            }
            else -> throw e
        }
    }

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"
    }

    fun saveToken(token: String, refreshToken: String) {
        try {
            sharedPreferences.edit {
                putString(KEY_TOKEN, token)
                putString(KEY_REFRESH_TOKEN, refreshToken)
                putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
            }
            Log.d("TokenManager", "Token saved successfully")
        } catch (e: Exception) {
            Log.e("TokenManager", "Error saving token: ${e.message}", e)
        }
    }

    fun getToken(): String? {
        return try {
            sharedPreferences.getString(KEY_TOKEN, null)
        } catch (e: Exception) {
            Log.e("TokenManager", "Error retrieving token: ${e.message}", e)
            null
        }
    }

    fun getRefreshToken(): String? {
        return try {
            sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
        } catch (e: Exception) {
            Log.e("TokenManager", "Error retrieving refresh token: ${e.message}", e)
            null
        }
    }

    fun getTokenTimestamp(): Long {
        return try {
            sharedPreferences.getLong(KEY_TOKEN_TIMESTAMP, 0)
        } catch (e: Exception) {
            Log.e("TokenManager", "Error retrieving token timestamp: ${e.message}", e)
            0
        }
    }

    fun clearTokens() {
        try {
            sharedPreferences.edit { clear() }
            Log.d("TokenManager", "Tokens cleared successfully")
        } catch (e: Exception) {
            Log.e("TokenManager", "Error clearing tokens: ${e.message}", e)
        }
    }

    suspend fun refreshToken(): Result<Boolean> {
        val refreshToken = getRefreshToken()
            ?: return Result.failure(Exception("No refresh token available"))

        return try {
            val response = apiService.refreshToken(RefreshTokenRequest(refreshToken))
            if (response.isSuccessful) {
                response.body()?.let {
                    saveToken(it.accessToken, it.refreshToken)
                    Log.d("TokenManager", "Token refreshed successfully")
                    Result.success(true)
                } ?: run {
                    Log.e("TokenManager", "Empty response while refreshing token")
                    Result.failure(Exception("Empty response"))
                }
            } else {
                Log.e("TokenManager", "Failed to refresh token: ${response.code()}")
                Result.failure(Exception("Failed to refresh token: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("TokenManager", "Error refreshing token: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun verifyToken(): Result<Boolean> {
        val token = getToken() ?: return Result.failure(Exception("No token available"))

        return try {
            val response = apiService.verifyToken("Bearer $token")
            if (response.isSuccessful) {
                Log.d("TokenManager", "Token verified successfully")
                Result.success(true)
            } else {
                Log.e("TokenManager", "Token verification failed with code: ${response.code()}")
                Result.failure(Exception("Token verification failed with code: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("TokenManager", "Error verifying token: ${e.message}", e)
            Result.failure(e)
        }
    }
}