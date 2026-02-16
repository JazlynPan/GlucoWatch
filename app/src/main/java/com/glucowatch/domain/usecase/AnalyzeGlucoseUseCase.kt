package com.glucowatch.domain.usecase

import com.glucowatch.domain.model.GlucoseReading
import com.glucowatch.domain.model.InsulinRecord
import com.glucowatch.domain.model.MealRecord
import javax.inject.Inject

/**
 * AI 血糖分析診斷用例
 * 模擬專業醫生分析血糖數據
 */
class AnalyzeGlucoseUseCase @Inject constructor() {
    
    /**
     * 完整分析血糖數據
     */
    suspend fun analyze(
        currentGlucose: GlucoseReading,
        recentReadings: List<GlucoseReading>,
        recentInsulin: List<InsulinRecord>,
        recentMeals: List<MealRecord>
    ): GlucoseAnalysisResult {
        
        // 1. 基礎數值分析
        val valueAnalysis = analyzeCurrentValue(currentGlucose)
        
        // 2. 趨勢分析
        val trendAnalysis = analyzeTrend(recentReadings)
        
        // 3. 波動分析
        val variabilityAnalysis = analyzeVariability(recentReadings)
        
        // 4. 胰島素效果分析
        val insulinEffect = analyzeInsulinEffect(recentReadings, recentInsulin)
        
        // 5. 餐後血糖分析
        val mealEffect = analyzeMealEffect(recentReadings, recentMeals)
        
        // 6. 生成診斷建議
        val diagnosis = generateDiagnosis(
            valueAnalysis,
            trendAnalysis,
            variabilityAnalysis,
            insulinEffect,
            mealEffect
        )
        
        return GlucoseAnalysisResult(
            currentValue = valueAnalysis,
            trend = trendAnalysis,
            variability = variabilityAnalysis,
            insulinEffect = insulinEffect,
            mealEffect = mealEffect,
            diagnosis = diagnosis,
            recommendations = generateRecommendations(diagnosis)
        )
    }
    
    /**
     * 1. 當前數值分析
     */
    private fun analyzeCurrentValue(reading: GlucoseReading): ValueAnalysis {
        val value = reading.glucose
        val status = reading.getStatus()
        
        val message = when {
            value < 54 -> "⚠️ 嚴重低血糖！立即處理！"
            value < 70 -> "⚠️ 低血糖，需要補充碳水化合物"
            value < 100 -> "✓ 血糖正常偏低，狀態良好"
            value <= 140 -> "✓ 血糖正常，控制得很好"
            value <= 180 -> "✓ 血糖正常偏高，注意飲食"
            value <= 250 -> "⚠️ 高血糖，可能需要補充胰島素"
            else -> "⚠️ 嚴重高血糖！立即就醫！"
        }
        
        val severity = when {
            value < 54 || value > 300 -> Severity.CRITICAL
            value < 70 || value > 250 -> Severity.HIGH
            value < 100 || value > 180 -> Severity.MEDIUM
            else -> Severity.NORMAL
        }
        
        return ValueAnalysis(
            value = value,
            status = status,
            message = message,
            severity = severity
        )
    }
    
