package com.tubesmobile.purrytify.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tubesmobile.purrytify.data.api.RetrofitClient
import com.tubesmobile.purrytify.data.local.TokenManager
import com.tubesmobile.purrytify.data.repository.UserRepository
import com.tubesmobile.purrytify.service.TokenVerificationService
import kotlinx.coroutines.flow.MutableStateFlow
import com.tubesmobile.purrytify.service.DataKeeper
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: UserRepository

    init {
        val tokenManager = TokenManager(application)
        repository = UserRepository(RetrofitClient.apiService, tokenManager)
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    // State baru untuk menyimpan email pengguna
    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail
    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName

    fun login(email: String, password: String) {
        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            val result = repository.login(email, password)
            _loginState.value = if (result.isSuccess) {
                // Start the token verification service
                val serviceIntent = Intent(getApplication(), TokenVerificationService::class.java)
                getApplication<Application>().startService(serviceIntent)

                fetchUserEmail()

                LoginState.Success
            } else {
                LoginState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }

    // Fungsi untuk mengambil email pengguna
    fun fetchUserEmail() {
        viewModelScope.launch {
            val result = repository.getProfile()
            val profile = result.getOrNull()

            _userEmail.value = if (result.isSuccess) {
                profile?.email
            } else {
                null
            }

            _userName.value = if (result.isSuccess) {
                profile?.username
            } else {
                null
            }

            DataKeeper.email = profile?.email
            DataKeeper.username = profile?.username
        }
    }


    sealed class LoginState {
        object Idle : LoginState()
        object Loading : LoginState()
        object Success : LoginState()
        data class Error(val message: String) : LoginState()
    }
}