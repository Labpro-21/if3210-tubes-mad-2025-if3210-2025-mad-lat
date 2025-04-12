package com.tubesmobile.purrytify.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
}
