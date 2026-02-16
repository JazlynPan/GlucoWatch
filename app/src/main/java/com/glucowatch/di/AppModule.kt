package com.glucowatch.di

import android.content.Context
import com.glucowatch.data.remote.XDripDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 應用程式級別依賴注入模塊
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }
    
    @Provides
    @Singleton
    fun provideXDripDataSource(@ApplicationContext context: Context): XDripDataSource {
        return XDripDataSource(context)
    }
}