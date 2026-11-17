package com.streambox.app.data

import com.streambox.app.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path
import okhttp3.RequestBody

data class CredentialsDto(val email: String, val password: String)
data class TokenDto(val access_token: String, val token_type: String)
data class UserDto(val id: Int, val email: String, val created_at: String)
data class PlaylistDto(val id: Int, val name: String, val channel_count: Int, val created_at: String)
data class ChannelDto(val id: Int, val name: String, val stream_url: String, val logo_url: String?, val group_title: String?, val tvg_id: String?)

interface StreamBoxApi {
    @POST("auth/register")
    suspend fun register(@Body body: CredentialsDto): TokenDto

    @POST("auth/login")
    suspend fun login(@Body body: CredentialsDto): TokenDto

    @GET("me")
    suspend fun me(@Header("Authorization") authorization: String): UserDto

    @POST("playlists/import")
    suspend fun importPlaylist(@Header("Authorization") authorization: String, @Query("name") name: String, @Body content: RequestBody): PlaylistDto

    @GET("playlists")
    suspend fun playlists(@Header("Authorization") authorization: String): List<PlaylistDto>

    @GET("playlists/{playlist_id}/channels")
    suspend fun channels(@Header("Authorization") authorization: String, @Path("playlist_id") id: Int): List<ChannelDto>

    @retrofit2.http.DELETE("playlists/{playlist_id}")
    suspend fun deletePlaylist(@Header("Authorization") authorization: String, @Path("playlist_id") id: Int)
}

object ApiClient {
    val api: StreamBoxApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StreamBoxApi::class.java)
    }
}
