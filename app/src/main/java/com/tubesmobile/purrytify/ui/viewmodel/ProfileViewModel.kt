package com.tubesmobile.purrytify.ui.viewmodel

import android.app.Application
import android.content.Intent
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

        android.util.Log.d("ProfileViewModel", "Loading profile...")

        viewModelScope.launch {
            val result = repository.getProfile()
            if (result.isSuccess) {
                android.util.Log.d("ProfileViewModel", "Profile loaded successfully")
                _profile.value = ProfileState.Success(result.getOrNull()!!)
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                android.util.Log.e("ProfileViewModel", "Error loading profile: $error")

                // Check if this is a token expiration that requires re-login
                if (error.contains("please login again")) {
                    _profile.value = ProfileState.SessionExpired
                } else {
                    _profile.value = ProfileState.Error(error)
                }
            }
        }
    }

    fun logout() {
        repository.logout()

        // Stop the token verification service
        val serviceIntent = Intent(getApplication(), TokenVerificationService::class.java)
        getApplication<Application>().stopService(serviceIntent)
    }

    sealed class ProfileState {
        object Loading : ProfileState()
        data class Success(val profile: ProfileResponse) : ProfileState()
        data class Error(val message: String) : ProfileState()
        object SessionExpired : ProfileState() // New state for expired session
    }
}