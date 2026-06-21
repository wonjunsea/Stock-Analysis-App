package com.example.termproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    // 전체 기업 정보 구조 (검색용 별칭 리스트 추가)
    data class Stock(
        val itemId: String,     // 메인 화면 UI ID (빈값이면 관심기업 노출 안됨)
        val name: String,       // 공식 표시명
        val symbol: String,     // 토스 API 심볼 (티커)
        val market: String,     // 시장 분류
        val logoId: String,     // 메인 화면 로고 ImageView ID
        val logoUrl: String,    // 로고 이미지 URL
        val aliases: List<String> // 검색 최적화를 위한 한글/영문 키워드 리스트
    )

    // 학습된 총 13개 기업 마스터 리스트 (itemId가 있는 6개만 첫 화면에 로딩됨)
    private val allTrainedStocks = listOf(
        // 미국 종목 (7개)
        Stock("itemApple",   "APPLE INC.",        "AAPL",   "나스닥", "imgApple",   "https://img.logo.dev/apple.com?token=pk_RSSkExm0R5Sbw_aw34FWSA", listOf("애플", "APPLE", "AAPL")),
        Stock("",            "MICROSOFT CORP.",   "MSFT",   "나스닥", "",           "https://img.logo.dev/microsoft.com?token=pk_RSSkExm0R5Sbw_aw34FWSA", listOf("마이크로소프트", "마소", "MICROSOFT", "MSFT")),
        Stock("itemGoogle",  "ALPHABET (GOOGLE)", "GOOGL",  "나스닥", "imgGoogle",  "https://img.logo.dev/google.com?token=pk_RSSkExm0R5Sbw_aw34FWSA", listOf("구글", "알파벳", "GOOGLE", "GOOGL", "GOOG")),
        Stock("",            "AMAZON.COM INC.",   "AMZN",   "나스닥", "",           "https://img.logo.dev/amazon.com?token=pk_RSSkExm0R5Sbw_aw34FWSA", listOf("아마존", "AMAZON", "AMZN")),
        Stock("itemTesla",   "TESLA INC.",        "TSLA",   "나스닥", "imgTesla",   "https://img.logo.dev/tesla.com?token=pk_RSSkExm0R5Sbw_aw34FWSA", listOf("테슬라", "TESLA", "TSLA")),
        Stock("itemNvidia",  "NVIDIA CORP.",      "NVDA",   "나스닥", "imgNvidia",  "https://img.logo.dev/nvidia.com?token=pk_RSSkExm0R5Sbw_aw34FWSA", listOf("엔비디아", "NVIDIA", "NVDA")),
        Stock("",            "META PLATFORMS",    "META",   "나스닥", "",           "https://img.logo.dev/meta.com?token=pk_RSSkExm0R5Sbw_aw34FWSA", listOf("메타", "페이스북", "META")),

        // 한국 종목 (6개)
        Stock("itemSamsung", "삼성전자",            "005930", "KOSPI",  "imgSamsung", "https://img.logo.dev/samsung.com?token=pk_RSSkExm0R5Sbw_aw34FWSA", listOf("삼성전자", "삼성", "SAMSUNG", "005930")),
        Stock("",            "SK하이닉스",          "000660", "KOSPI",  "",           "https://img.logo.dev/skhynix.com?token=pk_RSSkExm0R5Sbw_aw34FWSA", listOf("SK하이닉스", "하이닉스", "sk하이닉스", "HYNIX", "000660")),
        Stock("",            "NAVER",             "035420", "KOSPI",  "",           "https://img.logo.dev/naver.com?token=pk_RSSkExm0R5Sbw_aw34FWSA", listOf("네이버", "NAVER", "네버", "035420")),
        Stock("itemKakao",   "카카오",              "035720", "KOSPI",  "imgKakao",   "https://img.logo.dev/kakaocorp.com?token=pk_RSSkExm0R5Sbw_aw34FWSA", listOf("카카오", "KAKAO", "035720")),
        Stock("",            "현대자동차",          "005380", "KOSPI",  "",           "https://img.logo.dev/hyundai.com?token=pk_RSSkExm0R5Sbw_aw34FWSA", listOf("현대자동차", "현대차", "현대", "HYUNDAI", "005380")),
        Stock("",            "LG화학",             "051910", "KOSPI",  "",           "https://img.logo.dev/lgchem.com?token=pk_RSSkExm0R5Sbw_aw34FWSA", listOf("LG화학", "엘지화학", "LG화학", "LGCHEM", "051910"))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 화면에 노출할 초기 관심 기업 (itemId가 지정된 것만 필터링)
        val displayStocks = allTrainedStocks.filter { it.itemId.isNotEmpty() }

        // 관심 종목 항목들만 클릭 연결 및 로고 로드
        for (stock in displayStocks) {
            val itemRes = resources.getIdentifier(stock.itemId, "id", packageName)
            val item = findViewById<LinearLayout?>(itemRes)
            item?.setOnClickListener {
                goToDetail(stock.name, "${stock.symbol} · ${stock.market}")
            }

            // Glide로 회사 로고 불러오기
            val logoRes = resources.getIdentifier(stock.logoId, "id", packageName)
            val imgLogo = findViewById<ImageView?>(logoRes)
            if (imgLogo != null) {
                Glide.with(this)
                    .load(stock.logoUrl)
                    .placeholder(android.R.drawable.ic_menu_report_image)
                    .error(android.R.drawable.ic_dialog_alert)
                    .into(imgLogo)
            }
        }

        // 검색 기능 (학습된 13개 종목 전체 매칭 연동)
        val editSearch = findViewById<EditText>(R.id.editSearch)
        val btnSearch = findViewById<Button>(R.id.btnSearch)
        btnSearch.setOnClickListener {
            val keyword = editSearch.text.toString().trim().uppercase()
            if (keyword.isBlank()) {
                Toast.makeText(this, "검색어를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 13개 마스터 리스트에서 입력한 키워드와 연관된 주식이 있는지 찾기
            val matchedStock = allTrainedStocks.find { stock ->
                stock.symbol.uppercase() == keyword ||
                        stock.name.uppercase().contains(keyword) ||
                        stock.aliases.any { alias -> alias.uppercase().contains(keyword) }
            }

            if (matchedStock != null) {
                // 학습된 주식을 찾았다면 관심종목을 클릭했을 때와 완벽하게 똑같은 포맷으로 상세 페이지 이동
                goToDetail(matchedStock.name, "${matchedStock.symbol} · ${matchedStock.market}")
            } else {
                // 리스트에 없는 완전 새로운 주식을 검색했을 때의 기존 예외 처리 유지
                goToDetail(keyword, "$keyword · 검색")
            }
        }

        // 관심 종목 현재가 불러오기
        loadPrices(displayStocks)
    }

    private fun loadPrices(displayStocks: List<Stock>) {
        lifecycleScope.launch {
            for (stock in displayStocks) {
                val priceRes = resources.getIdentifier("${stock.itemId}Price", "id", packageName)
                val changeRes = resources.getIdentifier("${stock.itemId}Change", "id", packageName)
                val txtPrice = findViewById<TextView?>(priceRes)
                val txtChange = findViewById<TextView?>(changeRes)
                val curr = if (stock.symbol.length == 6 && stock.symbol.all { it.isDigit() }) "₩" else "$"

                try {
                    val candles = withContext(Dispatchers.IO) {
                        TossApi.fetchCandles(stock.symbol, interval = "1d", count = 2)
                    }
                    val closes = candles.mapNotNull { it.closePrice?.toFloatOrNull() }
                    if (closes.isNotEmpty()) {
                        val latest = closes.last()
                        val prev = if (closes.size >= 2) closes[closes.size - 2] else latest
                        val change = latest - prev
                        val percent = if (prev != 0f) change / prev * 100f else 0f

                        txtPrice?.text = "$curr${fmt(latest)}"
                        val arrow = if (change >= 0) "↑" else "↓"
                        txtChange?.text = "$arrow %+.2f%%".format(percent)
                        txtChange?.setTextColor(
                            if (change >= 0) 0xFF10B981.toInt() else 0xFFEF4444.toInt()
                        )
                    } else {
                        txtPrice?.text = "-"
                    }
                } catch (e: Exception) {
                    txtPrice?.text = "-"
                    txtChange?.text = ""
                }
            }
        }
    }

    private fun goToDetail(name: String, code: String) {
        val intent = Intent(this, DetailActivity::class.java)
        intent.putExtra("stockName", name)
        intent.putExtra("stockCode", code)
        startActivity(intent)
    }

    private fun fmt(value: Float): String =
        if (value == value.toLong().toFloat()) "%,d".format(value.toLong())
        else "%,.2f".format(value)
}