    /**
     * 2. 趨勢分析（最近3小時）
     */
    private fun analyzeTrend(readings: List<GlucoseReading>): TrendAnalysis {
        if (readings.size < 3) {
            return TrendAnalysis(
                direction = "數據不足",
                speed = 0.0,
                message = "需要更多數據才能分析趨勢",
                concern = false
            )
        }
        
        // 計算趨勢方向和速度
        val recent = readings.takeLast(12) // 最近1小時（每5分鐘一個點）
        val avgChange = recent.zipWithNext { a, b -> 
            (b.glucose - a.glucose) / ((b.timestamp - a.timestamp) / (60 * 1000.0))
        }.average() // mg/dL per minute
        
        val hourlyChange = avgChange * 60 // mg/dL per hour
        
        val direction = when {
            avgChange > 2.0 -> "快速上升 ⇈"
            avgChange > 1.0 -> "上升 ↑"
            avgChange > -1.0 -> "穩定 →"
            avgChange > -2.0 -> "下降 ↓"
            else -> "快速下降 ⇊"
        }
        
        val message = when {
            avgChange > 3.0 -> """
                血糖快速上升中（${hourlyChange.toInt()} mg/dL/小時）
                
                可能原因：
                • 剛用餐，碳水化合物吸收中
                • 胰島素劑量不足
                • 壓力或疾病影響
                
                建議：
                • 如果餐後不到2小時，這是正常的
                • 如果超過2小時仍在上升，可能需要補充修正劑量
                • 多喝水，適度活動
            """.trimIndent()
            
            avgChange < -3.0 -> """
                ⚠️ 血糖快速下降中（${hourlyChange.toInt()} mg/dL/小時）
                
                可能原因：
                • 胰島素劑量過多
                • 用餐時間延遲
                • 運動量過大
                
                建議：
                • 準備快速碳水（如果汁、糖果）
                • 密切監控，預防低血糖
                • 如果低於70，立即補充15g碳水
            """.trimIndent()
            
            avgChange.absoluteValue < 1.0 -> """
                血糖穩定（變化 ${hourlyChange.toInt()} mg/dL/小時）
                
                ✓ 很好的控制！
                • 胰島素劑量合適
                • 飲食控制得當
                • 繼續保持現在的管理方式
            """.trimIndent()
            
            else -> """
                血糖${if (avgChange > 0) "緩慢上升" else "緩慢下降"}（${hourlyChange.toInt()} mg/dL/小時）
                
                屬於正常波動範圍
                • 繼續觀察
                • 保持現有管理方式
            """.trimIndent()
        }
        
        return TrendAnalysis(
            direction = direction,
            speed = hourlyChange,
            message = message,
            concern = avgChange.absoluteValue > 3.0
        )
    }
    
    /**
     * 3. 血糖波動分析（變異性）
     */
    private fun analyzeVariability(readings: List<GlucoseReading>): VariabilityAnalysis {
        if (readings.size < 24) { // 至少需要2小時數據
            return VariabilityAnalysis(
                standardDeviation = 0.0,
                coefficientOfVariation = 0.0,
                message = "數據不足，無法分析波動性",
                quality = "未知"
            )
        }
        
        val values = readings.map { it.glucose }
        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        val sd = sqrt(variance)
        val cv = (sd / mean) * 100
        
        val quality = when {
            cv < 36 -> "優秀" to "血糖控制穩定"
            cv < 50 -> "良好" to "血糖波動在可接受範圍"
            else -> "需改善" to "血糖波動較大"
        }
        
        val message = """
            變異係數（CV）：${cv.toInt()}%
            標準差（SD）：${sd.toInt()} mg/dL
            
            評估：${quality.second}
            
            ${when {
                cv < 36 -> """
                    ✓ 優秀的血糖控制！
                    • 血糖波動小，穩定性好
                    • 說明胰島素劑量和飲食控制得當
                    • 繼續保持現有管理方式
                """.trimIndent()
                
                cv < 50 -> """
                    良好的血糖控制
                    • 血糖有一定波動，但在可接受範圍
                    • 可以考慮：
                      - 調整餐前胰島素劑量
                      - 更均衡的飲食分配
                      - 規律的運動時間
                """.trimIndent()
                
                else -> """
                    ⚠️ 血糖波動較大，需要改善
                    
                    建議措施：
                    • 記錄詳細的飲食日記
                    • 調整胰島素劑量（諮詢醫生）
                    • 避免高 GI 食物
                    • 規律作息和運動
                    • 考慮使用胰島素泵
                """.trimIndent()
            }}
        """.trimIndent()
        
        return VariabilityAnalysis(
            standardDeviation = sd,
            coefficientOfVariation = cv,
            message = message,
            quality = quality.first
        )
    }
    
