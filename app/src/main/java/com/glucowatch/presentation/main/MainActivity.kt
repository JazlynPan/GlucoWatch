package com.glucowatch.presentation.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glucowatch.domain.model.GlucoseReading
import com.glucowatch.domain.model.GlucoseStatus
import com.glucowatch.ui.theme.GlucoWatchTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*

/**
 * 主界面 Activity
 * 採用大字體、高對比度、簡潔設計，適合 50+ 歲使用者
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val viewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            GlucoWatchTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val latestReading by viewModel.latestReading.collectAsState()
    val recentReadings by viewModel.recentReadings.collectAsState()
    
    // 系統UI控制器（狀態欄顏色）
    val systemUiController = rememberSystemUiController()
    val statusBarColor = latestReading?.getStatus()?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    
    SideEffect {
        systemUiController.setStatusBarColor(statusBarColor)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "血糖監控",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = statusBarColor
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is MainUiState.Loading -> {
                    LoadingScreen()
                }
                is MainUiState.Success -> {
                    MainContent(
                        latestReading = latestReading,
                        recentReadings = recentReadings,
                        onRefresh = { viewModel.refresh() }
                    )
                }
                is MainUiState.Error -> {
                    ErrorScreen(
                        message = (uiState as MainUiState.Error).message,
                        onRetry = { viewModel.refresh() }
                    )
                }
            }
        }
    }
}

@Composable
fun MainContent(
    latestReading: GlucoseReading?,
    recentReadings: List<GlucoseReading>,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 當前血糖值（超大顯示）
        latestReading?.let { reading ->
            CurrentGlucoseCard(reading = reading)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 趨勢圖表
            GlucoseTrendChart(readings = recentReadings)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 快速操作按鈕
            QuickActionButtons()
        } ?: run {
            NoDataCard(onRefresh = onRefresh)
        }
    }
}

@Composable
fun CurrentGlucoseCard(reading: GlucoseReading) {
    val status = reading.getStatus()
    val backgroundColor = Color(status.color)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor.copy(alpha = 0.15f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 血糖值
            Text(
                text = reading.glucose.toInt().toString(),
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                color = backgroundColor
            )
            
            // 單位
            Text(
                text = "mg/dL",
                fontSize = 32.sp,
                color = backgroundColor.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 趨勢箭頭
            Text(
                text = reading.trend.arrow,
                fontSize = 56.sp
            )
            
            // 狀態描述
            Text(
                text = status.description,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = backgroundColor
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 時間
            Text(
                text = formatTime(reading.timestamp),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun GlucoseTrendChart(readings: List<GlucoseReading>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (readings.isEmpty()) {
                Text(
                    text = "暫無趨勢數據",
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                // TODO: 實現圖表（使用 MPAndroidChart 或 Compose Canvas）
                Text(
                    text = "📊 趨勢圖表\n(${readings.size} 筆記錄)",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun QuickActionButtons() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 記錄飲食
        Button(
            onClick = { /* TODO: 打開飲食記錄 */ },
            modifier = Modifier
                .weight(1f)
                .height(80.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "記錄飲食", fontSize = 18.sp)
            }
        }
        
        // 查看歷史
        Button(
            onClick = { /* TODO: 打開歷史記錄 */ },
            modifier = Modifier
                .weight(1f)
                .height(80.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "歷史記錄", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun NoDataCard(onRefresh: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "暫無血糖數據",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "請確保 xDrip+ 已安裝並正常運行",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text(text = "重新載入", fontSize = 22.sp)
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "正在載入...", fontSize = 24.sp)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "發生錯誤",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = message,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text(text = "重試", fontSize = 22.sp)
            }
        }
    }
}

/**
 * 格式化時間
 */
private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}