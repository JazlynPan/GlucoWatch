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
 * 多數據源血糖數據讀取器
 * 支持：xDrip+, Glimp, Dexcom Share, LibreLinkUp 等
 */
@Singleton
class MultiSourceGlucoseDataSource @Inject constructor(
    private val context: Context
) {
    
    companion object {
        // xDrip+ ContentProvider
        private const val XDRIP_URI = "content://com.eveningoutpost.dexdrip.provider/readings"
        
        // Glimp (另一個 Libre 讀取器)
        private const val GLIMP_URI = "content://it.ct.glicemia.provider/glucose"
        
        // 列名
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_GLUCOSE = "glucose"
        private const val COLUMN_TREND = "trend"
        private const val COLUMN_DELTA = "delta"
    }
    
    /**
     * 檢測可用的數據源
     */
    suspend fun detectAvailableSource(): DataSource = withContext(Dispatchers.IO) {
        // 1. 檢查 xDrip+
        if (isSourceAvailable(XDRIP_URI)) {
            return@withContext DataSource.XDRIP
        }
        
        // 2. 檢查 Glimp
        if (isSourceAvailable(GLIMP_URI)) {
            return@withContext DataSource.GLIMP
        }
        
        // 3. 預設為 xDrip+（建議用戶安裝）
        DataSource.XDRIP
    }
    
    /**
     * 從可用數據源獲取最新血糖
     */
    suspend fun getLatestReading(): Result<GlucoseReading> = withContext(Dispatchers.IO) {
        val source = detectAvailableSource()
        
        try {
            when (source) {
                DataSource.XDRIP -> readFromXDrip()
                DataSource.GLIMP -> readFromGlimp()
                DataSource.DEXCOM_SHARE -> Result.failure(Exception("Dexcom Share 需要網絡連接"))
                DataSource.LIBRE_LINK_UP -> Result.failure(Exception("LibreLinkUp 需要額外配置"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("讀取血糖數據失敗: ${e.message}", e))
        }
    }
    
    /**
     * 從 xDrip+ 讀取
     */
    private fun readFromXDrip(): Result<GlucoseReading> {
        try {
            val uri = Uri.parse(XDRIP_URI)
            val cursor = context.contentResolver.query(
                uri,
                null,
                null,
                null,
                "$COLUMN_TIMESTAMP DESC LIMIT 1"
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val reading = parseGlucoseReading(it, DataSource.XDRIP)
                    return Result.success(reading)
                }
            }
            
            return Result.failure(Exception("xDrip+ 沒有血糖數據"))
        } catch (e: Exception) {
            return Result.failure(Exception("無法連接到 xDrip+", e))
        }
    }
    
    /**
     * 從 Glimp 讀取
     */
    private fun readFromGlimp(): Result<GlucoseReading> {
        try {
            val uri = Uri.parse(GLIMP_URI)
            val cursor = context.contentResolver.query(
                uri,
                null,
                null,
                null,
                "$COLUMN_TIMESTAMP DESC LIMIT 1"
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val reading = parseGlucoseReading(it, DataSource.GLIMP)
                    return Result.success(reading)
                }
            }
            
            return Result.failure(Exception("Glimp 沒有血糖數據"))
        } catch (e: Exception) {
            return Result.failure(Exception("無法連接到 Glimp", e))
        }
    }
    
    /**
     * 獲取歷史數據
     */
    suspend fun getReadingsInRange(
        startTime: Long,
        endTime: Long
    ): Result<List<GlucoseReading>> = withContext(Dispatchers.IO) {
        val source = detectAvailableSource()
        val uri = when (source) {
            DataSource.XDRIP -> XDRIP_URI
            DataSource.GLIMP -> GLIMP_URI
            else -> return@withContext Result.failure(Exception("不支持的數據源"))
        }
        
        try {
            val readings = mutableListOf<GlucoseReading>()
            val selection = "$COLUMN_TIMESTAMP >= ? AND $COLUMN_TIMESTAMP <= ?"
            val selectionArgs = arrayOf(startTime.toString(), endTime.toString())
            
            val cursor = context.contentResolver.query(
                Uri.parse(uri),
                null,
                selection,
                selectionArgs,
                "$COLUMN_TIMESTAMP ASC"
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    readings.add(parseGlucoseReading(it, source))
                }
            }
            
            Result.success(readings)
        } catch (e: Exception) {
            Result.failure(Exception("讀取歷史數據失敗: ${e.message}", e))
        }
    }
    
    /**
     * 檢查數據源是否可用
     */
    private fun isSourceAvailable(uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(COLUMN_TIMESTAMP),
                null,
                null,
                "$COLUMN_TIMESTAMP DESC LIMIT 1"
            )
            val available = cursor != null && cursor.count > 0
            cursor?.close()
            available
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 解析 Cursor 為 GlucoseReading
     */
    private fun parseGlucoseReading(cursor: Cursor, source: DataSource): GlucoseReading {
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
            source = source.displayName
        )
    }
}

/**
 * 支持的數據源
 */
enum class DataSource(val displayName: String, val packageName: String) {
    XDRIP("xDrip+", "com.eveningoutpost.dexdrip"),
    GLIMP("Glimp", "it.ct.glicemia"),
    DEXCOM_SHARE("Dexcom Share", "com.dexcom.cgm"),
    LIBRE_LINK_UP("LibreLinkUp", "com.abbottdiabetescare.librelink");
    
    companion object {
        /**
         * 檢查應用是否已安裝
         */
        fun isInstalled(context: Context, source: DataSource): Boolean {
            return try {
                context.packageManager.getPackageInfo(source.packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}