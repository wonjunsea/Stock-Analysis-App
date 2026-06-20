package com.example.termproject

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

// ============================================================
//  이 파일 하나에 전부 포함:
//  ① Alpha Vantage (주가) ② TFLite (예측) ③ Gemini (조언)
// ============================================================

// ===== ① Alpha Vantage 데이터 형식 =====
data class AlphaResponse(
    @SerializedName("Time Series (Daily)")
    val timeSeries: Map<String, DailyData>?
)
data class DailyData(
    @SerializedName("4. close")
    val close: String
)
interface AlphaService {
    @GET("query")
    suspend fun getDaily(
        @Query("function") function: String = "TIME_SERIES_DAILY",
        @Query("symbol") symbol: String,
        @Query("apikey") apiKey: String
    ): AlphaResponse
}
object AlphaApi {
    private const val BASE_URL = "https://www.alphavantage.co/"
    val service: AlphaService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AlphaService::class.java)
    }
}

// ===== ③ Gemini 데이터 형식 =====
data class GeminiRequest(val contents: List<GContent>)
data class GContent(val parts: List<GPart>)
data class GPart(val text: String)
data class GeminiResponse(val candidates: List<GCandidate>?)
data class GCandidate(val content: GContentResp?)
data class GContentResp(val parts: List<GPart>?)
interface GeminiService {
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}
object GeminiApi {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    val service: GeminiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiService::class.java)
    }
}

// ============================================================
//  화면 (Activity)
// ============================================================
class AnalysisActivity : AppCompatActivity() {

    // ===== 키 설정 =====
    private val geminiKey = BuildConfig.GEMINI_API_KEY
    private val alphaKey = BuildConfig.ALPHA_API_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)

        // intent로 전달받은 종목 정보
        val stockName = intent.getStringExtra("stockName") ?: "종목명"
        val stockCode = intent.getStringExtra("stockCode") ?: "코드"
        val currentPrice = intent.getStringExtra("currentPrice") ?: "$0.00"

        // 종목코드만 추출 (예: "AAPL · 나스닥" -> "AAPL")
        val symbol = stockCode.split("·").firstOrNull()?.trim() ?: stockCode

        // 위젯 연결
        val txtStockName = findViewById<TextView>(R.id.txtStockName)
        val txtCurrentPrice = findViewById<TextView>(R.id.txtCurrentPrice)
        val txtPredictedPrice = findViewById<TextView>(R.id.txtPredictedPrice)
        val txtExpectedReturn = findViewById<TextView>(R.id.txtExpectedReturn)
        val txtAIAdvice = findViewById<TextView>(R.id.txtAIAdvice)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnShare = findViewById<Button>(R.id.btnShare)

        txtStockName.text = "$stockName ($symbol)"
        txtCurrentPrice.text = currentPrice

        btnBack.setOnClickListener { finish() }
        btnShare.setOnClickListener {
            Toast.makeText(this, "카카오톡 공유는 추후 연결 예정", Toast.LENGTH_SHORT).show()
        }

        // 로딩 표시
        txtPredictedPrice.text = "분석 중..."
        txtExpectedReturn.text = ""
        txtAIAdvice.text = "AI가 분석 중입니다..."

        // ===== 메인 작업: 주가 받기 → 예측 → 조언 =====
        runAnalysis(symbol, stockName, txtPredictedPrice, txtExpectedReturn, txtAIAdvice)
    }

    private fun runAnalysis(
        symbol: String,
        stockName: String,
        txtPredicted: TextView,
        txtReturn: TextView,
        txtAdvice: TextView
    ) {
        lifecycleScope.launch {
            try {
                // ===== ① Alpha Vantage로 60일치 종가 받기 =====
                val prices = withContext(Dispatchers.IO) {
                    val resp = AlphaApi.service.getDaily(symbol = symbol, apiKey = alphaKey)
                    // 날짜 최신순 정렬 후 종가만 추출
                    resp.timeSeries
                        ?.toSortedMap(compareByDescending { it })   // 최신 날짜 먼저
                        ?.values
                        ?.take(60)                                  // 최근 60일
                        ?.map { it.close.toFloat() }
                        ?.reversed()                                // 과거→최신 순서로
                        ?: emptyList()
                }

                if (prices.size < 60) {
                    txtPredicted.text = "데이터 부족"
                    txtAdvice.text = "주가 데이터를 충분히 받지 못했습니다. (API 한도 초과일 수 있음)"
                    return@launch
                }

                val currentRealPrice = prices.last()   // 가장 최근 종가

                // ===== ② TFLite로 예측 =====
                val predictedPrice = withContext(Dispatchers.Default) {
                    predictWithTFLite(prices)
                }

                // ===== 화면 표시 =====
                val returnRate = (predictedPrice - currentRealPrice) / currentRealPrice * 100
                txtPredicted.text = "$%.2f".format(predictedPrice)
                val arrow = if (returnRate >= 0) "↑" else "↓"
                txtReturn.text = "$arrow 예상 수익률 %+.2f%%".format(returnRate)

                // ===== ③ Gemini로 조언 =====
                val advice = withContext(Dispatchers.IO) {
                    val prompt = "$stockName 주식의 현재가는 약 $${"%.2f".format(currentRealPrice)}이고, " +
                            "AI 예측 모델이 내일 $${"%.2f".format(predictedPrice)}로 예측했어. " +
                            "이 정보를 바탕으로 투자 조언을 3~4문장으로 한국어로 해줘."
                    val req = GeminiRequest(listOf(GContent(listOf(GPart(prompt)))))
                    val gResp = GeminiApi.service.generateContent(geminiKey, req)
                    gResp.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: "조언을 가져오지 못했습니다."
                }
                txtAdvice.text = advice

            } catch (e: Exception) {
                txtPredicted.text = "오류"
                txtAdvice.text = "오류 발생: ${e.message}"
            }
        }
    }

    // ===== TFLite 예측 함수 =====
    private fun predictWithTFLite(prices: List<Float>): Float {
        // 1. 정규화 (0~1) - 받은 60일 데이터의 min/max 기준
        val min = prices.min()
        val max = prices.max()
        val range = if (max - min == 0f) 1f else (max - min)
        val normalized = prices.map { (it - min) / range }

        // 2. 모델 입력 형태로 변환 [1, 60, 1]
        val input = Array(1) { Array(60) { FloatArray(1) } }
        for (i in 0 until 60) {
            input[0][i][0] = normalized[i]
        }

        // 3. 모델 로드 & 실행
        val model = loadModelFile()
        val interpreter = Interpreter(model)
        val output = Array(1) { FloatArray(1) }
        interpreter.run(input, output)
        interpreter.close()

        // 4. 역정규화 (0~1 -> 실제 주가)
        val predNormalized = output[0][0]
        return predNormalized * range + min
    }

    // assets에서 모델 파일 읽기
    private fun loadModelFile(): MappedByteBuffer {
        val fd = assets.openFd("stock_lstm_model.tflite")
        val inputStream = FileInputStream(fd.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }
}