    /**
     * 4. 胰島素效果分析
     */
    private fun analyzeInsulinEffect(
        readings: List<GlucoseReading>,
        insulinRecords: List<InsulinRecord>
    ): InsulinEffectAnalysis {
        if (insulinRecords.isEmpty()) {
            return InsulinEffectAnalysis(
                hasActiveInsulin = false,
                effectiveness = 0.0,
                message = "暫無胰島素注射記錄"
            )
        }
        
        // 找出活性胰島素
        val activeInsulin = insulinRecords.filter { it.isActive() }
        
        if (activeInsulin.isEmpty()) {
            return InsulinEffectAnalysis(
                hasActiveInsulin = false,
                effectiveness = 0.0,
                message = "目前沒有活性胰島素"
            )
        }
        
        // 分析最近一次注射的效果
        val lastInsulin = insulinRecords.maxByOrNull { it.timestamp }!!
        val readingsAfterInsulin = readings.filter { 
            it.timestamp > lastInsulin.timestamp 
        }
        
        if (readingsAfterInsulin.size < 3) {
            return InsulinEffectAnalysis(
                hasActiveInsulin = true,
                effectiveness = 0.0,
                message = """
                    剛注射 ${lastInsulin.insulinType.displayName} ${lastInsulin.dosage}${lastInsulin.insulinType.unit}
                    
                    預期起效時間：${lastInsulin.insulinType.onsetTimeMinutes}分鐘後
                    預期峰值時間：${lastInsulin.insulinType.peakTimeMinutes / 60}小時後
                    
                    請耐心等待胰島素發揮作用
                """.trimIndent()
            )
        }
        
        // 計算血糖下降幅度
        val glucoseBeforeInsulin = readings
            .filter { it.timestamp <= lastInsulin.timestamp }
            .maxByOrNull { it.timestamp }?.glucose ?: 0.0
            
        val currentGlucose = readingsAfterInsulin.last().glucose
        val drop = glucoseBeforeInsulin - currentGlucose
        val timeSinceInsulin = (System.currentTimeMillis() - lastInsulin.timestamp) / (60 * 1000) // 分鐘
        
        val expectedDrop = lastInsulin.dosage * 50 // 假設 ISF = 1:50
        val effectiveness = if (expectedDrop > 0) (drop / expectedDrop) * 100 else 0.0
        
        val message = """
            最近注射：${lastInsulin.insulinType.displayName} ${lastInsulin.dosage}${lastInsulin.insulinType.unit}
            注射時間：${timeSinceInsulin.toInt()}分鐘前
            
            效果分析：
            • 注射前血糖：${glucoseBeforeInsulin.toInt()} mg/dL
            • 當前血糖：${currentGlucose.toInt()} mg/dL
            • 血糖下降：${drop.toInt()} mg/dL
            • 效果評估：${when {
                effectiveness > 80 -> "✓ 效果良好"
                effectiveness > 50 -> "效果一般"
                else -> "⚠️ 效果不佳"
            }}
            
            ${when {
                effectiveness > 80 -> """
                    胰島素發揮作用正常
                    • 劑量合適
                    • 繼續觀察
                """.trimIndent()
                
                effectiveness > 50 -> """
                    胰島素效果一般
                    • 可能需要增加劑量
                    • 或改善注射技巧
                    • 建議諮詢醫生
                """.trimIndent()
                
                effectiveness > 20 -> """
                    ⚠️ 胰島素效果不佳
                    
                    可能原因：
                    • 劑量不足
                    • 注射部位吸收不良
                    • 胰島素失效（過期或保存不當）
                    • 身體處於應激狀態（生病、壓力）
                    
                    建議：
                    • 檢查胰島素保存狀況
                    • 更換注射部位
                    • 諮詢醫生調整劑量
                """.trimIndent()
                
                else -> """
                    ⚠️ 血糖未下降或持續上升
                    
                    這是異常情況，可能原因：
                    • 嚴重胰島素抵抗
                    • 胰島素失效
                    • 劑量嚴重不足
                    • 身體處於應激狀態
                    • 同時有高碳水攝入
                    
                    建議立即：
                    • 檢查血酮（排除 DKA）
                    • 諮詢醫生
                    • 考慮補充修正劑量
                """.trimIndent()
            }}
        """.trimIndent()
        
        return InsulinEffectAnalysis(
            hasActiveInsulin = true,
            effectiveness = effectiveness,
            message = message
        )
    }
    
