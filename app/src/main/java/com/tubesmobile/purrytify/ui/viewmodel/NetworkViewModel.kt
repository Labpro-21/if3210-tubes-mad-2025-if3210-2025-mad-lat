package com.tubesmobile.purrytify.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.tubesmobile.purrytify.util.NetworkConnectivityManager
import java.util.concurrent.atomic.AtomicBoolean

class NetworkViewModel(application: Application) : AndroidViewModel(application) {
    private val networkManager = NetworkConnectivityManager(application)
    val isConnected = networkManager.isConnected

    private val isMonitoringStarted = AtomicBoolean(false)

    init {
        startNetworkMonitoring()
    }

    private fun startNetworkMonitoring() {
        try {
            if (!isMonitoringStarted.getAndSet(true)) {
                networkManager.startMonitoring()
            }
        } catch (e: Exception) {
            Log.e("Start Network Error", e.message.toString())
        }
    }

    override fun onCleared() {
        try {
            if (isMonitoringStarted.getAndSet(false)) {
                networkManager.stopMonitoring()
            }
        } catch (e: Exception) {
            Log.e("Stop Network Error", e.message.toString())
        }
        super.onCleared()
    }
}