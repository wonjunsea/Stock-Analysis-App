package com.example.termproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ============================================================
//  Alpha Vantage - 현재가 + 시가/고가/저가/거래량 (GLOBAL_QUOTE 하나로!)
// ============================================================
data class QuoteResponse(
    @SerializedName("Global Quote") val quote: QuoteData?
)
data class QuoteData(
    @SerializedName("02. open") val open: String,
    @SerializedName("03. high") val high: String,
    @SerializedName("04. low") val low: String,
    @SerializedName("05. price") val price: String,
    @SerializedName("06. volume") val volume: String,
    @SerializedName("09. change") val change: String,
    @SerializedName("10. change percent") val changePercent: String
)
interface QuoteService {
    @GET("query")
    suspend fun getQuote(
        @Query("function") function: String = "GLOBAL_QUOTE",
        @Query("symbol") symbol: String,
        @Query("apikey") apiKey: String
    ): QuoteResponse
}
object QuoteApi {
    private const val BASE_URL = "https://www.alphavantage.co/"
    val service: QuoteService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QuoteService::class.java)
    }
}

// ============================================================
//  상세화면
// ============================================================
class DetailActivity : AppCompatActivity() {

    private val alphaKey = BuildConfig.ALPHA_API_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val stockName = intent.getStringExtra("stockName") ?: "종목명"
        val stockCode = intent.getStringExtra("stockCode") ?: "코드"
        val symbol = stockCode.split("·").firstOrNull()?.trim() ?: stockCode

        // 위젯 연결
        val txtStockName = findViewById<TextView>(R.id.txtStockName)
        val txtStockCode = findViewById<TextView>(R.id.txtStockCode)
        val txtCurrentPrice = findViewById<TextView>(R.id.txtCurrentPrice)
        val txtChangeRate = findViewById<TextView>(R.id.txtChangeRate)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnAIPredict = findViewById<Button>(R.id.btnAIPredict)

        // 주요 지표 4개 (xml에 id 있어야 함, 없으면 null이라 무시됨)
        val txtHigh = findViewById<TextView?>(resources.getIdentifier("txtHigh", "id", packageName))
        val txtLow = findViewById<TextView?>(resources.getIdentifier("txtLow", "id", packageName))
        val txtOpen = findViewById<TextView?>(resources.getIdentifier("txtOpen", "id", packageName))
        val txtVolume = findViewById<TextView?>(resources.getIdentifier("txtVolume", "id", packageName))

        txtStockName.text = stockName
        txtStockCode.text = stockCode
        txtCurrentPrice.text = "불러오는 중..."
        txtChangeRate.text = ""

        var realPrice = "0.00"

        lifecycleScope.launch {
            try {
                val q = withContext(Dispatchers.IO) {
                    QuoteApi.service.getQuote(symbol = symbol, apiKey = alphaKey).quote
                }
                if (q != null) {
                    val price = q.price.toFloatOrNull() ?: 0f
                    val change = q.change.toFloatOrNull() ?: 0f
                    val percent = q.changePercent.replace("%", "")
                    realPrice = "%.2f".format(price)

                    val arrow = if (change >= 0) "↑" else "↓"
                    txtCurrentPrice.text = "$%.2f".format(price)
                    txtChangeRate.text = "$arrow $percent%% (%+.2f) 오늘".format(change)

                    // 주요 지표
                    txtOpen?.text = "$" + (q.open.toFloatOrNull()?.let { "%.2f".format(it) } ?: "-")
                    txtHigh?.text = "$" + (q.high.toFloatOrNull()?.let { "%.2f".format(it) } ?: "-")
                    txtLow?.text = "$" + (q.low.toFloatOrNull()?.let { "%.2f".format(it) } ?: "-")
                    txtVolume?.text = formatVolume(q.volume.toLongOrNull() ?: 0L)
                } else {
                    txtCurrentPrice.text = "데이터 없음"
                    txtChangeRate.text = "(API 한도 초과일 수 있음)"
                }
            } catch (e: Exception) {
                txtCurrentPrice.text = "오류"
                txtChangeRate.text = e.message ?: ""
            }
        }

        btnBack.setOnClickListener { finish() }
        btnAIPredict.setOnClickListener {
            val intent = Intent(this, AnalysisActivity::class.java)
            intent.putExtra("stockName", stockName)
            intent.putExtra("stockCode", stockCode)
            intent.putExtra("currentPrice", "$$realPrice")
            startActivity(intent)
        }
    }

    private fun formatVolume(vol: Long): String {
        return when {
            vol >= 1_000_000 -> "%.1fM".format(vol / 1_000_000.0)
            vol >= 1_000 -> "%.1fK".format(vol / 1_000.0)
            else -> vol.toString()
        }
    }
}