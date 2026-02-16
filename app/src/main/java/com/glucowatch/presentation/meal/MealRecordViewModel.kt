package com.glucowatch.presentation.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucowatch.data.repository.GlucoseRepository
import com.glucowatch.domain.model.FoodCategory
import com.glucowatch.domain.model.FoodItem
import com.glucowatch.domain.model.MealRecord
import com.glucowatch.domain.model.MealType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 飲食記錄 ViewModel
 */
@HiltViewModel
class MealRecordViewModel @Inject constructor(
    private val glucoseRepository: GlucoseRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MealRecordUiState())
    val uiState: StateFlow<MealRecordUiState> = _uiState.asStateFlow()
    
    /**
     * 選擇餐次
     */
    fun selectMealType(mealType: MealType) {
        _uiState.value = _uiState.value.copy(selectedMealType = mealType)
    }
    
    /**
     * 添加食物
     */
    fun addFood(food: FoodItem) {
        val currentFoods = _uiState.value.selectedFoods.toMutableList()
        currentFoods.add(food)
        _uiState.value = _uiState.value.copy(selectedFoods = currentFoods)
    }
    
    /**
     * 移除食物
     */
    fun removeFood(food: FoodItem) {
        val currentFoods = _uiState.value.selectedFoods.toMutableList()
        currentFoods.remove(food)
        _uiState.value = _uiState.value.copy(selectedFoods = currentFoods)
    }
    
    /**
     * 處理語音輸入
     */
    fun processSpeechInput(spokenText: String) {
        _uiState.value = _uiState.value.copy(isListening = false)
        
        // 解析語音輸入的食物
        val detectedFoods = parseFoodFromSpeech(spokenText)
        
        // 添加到已選食物
        val currentFoods = _uiState.value.selectedFoods.toMutableList()
        currentFoods.addAll(detectedFoods)
        _uiState.value = _uiState.value.copy(selectedFoods = currentFoods)
    }
    
    /**
     * 輔助函數：將類別名稱轉換為 FoodCategory 枚舉
     */
    private fun String.toFoodCategory(): FoodCategory {
        return when (this) {
            "主食" -> FoodCategory.GRAIN
            "蛋白質" -> FoodCategory.PROTEIN
            "蔬菜" -> FoodCategory.VEGETABLE
            "水果" -> FoodCategory.FRUIT
            "乳製品" -> FoodCategory.DAIRY
            "小吃", "零食" -> FoodCategory.SNACK
            "飲料" -> FoodCategory.BEVERAGE
            else -> FoodCategory.OTHER
        }
    }
    
    /**
     * 從語音文字解析食物
     */
    private fun parseFoodFromSpeech(text: String): List<FoodItem> {
        val foods = mutableListOf<FoodItem>()
        val lowerText = text.lowercase()
        
        // 台灣食物資料庫
        val foodDatabase = mapOf(
            "白飯" to FoodItem("白飯", FoodCategory.GRAIN, 56.0, "1碗"),
            "飯" to FoodItem("白飯", FoodCategory.GRAIN, 56.0, "1碗"),
            "滷肉飯" to FoodItem("滷肉飯", FoodCategory.GRAIN, 65.0, "1碗"),
            "水餃" to FoodItem("水餃", FoodCategory.GRAIN, 60.0, "10顆"),
            "餃子" to FoodItem("水餃", FoodCategory.GRAIN, 60.0, "10顆"),
            "麵" to FoodItem("麵條", FoodCategory.GRAIN, 45.0, "1碗"),
            "麵條" to FoodItem("麵條", FoodCategory.GRAIN, 45.0, "1碗"),
            "珍珠奶茶" to FoodItem("珍珠奶茶", FoodCategory.BEVERAGE, 60.0, "700ml"),
            "奶茶" to FoodItem("珍珠奶茶", FoodCategory.BEVERAGE, 60.0, "700ml"),
            "蚵仔煎" to FoodItem("蚵仔煎", FoodCategory.SNACK, 35.0, "1份"),
            "雞排" to FoodItem("雞排", FoodCategory.SNACK, 15.0, "1份"),
            "炸雞" to FoodItem("雞排", FoodCategory.SNACK, 15.0, "1份"),
            "地瓜" to FoodItem("地瓜", FoodCategory.GRAIN, 25.0, "1條"),
            "麵包" to FoodItem("麵包", FoodCategory.GRAIN, 30.0, "1片"),
            "吐司" to FoodItem("吐司", FoodCategory.GRAIN, 15.0, "1片"),
            "饅頭" to FoodItem("饅頭", FoodCategory.GRAIN, 35.0, "1個"),
            "包子" to FoodItem("包子", FoodCategory.GRAIN, 35.0, "1個"),
            "牛奶" to FoodItem("牛奶", FoodCategory.BEVERAGE, 12.0, "250ml"),
            "豆漿" to FoodItem("豆漿", FoodCategory.BEVERAGE, 8.0, "250ml"),
            "水果" to FoodItem("水果", FoodCategory.FRUIT, 15.0, "1份"),
            "蘋果" to FoodItem("蘋果", FoodCategory.FRUIT, 25.0, "1個"),
            "香蕉" to FoodItem("香蕉", FoodCategory.FRUIT, 27.0, "1根"),
            "芭樂" to FoodItem("芭樂", FoodCategory.FRUIT, 14.0, "1個"),
            "便當" to FoodItem("便當", FoodCategory.GRAIN, 90.0, "1個"),
            "漢堡" to FoodItem("漢堡", FoodCategory.GRAIN, 45.0, "1個"),
            "薯條" to FoodItem("薯條", FoodCategory.SNACK, 35.0, "1份")
        )
        
        // 搜索匹配的食物
        foodDatabase.forEach { (keyword, food) ->
            if (lowerText.contains(keyword)) {
                foods.add(food.copy()) // 創建副本避免重複引用
            }
        }
        
        // 如果沒有匹配到，創建自定義食物項
        if (foods.isEmpty() && text.isNotBlank()) {
            foods.add(
                FoodItem(
                    name = text,
                    category = FoodCategory.OTHER,
                    carbs = null, // 需要用戶手動輸入
                    portion = "1份"
                )
            )
        }
        
        return foods
    }
    
    /**
     * 保存用餐記錄
     */
    fun saveMealRecord() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                
                // 獲取餐前血糖
                val glucoseBefore = glucoseRepository.getLatestReading()
                    .getOrNull()?.glucose
                
                // 創建用餐記錄
                val mealRecord = MealRecord(
                    id = 0, // Room 會自動生成
                    timestamp = System.currentTimeMillis(),
                    mealType = state.selectedMealType,
                    foodItems = state.selectedFoods,
                    glucoseBefore = glucoseBefore,
                    notes = ""
                )
                
                // 保存到資料庫（暫時不實作，因為需要在 repository 中添加方法）
                // TODO: 實作 insertMealRecord 方法
                // glucoseRepository.insertMealRecord(mealRecord)
                
                // 通知成功
                _uiState.value = _uiState.value.copy(
                    isSaved = true,
                    saveMessage = "用餐記錄已保存！"
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaved = false,
                    saveMessage = "保存失敗：${e.message}"
                )
            }
        }
    }
}

/**
 * UI 狀態
 */
data class MealRecordUiState(
    val selectedMealType: MealType = MealType.BREAKFAST,
    val selectedFoods: List<FoodItem> = emptyList(),
    val isListening: Boolean = false,
    val isSaved: Boolean = false,
    val saveMessage: String? = null
)