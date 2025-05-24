package com.tubesmobile.purrytify.data.repository

import android.util.Log
import com.tubesmobile.purrytify.data.api.ApiService
import com.tubesmobile.purrytify.data.local.TokenManager
import com.tubesmobile.purrytify.data.model.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import android.webkit.MimeTypeMap

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

    suspend fun updateProfile(location: String?, photoFile: File?): Result<Unit> {
        val token = tokenManager.getToken()
        if (token == null) {
            Log.w("UserRepository", "Update profile attempt with no token. User needs to log in.")
            return Result.failure(Exception("User not authenticated. Please log in again."))
        }

        return try {
            val locationRequestBody: RequestBody? = location?.ifEmpty { null }?.let {
                it.toRequestBody("text/plain".toMediaTypeOrNull())
            }

            val photoPart: MultipartBody.Part? = photoFile?.let { file ->
                // Infer MIME type from file extension, default to "image/jpeg"
                val extension = file.extension.lowercase()
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/*"
                Log.d("UserRepository", "Inferred MIME type for ${file.name}: $mimeType with extension: $extension")
                val requestFileBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
                MultipartBody.Part.createFormData("profilePhoto", file.name, requestFileBody)
            }

            // At least one field must be present to update
            if (locationRequestBody == null && photoPart == null) {
                Log.i("UserRepository", "No changes provided for profile update.")
                return Result.success(Unit)
            }

            Log.d("UserRepository", "Attempting to update profile. Location: $location, Photo: ${photoFile?.name}")
            val response = apiService.updateProfile("Bearer $token", locationRequestBody, photoPart)

            if (response.isSuccessful) {
                Log.i("UserRepository", "Profile updated successfully.")
                Result.success(Unit)
            } else {
                Log.w("UserRepository", "Profile update API call failed: ${response.code()} - ${response.message()}")
                if (response.code() == 401 || response.code() == 403) {
                    Log.i("UserRepository", "Token expired/forbidden during profile update. Attempting refresh.")
                    val refreshResult = tokenManager.refreshToken()
                    if (refreshResult.isSuccess) {
                        Log.i("UserRepository", "Token refreshed successfully. Retrying profile update.")
                        val newAccessToken = tokenManager.getToken() // Get the newly refreshed token
                        if (newAccessToken != null) {
                            // Retry the call with the new token
                            val retryResponse = apiService.updateProfile("Bearer $newAccessToken", locationRequestBody, photoPart)
                            if (retryResponse.isSuccessful) {
                                Log.i("UserRepository", "Profile update successful after token refresh.")
                                Result.success(Unit)
                            } else {
                                Log.w("UserRepository", "Profile update failed after token refresh: ${retryResponse.code()} - ${retryResponse.message()}")
                                if (retryResponse.code() == 401 || retryResponse.code() == 403) {
                                    tokenManager.clearTokens()
                                    Result.failure(Exception("Session has expired. Please log in again."))
                                } else {
                                    Result.failure(Exception("Profile update failed after token refresh: ${retryResponse.code()} - ${retryResponse.message()}"))
                                }
                            }
                        } else {
                            Log.e("UserRepository", "Failed to retrieve new token after successful refresh.")
                            tokenManager.clearTokens()
                            Result.failure(Exception("Failed to retrieve credentials after refresh. Please log in again."))
                        }
                    } else {
                        Log.w("UserRepository", "Token refresh failed. Clearing tokens.")
                        tokenManager.clearTokens()
                        Result.failure(Exception("Session expired and refresh failed. Please log in again."))
                    }
                } else {
                    Result.failure(Exception("Profile update failed: ${response.code()} - ${response.message()}"))
                }
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Exception during profile update: ${e.message}", e)
            Result.failure(e)
        }
    }
}