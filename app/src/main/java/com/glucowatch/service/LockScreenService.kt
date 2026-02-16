package com.glucowatch.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.glucowatch.R
import com.glucowatch.data.repository.GlucoseRepository
import com.glucowatch.domain.model.GlucoseReading
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 鎖屏顯示服務
 * 在鎖屏時顯示最新血糖數值
 */
@AndroidEntryPoint
class LockScreenService : LifecycleService() {
    
    @Inject
    lateinit var glucoseRepository: GlucoseRepository
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    
    private lateinit var tvGlucose: TextView
    private lateinit var tvTrend: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvStatus: TextView
    
    override fun onCreate() {
        super.onCreate()
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 創建鎖屏覆蓋層
        createOverlayView()
        
        // 開始監聽血糖數據
        startGlucoseMonitoring()
    }
    
    /**
     * 創建覆蓋層視圖
     */
    private fun createOverlayView() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.lockscreen_glucose_overlay, null)
        
        // 初始化視圖
        tvGlucose = overlayView!!.findViewById(R.id.tv_glucose_value)
        tvTrend = overlayView!!.findViewById(R.id.tv_trend_arrow)
        tvTime = overlayView!!.findViewById(R.id.tv_last_update)
        tvStatus = overlayView!!.findViewById(R.id.tv_status)
        
        // 設置窗口參數
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            PixelFormat.TRANSLUCENT
        )
        
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 100 // 距離頂部100像素
        
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 開始監聽血糖數據
     */
    private fun startGlucoseMonitoring() {
        lifecycleScope.launch {
            // 實時更新血糖數據
            glucoseRepository.observeLatestReading().collect { result ->
                result.onSuccess { reading ->
                    updateDisplay(reading)
                }.onFailure { error ->
                    showError(error.message ?: "無法讀取血糖數據")
                }
            }
        }
    }
    
    /**
     * 更新顯示
     */
    private fun updateDisplay(reading: GlucoseReading) {
        tvGlucose.text = "${reading.glucose.toInt()}"
        tvTrend.text = reading.trend.arrow
        tvStatus.text = reading.getStatus().description
        
        val timeDiff = System.currentTimeMillis() - reading.timestamp
        val minutes = timeDiff / (60 * 1000)
        tvTime.text = when {
            minutes < 1 -> "剛剛"
            minutes < 60 -> "${minutes}分鐘前"
            else -> "${minutes / 60}小時前"
        }
        
        // 根據血糖狀態改變顏色
        val statusColor = when {
            reading.glucose < 70 -> resources.getColor(android.R.color.holo_red_light, null)
            reading.glucose <= 180 -> resources.getColor(android.R.color.holo_green_light, null)
            else -> resources.getColor(android.R.color.holo_orange_light, null)
        }
        
        tvGlucose.setTextColor(statusColor)
        tvStatus.setTextColor(statusColor)
    }
    
    /**
     * 顯示錯誤
     */
    private fun showError(message: String) {
        tvGlucose.text = "---"
        tvTrend.text = ""
        tvStatus.text = message
        tvTime.text = ""
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 移除覆蓋層
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
    
    companion object {
        const val ACTION_START = "com.glucowatch.service.LockScreenService.START"
        const val ACTION_STOP = "com.glucowatch.service.LockScreenService.STOP"
    }
}