package com.glucowatch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 血糖讀數 DAO
 */
@Dao
interface GlucoseReadingDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: GlucoseReadingEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<GlucoseReadingEntity>)
    
    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC LIMIT 1")
    fun getLatestReading(): Flow<GlucoseReadingEntity?>
    
    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC LIMIT :count")
    fun getRecentReadings(count: Int): Flow<List<GlucoseReadingEntity>>
    
    @Query("SELECT * FROM glucose_readings WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getReadingsInRange(startTime: Long, endTime: Long): Flow<List<GlucoseReadingEntity>>
    
    @Query("SELECT * FROM glucose_readings WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    suspend fun getReadingsSince(startTime: Long): List<GlucoseReadingEntity>
    
    @Query("DELETE FROM glucose_readings WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM glucose_readings")
    suspend fun getCount(): Int
}

/**
 * 用餐記錄 DAO
 */
@Dao
interface MealRecordDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meal: MealRecordEntity): Long
    
    @Update
    suspend fun update(meal: MealRecordEntity)
    
    @Query("SELECT * FROM meal_records ORDER BY timestamp DESC")
    fun getAllMeals(): Flow<List<MealRecordEntity>>
    
    @Query("SELECT * FROM meal_records WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getMealsInRange(startTime: Long, endTime: Long): Flow<List<MealRecordEntity>>
    
    @Query("SELECT * FROM meal_records WHERE id = :id")
    suspend fun getMealById(id: Long): MealRecordEntity?
    
    @Query("SELECT * FROM meal_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMeal(): MealRecordEntity?
    
    @Query("DELETE FROM meal_records WHERE id = :id")
    suspend fun deleteMeal(id: Long)
    
    @Query("SELECT COUNT(*) FROM meal_records")
    suspend fun getCount(): Int
}

/**
 * 警報歷史 DAO
 */
@Dao
interface AlarmHistoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmHistoryEntity): Long
    
    @Update
    suspend fun update(alarm: AlarmHistoryEntity)
    
    @Query("SELECT * FROM alarm_history ORDER BY timestamp DESC LIMIT :count")
    fun getRecentAlarms(count: Int): Flow<List<AlarmHistoryEntity>>
    
    @Query("SELECT * FROM alarm_history WHERE acknowledged = 0 ORDER BY timestamp DESC")
    fun getUnacknowledgedAlarms(): Flow<List<AlarmHistoryEntity>>
    
    @Query("UPDATE alarm_history SET acknowledged = 1, acknowledgedAt = :acknowledgedAt WHERE id = :id")
    suspend fun acknowledgeAlarm(id: Long, acknowledgedAt: Long)
    
    @Query("DELETE FROM alarm_history WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM alarm_history WHERE acknowledged = 0")
    suspend fun getUnacknowledgedCount(): Int
}