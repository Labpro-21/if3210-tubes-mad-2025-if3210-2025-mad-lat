package com.tubesmobile.purrytify.data.api

import com.tubesmobile.purrytify.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("api/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<LoginResponse>

    @GET("api/profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<ProfileResponse>

    @POST("api/refresh-token")
    suspend fun refreshToken(@Body refreshToken: RefreshTokenRequest): Response<TokenResponse>

    @GET("api/verify-token")
    suspend fun verifyToken(@Header("Authorization") token: String): Response<Any>

    @GET("api/top-songs/global")
    suspend fun getTopSongs(@Header("Authorization") token: String): Response<List<ApiSong>>
}