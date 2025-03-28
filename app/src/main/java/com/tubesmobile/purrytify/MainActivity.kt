package com.tubesmobile.purrytify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tubesmobile.purrytify.ui.screens.LoginScreen
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PurrytifyTheme {
                LoginScreen()
            }
        }
    }
}