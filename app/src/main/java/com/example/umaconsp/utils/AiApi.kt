package com.example.umaconsp.utils

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * Модель запроса для текстового сообщения (используется в Retrofit).
 */
data class ChatRequest(val message: String)

/**
 * Модель ответа от сервера (ожидаемый JSON: {"response": "..."}).
 */
data class ChatResponse(val response: String)

/**
 * Retrofit‑интерфейс для описания эндпоинтов сервера.
 * Хотя в приложении для потоковой передачи используются напрямую OkHttp + SSE,
 * эти методы могут использоваться для синхронных запросов (если потребуется).
 */
interface AiApiService {
    /**
     * Отправка текстового сообщения и получение ответа (не потоково).
     * @param request Объект с полем message
     */
    @POST("/")
    suspend fun sendMessage(@Body request: ChatRequest): retrofit2.Response<ChatResponse>

    /**
     * Отправка сообщения с изображениями (multipart/form-data).
     * @param message Текст сообщения как RequestBody
     * @param images Список частей multipart с изображениями
     */
    @Multipart
    @POST("/")
    suspend fun sendMessageWithImages(
        @Part("message") message: okhttp3.RequestBody,
        @Part images: List<MultipartBody.Part>
    ): retrofit2.Response<ChatResponse>
}

/**
 * Объект-синглтон для настройки сетевого взаимодействия с AI-сервером.
 * Предоставляет:
 * - OkHttp клиент с увеличенными таймаутами (для длительных SSE‑соединений).
 * - Retrofit‑сервис (может использоваться для не‑стриминговых запросов, но в текущей версии
 *   приложения все запросы идут через OkHttp напрямую с заголовком Accept: text/event-stream).
 * - Базовый URL, формируемый из текущего IP-адреса.
 *
 * При изменении IP (currentIp) происходит сброс кэшированного экземпляра Retrofit,
 * чтобы при следующем вызове service был создан с новым базовым адресом.
 */
object AiApi {
    // Порт, на котором работает сервер (FastAPI)
    private const val PORT = 5002

    // Текущий IP-адрес сервера (хранится в памяти, инициализируется из настроек приложения)
    private var _currentIp = "192.168.1.67"

    /**
     * Свойство для доступа и изменения IP-адреса.
     * При установке нового значения сбрасывает кэшированный экземпляр Retrofit-сервиса.
     */
    var currentIp: String
        get() = _currentIp
        set(value) {
            _currentIp = value
            _service = null   // при смене IP нужно пересоздать Retrofit с новым baseUrl
        }

    // Кэш для экземпляра AiApiService (создаётся лениво при первом обращении)
    private var _service: AiApiService? = null

    /**
     * OkHttp клиент, используемый как для Retrofit, так и для прямых SSE-запросов.
     * Таймауты увеличены до 60 секунд, т.к. ответы могут генерироваться долго.
     * retryOnConnectionFailure(true) — автоматически повторять попытки при сбоях соединения.
     */
    val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Retrofit‑сервис для синхронных запросов (не используется в стриминге,
     * но оставлен для возможного расширения функциональности).
     * Создаётся один раз при первом обращении, используя текущий IP.
     */
    val service: AiApiService
        get() {
            if (_service == null) {
                val baseUrl = "http://$_currentIp:$PORT/"
                _service = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)                     // используем тот же клиент
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(AiApiService::class.java)
            }
            return _service!!
        }

    /**
     * Базовый URL, формируемый из текущего IP и порта.
     * Используется в ChatViewModel для построения запросов напрямую через OkHttp.
     */
    val baseUrl: String
        get() = "http://$_currentIp:$PORT/"
}