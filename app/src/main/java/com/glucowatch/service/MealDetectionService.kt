package com.glucowatch.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.glucowatch.R
import com.glucowatch.data.repository.GlucoseRepository
import com.glucowatch.domain.model.GlucoseReading
import com.glucowatch.domain.model.MealType
import com.glucowatch.presentation.meal.MealRecordActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/**
 * 餐後血糖自動偵測服務
 * 當偵測到血糖上升時，詢問用戶是否在用餐
 */
@AndroidEntryPoint
class MealDetectionService : LifecycleService() {
    
    @Inject
    lateinit var glucoseRepository: GlucoseRepository
    
    private var recentReadings = mutableListOf<GlucoseReading>()
    private val detectionWindow = 6 // 檢測最近6個讀數（約30分鐘）
    private val risingThreshold = 30.0 // 血糖上升30 mg/dL 觸發詢問
    private val risingSpeed = 2.0 // 每分鐘上升 > 2 mg/dL
    
    private var lastAskTime = 0L
    private val askCooldown = 60 * 60 * 1000L // 1小時內不重複詢問
    
    override fun onCreate() {
        super.onCreate()
        
        createNotificationChannel()
        startMealDetection()
    }
    
    /**
     * 創建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "用餐提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "餐後血糖記錄提醒"
                setShowBadge(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    /**
     * 開始餐後偵測
     */
    private fun startMealDetection() {
        lifecycleScope.launch {
            glucoseRepository.observeLatestReading().collect { result ->
                result.onSuccess { reading ->
                    analyzeGlucoseRise(reading)
                }
            }
        }
    }
    
    /**
     * 分析血糖上升
     */
    private fun analyzeGlucoseRise(newReading: GlucoseReading) {
        // 添加新讀數
        recentReadings.add(newReading)
        
        // 只保留最近的讀數
        if (recentReadings.size > detectionWindow) {
            recentReadings.removeAt(0)
        }
        
        // 需要至少3個讀數才能分析
        if (recentReadings.size < 3) {
            return
        }
        
        // 檢查冷卻時間
        val now = System.currentTimeMillis()
        if (now - lastAskTime < askCooldown) {
            return
        }
        
        // 計算血糖上升幅度和速度
        val firstReading = recentReadings.first()
        val lastReading = recentReadings.last()
        
        val glucoseRise = lastReading.glucose - firstReading.glucose
        val timeDiff = (lastReading.timestamp - firstReading.timestamp) / (60 * 1000.0) // 分鐘
        val riseSpeed = if (timeDiff > 0) glucoseRise / timeDiff else 0.0
        
        // 判斷是否為餐後血糖上升
        val isMealLikeRise = glucoseRise >= risingThreshold && 
                             riseSpeed >= risingSpeed &&
                             isConsistentRise()
        
        if (isMealLikeRise) {
            lastAskTime = now
            askUserAboutMeal(lastReading)
        }
    }
    
    /**
     * 檢查是否為持續上升
     */
    private fun isConsistentRise(): Boolean {
        if (recentReadings.size < 3) return false
        
        var risingCount = 0
        for (i in 0 until recentReadings.size - 1) {
            if (recentReadings[i + 1].glucose > recentReadings[i].glucose) {
                risingCount++
            }
        }
        
        // 至少70%的讀數呈上升趨勢
        return risingCount.toDouble() / (recentReadings.size - 1) >= 0.7
    }
    
    /**
     * 詢問用戶是否在用餐
     */
    private fun askUserAboutMeal(reading: GlucoseReading) {
        val now = System.currentTimeMillis()
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        
        // 智能判斷餐次
        val suggestedMealType = when (currentHour) {
            in 6..9 -> MealType.BREAKFAST
            in 10..13 -> MealType.LUNCH
            in 14..17 -> MealType.SNACK
            in 18..21 -> MealType.DINNER
            else -> MealType.SNACK
        }
        
        val glucoseRise = reading.glucose - recentReadings.first().glucose
        
        // 創建記錄用餐的 Intent
        val recordIntent = Intent(this, MealRecordActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("MEAL_TYPE", suggestedMealType.name)
            putExtra("GLUCOSE_BEFORE", recentReadings.first().glucose)
            putExtra("GLUCOSE_AFTER", reading.glucose)
            putExtra("AUTO_DETECTED", true)
        }
        
        val recordPendingIntent = PendingIntent.getActivity(
            this,
            0,
            recordIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // 創建忽略的 Intent
        val dismissIntent = Intent(this, MealDetectionService::class.java).apply {
            action = ACTION_DISMISS
        }
        
        val dismissPendingIntent = PendingIntent.getService(
            this,
            1,
            dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // 創建通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("您正在用餐嗎？🍽️")
            .setContentText(
                "監康官發現您的血糖上升了 ${glucoseRise.toInt()} mg/dL\n" +
                "建議記錄為：${suggestedMealType.displayName}"
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "您的血糖從 ${recentReadings.first().glucose.toInt()} mg/dL " +
                        "上升到 ${reading.glucose.toInt()} mg/dL\n\n" +
                        "監康官建議記錄為：${suggestedMealType.displayName}\n" +
                        "這樣可以幫助您更好地追蹤餐後血糖反應"
                    )
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_meal,
                "是，記錄用餐",
                recordPendingIntent
            )
            .addAction(
                R.drawable.ic_close,
                "不是",
                dismissPendingIntent
            )
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID_MEAL_QUESTION, notification)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        if (intent?.action == ACTION_DISMISS) {
            // 用戶選擇"不是"
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.cancel(NOTIFICATION_ID_MEAL_QUESTION)
        }
        
        return START_STICKY
    }
    
    companion object {
        private const val CHANNEL_ID = "meal_detection"
        private const val NOTIFICATION_ID_MEAL_QUESTION = 2001
        private const val ACTION_DISMISS = "com.glucowatch.service.DISMISS_MEAL_QUESTION"
    }
}