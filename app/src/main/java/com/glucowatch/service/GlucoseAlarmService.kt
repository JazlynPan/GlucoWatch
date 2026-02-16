package com.glucowatch.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.glucowatch.R
import com.glucowatch.data.repository.GlucoseRepository
import com.glucowatch.domain.model.GlucoseReading
import com.glucowatch.domain.model.GlucoseStatus
import com.glucowatch.presentation.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 血糖警報服務
 * 監控血糖數值，當出現高低血糖時發送警報
 */
@AndroidEntryPoint
class GlucoseAlarmService : LifecycleService() {
    
    @Inject
    lateinit var glucoseRepository: GlucoseRepository
    
    private var lastAlarmTime = 0L
    private val alarmCooldown = 15 * 60 * 1000L // 15分鐘冷卻時間，避免頻繁警報
    
    private var lowThreshold = 70.0
    private var highThreshold = 180.0
    private var criticalLowThreshold = 54.0
    private var criticalHighThreshold = 250.0
    
    override fun onCreate() {
        super.onCreate()
        
        // 創建通知渠道
        createNotificationChannel()
        
        // 前台服務
        startForeground(NOTIFICATION_ID_SERVICE, createServiceNotification())
        
        // 開始監控血糖
        startGlucoseMonitoring()
    }
    
    /**
     * 創建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_ID_SERVICE,
                    "血糖監控服務",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "持續監控血糖數值"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_ID_ALARM,
                    "血糖警報",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "高低血糖警報通知"
                    enableVibration(true)
                    enableLights(true)
                    setShowBadge(true)
                },
                NotificationChannel(
                    CHANNEL_ID_CRITICAL,
                    "緊急血糖警報",
                    NotificationManager.IMPORTANCE_MAX
                ).apply {
                    description = "嚴重高低血糖警報"
                    enableVibration(true)
                    enableLights(true)
                    setShowBadge(true)
                }
            )
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            channels.forEach { notificationManager?.createNotificationChannel(it) }
        }
    }
    
    /**
     * 創建前台服務通知
     */
    private fun createServiceNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("監康官血糖監控")
            .setContentText("正在監控您的血糖數值")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    /**
     * 開始監控血糖
     */
    private fun startGlucoseMonitoring() {
        lifecycleScope.launch {
            glucoseRepository.observeLatestReading().collect { result ->
                result.onSuccess { reading ->
                    checkAndSendAlarm(reading)
                }
            }
        }
    }
    
    /**
     * 檢查並發送警報
     */
    private fun checkAndSendAlarm(reading: GlucoseReading) {
        val now = System.currentTimeMillis()
        
        // 檢查冷卻時間
        if (now - lastAlarmTime < alarmCooldown) {
            return
        }
        
        val status = reading.getStatus()
        
        when (status) {
            GlucoseStatus.CRITICALLY_LOW -> {
                sendCriticalAlarm(
                    "嚴重低血糖！",
                    "您的血糖只有 ${reading.glucose.toInt()} mg/dL\n立即攝入15g快速碳水！",
                    reading
                )
                vibratePhone(longArrayOf(0, 500, 200, 500, 200, 500))
            }
            
            GlucoseStatus.LOW -> {
                sendAlarm(
                    "低血糖警報",
                    "您的血糖為 ${reading.glucose.toInt()} mg/dL\n建議補充碳水化合物",
                    reading
                )
                vibratePhone(longArrayOf(0, 300, 200, 300))
            }
            
            GlucoseStatus.HIGH -> {
                sendAlarm(
                    "高血糖提醒",
                    "您的血糖為 ${reading.glucose.toInt()} mg/dL\n注意多喝水，檢查胰島素",
                    reading
                )
                vibratePhone(longArrayOf(0, 300, 200, 300))
            }
            
            GlucoseStatus.CRITICALLY_HIGH -> {
                sendCriticalAlarm(
                    "嚴重高血糖！",
                    "您的血糖高達 ${reading.glucose.toInt()} mg/dL\n請立即處理並考慮就醫！",
                    reading
                )
                vibratePhone(longArrayOf(0, 500, 200, 500, 200, 500))
            }
            
            else -> {
                // 血糖正常，不需要警報
            }
        }
    }
    
    /**
     * 發送一般警報
     */
    private fun sendAlarm(title: String, message: String, reading: GlucoseReading) {
        lastAlarmTime = System.currentTimeMillis()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALARM)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .addAction(
                R.drawable.ic_notification,
                "查看詳情",
                pendingIntent
            )
            .build()
        
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(NOTIFICATION_ID_ALARM, notification)
        
        // 發送手錶通知（如果支持）
        sendWearableNotification(title, message)
    }
    
    /**
     * 發送緊急警報
     */
    private fun sendCriticalAlarm(title: String, message: String, reading: GlucoseReading) {
        lastAlarmTime = System.currentTimeMillis()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("SHOW_EMERGENCY", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_CRITICAL)
            .setContentTitle("⚠️ $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true) // 無法劃掉，必須處理
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setColor(resources.getColor(android.R.color.holo_red_dark, null))
            .addAction(
                R.drawable.ic_notification,
                "立即處理",
                pendingIntent
            )
            .build()
        
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(NOTIFICATION_ID_CRITICAL, notification)
        
        // 發送手錶緊急通知
        sendWearableNotification(title, message, isCritical = true)
    }
    
    /**
     * 發送手錶通知
     */
    private fun sendWearableNotification(
        title: String,
        message: String,
        isCritical: Boolean = false
    ) {
        // TODO: 整合 Wear OS 或其他智能手錶 SDK
        // 例如：Google Wear OS, Samsung Galaxy Watch, Apple Watch (需要配對 iPhone)
        
        // 示例代碼（需要添加 Wear OS 依賴）:
        /*
        val dataClient = Wearable.getDataClient(this)
        val putDataReq = PutDataMapRequest.create("/glucose_alarm").apply {
            dataMap.putString("title", title)
            dataMap.putString("message", message)
            dataMap.putBoolean("critical", isCritical)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest()
        
        dataClient.putDataItem(putDataReq)
        */
    }
    
    /**
     * 震動手機
     */
    private fun vibratePhone(pattern: LongArray) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
    
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
    
    companion object {
        private const val CHANNEL_ID_SERVICE = "glucose_monitor_service"
        private const val CHANNEL_ID_ALARM = "glucose_alarm"
        private const val CHANNEL_ID_CRITICAL = "glucose_critical_alarm"
        
        private const val NOTIFICATION_ID_SERVICE = 1001
        private const val NOTIFICATION_ID_ALARM = 1002
        private const val NOTIFICATION_ID_CRITICAL = 1003
    }
}