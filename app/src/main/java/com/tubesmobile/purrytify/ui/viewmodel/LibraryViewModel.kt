package com.tubesmobile.purrytify.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LibraryViewModel : ViewModel() {
    private val _selectedTab = MutableStateFlow("All Songs")
    val selectedTab: StateFlow<String> = _selectedTab

    fun setSelectedTab(tab: String) {
        _selectedTab.value = tab
    }
}