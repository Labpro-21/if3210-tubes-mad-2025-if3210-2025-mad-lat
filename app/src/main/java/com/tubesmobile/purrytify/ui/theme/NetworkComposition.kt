package com.tubesmobile.purrytify.ui.theme

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.StateFlow

val LocalNetworkStatus = compositionLocalOf<StateFlow<Boolean>> { error("No network status provided") }