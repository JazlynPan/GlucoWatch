package com.glucowatch.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 血糖讀數數據模型
 */
@Parcelize
data class GlucoseReading(
    val id: Long = 0,
    val timestamp: Long,           // 時間戳（毫秒）
    val glucose: Double,           // 血糖值 (mg/dL)
    val trend: GlucoseTrend,       // 趨勢
    val delta: Double = 0.0,       // 與上次的差值
    val source: String = "xDrip+"  // 數據來源
) : Parcelable {
    
    /**
     * 獲取血糖狀態
     */
    fun getStatus(): GlucoseStatus {
        return when {
            glucose < 54 -> GlucoseStatus.CRITICALLY_LOW
            glucose < 70 -> GlucoseStatus.LOW
            glucose <= 180 -> GlucoseStatus.NORMAL
            glucose <= 250 -> GlucoseStatus.HIGH
            else -> GlucoseStatus.CRITICALLY_HIGH
        }
    }
    
    /**
     * 是否需要警報
     */
    fun needsAlert(): Boolean {
        return getStatus() != GlucoseStatus.NORMAL
    }
    
    /**
     * 獲取顯示文本
     */
    fun getDisplayText(): String {
        return "${glucose.toInt()} mg/dL ${trend.arrow}"
    }
}

/**
 * 血糖趨勢枚舉
 */
@Parcelize
enum class GlucoseTrend(val value: Int, val arrow: String, val description: String) : Parcelable {
    RAPIDLY_FALLING(-2, "⇊", "快速下降"),
    FALLING(-1, "↓", "下降"),
    STABLE(0, "→", "穩定"),
    RISING(1, "↑", "上升"),
    RAPIDLY_RISING(2, "⇈", "快速上升"),
    UNKNOWN(99, "?", "未知");
    
    companion object {
        fun fromValue(value: Int): GlucoseTrend {
            return values().find { it.value == value } ?: UNKNOWN
        }
    }
}

/**
 * 血糖狀態枚舉
 */
enum class GlucoseStatus(val color: Long, val description: String) {
    CRITICALLY_LOW(0xFFFF0000, "嚴重低血糖"),    // 紅色
    LOW(0xFFFFA500, "低血糖"),                  // 橙色
    NORMAL(0xFF00C853, "正常"),                 // 綠色
    HIGH(0xFFFFA500, "高血糖"),                 // 橙色
    CRITICALLY_HIGH(0xFFFF0000, "嚴重高血糖");  // 紅色
    
    fun isAbnormal(): Boolean {
        return this != NORMAL
    }
}