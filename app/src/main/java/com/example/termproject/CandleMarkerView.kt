package com.example.termproject

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.CandleEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF

/**
 * 캔들을 누르면 그 봉의 날짜 + 시/고/저/종을 보여주는 말풍선.
 * 날짜는 CandleEntry 의 x(인덱스)로 candles 리스트에서 찾는다.
 */
class CandleMarkerView(
    context: Context,
    private val candles: List<Candle>,
    private val unit: String
) : MarkerView(context, R.layout.marker_candle) {

    private val tvDate = findViewById<TextView>(R.id.tvMarkerDate)
    private val tvOpen = findViewById<TextView>(R.id.tvMarkerOpen)
    private val tvHigh = findViewById<TextView>(R.id.tvMarkerHigh)
    private val tvLow = findViewById<TextView>(R.id.tvMarkerLow)
    private val tvClose = findViewById<TextView>(R.id.tvMarkerClose)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e is CandleEntry) {
            val date = candles.getOrNull(e.x.toInt())?.date ?: ""
            tvDate.text = date
            tvOpen.text = "시 $unit${fmt(e.open)}"
            tvHigh.text = "고 $unit${fmt(e.high)}"
            tvLow.text = "저 $unit${fmt(e.low)}"
            tvClose.text = "종 $unit${fmt(e.close)}"
        }
        super.refreshContent(e, highlight)
    }

    // 말풍선을 봉 위 중앙에 띄우고, 화면 밖으로 나가면 안쪽으로 당긴다.
    override fun getOffsetForDrawingAtPoint(posX: Float, posY: Float): MPPointF {
        val w = width.toFloat()
        val h = height.toFloat()
        var x = -w / 2f
        var y = -h - 12f

        chartView?.let { chart ->
            if (posX + x < 0f) x = -posX
            else if (posX + x + w > chart.width) x = chart.width - posX - w
            if (posY + y < 0f) y = 12f
        }
        return MPPointF(x, y)
    }

    private fun fmt(v: Float): String =
        if (v == v.toLong().toFloat()) "%,d".format(v.toLong()) else "%,.2f".format(v)
}