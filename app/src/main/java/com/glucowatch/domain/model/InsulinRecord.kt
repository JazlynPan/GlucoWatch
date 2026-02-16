package com.glucowatch.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.*

/**
 * 胰島素注射記錄
 * 支持多種類型的胰島素，包括替爾泊肽（Tirzepatide）
 */
@Parcelize
data class InsulinRecord(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val insulinType: InsulinType,           // 胰島素類型
    val dosage: Double,                      // 劑量（單位：mg 或 units）
    val injectionSite: InjectionSite? = null, // 注射部位
    val glucoseBeforeInjection: Double? = null, // 注射前血糖
    val notes: String = ""                   // 備註
) : Parcelable {
    
    /**
     * 獲取預期生效時間
     */
    fun getExpectedOnsetTime(): Long {
        return timestamp + insulinType.onsetTimeMinutes * 60 * 1000L
    }
    
    /**
     * 獲取預期峰值時間
     */
    fun getExpectedPeakTime(): Long {
        return timestamp + insulinType.peakTimeMinutes * 60 * 1000L
    }
    
    /**
     * 獲取預期持續時間結束
     */
    fun getExpectedEndTime(): Long {
        return timestamp + insulinType.durationHours * 60 * 60 * 1000L
    }
    
    /**
     * 檢查現在是否在藥效期內
     */
    fun isActive(): Boolean {
        val now = System.currentTimeMillis()
        return now >= getExpectedOnsetTime() && now <= getExpectedEndTime()
    }
    
    /**
     * 獲取顯示文本
     */
    fun getDisplayText(): String {
        return "${insulinType.displayName} ${dosage}${insulinType.unit}"
    }
}

/**
 * 胰島素類型
 */
@Parcelize
enum class InsulinType(
    val displayName: String,
    val emoji: String,
    val unit: String,              // 單位
    val onsetTimeMinutes: Int,     // 起效時間（分鐘）
    val peakTimeMinutes: Int,      // 峰值時間（分鐘）
    val durationHours: Int,        // 持續時間（小時）
    val description: String
) : Parcelable {
    
    // GLP-1 受體激動劑（替爾泊肽等）
    TIRZEPATIDE(
        displayName = "替爾泊肽",
        emoji = "💉",
        unit = "mg",
        onsetTimeMinutes = 60,        // 約1小時開始起效
        peakTimeMinutes = 24 * 60,    // 24小時達峰
        durationHours = 168,          // 持續約7天（每週一次）
        description = "GLP-1/GIP 雙重激動劑，用於2型糖尿病和體重管理"
    ),
    
    SEMAGLUTIDE(
        displayName = "司美格魯肽",
        emoji = "💉",
        unit = "mg",
        onsetTimeMinutes = 60,
        peakTimeMinutes = 24 * 60,
        durationHours = 168,          // 每週一次
        description = "GLP-1 受體激動劑"
    ),
    
    LIRAGLUTIDE(
        displayName = "利拉魯肽",
        emoji = "💉",
        unit = "mg",
        onsetTimeMinutes = 30,
        peakTimeMinutes = 8 * 60,     // 8-12小時
        durationHours = 24,           // 每日一次
        description = "GLP-1 受體激動劑"
    ),
    
    // 速效胰島素
    RAPID_ACTING(
        displayName = "速效胰島素",
        emoji = "⚡",
        unit = "U",
        onsetTimeMinutes = 15,        // 10-30分鐘
        peakTimeMinutes = 60,         // 1-3小時
        durationHours = 5,            // 3-5小時
        description = "餐前注射，如諾和銳、優泌樂"
    ),
    
    // 短效胰島素
    SHORT_ACTING(
        displayName = "短效胰島素",
        emoji = "🔸",
        unit = "U",
        onsetTimeMinutes = 30,        // 30-60分鐘
        peakTimeMinutes = 150,        // 2-4小時
        durationHours = 8,            // 5-8小時
        description = "餐前注射，如普通胰島素"
    ),
    
    // 中效胰島素
    INTERMEDIATE_ACTING(
        displayName = "中效胰島素",
        emoji = "🔷",
        unit = "U",
        onsetTimeMinutes = 120,       // 1-3小時
        peakTimeMinutes = 480,        // 4-12小時
        durationHours = 18,           // 12-18小時
        description = "如 NPH 胰島素"
    ),
    
    // 長效胰島素
    LONG_ACTING(
        displayName = "長效胰島素",
        emoji = "🔵",
        unit = "U",
        onsetTimeMinutes = 90,        // 1-2小時
        peakTimeMinutes = -1,         // 無明顯峰值
        durationHours = 24,           // 20-24小時或更長
        description = "基礎胰島素，如來得時、諾和平"
    ),
    
    // 超長效胰島素
    ULTRA_LONG_ACTING(
        displayName = "超長效胰島素",
        emoji = "🔵",
        unit = "U",
        onsetTimeMinutes = 120,
        peakTimeMinutes = -1,         // 無峰值
        durationHours = 42,           // 超過42小時
        description = "如德谷胰島素"
    ),
    
    // 預混胰島素
    PREMIXED(
        displayName = "預混胰島素",
        emoji = "🔀",
        unit = "U",
        onsetTimeMinutes = 30,
        peakTimeMinutes = 180,
        durationHours = 16,
        description = "速效/短效與中效的混合"
    );
    
    /**
     * 是否有明顯峰值
     */
    fun hasPeak(): Boolean = peakTimeMinutes > 0
}

