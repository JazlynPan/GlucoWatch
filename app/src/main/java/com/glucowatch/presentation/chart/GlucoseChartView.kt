package com.glucowatch.presentation.chart

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.glucowatch.domain.model.GlucoseReading
import kotlin.math.max
import kotlin.math.min

/**
 * 血糖趨勢圖表組件
 * 24小時血糖走勢圖，適合 50+ 歲長輩使用
 */
class GlucoseChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // 數據
    private var readings = listOf<GlucoseReading>()
    
    // 繪圖工具
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 0f, 1000f,
            Color.parseColor("#802196F3"),
            Color.parseColor("#002196F3"),
            Shader.TileMode.CLAMP
        )
        style = Paint.Style.FILL
    }
    
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#424242")
        textSize = 36f // 大字體，方便長輩閱讀
        textAlign = Paint.Align.CENTER
    }
    
    private val targetRangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        alpha = 50
        style = Paint.Style.FILL
    }
    
    private val highRangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC107")
        alpha = 50
        style = Paint.Style.FILL
    }
    
    private val lowRangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336")
        alpha = 50
        style = Paint.Style.FILL
    }
    
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
    }
    
    // 血糖範圍設置
    private val targetLow = 70.0
    private val targetHigh = 180.0
    private val criticalHigh = 250.0
    private val criticalLow = 54.0
    
    // 圖表邊距
    private val paddingTop = 80f
    private val paddingBottom = 100f
    private val paddingLeft = 100f
    private val paddingRight = 60f
    
    /**
     * 更新圖表數據
     */
    fun setData(newReadings: List<GlucoseReading>) {
        readings = newReadings.sortedBy { it.timestamp }
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (readings.isEmpty()) {
            drawEmptyState(canvas)
            return
        }
        
        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom
        
        // 1. 繪製背景範圍
        drawGlucoseRanges(canvas, chartWidth, chartHeight)
        
        // 2. 繪製網格線
        drawGrid(canvas, chartWidth, chartHeight)
        
        // 3. 繪製座標軸標籤
        drawLabels(canvas, chartWidth, chartHeight)
        
        // 4. 繪製血糖曲線
        drawGlucoseLine(canvas, chartWidth, chartHeight)
        
        // 5. 繪製數據點
        drawDataPoints(canvas, chartWidth, chartHeight)
        
        // 6. 繪製最新數值標註
        drawCurrentValue(canvas, chartWidth, chartHeight)
    }
    
    /**
     * 繪製空狀態
     */
    private fun drawEmptyState(canvas: Canvas) {
        val message = "暫無血糖數據"
        val hint = "請確保血糖儀正常連接"
        
        textPaint.textSize = 48f
        textPaint.color = Color.GRAY
        canvas.drawText(message, width / 2f, height / 2f - 50f, textPaint)
        
        textPaint.textSize = 36f
        canvas.drawText(hint, width / 2f, height / 2f + 50f, textPaint)
    }
    
    /**
     * 繪製血糖範圍背景
     */
    private fun drawGlucoseRanges(canvas: Canvas, chartWidth: Float, chartHeight: Float) {
        val maxGlucose = 400.0
        val minGlucose = 40.0
        
        // 繪製危險低血糖區域 (< 54)
        val criticalLowY = glucoseToY(criticalLow, chartHeight, maxGlucose, minGlucose)
        val bottomY = paddingTop + chartHeight
        canvas.drawRect(
            paddingLeft,
            criticalLowY,
            paddingLeft + chartWidth,
            bottomY,
            lowRangePaint
        )
        
        // 繪製目標範圍 (70-180)
        val targetLowY = glucoseToY(targetLow, chartHeight, maxGlucose, minGlucose)
        val targetHighY = glucoseToY(targetHigh, chartHeight, maxGlucose, minGlucose)
        canvas.drawRect(
            paddingLeft,
            targetHighY,
            paddingLeft + chartWidth,
            targetLowY,
            targetRangePaint
        )
        
        // 繪製高血糖警告區域 (180-250)
        val criticalHighY = glucoseToY(criticalHigh, chartHeight, maxGlucose, minGlucose)
        canvas.drawRect(
            paddingLeft,
            criticalHighY,
            paddingLeft + chartWidth,
            targetHighY,
            highRangePaint
        )
    }
    
    /**
     * 繪製網格線
     */
    private fun drawGrid(canvas: Canvas, chartWidth: Float, chartHeight: Float) {
        val maxGlucose = 400.0
        val minGlucose = 40.0
        
        // 水平網格線（血糖刻度）
        val glucoseSteps = listOf(50.0, 100.0, 150.0, 200.0, 250.0, 300.0, 350.0)
        glucoseSteps.forEach { glucose ->
            val y = glucoseToY(glucose, chartHeight, maxGlucose, minGlucose)
            canvas.drawLine(
                paddingLeft,
                y,
                paddingLeft + chartWidth,
                y,
                gridPaint
            )
        }
        
        // 垂直網格線（時間刻度）
        val timeSteps = 6 // 每4小時一條線
        repeat(timeSteps + 1) { i ->
            val x = paddingLeft + (chartWidth / timeSteps) * i
            canvas.drawLine(
                x,
                paddingTop,
                x,
                paddingTop + chartHeight,
                gridPaint
            )
        }
    }
    
    /**
     * 繪製座標軸標籤
     */
    private fun drawLabels(canvas: Canvas, chartWidth: Float, chartHeight: Float) {
        val maxGlucose = 400.0
        val minGlucose = 40.0
        
        textPaint.textSize = 36f
        textPaint.color = Color.parseColor("#424242")
        
        // Y軸標籤（血糖值）
        textPaint.textAlign = Paint.Align.RIGHT
        val glucoseSteps = listOf(50.0, 100.0, 150.0, 200.0, 250.0, 300.0, 350.0)
        glucoseSteps.forEach { glucose ->
            val y = glucoseToY(glucose, chartHeight, maxGlucose, minGlucose)
            canvas.drawText(
                "${glucose.toInt()}",
                paddingLeft - 20f,
                y + 12f,
                textPaint
            )
        }
        
        // X軸標籤（時間）
        textPaint.textAlign = Paint.Align.CENTER
        val timeSteps = 6
        val now = System.currentTimeMillis()
        repeat(timeSteps + 1) { i ->
            val x = paddingLeft + (chartWidth / timeSteps) * i
            val timeAgo = (6 - i) * 4 // 24小時前到現在，每格4小時
            val label = if (timeAgo == 0) "現在" else "${timeAgo}小時前"
            canvas.drawText(
                label,
                x,
                paddingTop + chartHeight + 50f,
                textPaint
            )
        }
        
        // 單位標籤
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 32f
        canvas.drawText(
            "mg/dL",
            paddingLeft - 90f,
            paddingTop - 30f,
            textPaint
        )
    }
    
    /**
     * 繪製血糖曲線
     */
    private fun drawGlucoseLine(canvas: Canvas, chartWidth: Float, chartHeight: Float) {
        if (readings.size < 2) return
        
        val maxGlucose = 400.0
        val minGlucose = 40.0
        val now = System.currentTimeMillis()
        val timeRange = 24 * 60 * 60 * 1000L // 24小時
        val startTime = now - timeRange
        
        // 創建路徑
        val linePath = Path()
        val fillPath = Path()
        
        var isFirstPoint = true
        
        readings.forEach { reading ->
            if (reading.timestamp >= startTime) {
                val x = timeToX(reading.timestamp, chartWidth, now, timeRange)
                val y = glucoseToY(reading.glucose, chartHeight, maxGlucose, minGlucose)
                
                if (isFirstPoint) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, paddingTop + chartHeight)
                    fillPath.lineTo(x, y)
                    isFirstPoint = false
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
        }
        
        // 完成填充路徑
        if (!isFirstPoint) {
            val lastReading = readings.last()
            val lastX = timeToX(lastReading.timestamp, chartWidth, now, timeRange)
            fillPath.lineTo(lastX, paddingTop + chartHeight)
            fillPath.close()
        }
        
        // 繪製填充和線條
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
    }
    
    /**
     * 繪製數據點
     */
    private fun drawDataPoints(canvas: Canvas, chartWidth: Float, chartHeight: Float) {
        val maxGlucose = 400.0
        val minGlucose = 40.0
        val now = System.currentTimeMillis()
        val timeRange = 24 * 60 * 60 * 1000L
        val startTime = now - timeRange
        
        readings.forEach { reading ->
            if (reading.timestamp >= startTime) {
                val x = timeToX(reading.timestamp, chartWidth, now, timeRange)
                val y = glucoseToY(reading.glucose, chartHeight, maxGlucose, minGlucose)
                
                // 根據血糖值改變點的顏色
                pointPaint.color = when {
                    reading.glucose < criticalLow -> Color.parseColor("#F44336") // 紅色
                    reading.glucose < targetLow -> Color.parseColor("#FF9800") // 橘色
                    reading.glucose <= targetHigh -> Color.parseColor("#4CAF50") // 綠色
                    reading.glucose <= criticalHigh -> Color.parseColor("#FFC107") // 黃色
                    else -> Color.parseColor("#F44336") // 紅色
                }
                
                canvas.drawCircle(x, y, 10f, pointPaint)
                
                // 外圈白邊
                pointPaint.style = Paint.Style.STROKE
                pointPaint.color = Color.WHITE
                pointPaint.strokeWidth = 4f
                canvas.drawCircle(x, y, 10f, pointPaint)
                pointPaint.style = Paint.Style.FILL
            }
        }
    }
    
    /**
     * 繪製當前血糖值標註
     */
    private fun drawCurrentValue(canvas: Canvas, chartWidth: Float, chartHeight: Float) {
        if (readings.isEmpty()) return
        
        val latestReading = readings.last()
        val maxGlucose = 400.0
        val minGlucose = 40.0
        val now = System.currentTimeMillis()
        val timeRange = 24 * 60 * 60 * 1000L
        
        val x = timeToX(latestReading.timestamp, chartWidth, now, timeRange)
        val y = glucoseToY(latestReading.glucose, chartHeight, maxGlucose, minGlucose)
        
        // 繪製標註框
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 4f, Color.parseColor("#40000000"))
        }
        
        val boxWidth = 200f
        val boxHeight = 100f
        val boxX = if (x > width / 2) x - boxWidth - 40f else x + 40f
        val boxY = y - boxHeight / 2
        
        val boxRect = RectF(boxX, boxY, boxX + boxWidth, boxY + boxHeight)
        canvas.drawRoundRect(boxRect, 20f, 20f, boxPaint)
        
        // 繪製數值
        textPaint.textSize = 56f
        textPaint.color = when {
            latestReading.glucose < targetLow -> Color.parseColor("#F44336")
            latestReading.glucose <= targetHigh -> Color.parseColor("#4CAF50")
            else -> Color.parseColor("#FFC107")
        }
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            "${latestReading.glucose.toInt()}",
            boxX + boxWidth / 2,
            boxY + boxHeight / 2 + 20f,
            textPaint
        )
        
        // 繪製趨勢箭頭
        textPaint.textSize = 40f
        canvas.drawText(
            latestReading.trend.arrow,
            boxX + boxWidth / 2,
            boxY + boxHeight - 10f,
            textPaint
        )
        
        // 繪製連接線
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BDBDBD")
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        
        if (x > width / 2) {
            canvas.drawLine(x, y, boxX + boxWidth, boxY + boxHeight / 2, arrowPaint)
        } else {
            canvas.drawLine(x, y, boxX, boxY + boxHeight / 2, arrowPaint)
        }
    }
    
    /**
     * 時間戳轉換為X坐標
     */
    private fun timeToX(
        timestamp: Long,
        chartWidth: Float,
        now: Long,
        timeRange: Long
    ): Float {
        val ratio = (now - timestamp).toFloat() / timeRange
        return paddingLeft + chartWidth * (1 - ratio)
    }
    
    /**
     * 血糖值轉換為Y坐標
     */
    private fun glucoseToY(
        glucose: Double,
        chartHeight: Float,
        maxGlucose: Double,
        minGlucose: Double
    ): Float {
        val clampedGlucose = max(minGlucose, min(maxGlucose, glucose))
        val ratio = (clampedGlucose - minGlucose) / (maxGlucose - minGlucose)
        return paddingTop + chartHeight * (1 - ratio).toFloat()
    }
}