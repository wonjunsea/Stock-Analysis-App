package com.example.termproject

import com.google.gson.annotations.SerializedName
import retrofit2.HttpException
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

// 캔들 응답
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

    private suspend fun accessToken(): String {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < tokenExpiryMs) return it }

        // ===== 디버그: 토큰 발급 단계 에러를 자세히 =====
        val resp = try {
            service.issueToken(
                clientId = BuildConfig.TOSS_CLIENT_ID,
                clientSecret = BuildConfig.TOSS_CLIENT_SECRET
            )
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string() ?: "(본문 없음)"
            throw IllegalStateException("[토큰발급 ${e.code()}] $body")
        }
        val token = resp.accessToken
            ?: throw IllegalStateException("토스 토큰 발급 실패 (access_token이 null)")

        cachedToken = token
        tokenExpiryMs = now + ((resp.expiresIn ?: 0L) - 60).coerceAtLeast(0) * 1000
        return token
    }

    suspend fun fetchCandles(symbol: String, interval: String = "1d", count: Int = 60): List<TossCandle> {
        // ===== 디버그: 캔들 조회 단계 에러를 자세히 =====
        val resp = try {
            service.getCandles(
                authorization = "Bearer ${accessToken()}",
                symbol = symbol,
                interval = interval,
                count = count
            )
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string() ?: "(본문 없음)"
            throw IllegalStateException("[캔들조회 ${e.code()}] $body")
        }
        val candles = resp.result?.candles ?: emptyList()
        return candles.sortedBy { it.timestamp ?: "" }
    }
}