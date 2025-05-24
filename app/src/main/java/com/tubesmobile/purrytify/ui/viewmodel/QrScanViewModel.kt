package com.tubesmobile.purrytify.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class QrScanViewModel : ViewModel() {
    private val _scanResult = MutableStateFlow<String?>(null)
    val scanResult: StateFlow<String?> = _scanResult.asStateFlow()

    fun setScanResult(deepLink: String?) {
        _scanResult.value = deepLink
    }

    fun clearScanResult() {
        _scanResult.value = null
    }

    fun parseDeepLink(deepLink: String): Pair<Int?, Boolean> {
        return try {
            val uri = Uri.parse(deepLink)
            if (uri.scheme == "purrytify" && uri.host == "song") {
                val songId = uri.pathSegments.firstOrNull()?.toIntOrNull()
                songId to true
            } else {
                null to false
            }
        } catch (e: Exception) {
            null to false
        }
    }
}