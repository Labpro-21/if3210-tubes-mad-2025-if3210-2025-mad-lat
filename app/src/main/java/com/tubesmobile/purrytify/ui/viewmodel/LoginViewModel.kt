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
import java.util.regex.Pattern

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: UserRepository

    init {
        val tokenManager = TokenManager(application)
        repository = UserRepository(RetrofitClient.apiService, tokenManager)
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail

    fun login(email: String, password: String) {
        if (!isValidEmail(email)) {
            _loginState.value = LoginState.Error("Invalid email format")
            return
        }
        if (password.length < 8) {
            _loginState.value = LoginState.Error("Password must be at least 8 characters")
            return
        }

        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            try {
                val result = repository.login(email.trim(), password)
                _loginState.value = if (result.isSuccess) {
                    val serviceIntent = Intent(getApplication(), TokenVerificationService::class.java)
                    getApplication<Application>().startService(serviceIntent)

                    val sanitizedEmail = sanitizeText(email.trim())
                    _userEmail.value = sanitizedEmail
                    DataKeeper.email = sanitizedEmail

                    LoginState.Success
                } else {
                    val errorMessage = sanitizeText(
                        result.exceptionOrNull()?.message ?: "Unknown error"
                    )
                    LoginState.Error(errorMessage)
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("Failed to connect to server")
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
        _userEmail.value = null
        DataKeeper.email = null
    }

    fun fetchUserEmail() {
        viewModelScope.launch {
            try {
                val result = repository.getProfile()
                val email = result.getOrNull()?.email
                _userEmail.value = if (result.isSuccess && email != null && isValidEmail(email)) {
                    sanitizeText(email)
                } else {
                    null
                }
                DataKeeper.email = _userEmail.value
            } catch (e: Exception) {
                _userEmail.value = null
                DataKeeper.email = null
            }
        }
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

    sealed class LoginState {
        object Idle : LoginState()
        object Loading : LoginState()
        object Success : LoginState()
        data class Error(val message: String) : LoginState()
    }
}
