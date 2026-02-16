package com.glucowatch.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 血糖讀數實體（Room）
 */
@Entity(tableName = "glucose_readings")
data class GlucoseReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val glucose: Double,
    val trend: Int,
    val delta: Double,
    val source: String
)

/**
 * 用餐記錄實體（Room）
 */
@Entity(tableName = "meal_records")
data class MealRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val mealType: String,
    val glucoseBefore: Double?,
    val glucoseAfter1h: Double?,
    val glucoseAfter2h: Double?,
    val foodItemsJson: String,  // JSON 格式的食物清單
    val notes: String,
    val photoUri: String?
)

/**
 * 警報歷史實體（Room）
 */
@Entity(tableName = "alarm_history")
data class AlarmHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val alarmType: String,      // LOW, HIGH, CRITICALLY_LOW, CRITICALLY_HIGH
    val glucoseValue: Double,
    val acknowledged: Boolean = false,
    val acknowledgedAt: Long? = null
)

/**
 * 胰島素注射記錄實體（Room）
 */
@Entity(tableName = "insulin_records")
data class InsulinRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val insulinType: String,        // 胰島素類型（枚舉名稱）
    val dosage: Double,             // 劑量
    val injectionSite: String?,     // 注射部位
    val glucoseBeforeInjection: Double?, // 注射前血糖
    val notes: String
)