package com.tubesmobile.purrytify.viewmodel

import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tubesmobile.purrytify.data.api.RetrofitClient
import com.tubesmobile.purrytify.data.local.TokenManager
import com.tubesmobile.purrytify.data.model.ApiSong
import com.tubesmobile.purrytify.service.DataKeeper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class OnlineSongsViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val _onlineGlobalSongs = MutableStateFlow<List<ApiSong>>(emptyList())
    val onlineGlobalSongs: StateFlow<List<ApiSong>> = _onlineGlobalSongs

    private val _onlineCountrySongs = MutableStateFlow<List<ApiSong>>(emptyList())
    val onlineCountrySongs: StateFlow<List<ApiSong>> = _onlineCountrySongs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    val location = DataKeeper.location

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        fetchOnlineGlobalSongs(TokenManager(application))
        fetchOnlineCountrySongs(TokenManager(application))
    }

    fun fetchOnlineGlobalSongs(tokenManager: TokenManager) {
        val token = tokenManager.getToken()
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.apiService.getTopGlobalSongs("Bearer $token")
                if (response.isSuccessful) {
                    _onlineGlobalSongs.value = response.body() ?: emptyList()
                    _error.value = null
                } else {
                    _error.value = "Failed to fetch global songs: ${response.message()}"
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

    fun fetchOnlineCountrySongs(tokenManager: TokenManager) {
        val token = tokenManager.getToken()
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.apiService.getTopCountrySongs("Bearer $token", location.toString())
                if (response.isSuccessful) {
                    _onlineCountrySongs.value = response.body() ?: emptyList()
                    _error.value = null
                } else {
                    _error.value = "Failed to fetch country songs: ${response.message()}"
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
