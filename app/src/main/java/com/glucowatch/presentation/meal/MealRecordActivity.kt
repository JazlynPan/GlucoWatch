package com.glucowatch.presentation.meal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glucowatch.domain.model.FoodCategory
import com.glucowatch.domain.model.FoodItem
import com.glucowatch.domain.model.MealType
import com.glucowatch.ui.theme.GlucoWatchTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 飲食記錄 Activity
 * 提供簡易的飲食記錄功能，支持語音輸入
 */
@AndroidEntryPoint
class MealRecordActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 權限已授予
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 檢查麥克風權限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        
        setContent {
            GlucoWatchTheme {
                MealRecordScreen()
            }
        }
    }
}

@Composable
fun MealRecordScreen(
    viewModel: MealRecordViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    // 語音識別結果
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )?.firstOrNull()
            
            spokenText?.let { viewModel.processSpeechInput(it) }
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 標題欄
            MealRecordHeader(
                onBackClick = { (context as? ComponentActivity)?.finish() }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 餐次選擇
            MealTypeSelector(
                selectedMealType = uiState.selectedMealType,
                onMealTypeSelect = { viewModel.selectMealType(it) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 語音輸入按鈕（超大，方便長輩使用）
            VoiceInputButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
                        putExtra(
                            RecognizerIntent.EXTRA_PROMPT,
                            "請說出您吃了什麼食物"
                        )
                    }
                    speechRecognizerLauncher.launch(intent)
                },
                isListening = uiState.isListening
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 快速選擇常見食物
            QuickFoodSelector(
                onFoodSelect = { food -> viewModel.addFood(food) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 已選擇的食物列表
            Text(
                text = "已記錄的食物",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.selectedFoods) { food ->
                    FoodItemCard(
                        food = food,
                        onRemove = { viewModel.removeFood(food) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 保存按鈕
            Button(
                onClick = { viewModel.saveMealRecord() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                enabled = uiState.selectedFoods.isNotEmpty()
            ) {
                Text(
                    text = "保存記錄",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealRecordHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.size(48.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = "記錄飲食",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MealTypeSelector(
    selectedMealType: MealType,
    onMealTypeSelect: (MealType) -> Unit
) {
    Column {
        Text(
            text = "請選擇餐次",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MealType.values().forEach { mealType ->
                MealTypeChip(
                    mealType = mealType,
                    isSelected = mealType == selectedMealType,
                    onClick = { onMealTypeSelect(mealType) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun MealTypeChip(
    mealType: MealType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Box(
        modifier = modifier
            .height(72.dp)
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = mealType.emoji,
                fontSize = 28.sp
            )
            Text(
                text = mealType.displayName,
                fontSize = 20.sp,
                color = textColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun VoiceInputButton(
    onClick: () -> Unit,
    isListening: Boolean
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isListening) {
                Color(0xFFE53935) // 錄音中顯示紅色
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "語音輸入",
                modifier = Modifier.size(56.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = if (isListening) "正在聽..." else "語音輸入\n說出您吃的食物",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 36.sp
            )
        }
    }
}

@Composable
fun QuickFoodSelector(onFoodSelect: (FoodItem) -> Unit) {
    Column {
        Text(
            text = "快速選擇",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 台灣常見食物
        val commonFoods = listOf(
            FoodItem("白飯", FoodCategory.GRAIN, 56.0, "1碗"),
            FoodItem("滷肉飯", FoodCategory.GRAIN, 65.0, "1碗"),
            FoodItem("水餃", FoodCategory.GRAIN, 60.0, "10顆"),
            FoodItem("麵條", FoodCategory.GRAIN, 45.0, "1碗"),
            FoodItem("珍珠奶茶", FoodCategory.BEVERAGE, 60.0, "700ml"),
            FoodItem("蚵仔煎", FoodCategory.SNACK, 35.0, "1份"),
            FoodItem("雞排", FoodCategory.SNACK, 15.0, "1份"),
            FoodItem("地瓜", FoodCategory.GRAIN, 25.0, "1條")
        )
        
        LazyColumn(
            modifier = Modifier.height(200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(commonFoods) { food ->
                QuickFoodItem(
                    food = food,
                    onClick = { onFoodSelect(food) }
                )
            }
        }
    }
}

@Composable
fun QuickFoodItem(
    food: FoodItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = food.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${food.portion}",
                    fontSize = 18.sp,
                    color = Color.Gray
                )
            }
            
            Text(
                text = "${food.carbs?.toInt()}g 碳水",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun FoodItemCard(
    food: FoodItem,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = food.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${food.category} | ${food.portion}",
                    fontSize = 18.sp,
                    color = Color.Gray
                )
                Text(
                    text = "碳水：${food.carbs?.toInt()}g",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "刪除",
                    modifier = Modifier.size(32.dp),
                    tint = Color.Red
                )
            }
        }
    }
}