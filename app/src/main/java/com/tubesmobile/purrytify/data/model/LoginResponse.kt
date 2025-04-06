package com.tubesmobile.purrytify.data.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("accessToken") val accessToken: String
)