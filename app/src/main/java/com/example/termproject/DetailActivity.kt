package com.example.termproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        // MainActivity에서 intent로 전달받은 데이터 꺼내기
        val stockName = intent.getStringExtra("stockName") ?: "종목명"
        val stockCode = intent.getStringExtra("stockCode") ?: "코드"
        val currentPrice = intent.getStringExtra("currentPrice") ?: "$0.00"
        val changeRate = intent.getStringExtra("changeRate") ?: ""

        // 화면 위젯 연결
        val txtStockName = findViewById<TextView>(R.id.txtStockName)
        val txtStockCode = findViewById<TextView>(R.id.txtStockCode)
        val txtCurrentPrice = findViewById<TextView>(R.id.txtCurrentPrice)
        val txtChangeRate = findViewById<TextView>(R.id.txtChangeRate)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnAIPredict = findViewById<Button>(R.id.btnAIPredict)

        // 전달받은 데이터를 화면에 표시
        txtStockName.text = stockName
        txtStockCode.text = stockCode
        txtCurrentPrice.text = currentPrice
        txtChangeRate.text = changeRate

        // 뒤로가기 버튼 → 이전 화면(Main)으로
        btnBack.setOnClickListener {
            finish()
        }

        // AI 예측 버튼 → 분석화면으로 데이터 담아 이동 (intent 전달 ②)
        btnAIPredict.setOnClickListener {
            val analysisIntent = Intent(this, AnalysisActivity::class.java)
            analysisIntent.putExtra("stockName", stockName)
            analysisIntent.putExtra("stockCode", stockCode)
            analysisIntent.putExtra("currentPrice", currentPrice)
            startActivity(analysisIntent)
        }
    }
}