package com.tubesmobile.purrytify.data.api

import com.tubesmobile.purrytify.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
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
    suspend fun getTopGlobalSongs(@Header("Authorization") token: String): Response<List<ApiSong>>

    @GET("api/top-songs/{countryId}")
    suspend fun getTopCountrySongs(
        @Header("Authorization") token: String,
        @Path("countryId") countryId: String
    ): Response<List<ApiSong>>

    @Multipart
    @PATCH("api/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Part("location") location: RequestBody?,
        @Part profilePhoto: MultipartBody.Part?
    ): Response<Unit>
}