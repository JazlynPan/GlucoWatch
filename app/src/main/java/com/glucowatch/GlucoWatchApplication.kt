package com.glucowatch

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * GlucoWatch Application
 */
@HiltAndroidApp
class GlucoWatchApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // 初始化操作可以在這裡進行
    }
}