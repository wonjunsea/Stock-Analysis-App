package com.example.termproject

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.CandleStickChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.CandleData
import com.github.mikephil.charting.data.CandleDataSet
import com.github.mikephil.charting.data.CandleEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 차트 표시용 일봉 한 개 (과거 -> 최신 순) */
data class Candle(
    val date: String,
    val open: Float,
    val high: Float,
    val low: Float,
    val close: Float,
    val volume: Long
)

class DetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val stockName = intent.getStringExtra("stockName") ?: "종목명"
        val stockCode = intent.getStringExtra("stockCode") ?: "코드"
        val symbol = stockCode.split("·").firstOrNull()?.trim() ?: stockCode

        val txtStockName = findViewById<TextView>(R.id.txtStockName)
        val txtStockCode = findViewById<TextView>(R.id.txtStockCode)
        val txtCurrentPrice = findViewById<TextView>(R.id.txtCurrentPrice)
        val txtChangeRate = findViewById<TextView>(R.id.txtChangeRate)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnAIPredict = findViewById<Button>(R.id.btnAIPredict)

        val candleChart = findViewById<CandleStickChart>(R.id.candleChart)
        val chartProgress = findViewById<ProgressBar>(R.id.chartProgress)
        val txtChartStatus = findViewById<TextView>(R.id.txtChartStatus)

        val txtHigh = findViewById<TextView?>(R.id.txtHigh)
        val txtLow = findViewById<TextView?>(R.id.txtLow)
        val txtOpen = findViewById<TextView?>(R.id.txtOpen)
        val txtVolume = findViewById<TextView?>(R.id.txtVolume)

        txtStockName.text = stockName
        txtStockCode.text = stockCode
        txtCurrentPrice.text = "불러오는 중..."
        txtChangeRate.text = ""

        setupChart(candleChart)

        // 토스 캔들 한 번 받아서 차트 + 현재가 + 지표를 모두 채운다.
        chartProgress.visibility = View.VISIBLE
        txtChartStatus.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val candles = withContext(Dispatchers.IO) {
                    TossApi.fetchCandles(symbol, interval = "1d", count = 120).map { c ->
                        Candle(
                            date = (c.timestamp ?: "").take(10),
                            open = c.openPrice?.toFloatOrNull() ?: 0f,
                            high = c.highPrice?.toFloatOrNull() ?: 0f,
                            low = c.lowPrice?.toFloatOrNull() ?: 0f,
                            close = c.closePrice?.toFloatOrNull() ?: 0f,
                            volume = c.volume?.toDoubleOrNull()?.toLong() ?: 0L
                        )
                    }.filter { it.open > 0f && it.close > 0f }
                }

                if (candles.isEmpty()) {
                    txtCurrentPrice.text = "데이터 없음"
                    showChartStatus(chartProgress, txtChartStatus, candleChart, "표시할 차트 데이터가 없습니다.")
                    return@launch
                }

                val curr = unit(symbol)
                val latest = candles.last()
                val prevClose = if (candles.size >= 2) candles[candles.size - 2].close else latest.open
                val change = latest.close - prevClose
                val percent = if (prevClose != 0f) change / prevClose * 100f else 0f
                val arrow = if (change >= 0) "↑" else "↓"

                txtCurrentPrice.text = "$curr${fmt(latest.close)}"
                txtChangeRate.text = "$arrow %+.2f%% (%s%s) 오늘".format(percent, curr, fmt(change))

                txtOpen?.text = "$curr${fmt(latest.open)}"
                txtHigh?.text = "$curr${fmt(latest.high)}"
                txtLow?.text = "$curr${fmt(latest.low)}"
                txtVolume?.text = formatVolume(latest.volume)

                renderCandles(candleChart, candles, curr)
                chartProgress.visibility = View.GONE
                txtChartStatus.visibility = View.GONE
                candleChart.visibility = View.VISIBLE
            } catch (e: Exception) {
                txtCurrentPrice.text = "오류"
                txtChangeRate.text = e.message ?: ""
                showChartStatus(chartProgress, txtChartStatus, candleChart, "차트 오류\n${e.message ?: ""}")
            }
        }

        btnBack.setOnClickListener { finish() }
        btnAIPredict.setOnClickListener {
            val intent = Intent(this, AnalysisActivity::class.java)
            intent.putExtra("stockName", stockName)
            intent.putExtra("stockCode", stockCode)
            intent.putExtra("currentPrice", txtCurrentPrice.text.toString())
            startActivity(intent)
        }
    }

    private fun setupChart(chart: CandleStickChart) {
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setNoDataText("차트 데이터를 불러오는 중…")
        chart.setNoDataTextColor(Color.parseColor("#64748B"))
        chart.setBackgroundColor(Color.TRANSPARENT)

        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.isDoubleTapToZoomEnabled = true
        chart.setDrawGridBackground(false)

        // 캔들을 누르면(또는 손가락을 끌면) 그 봉이 하이라이트되고 말풍선이 뜬다.
        chart.isHighlightPerTapEnabled = true
        chart.isHighlightPerDragEnabled = true

        val labelColor = Color.parseColor("#94A3B8")
        val gridColor = Color.parseColor("#334155")

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            textColor = labelColor
            granularity = 1f
            setAvoidFirstLastClipping(true)
        }
        chart.axisRight.apply {
            textColor = labelColor
            this.gridColor = gridColor
        }
        chart.axisLeft.isEnabled = false
    }

    private fun showChartStatus(
        progress: ProgressBar,
        status: TextView,
        chart: CandleStickChart,
        message: String
    ) {
        progress.visibility = View.GONE
        chart.visibility = View.INVISIBLE
        status.visibility = View.VISIBLE
        status.text = message
    }

    private fun renderCandles(chart: CandleStickChart, candles: List<Candle>, unit: String) {
        val entries = candles.mapIndexed { index, c ->
            CandleEntry(index.toFloat(), c.high, c.low, c.open, c.close)
        }

        val upColor = Color.parseColor("#10B981")
        val downColor = Color.parseColor("#EF4444")

        val dataSet = CandleDataSet(entries, "주가").apply {
            setDrawValues(false)
            shadowWidth = 0.8f
            shadowColorSameAsCandle = true
            increasingColor = upColor
            increasingPaintStyle = Paint.Style.FILL
            decreasingColor = downColor
            decreasingPaintStyle = Paint.Style.FILL
            neutralColor = Color.GRAY
            highLightColor = Color.parseColor("#94A3B8")
        }

        chart.data = CandleData(dataSet)
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(candles.map { it.date })

        // 캔들을 누르면 날짜 + 시/고/저/종을 보여주는 말풍선
        val marker = CandleMarkerView(this, candles, unit)
        marker.chartView = chart
        chart.marker = marker

        chart.setVisibleXRangeMaximum(40f)
        chart.moveViewToX(entries.size.toFloat())
        chart.invalidate()
    }

    // 6자리 숫자면 한국 종목(₩), 아니면 미국 종목($)
    private fun unit(symbol: String): String =
        if (symbol.length == 6 && symbol.all { it.isDigit() }) "₩" else "$"

    private fun fmt(value: Float): String =
        if (value == value.toLong().toFloat()) "%,d".format(value.toLong())
        else "%,.2f".format(value)

    private fun formatVolume(vol: Long): String {
        return when {
            vol >= 1_000_000 -> "%.1fM".format(vol / 1_000_000.0)
            vol >= 1_000 -> "%.1fK".format(vol / 1_000.0)
            else -> vol.toString()
        }
    }
}