package com.example.termproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 관심 기업 5개 항목 연결
        val itemApple = findViewById<LinearLayout>(R.id.itemApple)
        val itemSamsung = findViewById<LinearLayout>(R.id.itemSamsung)
        val itemTesla = findViewById<LinearLayout>(R.id.itemTesla)
        val itemNvidia = findViewById<LinearLayout>(R.id.itemNvidia)
        val itemKakao = findViewById<LinearLayout>(R.id.itemKakao)

        // 검색 관련
        val editSearch = findViewById<EditText>(R.id.editSearch)
        val btnSearch = findViewById<Button>(R.id.btnSearch)

        // 각 기업 클릭 → 상세화면으로 이동 (종목 정보를 intent에 담아 전달)
        itemApple.setOnClickListener {
            goToDetail("APPLE INC.", "AAPL · 나스닥", "$178.50", "↑ +2.45% (+$4.28) 오늘")
        }
        itemSamsung.setOnClickListener {
            goToDetail("삼성전자", "005930 · KOSPI", "₩71,200", "↓ -0.84% (-₩600) 오늘")
        }
        itemTesla.setOnClickListener {
            goToDetail("TESLA INC.", "TSLA · 나스닥", "$242.84", "↑ +5.12% (+$11.82) 오늘")
        }
        itemNvidia.setOnClickListener {
            goToDetail("NVIDIA CORP.", "NVDA · 나스닥", "$485.09", "↑ +1.78% (+$8.49) 오늘")
        }
        itemKakao.setOnClickListener {
            goToDetail("카카오", "035720 · KOSPI", "₩48,750", "↑ +0.31% (+₩150) 오늘")
        }

        // 검색 버튼 (입력값을 종목명으로 상세화면에 전달)
        btnSearch.setOnClickListener {
            val keyword = editSearch.text.toString()
            if (keyword.isBlank()) {
                Toast.makeText(this, "검색어를 입력하세요", Toast.LENGTH_SHORT).show()
            } else {
                goToDetail(keyword.uppercase(), "검색 결과", "$0.00", "데이터 없음")
            }
        }
    }

    // 상세화면으로 데이터를 담아 이동하는 함수 (intent 전달 ①)
    private fun goToDetail(name: String, code: String, price: String, change: String) {
        val intent = Intent(this, DetailActivity::class.java)
        intent.putExtra("stockName", name)
        intent.putExtra("stockCode", code)
        intent.putExtra("currentPrice", price)
        intent.putExtra("changeRate", change)
        startActivity(intent)
    }
}