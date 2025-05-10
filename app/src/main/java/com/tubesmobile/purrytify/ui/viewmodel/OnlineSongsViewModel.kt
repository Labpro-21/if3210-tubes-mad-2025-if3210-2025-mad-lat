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
        val token = tokenManager.getToken()
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.apiService.getTopSongs("Bearer $token")
                if (response.isSuccessful) {
                    _onlineSongs.value = response.body() ?: emptyList()
                    _error.value = null
                } else {
                    _error.value = "Failed to fetch online songs: ${response.message()}"
                }
            } catch (e: IOException) {
                _error.value = "Network error: ${e.message}"
            } catch (e: HttpException) {
                _error.value = "HTTP error: ${e.message}"
            } catch (e: Exception) {
                _error.value = "Unexpected error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}