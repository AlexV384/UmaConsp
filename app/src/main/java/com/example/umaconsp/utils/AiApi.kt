package com.example.umaconsp.utils

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit


data class ChatRequest(val message: String)


data class ChatResponse(val response: String)


interface AiApiService {
    @POST("/")
    suspend fun sendMessage(@Body request: ChatRequest): retrofit2.Response<ChatResponse>
    @Multipart
    @POST("/")
    suspend fun sendMessageWithImages(
        @Part("message") message: okhttp3.RequestBody,
        @Part images: List<MultipartBody.Part>
    ): retrofit2.Response<ChatResponse>
}

object AiApi {
    private const val PORT = 5002
    private var _currentIp = "192.168.1.67"
    var currentIp: String
        get() = _currentIp
        set(value) {
            _currentIp = value
            _service = null
        }

    private var _service: AiApiService? = null

    val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val service: AiApiService
        get() {
            if (_service == null) {
                val baseUrl = "http://$_currentIp:$PORT/"
                _service = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(AiApiService::class.java)
            }
            return _service!!
        }

    val baseUrl: String
        get() = "http://$_currentIp:$PORT/"
}