/**
 * 注射部位
 */
@Parcelize
enum class InjectionSite(val displayName: String, val emoji: String) : Parcelable {
    ABDOMEN("腹部", "🔴"),
    THIGH("大腿", "🟠"),
    ARM("上臂", "🟡"),
    BUTTOCK("臀部", "🟢");
    
    companion object {
        /**
         * 獲取建議的輪換部位
         * 避免在同一部位連續注射
         */
        fun getRecommendedRotation(lastSite: InjectionSite?): List<InjectionSite> {
            return if (lastSite != null) {
                values().filter { it != lastSite }
            } else {
                values().toList()
            }
        }
    }
}

/**
 * 胰島素對血糖的預期影響
 */
data class InsulinEffect(
    val insulinRecord: InsulinRecord,
    val currentTime: Long = System.currentTimeMillis()
) {
    /**
     * 計算當前時刻的相對影響強度（0-1）
     */
    fun getCurrentEffectStrength(): Double {
        if (!insulinRecord.isActive()) return 0.0
        
        val timeSinceInjection = currentTime - insulinRecord.timestamp
        val minutesSinceInjection = timeSinceInjection / (60 * 1000)
        
        val type = insulinRecord.insulinType
        
        // 如果沒有峰值（長效），使用穩定曲線
        if (!type.hasPeak()) {
            return if (minutesSinceInjection < type.onsetTimeMinutes) {
                minutesSinceInjection.toDouble() / type.onsetTimeMinutes
            } else if (minutesSinceInjection > type.durationHours * 60 - 120) {
                // 最後2小時逐漸減弱
                val minutesToEnd = type.durationHours * 60 - minutesSinceInjection
                minutesToEnd / 120.0
            } else {
                1.0 // 穩定期
            }
        }
        
        // 有峰值的情況，使用拋物線曲線
        return when {
            minutesSinceInjection < type.onsetTimeMinutes -> {
                // 起效階段
                minutesSinceInjection.toDouble() / type.onsetTimeMinutes
            }
            minutesSinceInjection < type.peakTimeMinutes -> {
                // 上升到峰值
                0.5 + 0.5 * (minutesSinceInjection - type.onsetTimeMinutes) / 
                    (type.peakTimeMinutes - type.onsetTimeMinutes)
            }
            else -> {
                // 從峰值下降
                val minutesAfterPeak = minutesSinceInjection - type.peakTimeMinutes
                val totalDeclineTime = type.durationHours * 60 - type.peakTimeMinutes
                1.0 - (minutesAfterPeak.toDouble() / totalDeclineTime)
            }
        }.coerceIn(0.0, 1.0)
    }
    
    /**
     * 獲取效果描述
     */
    fun getEffectDescription(): String {
        val strength = getCurrentEffectStrength()
        return when {
            strength < 0.1 -> "幾乎無效"
            strength < 0.3 -> "開始起效"
            strength < 0.7 -> "逐漸增強"
            strength < 0.9 -> "接近峰值"
            strength >= 0.9 -> "峰值效果"
            else -> "效果減弱"
        }
    }
}