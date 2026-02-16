package com.glucowatch.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucowatch.data.repository.GlucoseRepository
import com.glucowatch.domain.model.GlucoseReading
import com.glucowatch.domain.usecase.AnalyzeGlucoseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 監康官 AI 助手 ViewModel
 * 智能對話式血糖管理助手
 */
@HiltViewModel
class AIAssistantViewModel @Inject constructor(
    private val glucoseRepository: GlucoseRepository,
    private val analyzeGlucoseUseCase: AnalyzeGlucoseUseCase
) : ViewModel() {
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()
    
    private var currentAnalysis: com.glucowatch.domain.usecase.GlucoseAnalysisResult? = null
    
    init {
        // 監康官的歡迎訊息
        addMessage(ChatMessage(
            id = System.currentTimeMillis(),
            sender = MessageSender.AI,
            content = """
                您好！我是您的血糖管理助手「監康官」👋
                
                我可以幫您：
                • 📊 分析血糖趨勢和波動
                • 💉 評估胰島素使用效果
                • 🍽️ 提供飲食建議
                • 🏃 制定運動計劃
                • 📈 解讀血糖數據
                
                請問有什麼我可以幫助您的嗎？
                
                常見問題：
                1️⃣ 我的血糖控制得怎麼樣？
                2️⃣ 為什麼血糖會突然升高？
                3️⃣ 我該怎麼調整胰島素劑量？
                4️⃣ 有什麼飲食建議嗎？
            """.trimIndent(),
            timestamp = System.currentTimeMillis()
        ))
    }
    
    /**
     * 發送用戶消息
     */
    fun sendMessage(content: String) {
        if (content.isBlank()) return
        
        // 添加用戶消息
        addMessage(ChatMessage(
            id = System.currentTimeMillis(),
            sender = MessageSender.USER,
            content = content,
            timestamp = System.currentTimeMillis()
        ))
        
        // 監康官思考並回覆
        respondToUser(content)
    }
    
    /**
     * 監康官智能回覆
     */
    private fun respondToUser(userMessage: String) {
        viewModelScope.launch {
            _isTyping.value = true
            
            try {
                // 識別用戶意圖
                val intent = recognizeIntent(userMessage)
                
                // 根據意圖生成回覆
                val response = when (intent) {
                    UserIntent.CHECK_GLUCOSE -> handleCheckGlucose()
                    UserIntent.ASK_TREND -> handleAskTrend()
                    UserIntent.ASK_WHY_HIGH -> handleWhyHigh()
                    UserIntent.ASK_WHY_LOW -> handleWhyLow()
                    UserIntent.ASK_INSULIN -> handleInsulinQuestion()
                    UserIntent.ASK_DIET -> handleDietQuestion()
                    UserIntent.ASK_EXERCISE -> handleExerciseQuestion()
                    UserIntent.GENERAL_HEALTH -> handleGeneralHealth()
                    UserIntent.GREETING -> handleGreeting()
                    else -> handleUnknown(userMessage)
                }
                
                // 監康官回覆
                addMessage(ChatMessage(
                    id = System.currentTimeMillis(),
                    sender = MessageSender.AI,
                    content = response,
                    timestamp = System.currentTimeMillis()
                ))
                
            } catch (e: Exception) {
                addMessage(ChatMessage(
                    id = System.currentTimeMillis(),
                    sender = MessageSender.AI,
                    content = "抱歉，我遇到了一點問題：${e.message}\n\n請稍後再試，或者換個問題問我。",
                    timestamp = System.currentTimeMillis()
                ))
            } finally {
                _isTyping.value = false
            }
        }
    }
    
    /**
     * 識別用戶意圖（簡單版本，實際可用 NLP 或 LLM）
     */
    private fun recognizeIntent(message: String): UserIntent {
        val lowerMessage = message.lowercase()
        
        return when {
            // 檢查血糖
            lowerMessage.contains("血糖") && (
                lowerMessage.contains("多少") || 
                lowerMessage.contains("現在") ||
                lowerMessage.contains("當前") ||
                lowerMessage.contains("目前")
            ) -> UserIntent.CHECK_GLUCOSE
            
            // 詢問趨勢
            lowerMessage.contains("趨勢") ||
            lowerMessage.contains("變化") ||
            lowerMessage.contains("走向") -> UserIntent.ASK_TREND
            
            // 為什麼高血糖
            lowerMessage.contains("為什麼") && (
                lowerMessage.contains("高") ||
                lowerMessage.contains("升") ||
                lowerMessage.contains("上"升")
            ) -> UserIntent.ASK_WHY_HIGH
            
            // 為什麼低血糖
            lowerMessage.contains("為什麼") && (
                lowerMessage.contains("低") ||
                lowerMessage.contains("降") ||
                lowerMessage.contains("下降")
            ) -> UserIntent.ASK_WHY_LOW
            
            // 胰島素問題
            lowerMessage.contains("胰島素") ||
            lowerMessage.contains("劑量") ||
            lowerMessage.contains("注射") -> UserIntent.ASK_INSULIN
            
            // 飲食問題
            lowerMessage.contains("吃") ||
            lowerMessage.contains("飲食") ||
            lowerMessage.contains("食物") ||
            lowerMessage.contains("餐") -> UserIntent.ASK_DIET
            
            // 運動問題
            lowerMessage.contains("運動") ||
            lowerMessage.contains("活動") ||
            lowerMessage.contains("鍛煉") -> UserIntent.ASK_EXERCISE
            
            // 整體健康評估
            lowerMessage.contains("控制") && lowerMessage.contains("怎麼樣") ||
            lowerMessage.contains("評估") ||
            lowerMessage.contains("報告") -> UserIntent.GENERAL_HEALTH
            
            // 問候
            lowerMessage.contains("你好") ||
            lowerMessage.contains("哈囉") ||
            lowerMessage.contains("嗨") -> UserIntent.GREETING
            
            else -> UserIntent.UNKNOWN
        }
    }
    
    /**
     * 處理：檢查當前血糖
     */
    private suspend fun handleCheckGlucose(): String {
        val result = glucoseRepository.getLatestReading()
        
        return result.fold(
            onSuccess = { reading ->
                val analysis = analyzeCurrentGlucose(reading)
                
                """
                    📊 您目前的血糖數據
                    
                    血糖值：${reading.glucose.toInt()} mg/dL
                    狀態：${reading.getStatus().description}
                    趨勢：${reading.trend.arrow} ${reading.trend.description}
                    時間：${formatTime(reading.timestamp)}
                    
                    ${analysis}
                    
                    有什麼其他想了解的嗎？
                """.trimIndent()
            },
            onFailure = { error ->
                """
                    ⚠️ 抱歉，我無法讀取血糖數據
                    
                    原因：${error.message}
                    
                    請檢查：
                    • xDrip+ 是否正在運行
                    • 血糖儀連接是否正常
                    • APP 權限是否已授予
                """.trimIndent()
            }
        )
    }
    
    /**
     * 處理：詢問趨勢
     */
    private suspend fun handleAskTrend(): String {
        // 獲取最近3小時數據
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (3 * 60 * 60 * 1000)
        
        return glucoseRepository.getReadingsInRange(startTime, endTime)
            .fold(
                onSuccess = { readings ->
                    if (readings.isEmpty()) {
                        return@fold "暫無足夠數據分析趨勢，請稍後再試。"
                    }
                    
                    val trend = analyzeTrend(readings)
                    
                    """
                        📈 血糖趨勢分析（最近3小時）
                        
                        ${trend}
                        
                        💡 監康官建議：
                        ${generateTrendAdvice(readings)}
                    """.trimIndent()
                },
                onFailure = { error ->
                    "抱歉，無法分析趨勢：${error.message}"
                }
            )
    }
    
    /**
     * 處理：為什麼血糖高
     */
    private suspend fun handleWhyHigh(): String {
        val result = glucoseRepository.getLatestReading()
        
        return result.fold(
            onSuccess = { reading ->
                if (reading.glucose <= 180) {
                    return@fold """
                        您目前的血糖是 ${reading.glucose.toInt()} mg/dL，
                        在正常範圍內，不算高哦！👍
                        
                        繼續保持良好的控制！
                    """.trimIndent()
                }
                
                """
                    🔍 血糖偏高的可能原因分析
                    
                    您目前血糖：${reading.glucose.toInt()} mg/dL
                    
                    常見原因：
                    
                    1️⃣ 飲食因素
                    • 攝入過多碳水化合物
                    • 食用高 GI 食物（如白飯、麵包、含糖飲料）
                    • 餐食份量過大
                    
                    2️⃣ 胰島素因素
                    • 胰島素劑量不足
                    • 注射時間不當（應餐前15-30分鐘）
                    • 注射部位吸收不良
                    
                    3️⃣ 生活因素
                    • 缺乏運動
                    • 壓力過大
                    • 睡眠不足
                    • 生病或感染
                    
                    4️⃣ 藥物因素
                    • 忘記或漏打胰島素
                    • 胰島素保存不當（失效）
                    • 其他藥物影響（如類固醇）
                    
                    📋 監康官的建議：
                    
                    立即措施：
                    • 多喝水（每小時250ml）
                    • 避免再攝入碳水
                    • 如果持續高於250，考慮補充修正劑量
                    • 檢查血酮（如有試紙）
                    
                    長期改善：
                    • 記錄飲食日記，找出高血糖觸發因素
                    • 諮詢醫生調整胰島素劑量
                    • 規律運動（每天30分鐘）
                    • 學習計算碳水化合物
                    
                    ⚠️ 如果血糖持續 > 300 mg/dL，請立即就醫！
                    
                    需要我提供更具體的建議嗎？
                """.trimIndent()
            },
            onFailure = { error ->
                "抱歉，無法讀取血糖數據：${error.message}"
            }
        )
    }
    
    /**
     * 處理：為什麼血糖低
     */
    private suspend fun handleWhyLow(): String {
        val result = glucoseRepository.getLatestReading()
        
        return result.fold(
            onSuccess = { reading ->
                if (reading.glucose >= 70) {
                    return@fold """
                        您目前的血糖是 ${reading.glucose.toInt()} mg/dL，
                        在正常範圍內，不算低哦！👍
                    """.trimIndent()
                }
                
                val severity = when {
                    reading.glucose < 54 -> "⚠️⚠️⚠️ 嚴重低血糖！"
                    else -> "⚠️ 低血糖"
                }
                
                """
                    $severity
                    
                    您目前血糖：${reading.glucose.toInt()} mg/dL
                    
                    🔍 低血糖的可能原因：
                    
                    1️⃣ 胰島素過量
                    • 注射劑量過多
                    • 多次注射在同一部位累積
                    • 注射時間過早（距離用餐太久）
                    
                    2️⃣ 飲食不足
                    • 碳水攝入過少
                    • 延遲用餐時間
                    • 漏餐或忘記吃東西
                    
                    3️⃣ 運動影響
                    • 運動量過大
                    • 運動前未補充碳水
                    • 運動後胰島素敏感度增加
                    
                    4️⃣ 其他因素
                    • 飲酒（抑制肝糖輸出）
                    • 腸胃問題（吸收不良）
                    • 腎功能問題
                    
                    ${if (reading.glucose < 54) """
                        🚨 立即處理（15-15法則）：
                        
                        1. 立即攝入15g快速碳水：
                           • 果汁 150ml
                           • 可樂 150ml
                           • 葡萄糖錠 3-4顆
                           • 糖果 3-4顆
                        
                        2. 等待15分鐘
                        
                        3. 重新測血糖
                        
                        4. 如果仍 < 70，重複步驟1-3
                        
                        5. 血糖恢復後，吃點心（如餅乾+牛奶）
                        
                        ⚠️ 如果出現以下症狀，請立即就醫：
                        • 意識模糊
                        • 無法進食
                        • 痙攣
                        • 昏迷
                    """.trimIndent() else """
                        💡 建議處理：
                        
                        1. 攝入15g碳水化合物
                        2. 15分鐘後重測血糖
                        3. 找出低血糖原因
                        4. 調整胰島素或飲食計劃
                    """.trimIndent()}
                    
                    需要更多協助嗎？
                """.trimIndent()
            },
            onFailure = { error ->
                "抱歉，無法讀取血糖數據：${error.message}"
            }
        )
    }
    
    /**
     * 處理：胰島素問題
     */
    private suspend fun handleInsulinQuestion(): String {
        return """
            💉 關於胰島素使用，監康官為您解答
            
            常見問題：
            
            1️⃣ 何時注射？
            • 速效胰島素：餐前15-30分鐘
            • 長效胰島素：每天固定時間
            • 替爾泊肽：每週固定一天
            
            2️⃣ 劑量如何調整？
            ⚠️ 重要：劑量調整必須諮詢醫生！
            
            一般原則：
            • 根據餐前血糖和碳水攝入
            • 使用胰島素碳水比（ICR）計算
            • 使用胰島素敏感因子（ISF）修正
            
            3️⃣ 注射部位輪換
            建議順序：
            • 腹部（吸收最快）
            • 大腿外側
            • 上臂外側
            • 臀部
            
            每次間隔 2-3 公分
            避免連續在同一部位注射
            
            4️⃣ 胰島素保存
            • 未開封：冰箱冷藏（2-8°C）
            • 使用中：室溫（不超過30°C）
            • 避免陽光直射
            • 避免冷凍
            • 注意有效期
            
            5️⃣ 注射技巧
            • 捏起皮膚（避免注射到肌肉）
            • 45-90度角進針
            • 注射後按壓5-10秒
            • 不要揉搓注射部位
            
            📊 想看您的胰島素使用效果分析嗎？
            
            請告訴我：「分析胰島素效果」
        """.trimIndent()
    }
    
    /**
     * 處理：飲食問題
     */
    private suspend fun handleDietQuestion(): String {
        return """
            🍽️ 糖尿病飲食管理建議
            
            📌 基本原則：
            
            1️⃣ 控制碳水化合物
            • 每餐 45-60g 碳水（約3-4份）
            • 選擇低 GI 食物
            • 均勻分配在三餐
            
            2️⃣ 台灣常見食物碳水含量：
            
            主食類（高碳水）：
            • 白飯 1碗（200g）= 56g 碳水
            • 麵條 1碗 = 45g 碳水
            • 水餃 10顆 = 60g 碳水
            • 滷肉飯 1碗 = 65g 碳水
            
            飲料類（需注意）：
            • 珍珠奶茶 700ml = 60g 碳水 ⚠️
            • 可樂 350ml = 39g 碳水 ⚠️
            • 柳橙汁 200ml = 24g 碳水
            
            小吃類：
            • 雞排 = 15g 碳水
            • 鹽酥雞 = 10g 碳水
            • 蚵仔煎 = 35g 碳水
            • 臭豆腐 = 20g 碳水
            
            3️⃣ 建議的餐盤比例：
            
            🥗 蔬菜：1/2
            • 綠葉蔬菜
            • 花椰菜
            • 番茄
            • 菇類
            
            🍚 碳水：1/4
            • 糙米飯
            • 地瓜
            • 全麥麵包
            • 燕麥
            
            🥩 蛋白質：1/4
            • 雞肉
            • 魚肉
            • 豆腐
            • 蛋
            
            4️⃣ 用餐順序（重要！）
            
            ① 先吃蔬菜（增加飽足感，減緩血糖上升）
            ② 再吃蛋白質
            ③ 最後吃碳水
            
            這樣可以讓血糖上升更平穩！
            
            5️⃣ 避免的食物：
            • 含糖飲料（珍奶、汽水）
            • 糕點、蛋糕
            • 精緻澱粉（白飯、白麵包）
            • 油炸食物
            
            6️⃣ 推薦的點心：
            • 無糖優格 + 堅果
            • 番茄 + 起司
            • 水煮蛋
            • 無糖豆漿
            
            💡 監康官小技巧：
            
            外食建議：
            • 滷肉飯 → 換成烤雞腿飯（白飯減半）
            • 珍奶 → 無糖綠茶
            • 炒麵 → 青菜炒肉片
            
            想知道具體某種食物的建議嗎？
            告訴我食物名稱，我幫您分析！
        """.trimIndent()
    }
    
    /**
     * 處理：運動問題
     */
    private suspend fun handleExerciseQuestion(): String {
        return """
            🏃 運動與血糖管理
            
            為什麼要運動？
            • 增加胰島素敏感度
            • 幫助血糖下降
            • 改善心血管健康
            • 控制體重
            
            📋 運動前準備：
            
            1. 測血糖
            • > 250 mg/dL：檢查血酮，暫緩運動
            • 180-250 mg/dL：可以運動
            • 100-180 mg/dL：理想運動範圍
            • 70-100 mg/dL：先吃15g碳水
            • < 70 mg/dL：補充碳水，血糖回升後再運動
            
            2. 準備快速碳水
            隨身攜帶：
            • 葡萄糖錠
            • 糖果
            • 果汁盒
            
            3. 告知他人
            讓運動夥伴知道您有糖尿病
            
            🎯 推薦運動（適合50+歲）：
            
            有氧運動（每天30分鐘）：
            • 快走 ⭐⭐⭐⭐⭐
            • 游泳
            • 騎自行車
            • 太極拳
            • 跳舞
            
            阻力訓練（每週2-3次）：
            • 彈力帶訓練
            • 輕量啞鈴
            • 深蹲（扶椅子）
            
            柔軟度訓練：
            • 瑜伽
            • 伸展運動
            
            ⏰ 最佳運動時間：
            
            • 餐後1-2小時 ⭐（最佳）
            • 避免胰島素高峰期
            • 避免空腹運動
            
            📊 運動對血糖的影響：
            
            短期（運動中）：
            • 血糖下降（肌肉消耗葡萄糖）
            • 效果可持續12-24小時
            
            長期：
            • 胰島素敏感度提升
            • 可能需要減少胰島素劑量
            
            ⚠️ 運動後注意事項：
            
            1. 運動後2-4小時內監測血糖
            2. 注意延遲性低血糖
            3. 運動日可能需要減少基礎胰島素
            4. 補充適量碳水和蛋白質
            
            💡 監康官的運動計劃建議：
            
            初級（剛開始）：
            • 每天快走15分鐘
            • 逐漸增加到30分鐘
            • 速度：能說話但不能唱歌
            
            進階：
            • 快走30分鐘 + 阻力訓練15分鐘
            • 每週5天
            
            想要個性化運動計劃嗎？
            告訴我您的情況（年齡、運動習慣），我幫您設計！
        """.trimIndent()
    }
    
    /**
     * 處理：整體健康評估
     */
    private suspend fun handleGeneralHealth(): String {
        // TODO: 調用完整分析
        return """
            📊 為您生成完整健康報告...
            
            請稍候，監康官正在分析您的數據...
            
            分析項目：
            • 血糖趨勢
            • 血糖波動
            • 胰島素效果
            • 餐後反應
            • 目標範圍時間
            
            報告將在 3 秒後顯示
        """.trimIndent()
    }
    
    /**
     * 處理：問候
     */
    private fun handleGreeting(): String {
        val greetings = listOf(
            "您好！監康官隨時為您服務！有什麼問題儘管問我 😊",
            "嗨！很高興見到您！今天血糖控制得如何？",
            "您好！監康官在這裡！需要什麼幫助嗎？",
            "哈囉！讓監康官幫您管理血糖！"
        )
        return greetings.random()
    }
    
    /**
     * 處理：未知問題
     */
    private fun handleUnknown(message: String): String {
        return """
            嗯...監康官還在學習中 🤔
            
            我暫時還不太理解「$message」
            
            您可以試試問我：
            • 「我的血糖怎麼樣？」
            • 「為什麼血糖會高？」
            • 「我該吃什麼？」
            • 「如何運動？」
            • 「胰島素怎麼打？」
            
            或者用更簡單的方式描述您的問題！
        """.trimIndent()
    }
    
    // 輔助函數
    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }
    
    private fun analyzeCurrentGlucose(reading: GlucoseReading): String {
        return when {
            reading.glucose < 70 -> "⚠️ 需要注意，血糖偏低"
            reading.glucose <= 180 -> "✓ 血糖在目標範圍內"
            else -> "⚠️ 血糖偏高，注意控制"
        }
    }
    
    private fun analyzeTrend(readings: List<GlucoseReading>): String {
        // 簡化版趨勢分析
        val recent = readings.takeLast(6)
        val isRising = recent.zipWithNext().count { (a, b) -> b.glucose > a.glucose } >= 4
        val isFalling = recent.zipWithNext().count { (a, b) -> b.glucose < a.glucose } >= 4
        
        return when {
            isRising -> "📈 血糖呈上升趨勢"
            isFalling -> "📉 血糖呈下降趨勢"
            else -> "➡️ 血糖相對穩定"
        }
    }
    
    private fun generateTrendAdvice(readings: List<GlucoseReading>): String {
        val avg = readings.map { it.glucose }.average()
        return when {
            avg < 100 -> "整體偏低，注意補充碳水"
            avg <= 140 -> "控制得很好，繼續保持！"
            else -> "整體偏高，注意飲食和胰島素"
        }
    }
    
    private fun formatTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (60 * 1000)
        return when {
            minutes < 1 -> "剛剛"
            minutes < 60 -> "${minutes}分鐘前"
            else -> "${minutes / 60}小時前"
        }
    }
}

/**
 * 聊天消息
 */
data class ChatMessage(
    val id: Long,
    val sender: MessageSender,
    val content: String,
    val timestamp: Long
)

enum class MessageSender {
    USER,   // 用戶
    AI      // 監康官
}

/**
 * 用戶意圖
 */
enum class UserIntent {
    CHECK_GLUCOSE,      // 檢查血糖
    ASK_TREND,          // 詢問趨勢
    ASK_WHY_HIGH,       // 為什麼高
    ASK_WHY_LOW,        // 為什麼低
    ASK_INSULIN,        // 胰島素問題
    ASK_DIET,           // 飲食問題
    ASK_EXERCISE,       // 運動問題
    GENERAL_HEALTH,     // 整體評估
    GREETING,           // 問候
    UNKNOWN             // 未知
}