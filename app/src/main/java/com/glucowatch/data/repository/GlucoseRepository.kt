package com.glucowatch.data.repository

import com.glucowatch.data.local.GlucoseReadingDao
import com.glucowatch.data.local.GlucoseReadingEntity
import com.glucowatch.data.remote.XDripDataSource
import com.glucowatch.domain.model.GlucoseReading
import com.glucowatch.domain.model.GlucoseTrend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 血糖數據倉庫
 * 負責協調 xDrip+ 數據源和本地數據庫
 */
@Singleton
class GlucoseRepository @Inject constructor(
    private val xDripDataSource: XDripDataSource,
    private val glucoseReadingDao: GlucoseReadingDao
) {
    
    /**
     * 獲取最新血糖讀數（優先從 xDrip+ 讀取，同時保存到本地）
     */
    suspend fun getLatestReading(): Result<GlucoseReading> {
        return try {
            // 從 xDrip+ 讀取最新數據
            val result = xDripDataSource.getLatestReading()
            
            result.onSuccess { reading ->
                // 保存到本地數據庫
                glucoseReadingDao.insert(reading.toEntity())
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 獲取最近的血糖讀數
     */
    fun getRecentReadings(count: Int = 24): Flow<List<GlucoseReading>> {
        return glucoseReadingDao.getRecentReadings(count).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    /**
     * 獲取指定時間範圍內的血糖讀數
     */
    fun getReadingsInRange(startTime: Long, endTime: Long): Flow<List<GlucoseReading>> {
        return glucoseReadingDao.getReadingsInRange(startTime, endTime).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    /**
     * 觀察最新血糖讀數
     */
    fun observeLatestReading(): Flow<Result<GlucoseReading>> {
        return glucoseReadingDao.getRecentReadings(1).map { entities ->
            if (entities.isNotEmpty()) {
                Result.success(entities.first().toDomainModel())
            } else {
                Result.failure(Exception("No glucose data available"))
            }
        }
    }
    
    /**
     * 從 xDrip+ 同步數據
     */
    suspend fun syncFromXDrip(): Result<Int> {
        return try {
            // 獲取最近 24 小時的數據
            val result = xDripDataSource.getRecentReadings(count = 288) // 24小時 * 12次/小時
            
            result.onSuccess { readings ->
                // 保存到本地
                val entities = readings.map { it.toEntity() }
                glucoseReadingDao.insertAll(entities)
            }
            
            result.map { it.size }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 檢查 xDrip+ 連接狀態
     */
    suspend fun checkXDripConnection(): Result<Boolean> {
        return xDripDataSource.checkConnection()
    }
    
    /**
     * 清理舊數據（保留 7 天）
     */
    suspend fun cleanOldData() {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        glucoseReadingDao.deleteOlderThan(sevenDaysAgo)
    }
    
    /**
     * 檢測血糖上升趨勢（用於判斷是否正在用餐）
     */
    suspend fun detectRisingTrend(): Boolean {
        val recentReadings = glucoseReadingDao.getReadingsSince(
            System.currentTimeMillis() - (30 * 60 * 1000) // 最近 30 分鐘
        )
        
        if (recentReadings.size < 3) return false
        
        // 檢查是否連續上升
        val isRising = recentReadings.zipWithNext().all { (newer, older) ->
            newer.glucose > older.glucose
        }
        
        // 檢查上升幅度是否超過 20 mg/dL
        val totalIncrease = recentReadings.first().glucose - recentReadings.last().glucose
        
        return isRising && totalIncrease > 20
    }
}

/**
 * 擴展函數：GlucoseReading -> Entity
 */
private fun GlucoseReading.toEntity(): GlucoseReadingEntity {
    return GlucoseReadingEntity(
        id = id,
        timestamp = timestamp,
        glucose = glucose,
        trend = trend.value,
        delta = delta,
        source = source
    )
}

/**
 * 擴展函數：Entity -> GlucoseReading
 */
private fun GlucoseReadingEntity.toDomainModel(): GlucoseReading {
    return GlucoseReading(
        id = id,
        timestamp = timestamp,
        glucose = glucose,
        trend = GlucoseTrend.fromValue(trend),
        delta = delta,
        source = source
    )
}