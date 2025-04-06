package com.tubesmobile.purrytify.data.model

data class LoginRequest(
    val email: String,   // Format: {NIM}@std.stei.itb.ac.id
    val password: String // Format: {NIM}
)