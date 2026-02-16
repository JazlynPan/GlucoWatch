package com.glucowatch.di

import android.content.Context
import androidx.room.Room
import com.glucowatch.data.local.GlucoWatchDatabase
import com.glucowatch.data.local.GlucoseReadingDao
import com.glucowatch.data.local.MealRecordDao
import com.glucowatch.data.local.AlarmHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 數據庫依賴注入模塊
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): GlucoWatchDatabase {
        return Room.databaseBuilder(
            context,
            GlucoWatchDatabase::class.java,
            GlucoWatchDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideGlucoseReadingDao(database: GlucoWatchDatabase): GlucoseReadingDao {
        return database.glucoseReadingDao()
    }
    
    @Provides
    @Singleton
    fun provideMealRecordDao(database: GlucoWatchDatabase): MealRecordDao {
        return database.mealRecordDao()
    }
    
    @Provides
    @Singleton
    fun provideAlarmHistoryDao(database: GlucoWatchDatabase): AlarmHistoryDao {
        return database.alarmHistoryDao()
    }
}