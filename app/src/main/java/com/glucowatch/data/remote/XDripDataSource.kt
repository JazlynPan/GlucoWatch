package com.glucowatch.data.remote

import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.glucowatch.domain.model.GlucoseReading
import com.glucowatch.domain.model.GlucoseTrend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * xDrip+ 數據源
 * 通過 ContentProvider 讀取 xDrip+ 的血糖數據
 */
@Singleton
class XDripDataSource @Inject constructor(
    private val context: Context
) {
    
    companion object {
        // xDrip+ ContentProvider URI
        private const val XDRIP_PROVIDER_URI = "content://com.eveningoutpost.dexdrip.provider/readings"
        
        // 列名
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_GLUCOSE = "glucose"
        private const val COLUMN_TREND = "trend"
        private const val COLUMN_DELTA = "delta"
        
        // 檢查 xDrip+ 是否已安裝
        fun isXDripInstalled(context: Context): Boolean {
            val packageManager = context.packageManager
            return try {
                packageManager.getPackageInfo("com.eveningoutpost.dexdrip", 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * 獲取最新的血糖讀數
     */
    suspend fun getLatestReading(): Result<GlucoseReading> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(XDRIP_PROVIDER_URI)
            val cursor = context.contentResolver.query(
                uri,
                null,
                null,
                null,
                "$COLUMN_TIMESTAMP DESC LIMIT 1"
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val reading = parseGlucoseReading(it)
                    Result.success(reading)
                } else {
                    Result.failure(Exception("沒有可用的血糖數據"))
                }
            } ?: Result.failure(Exception("無法連接到 xDrip+，請確保 xDrip+ 已安裝並正在運行"))
        } catch (e: Exception) {
            Result.failure(Exception("讀取 xDrip+ 數據失敗: ${e.message}", e))
        }
    }
    
    /**
     * 獲取指定時間範圍內的血糖讀數
     * @param startTime 開始時間（毫秒）
     * @param endTime 結束時間（毫秒）
     */
    suspend fun getReadings(startTime: Long, endTime: Long): Result<List<GlucoseReading>> = 
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(XDRIP_PROVIDER_URI)
                val selection = "$COLUMN_TIMESTAMP >= ? AND $COLUMN_TIMESTAMP <= ?"
                val selectionArgs = arrayOf(startTime.toString(), endTime.toString())
                
                val cursor = context.contentResolver.query(
                    uri,
                    null,
                    selection,
                    selectionArgs,
                    "$COLUMN_TIMESTAMP DESC"
                )
                
                val readings = mutableListOf<GlucoseReading>()
                cursor?.use {
                    while (it.moveToNext()) {
                        readings.add(parseGlucoseReading(it))
                    }
                }
                
                Result.success(readings)
            } catch (e: Exception) {
                Result.failure(Exception("讀取 xDrip+ 歷史數據失敗: ${e.message}", e))
            }
        }
    
    /**
     * 獲取最近 N 條血糖讀數
     */
    suspend fun getRecentReadings(count: Int = 24): Result<List<GlucoseReading>> = 
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(XDRIP_PROVIDER_URI)
                val cursor = context.contentResolver.query(
                    uri,
                    null,
                    null,
                    null,
                    "$COLUMN_TIMESTAMP DESC LIMIT $count"
                )
                
                val readings = mutableListOf<GlucoseReading>()
                cursor?.use {
                    while (it.moveToNext()) {
                        readings.add(parseGlucoseReading(it))
                    }
                }
                
                Result.success(readings)
            } catch (e: Exception) {
                Result.failure(Exception("讀取 xDrip+ 數據失敗: ${e.message}", e))
            }
        }
    
    /**
     * 解析 Cursor 為 GlucoseReading
     */
    private fun parseGlucoseReading(cursor: Cursor): GlucoseReading {
        val timestampIndex = cursor.getColumnIndex(COLUMN_TIMESTAMP)
        val glucoseIndex = cursor.getColumnIndex(COLUMN_GLUCOSE)
        val trendIndex = cursor.getColumnIndex(COLUMN_TREND)
        val deltaIndex = cursor.getColumnIndex(COLUMN_DELTA)
        
        val timestamp = if (timestampIndex >= 0) cursor.getLong(timestampIndex) else System.currentTimeMillis()
        val glucose = if (glucoseIndex >= 0) cursor.getDouble(glucoseIndex) else 0.0
        val trendValue = if (trendIndex >= 0) cursor.getInt(trendIndex) else 0
        val delta = if (deltaIndex >= 0) cursor.getDouble(deltaIndex) else 0.0
        
        return GlucoseReading(
            timestamp = timestamp,
            glucose = glucose,
            trend = GlucoseTrend.fromValue(trendValue),
            delta = delta,
            source = "xDrip+"
        )
    }
    
    /**
     * 檢查 xDrip+ 連接狀態
     */
    suspend fun checkConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!isXDripInstalled(context)) {
                return@withContext Result.failure(Exception("xDrip+ 未安裝"))
            }
            
            val uri = Uri.parse(XDRIP_PROVIDER_URI)
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(COLUMN_TIMESTAMP),
                null,
                null,
                "$COLUMN_TIMESTAMP DESC LIMIT 1"
            )
            
            val isConnected = cursor != null && cursor.count > 0
            cursor?.close()
            
            Result.success(isConnected)
        } catch (e: Exception) {
            Result.failure(Exception("無法連接到 xDrip+: ${e.message}", e))
        }
    }
}