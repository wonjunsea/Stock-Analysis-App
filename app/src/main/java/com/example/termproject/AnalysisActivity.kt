package com.example.termproject

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AnalysisActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)

        // DetailActivity에서 intent로 전달받은 데이터 꺼내기
        val stockName = intent.getStringExtra("stockName") ?: "종목명"
        val stockCode = intent.getStringExtra("stockCode") ?: "코드"
        val currentPrice = intent.getStringExtra("currentPrice") ?: "$0.00"

        // 화면 위젯 연결
        val txtStockName = findViewById<TextView>(R.id.txtStockName)
        val txtCurrentPrice = findViewById<TextView>(R.id.txtCurrentPrice)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnShare = findViewById<Button>(R.id.btnShare)

        // 전달받은 데이터 표시 (종목명 + 코드만 표시)
        // stockCode가 "AAPL · 나스닥" 형태이므로 앞부분만 잘라서 표시
        val codeOnly = stockCode.split("·").firstOrNull()?.trim() ?: stockCode
        txtStockName.text = "$stockName ($codeOnly)"
        txtCurrentPrice.text = currentPrice

        // 뒤로가기 버튼 → 이전 화면(Detail)으로
        btnBack.setOnClickListener {
            finish()
        }

        // 카카오톡 공유 버튼 (지금은 안내 메시지만 / 나중에 Kakao SDK 연결)
        btnShare.setOnClickListener {
            Toast.makeText(this, "카카오톡 공유 기능은 추후 연결 예정입니다", Toast.LENGTH_SHORT).show()
        }
    }
}