package com.tubesmobile.purrytify.viewmodel

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tubesmobile.purrytify.data.api.RetrofitClient
import com.tubesmobile.purrytify.data.local.TokenManager
import com.tubesmobile.purrytify.data.model.ApiSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class OnlineSongsViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val _onlineSongs = MutableStateFlow<List<ApiSong>>(emptyList())
    val onlineSongs: StateFlow<List<ApiSong>> = _onlineSongs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        fetchOnlineSongs(TokenManager(application))
    }

    fun fetchOnlineSongs(tokenManager: TokenManager) {
        viewModelScope.launch {
            _isLoading.value = true
            var token = tokenManager.getToken()

            try {
                val response = RetrofitClient.apiService.getTopSongs("Bearer $token")
                if (response.isSuccessful) {
                    _onlineSongs.value = response.body() ?: emptyList()
                    _error.value = null
                } else {
                    if (response.code() == 401 || response.code() == 403) {
                        _error.value = "Token expired. Attempting to refresh..."
                        val refreshResult = tokenManager.refreshToken()
                        if (refreshResult.isSuccess) {
                            fetchOnlineSongs(tokenManager)
                            return@launch
                        } else {
                            _error.value = "Token expired and refresh failed."
                            tokenManager.clearTokens()
                        }
                    } else {
                        _error.value = "Failed to fetch online songs: ${response.message()} (Code: ${response.code()})"
                    }
                }
            } catch (e: IOException) {
                _error.value = "Network error: ${e.message}"
            } catch (e: Exception) {
                _error.value = "Unexpected error: ${e.message}"
            } finally {
                if (_error.value?.contains("Attempting to refresh...") != true) {
                    _isLoading.value = false
                }
            }
        }
    }
}