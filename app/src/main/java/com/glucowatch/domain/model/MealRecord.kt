package com.glucowatch.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 用餐記錄數據模型
 */
@Parcelize
data class MealRecord(
    val id: Long = 0,
    val timestamp: Long,                    // 用餐時間
    val mealType: MealType,                 // 餐別
    val glucoseBefore: Double?,             // 餐前血糖
    val glucoseAfter1h: Double? = null,     // 餐後 1 小時血糖
    val glucoseAfter2h: Double? = null,     // 餐後 2 小時血糖
    val foodItems: List<FoodItem> = emptyList(), // 食物清單
    val notes: String = "",                 // 備註
    val photoUri: String? = null            // 照片 URI
) : Parcelable {
    
    /**
     * 獲取血糖峰值
     */
    fun getPeakGlucose(): Double? {
        return listOfNotNull(glucoseBefore, glucoseAfter1h, glucoseAfter2h).maxOrNull()
    }
    
    /**
     * 獲取血糖上升幅度
     */
    fun getGlucoseIncrease(): Double? {
        return glucoseBefore?.let { before ->
            listOfNotNull(glucoseAfter1h, glucoseAfter2h).maxOrNull()?.let { peak ->
                peak - before
            }
        }
    }
    
    /**
     * 是否完整記錄（有餐前和至少一個餐後）
     */
    fun isComplete(): Boolean {
        return glucoseBefore != null && (glucoseAfter1h != null || glucoseAfter2h != null)
    }
}

/**
 * 餐別枚舉
 */
@Parcelize
enum class MealType(val displayName: String, val emoji: String) : Parcelable {
    BREAKFAST("早餐", "🌅"),
    LUNCH("午餐", "☀️"),
    DINNER("晚餐", "🌙"),
    SNACK("點心", "🍪");
    
    companion object {
        fun fromTimestamp(timestamp: Long): MealType {
            val hour = java.util.Calendar.getInstance().apply {
                timeInMillis = timestamp
            }.get(java.util.Calendar.HOUR_OF_DAY)
            
            return when (hour) {
                in 5..10 -> BREAKFAST
                in 11..15 -> LUNCH
                in 16..21 -> DINNER
                else -> SNACK
            }
        }
    }
}

/**
 * 食物項目
 */
@Parcelize
data class FoodItem(
    val name: String,           // 食物名稱
    val category: FoodCategory, // 食物類別
    val carbs: Double? = null,  // 碳水化合物（克）
    val portion: String = ""    // 份量描述
) : Parcelable

/**
 * 食物類別
 */
@Parcelize
enum class FoodCategory(val displayName: String, val emoji: String) : Parcelable {
    GRAIN("主食", "🍚"),
    PROTEIN("蛋白質", "🥩"),
    VEGETABLE("蔬菜", "🥬"),
    FRUIT("水果", "🍎"),
    DAIRY("乳製品", "🥛"),
    SNACK("零食", "🍪"),
    BEVERAGE("飲料", "🥤"),
    OTHER("其他", "🍽️")
}