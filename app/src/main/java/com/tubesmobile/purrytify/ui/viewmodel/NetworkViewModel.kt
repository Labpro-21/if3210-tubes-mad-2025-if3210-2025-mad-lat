package com.tubesmobile.purrytify.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.tubesmobile.purrytify.util.NetworkConnectivityManager

class NetworkViewModel(application: Application) : AndroidViewModel(application) {
    private val networkManager = NetworkConnectivityManager(application)
    val isConnected = networkManager.isConnected

    init {
        networkManager.startMonitoring()
    }

    override fun onCleared() {
        networkManager.stopMonitoring()
        super.onCleared()
    }
}