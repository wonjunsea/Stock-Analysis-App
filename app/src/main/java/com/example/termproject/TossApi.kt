package com.example.termproject

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

// 토스 OAuth2 토큰 응답
data class TossTokenResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long? = null
)

// 캔들 응답 (성공 시 result 안에 들어온다)
data class TossCandleResponse(
    @SerializedName("result") val result: TossCandleResult? = null
)
data class TossCandleResult(
    @SerializedName("candles") val candles: List<TossCandle>? = null,
    @SerializedName("nextBefore") val nextBefore: String? = null
)
data class TossCandle(
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("openPrice") val openPrice: String? = null,
    @SerializedName("highPrice") val highPrice: String? = null,
    @SerializedName("lowPrice") val lowPrice: String? = null,
    @SerializedName("closePrice") val closePrice: String? = null,
    @SerializedName("volume") val volume: String? = null
)

interface TossService {
    @FormUrlEncoded
    @POST("oauth2/token")
    suspend fun issueToken(
        @Field("grant_type") grantType: String = "client_credentials",
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String
    ): TossTokenResponse

    @GET("api/v1/candles")
    suspend fun getCandles(
        @Header("Authorization") authorization: String,
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "1d",
        @Query("count") count: Int = 60,
        @Query("adjusted") adjusted: Boolean = true
    ): TossCandleResponse
}

object TossApi {
    private const val BASE_URL = "https://openapi.tossinvest.com/"

    private val service: TossService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TossService::class.java)
    }

    private var cachedToken: String? = null
    private var tokenExpiryMs: Long = 0L
    private val tokenMutex = Mutex()  // 토큰 동시 발급 방지

    private suspend fun accessToken(): String {
        val now = System.currentTimeMillis()
        // 캐시된 토큰이 유효하면 바로 반환
        cachedToken?.let { if (now < tokenExpiryMs) return it }

        // 여러 요청이 동시에 들어와도 토큰은 한 번만 발급 (Mutex)
        return tokenMutex.withLock {
            val now2 = System.currentTimeMillis()
            // 잠금 기다리는 사이 다른 코루틴이 이미 발급했을 수 있으니 재확인
            cachedToken?.let { if (now2 < tokenExpiryMs) return@withLock it }

            val resp = service.issueToken(
                clientId = BuildConfig.TOSS_CLIENT_ID,
                clientSecret = BuildConfig.TOSS_CLIENT_SECRET
            )
            val token = resp.accessToken
                ?: throw IllegalStateException("토스 토큰 발급 실패 (client id/secret 확인)")

            cachedToken = token
            tokenExpiryMs = now2 + ((resp.expiresIn ?: 0L) - 60).coerceAtLeast(0) * 1000
            token
        }
    }

    /** 캔들을 과거 -> 최신 순으로 반환한다. (토스는 최신순으로 주므로 정렬) */
    suspend fun fetchCandles(symbol: String, interval: String = "1d", count: Int = 60): List<TossCandle> {
        val resp = service.getCandles(
            authorization = "Bearer ${accessToken()}",
            symbol = symbol,
            interval = interval,
            count = count
        )
        val candles = resp.result?.candles ?: emptyList()
        return candles.sortedBy { it.timestamp ?: "" }
    }
}