    /**
     * 5. 餐後血糖分析
     */
    private fun analyzeMealEffect(
        readings: List<GlucoseReading>,
        meals: List<MealRecord>
    ): MealEffectAnalysis {
        if (meals.isEmpty()) {
            return MealEffectAnalysis(
                hasRecentMeal = false,
                peakValue = 0.0,
                message = "暫無用餐記錄"
            )
        }
        
        val lastMeal = meals.maxByOrNull { it.timestamp }!!
        val timeSinceMeal = (System.currentTimeMillis() - lastMeal.timestamp) / (60 * 1000) // 分鐘
        
        if (timeSinceMeal > 180) { // 超過3小時
            return MealEffectAnalysis(
                hasRecentMeal = false,
                peakValue = 0.0,
                message = "最近沒有用餐"
            )
        }
        
        val readingsAfterMeal = readings.filter { 
            it.timestamp > lastMeal.timestamp 
        }
        
        if (readingsAfterMeal.isEmpty()) {
            return MealEffectAnalysis(
                hasRecentMeal = true,
                peakValue = 0.0,
                message = "剛用餐，等待血糖反應"
            )
        }
        
        val glucoseBeforeMeal = lastMeal.glucoseBefore ?: 
            readings.filter { it.timestamp <= lastMeal.timestamp }
                .maxByOrNull { it.timestamp }?.glucose ?: 0.0
                
        val peakGlucose = readingsAfterMeal.maxOf { it.glucose }
        val currentGlucose = readingsAfterMeal.last().glucose
        val increase = peakGlucose - glucoseBeforeMeal
        
        val message = """
            最近用餐：${lastMeal.mealType.displayName}
            時間：${timeSinceMeal.toInt()}分鐘前
            碳水攝入：${lastMeal.foodItems.sumOf { it.carbs ?: 0.0 }.toInt()}g
            
            血糖反應：
            • 餐前血糖：${glucoseBeforeMeal.toInt()} mg/dL
            • 峰值血糖：${peakGlucose.toInt()} mg/dL
            • 當前血糖：${currentGlucose.toInt()} mg/dL
            • 血糖上升：${increase.toInt()} mg/dL
            
            評估：${when {
                increase < 30 -> """
                    ✓ 優秀的餐後血糖控制！
                    • 血糖上升幅度小
                    • 胰島素劑量合適
                    • 飲食選擇得當
                """.trimIndent()
                
                increase < 50 -> """
                    ✓ 良好的餐後血糖控制
                    • 血糖上升在可接受範圍
                    • 繼續保持
                """.trimIndent()
                
                increase < 80 -> """
                    餐後血糖略高
                    
                    改善建議：
                    • 考慮增加餐前胰島素劑量
                    • 選擇低 GI 食物
                    • 餐後適度活動
                """.trimIndent()
                
                else -> """
                    ⚠️ 餐後血糖上升過多
                    
                    可能原因：
                    • 碳水攝入過多
                    • 胰島素劑量不足
                    • 高 GI 食物影響
                    
                    建議：
                    • 調整胰島素碳水比（ICR）
                    • 減少單次碳水攝入
                    • 選擇低 GI 食物
                    • 餐後散步15-30分鐘
                    • 諮詢醫生調整方案
                """.trimIndent()
            }}
            
            ${if (timeSinceMeal < 120) """
                注意：距離用餐還不到2小時，血糖可能仍在上升中
            """.trimIndent() else ""}
        """.trimIndent()
        
        return MealEffectAnalysis(
            hasRecentMeal = true,
            peakValue = peakGlucose,
            message = message
        )
    }
    
