package com.glucowatch.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * GlucoWatch 數據庫
 */
@Database(
    entities = [
        GlucoseReadingEntity::class,
        MealRecordEntity::class,
        AlarmHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GlucoWatchDatabase : RoomDatabase() {
    
    abstract fun glucoseReadingDao(): GlucoseReadingDao
    abstract fun mealRecordDao(): MealRecordDao
    abstract fun alarmHistoryDao(): AlarmHistoryDao
    
    companion object {
        const val DATABASE_NAME = "glucowatch.db"
    }
}