package com.example.termproject

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// ============================================================
//  이 파일 하나에 전부 들어있음 (데이터형식 + 통신 + 화면)
//  추가 파일 만들 필요 없음!
// ============================================================

// ===== 요청 데이터 형식 (Postman의 JSON 구조와 동일) =====
data class GeminiRequest(val contents: List<Content>)
data class Content(val parts: List<Part>)
data class Part(val text: String)

// ===== 응답 데이터 형식 =====
data class GeminiResponse(val candidates: List<Candidate>?)
data class Candidate(val content: ContentResponse?)
data class ContentResponse(val parts: List<Part>?)

// ===== Retrofit 통신 정의 =====
interface GeminiService {
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

// ===== Retrofit 객체 (한 번 만들어 재사용) =====
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
//  화면(Activity)
// ============================================================
class AnalysisActivity : AppCompatActivity() {

    // local.properties에 저장한 키를 불러옴
    private val apiKey = BuildConfig.GEMINI_API_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)

        // DetailActivity에서 intent로 전달받은 데이터
        val stockName = intent.getStringExtra("stockName") ?: "종목명"
        val stockCode = intent.getStringExtra("stockCode") ?: "코드"
        val currentPrice = intent.getStringExtra("currentPrice") ?: "$0.00"

        // 화면 위젯 연결
        val txtStockName = findViewById<TextView>(R.id.txtStockName)
        val txtCurrentPrice = findViewById<TextView>(R.id.txtCurrentPrice)
        val txtAIAdvice = findViewById<TextView>(R.id.txtAIAdvice)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnShare = findViewById<Button>(R.id.btnShare)

        // 종목명 + 코드 표시
        val codeOnly = stockCode.split("·").firstOrNull()?.trim() ?: stockCode
        txtStockName.text = "$stockName ($codeOnly)"
        txtCurrentPrice.text = currentPrice

        // 뒤로가기
        btnBack.setOnClickListener { finish() }

        // 카카오톡 공유 (나중에 연결)
        btnShare.setOnClickListener {
            Toast.makeText(this, "카카오톡 공유 기능은 추후 연결 예정입니다", Toast.LENGTH_SHORT).show()
        }

        // ===== Gemini에게 투자 조언 요청 =====
        txtAIAdvice.text = "AI가 분석 중입니다..."
        fetchAIAdvice(stockName, txtAIAdvice)
    }

    // 종목명으로 Gemini에게 투자 조언 요청
    private fun fetchAIAdvice(stockName: String, target: TextView) {
        lifecycleScope.launch {
            try {
                val prompt = "$stockName 주식에 대한 투자 조언을 3~4문장으로 알려줘. " +
                        "현재 추세, 상승/하락 가능성, 투자 시 주의점을 포함해서 한국어로 답변해줘."

                val request = GeminiRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt))))
                )

                // 통신은 백그라운드(IO)에서
                val response = withContext(Dispatchers.IO) {
                    GeminiApi.service.generateContent(apiKey, request)
                }

                // 응답에서 text 꺼내기
                val advice = response.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    ?: "분석 결과를 가져오지 못했습니다."

                target.text = advice

            } catch (e: Exception) {
                target.text = "오류가 발생했습니다: ${e.message}"
            }
        }
    }
}