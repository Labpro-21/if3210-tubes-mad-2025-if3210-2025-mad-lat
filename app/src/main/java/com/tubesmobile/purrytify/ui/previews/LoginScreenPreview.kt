package com.tubesmobile.purrytify.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.tubesmobile.purrytify.ui.screens.LoginScreen
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    PurrytifyTheme {
        LoginScreen()
    }
}