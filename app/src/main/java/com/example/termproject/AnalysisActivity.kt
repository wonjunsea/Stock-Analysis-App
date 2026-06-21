package com.example.termproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.tensorflow.lite.Interpreter
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit

// ===== Gemini 데이터 형식 (구글 API 최신 명세 공식 표준 규격으로 보정) =====
data class GeminiRequest(val contents: List<GContent>)
data class GContent(val parts: List<GPart>, val role: String = "user")
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

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS) // 시간 유예를 45초로 더 늘림
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    val service: GeminiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiService::class.java)
    }
}

class AnalysisActivity : AppCompatActivity() {

    private val geminiKey = BuildConfig.GEMINI_API_KEY
    private val LOGO_DEV_PUBLIC_KEY = "pk_RSSkExm0R5Sbw_aw34FWSA"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)

        val stockName = intent.getStringExtra("stockName") ?: "종목명"
        val stockCode = intent.getStringExtra("stockCode") ?: "코드"
        val currentPrice = intent.getStringExtra("currentPrice") ?: "0"

        val symbol = stockCode.split("·").firstOrNull()?.trim() ?: stockCode
        val curr = if (symbol.length == 6 && symbol.all { it.isDigit() }) "₩" else "$"

        val txtStockName = findViewById<TextView>(R.id.txtStockName)
        val txtCurrentPrice = findViewById<TextView>(R.id.txtCurrentPrice)
        val txtPredictedPrice = findViewById<TextView>(R.id.txtPredictedPrice)
        val txtExpectedReturn = findViewById<TextView>(R.id.txtExpectedReturn)
        val txtAIAdvice = findViewById<TextView>(R.id.txtAIAdvice)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnShare = findViewById<Button>(R.id.btnShare)

        val imgStockLogo = findViewById<ImageView?>(R.id.imgStockLogo)
        if (imgStockLogo != null) {
            Glide.with(this)
                .load(logoUrlFor(symbol))
                .placeholder(android.R.drawable.ic_menu_report_image)
                .error(android.R.drawable.ic_dialog_alert)
                .into(imgStockLogo)
        }

        txtStockName.text = "$stockName ($symbol)"
        txtCurrentPrice.text = currentPrice

        btnBack.setOnClickListener { finish() }

        // 카카오톡 외부 APP 공유하기 기능
        btnShare.setOnClickListener {
            try {
                val shareText = """
                    [AI 주식 예측 분석 결과]
                    📈 종목명: ${txtStockName.text}
                    💵 현재가: ${txtCurrentPrice.text}
                    🔮 내일 AI 예측가: ${txtPredictedPrice.text}
                    📊 ${txtExpectedReturn.text}
                    
                    🤖 AI 투자 조언:
                    ${txtAIAdvice.text}
                """.trimIndent()

                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                    `package` = "com.kakao.talk"
                }
                startActivity(sendIntent)
            } catch (e: Exception) {
                try {
                    val backupIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, """
                            [AI 주식 예측 분석 결과]
                            📈 종목명: ${txtStockName.text}
                            💵 현재가: ${txtCurrentPrice.text}
                            🔮 내일 AI 예측가: ${txtPredictedPrice.text}
                        """.trimIndent())
                        type = "text/plain"
                    }
                    val chooser = Intent.createChooser(backupIntent, "분석 결과 공유하기")
                    startActivity(chooser)
                } catch (ex: Exception) {
                    Toast.makeText(this, "공유 가능한 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        txtPredictedPrice.text = "분석 중..."
        txtExpectedReturn.text = ""
        txtAIAdvice.text = "AI가 분석 중입니다..."

        runAnalysis(symbol, stockName, curr, txtPredictedPrice, txtExpectedReturn, txtAIAdvice)
    }

    private fun runAnalysis(
        symbol: String,
        stockName: String,
        curr: String,
        txtPredicted: TextView,
        txtReturn: TextView,
        txtAdvice: TextView
    ) {
        lifecycleScope.launch {
            var currentRealPrice = 0f
            var predictedPrice = 0f
            var isPredictionSuccess = false

            // [블록 A] 주가 수집 및 TFLite 모델 예측 실행
            try {
                val prices = withContext(Dispatchers.IO) {
                    TossApi.fetchCandles(symbol, interval = "1d", count = 60)
                        .mapNotNull { it.closePrice?.toFloatOrNull() }
                }

                if (prices.size < 60) {
                    txtPredicted.text = "데이터 부족"
                    txtAdvice.text = "주가 데이터를 충분히 받지 못했습니다. (토스 API 응답 ${prices.size}일)"
                    return@launch
                }

                currentRealPrice = prices.last()

                predictedPrice = withContext(Dispatchers.Default) {
                    predictWithTFLite(prices)
                }

                val returnRate = (predictedPrice - currentRealPrice) / currentRealPrice * 100
                txtPredicted.text = "$curr${fmtPrice(predictedPrice)}"
                val arrow = if (returnRate >= 0) "↑" else "↓"
                txtReturn.text = "$arrow 예상 수익률 %+.2f%%".format(returnRate)

                isPredictionSuccess = true

            } catch (e: Exception) {
                txtPredicted.text = "오류"
                txtReturn.text = ""
                txtAdvice.text = "주가 데이터 분석 오류: ${e.message}"
            }

            // [블록 B] Gemini 조언 받아오기 (원인 진단용 로그 및 예외 처리 세분화)
            if (isPredictionSuccess) {
                try {
                    val advice = withContext(Dispatchers.IO) {
                        val prompt = """
                            당신은 전문 금융 애널리스트입니다. 아래 정보를 바탕으로 브리핑을 작성해 주세요.
                            
                            - 종목명: $stockName ($symbol)
                            - 현재가: $curr${fmtPrice(currentRealPrice)}
                            - 내일 AI 예측가: $curr${fmtPrice(predictedPrice)}
                            
                            [요구사항]
                            1. 이 종목의 최근 시장 흐름이나 이슈 스타일을 고려하여 단기 시황 전망을 3줄 내외로 요약해 주십시오. (해당 기업의 전반적인 최신 트렌드를 기반으로 자연스럽게 작성)
                            2. 투자자가 취하면 좋을 전략적 조언을 3~4문장으로 신중하고 전문성 있게 작성해 주십시오.
                            3. 줄바꿈을 자주 사용하여 모바일 화면에서 가독성이 좋게 한국어로 작성해 주십시오.
                        """.trimIndent()

                        val req = GeminiRequest(listOf(GContent(listOf(GPart(prompt)))))

                        // 💡 만약 여기서 터진다면 API KEY가 비어있거나 잘못되었을 확률이 매우 높습니다.
                        Log.d("GeminiDebug", "보내는 API KEY: $geminiKey")

                        val gResp = GeminiApi.service.generateContent(geminiKey, req)
                        gResp.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            ?: "조언 문장을 생성하지 못했습니다."
                    }
                    txtAdvice.text = advice

                } catch (e: HttpException) {
                    // 구글 서버가 에러 코드를 준 경우 (400, 403, 429 등)
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e("GeminiDebug", "HTTP 에러 발생: ${e.code()}, 내용: $errorBody")

                    if (e.code() == 403 || e.code() == 400) {
                        txtAdvice.text = "API Key 인증 오류 또는 요청 형식 오류가 발생했습니다. (Code: ${e.code()})"
                    } else {
                        txtAdvice.text = "구글 API 서버 오류: ${e.message()} (Code: ${e.code()})"
                    }
                } catch (e: Exception) {
                    // 진짜 네트워크 단절 또는 순수 타임아웃인 경우
                    Log.e("GeminiDebug", "일반 예외 발생: ${e.message}", e)
                    txtAdvice.text = "네트워크 연결이 지연되고 있습니다. 잠시 후 다시 시도해 주세요."
                }
            }
        }
    }

    private fun fmtPrice(value: Float): String = "%,d".format(value.toLong())

    private fun predictWithTFLite(prices: List<Float>): Float {
        val min = prices.min()
        val max = prices.max()
        val range = if (max - min == 0f) 1f else (max - min)
        val normalized = prices.map { (it - min) / range }

        val input = Array(1) { Array(60) { FloatArray(1) } }
        for (i in 0 until 60) {
            input[0][i][0] = normalized[i]
        }

        val model = loadModelFile()
        val interpreter = Interpreter(model)
        val output = Array(1) { FloatArray(1) }
        interpreter.run(input, output)
        interpreter.close()

        val predNormalized = output[0][0]
        return predNormalized * range + min
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fd = assets.openFd("stock_lstm_model.tflite")
        val inputStream = FileInputStream(fd.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    private fun logoUrlFor(symbol: String): String {
        val domain = when (symbol.uppercase()) {
            "AAPL" -> "apple.com"
            "MSFT" -> "microsoft.com"
            "GOOGL", "GOOG" -> "google.com"
            "AMZN" -> "amazon.com"
            "TSLA" -> "tesla.com"
            "NVDA" -> "nvidia.com"
            "META" -> "meta.com"
            "005930" -> "samsung.com"
            "000660" -> "skhynix.com"
            "035420" -> "naver.com"
            "035720" -> "kakaocorp.com"
            "005380" -> "hyundai.com"
            "051910" -> "lgchem.com"
            else -> "example.com"
        }
        return "https://img.logo.dev/$domain?token=$LOGO_DEV_PUBLIC_KEY"
    }
}