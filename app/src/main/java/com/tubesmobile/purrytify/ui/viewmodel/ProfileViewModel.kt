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

        viewModelScope.launch {
            val result = repository.getProfile()
            _profile.value = if (result.isSuccess) {
                ProfileState.Success(result.getOrNull()!!)
            } else {
                ProfileState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
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
    }
}