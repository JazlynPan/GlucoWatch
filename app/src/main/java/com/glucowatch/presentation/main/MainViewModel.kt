package com.glucowatch.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucowatch.data.repository.GlucoseRepository
import com.glucowatch.domain.model.GlucoseReading
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主界面 ViewModel
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val glucoseRepository: GlucoseRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    private val _latestReading = MutableStateFlow<GlucoseReading?>(null)
    val latestReading: StateFlow<GlucoseReading?> = _latestReading.asStateFlow()
    
    private val _recentReadings = MutableStateFlow<List<GlucoseReading>>(emptyList())
    val recentReadings: StateFlow<List<GlucoseReading>> = _recentReadings.asStateFlow()
    
    init {
        loadData()
        observeRecentReadings()
    }
    
    /**
     * 加載數據
     */
    fun loadData() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            
            try {
                // 從 xDrip+ 同步數據
                glucoseRepository.syncFromXDrip()
                
                // 獲取最新讀數
                val result = glucoseRepository.getLatestReading()
                
                result.onSuccess { reading ->
                    _latestReading.value = reading
                    _uiState.value = MainUiState.Success
                }.onFailure { error ->
                    _uiState.value = MainUiState.Error(error.message ?: "未知錯誤")
                }
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.message ?: "加載數據失敗")
            }
        }
    }
    
    /**
     * 觀察最近的血糖讀數
     */
    private fun observeRecentReadings() {
        viewModelScope.launch {
            glucoseRepository.getRecentReadings(24).collect { readings ->
                _recentReadings.value = readings
            }
        }
    }
    
    /**
     * 刷新數據
     */
    fun refresh() {
        loadData()
    }
    
    /**
     * 檢查 xDrip+ 連接
     */
    fun checkXDripConnection() {
        viewModelScope.launch {
            val result = glucoseRepository.checkXDripConnection()
            result.onFailure { error ->
                _uiState.value = MainUiState.Error("xDrip+ 未連接: ${error.message}")
            }
        }
    }
}

/**
 * 主界面 UI 狀態
 */
sealed class MainUiState {
    object Loading : MainUiState()
    object Success : MainUiState()
    data class Error(val message: String) : MainUiState()
}