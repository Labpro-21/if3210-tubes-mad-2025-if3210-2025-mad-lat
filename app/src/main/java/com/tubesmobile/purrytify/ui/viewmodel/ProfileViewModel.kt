package com.tubesmobile.purrytify.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tubesmobile.purrytify.data.api.RetrofitClient
import com.tubesmobile.purrytify.data.local.TokenManager
import com.tubesmobile.purrytify.data.model.ProfileResponse
import com.tubesmobile.purrytify.data.repository.UserRepository
import com.tubesmobile.purrytify.service.TokenVerificationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.flow.asStateFlow
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: UserRepository

    init {
        val tokenManager = TokenManager(application)
        repository = UserRepository(RetrofitClient.apiService, tokenManager)
    }

    private val _profile = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val profile: StateFlow<ProfileState> = _profile

    fun loadProfile() {
        _profile.value = ProfileState.Loading

        viewModelScope.launch {
            try {
                val result = repository.getProfile()
                if (result.isSuccess) {
                    val profileData = result.getOrNull()
                    if (profileData != null && isValidProfile(profileData)) {
                        _profile.value = ProfileState.Success(profileData)
                    } else {
                        _profile.value = ProfileState.Error("Invalid profile data")
                    }
                } else {
                    val errorMessage = sanitizeText(
                        result.exceptionOrNull()?.message ?: "Unknown error"
                    )
                    _profile.value = when {
                        errorMessage.contains("token expired", ignoreCase = true) -> {
                            ProfileState.SessionExpired
                        }
                        else -> ProfileState.Error(errorMessage)
                    }
                }
            } catch (e: Exception) {
                _profile.value = ProfileState.Error("Failed to connect to server")
            }
        }
    }

    fun logout() {
        repository.logout()

        try {
            val serviceIntent = Intent(getApplication(), TokenVerificationService::class.java)
            getApplication<Application>().stopService(serviceIntent)
        } catch (e: Exception) {
            Log.e("Logout Error", e.message.toString())
        }
    }

    private fun isValidProfile(profile: ProfileResponse): Boolean {
        return profile.email.isNotBlank() && isValidEmail(profile.email) &&
                profile.username.isNotBlank() &&
                profile.location.isNotBlank()
    }

    private fun isValidEmail(email: String): Boolean {
        val emailPattern = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
            Pattern.CASE_INSENSITIVE
        )
        return emailPattern.matcher(email.trim()).matches()
    }

    private fun sanitizeText(text: String): String {
        val maxLength = 100
        val safeText = text.replace(Regex("[<>\"&]"), "")
        return if (safeText.length > maxLength) safeText.substring(0, maxLength) else safeText
    }

    sealed class ProfileState {
        object Loading : ProfileState()
        data class Success(val profile: ProfileResponse) : ProfileState()
        data class Error(val message: String) : ProfileState()
        object SessionExpired : ProfileState()
    }

    sealed class ProfileUpdateState {
        object Idle : ProfileUpdateState() // Default state
        object Loading : ProfileUpdateState()
        object Success : ProfileUpdateState()
        data class Error(val message: String) : ProfileUpdateState()
    }

    private val _profileUpdateState = MutableStateFlow<ProfileUpdateState>(ProfileUpdateState.Idle)

    fun updateProfile(
        location: String?,
        profilePhotoUri: Uri?,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        _profileUpdateState.value = ProfileUpdateState.Loading
        viewModelScope.launch {
            var tempPhotoFile: File? = null
            try {
                if (profilePhotoUri != null) {
                    tempPhotoFile = getFileFromUri(getApplication<Application>().applicationContext, profilePhotoUri, "profile_photo_update")
                    if (tempPhotoFile == null) {
                        val errorMsg = "Failed to process profile photo for upload."
                        _profileUpdateState.value = ProfileUpdateState.Error(errorMsg)
                        onFailure(errorMsg)
                        return@launch
                    }
                }

                val result = repository.updateProfile(location, tempPhotoFile)

                if (result.isSuccess) {
                    _profileUpdateState.value = ProfileUpdateState.Success
                    onSuccess()
                } else {
                    val errorMsg = sanitizeText(result.exceptionOrNull()?.message ?: "Failed to update profile.")
                    _profileUpdateState.value = ProfileUpdateState.Error(errorMsg)
                    onFailure(errorMsg)
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Update profile exception: ${e.message}", e)
                val errorMsg = sanitizeText(e.message ?: "An unexpected error occurred during update.")
                _profileUpdateState.value = ProfileUpdateState.Error(errorMsg)
                onFailure(errorMsg)
            } finally {
                tempPhotoFile?.delete()
            }
        }
    }

    private fun getFileFromUri(context: Context, uri: Uri, prefix: String): File? {
        val logTag = "ProfileViewModel_GFU" // Logging bcs this func really prone to error, so dont delete this log
        Log.d(logTag, "Attempting to process URI: $uri")
        var inputStream: InputStream? = null
        try {
            // Step A: Try to open an InputStream from the URI
            Log.d(logTag, "Step A: Attempting context.contentResolver.openInputStream(uri)")
            inputStream = context.contentResolver.openInputStream(uri)

            if (inputStream == null) {
                Log.e(logTag, "Step A FAILED: context.contentResolver.openInputStream(uri) returned null. URI may be invalid or inaccessible.")
                return null
            }
            Log.i(logTag, "Step A SUCCESS: Successfully opened InputStream for URI: $uri")

            // Step B: Prepare the destination temporary file
            val originalFileName = getFileNameFromUri(context, uri) ?: "${prefix}_${System.currentTimeMillis()}"
            val safeFileName = sanitizeFileNameForInternalStorage(originalFileName)
            val tempFile = File(context.cacheDir, safeFileName)
            Log.d(logTag, "Step B: Target temporary file path will be: ${tempFile.absolutePath}")

            if (tempFile.exists()) {
                if (tempFile.delete()) {
                    Log.d(logTag, "Step B: Deleted pre-existing temp file at: ${tempFile.absolutePath}")
                } else {
                    Log.w(logTag, "Step B: Could not delete pre-existing temp file at: ${tempFile.absolutePath}")
                }
            }

            var bytesCopied: Long = 0L
            // Step C: Copy data from InputStream to the temporary File
            Log.d(logTag, "Step C: Attempting to copy InputStream to FileOutputStream for ${tempFile.absolutePath}")
            FileOutputStream(tempFile).use { outputStream ->
                bytesCopied = inputStream.copyTo(outputStream)
            }
            Log.i(logTag, "Step C SUCCESS: Copied $bytesCopied bytes to ${tempFile.absolutePath}")

            // Step D: Final check on the created temporary file
            Log.d(logTag, "Step D: Checking existence and size of temp file: ${tempFile.absolutePath}")
            if (tempFile.exists() && tempFile.length() > 0) {
                if (bytesCopied == tempFile.length()) { // Good sanity check
                    Log.i(logTag, "Step D SUCCESS: Temporary file created successfully. Path: ${tempFile.absolutePath}, Size: ${tempFile.length()}")
                    return tempFile
                } else {
                    Log.e(logTag, "Step D FAILED: Temporary file size (${tempFile.length()}) does not match bytes copied ($bytesCopied). Path: ${tempFile.absolutePath}")
                    tempFile.delete()
                    return null
                }
            } else {
                Log.e(logTag, "Step D FAILED: Temporary file does not exist or is empty. Path: ${tempFile.absolutePath}, Exists: ${tempFile.exists()}, Length: ${tempFile.length()}")
                return null
            }

        } catch (e: Exception) {
            Log.e(logTag, "EXCEPTION caught in getFileFromUri for URI: $uri", e)
            return null
        } finally {
            try {
                inputStream?.close()
                Log.d(logTag, "InputStream closed in finally block.")
            } catch (e: java.io.IOException) {
                Log.e(logTag, "Error closing InputStream in finally block.", e)
            }
        }
    }

    // Helper to get original file name from URI
    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            result = cursor.getString(displayNameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error querying display name for URI: $uri", e)
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1 && cut != null) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun sanitizeFileNameForInternalStorage(fileName: String): String {
        return fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(60)
    }
}