    /**
     * 6. 生成綜合診斷
     */
    private fun generateDiagnosis(
        value: ValueAnalysis,
        trend: TrendAnalysis,
        variability: VariabilityAnalysis,
        insulin: InsulinEffectAnalysis,
        meal: MealEffectAnalysis
    ): String {
        val concerns = mutableListOf<String>()
        val achievements = mutableListOf<String>()
        
        // 收集問題和優點
        if (value.severity >= Severity.HIGH) {
            concerns.add("當前血糖${if (value.value < 70) "過低" else "過高"}")
        } else if (value.severity == Severity.NORMAL) {
            achievements.add("血糖值在目標範圍內")
        }
        
        if (trend.concern) {
            concerns.add("血糖變化速度較快")
        }
        
        if (variability.quality == "需改善") {
            concerns.add("血糖波動較大")
        } else if (variability.quality == "優秀") {
            achievements.add("血糖控制穩定")
        }
        
        // 生成診斷報告
        return buildString {
            appendLine("📊 綜合診斷報告")
            appendLine("=" .repeat(40))
            appendLine()
            
            if (achievements.isNotEmpty()) {
                appendLine("✓ 做得好的方面：")
                achievements.forEach { appendLine("  • $it") }
                appendLine()
            }
            
            if (concerns.isNotEmpty()) {
                appendLine("⚠️ 需要關注的問題：")
                concerns.forEach { appendLine("  • $it") }
                appendLine()
            } else {
                appendLine("✓ 目前血糖控制良好，繼續保持！")
                appendLine()
            }
            
            appendLine("詳細分析請查看各項指標說明")
        }
    }
    
    /**
     * 7. 生成個性化建議
     */
    private fun generateRecommendations(diagnosis: String): List<String> {
        // TODO: 根據診斷生成具體的行動建議
        return listOf(
            "定期檢測血糖（每天至少4-6次）",
            "保持規律的飲食時間",
            "記錄每餐的碳水攝入量",
            "餐後適度運動（散步15-30分鐘）",
            "保持充足睡眠",
            "定期檢查胰島素保存狀況"
        )
    }
}

// 輔助函數
private fun Double.pow(n: Int): Double = Math.pow(this, n.toDouble())
private fun sqrt(value: Double): Double = Math.sqrt(value)
private val Double.absoluteValue: Double get() = Math.abs(this)

/**
 * 分析結果數據類
 */
data class GlucoseAnalysisResult(
    val currentValue: ValueAnalysis,
    val trend: TrendAnalysis,
    val variability: VariabilityAnalysis,
    val insulinEffect: InsulinEffectAnalysis,
    val mealEffect: MealEffectAnalysis,
    val diagnosis: String,
    val recommendations: List<String>
)

data class ValueAnalysis(
    val value: Double,
    val status: com.glucowatch.domain.model.GlucoseStatus,
    val message: String,
    val severity: Severity
)

data class TrendAnalysis(
    val direction: String,
    val speed: Double, // mg/dL per hour
    val message: String,
    val concern: Boolean
)

data class VariabilityAnalysis(
    val standardDeviation: Double,
    val coefficientOfVariation: Double,
    val message: String,
    val quality: String // "優秀", "良好", "需改善"
)

data class InsulinEffectAnalysis(
    val hasActiveInsulin: Boolean,
    val effectiveness: Double, // 0-100%
    val message: String
)

data class MealEffectAnalysis(
    val hasRecentMeal: Boolean,
    val peakValue: Double,
    val message: String
)

enum class Severity {
    NORMAL,
    MEDIUM,
    HIGH,
    CRITICAL
}