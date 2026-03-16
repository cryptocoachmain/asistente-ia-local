package com.dnklabs.asistenteialocal.ui.screens.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dnklabs.asistenteialocal.data.local.AppDatabase
import com.dnklabs.asistenteialocal.data.local.AppLogger
import com.dnklabs.asistenteialocal.data.local.ChatMessage
import com.dnklabs.asistenteialocal.data.local.ConversationEntity
import com.dnklabs.asistenteialocal.data.local.ModelSettingsManager
import com.dnklabs.asistenteialocal.data.local.TokenWarningManager
import com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

sealed class ChatUiState {
    object Idle : ChatUiState()
    object Loading : ChatUiState()
    data class Streaming(val partialResponse: String) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

class ChatViewModel(
    private val llamaRepository: com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository,
    private val ttsManager: TextToSpeechManager? = null,
    private val audioRecorder: AudioRecorder? = null,
    private val tokenManager: com.dnklabs.asistenteialocal.data.local.TokenWarningManager? = null,
    private val modelPersistence: com.dnklabs.asistenteialocal.data.local.ModelPersistenceManager? = null,
    private val modelSettingsManager: ModelSettingsManager? = null,
    private val context: Context? = null
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val database: AppDatabase? = context?.let { AppDatabase.getDatabase(it) }

    private val _messages = MutableStateFlow<List<ChatMessageDisplay>>(emptyList())
    val messages: StateFlow<List<ChatMessageDisplay>> = _messages.asStateFlow()

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(false)
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    private val _tokenStats = MutableStateFlow(tokenManager?.getTokenStats())
    val tokenStats: StateFlow<com.dnklabs.asistenteialocal.data.local.TokenStats?> = _tokenStats.asStateFlow()

    private val _showTokenWarning = MutableStateFlow(false)
    val showTokenWarning: StateFlow<Boolean> = _showTokenWarning.asStateFlow()
    
    private val _isModelInitialized = MutableStateFlow(false)
    val isModelInitialized: StateFlow<Boolean> = _isModelInitialized.asStateFlow()
    
    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()
    
    private val _initializationError = MutableStateFlow<String?>(null)
    val initializationError: StateFlow<String?> = _initializationError.asStateFlow()

    private val _transcribedText = MutableStateFlow<String?>(null)
    val transcribedText: StateFlow<String?> = _transcribedText.asStateFlow()

    private val _ramWarning = MutableStateFlow<String?>(null)
    val ramWarning: StateFlow<String?> = _ramWarning.asStateFlow()

    private val _contextWarningVisible = MutableStateFlow(false)
    val contextWarningVisible: StateFlow<Boolean> = _contextWarningVisible.asStateFlow()

    private val _contextWarningMessage = MutableStateFlow("")
    val contextWarningMessage: StateFlow<String> = _contextWarningMessage.asStateFlow()

    private val appSignature = "\n\nTe estoy regalando mi trabajo, solo te pido tu comentario en mis redes sociales:\n🔹 X: https://x.com/DNKLabsApps\n🔹 LinkedIn: https://www.linkedin.com/in/diego-mtez-fdez/"

    private var conversationId: String = UUID.randomUUID().toString()
    private var messageHistory = mutableListOf<ChatMessage>()

    init {
        // Intentar inicializar el modelo automáticamente
        viewModelScope.launch {
            checkDeviceRam()
            initializeModel()
        }
        
        addMessage(
            content = "¡Hola! Soy tu asistente de IA local. ¿En qué puedo ayudarte hoy?",
            isUser = false
        )
    }

    fun startNewChat() {
        conversationId = UUID.randomUUID().toString()
        messageHistory.clear()
        _messages.value = emptyList()
        tokenManager?.resetCounter() // Also clear tokens
        _tokenStats.value = tokenManager?.getTokenStats()
        addMessage(
            content = "¡Hola! Soy tu asistente de IA local. ¿En qué puedo ayudarte hoy?",
            isUser = false
        )
    }

    fun shareMessage(context: Context, content: String) {
        val sendIntent: android.content.Intent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, content)
            type = "text/plain"
        }
        val shareIntent = android.content.Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }

    fun copyToClipboard(context: Context, content: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Mensaje de IA", content)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, "Copiado al portapapeles", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun loadConversation(convId: String) {
        viewModelScope.launch {
            database?.let { db ->
                try {
                    val conversation = db.conversationDao().getById(convId)
                    conversation?.let { conv ->
                        android.util.Log.d("ChatViewModel", "Cargando conversación: ${conv.id}")
                        conversationId = conv.id
                        messageHistory.clear()
                        
                        if (conv.messagesJson.isNotBlank()) {
                            try {
                                val messagesArray = JSONArray(conv.messagesJson)
                                for (i in 0 until messagesArray.length()) {
                                    val msgObj = messagesArray.getJSONObject(i)
                                    messageHistory.add(
                                        ChatMessage(
                                            id = msgObj.optString("id", UUID.randomUUID().toString()),
                                            role = msgObj.optString("role", "user"),
                                            content = msgObj.optString("content", ""),
                                            timestamp = msgObj.optLong("timestamp", System.currentTimeMillis()),
                                            responseTimeMs = if (msgObj.has("responseTimeMs")) msgObj.optLong("responseTimeMs") else null
                                        )
                                    )
                                }
                            } catch (e: org.json.JSONException) {
                                android.util.Log.e("ChatViewModel", "Error al parsear JSON de la conversación", e)
                            }
                        }
                        
                        _messages.value = messageHistory.map { msg ->
                            ChatMessageDisplay(
                                id = msg.id,
                                content = msg.content,
                                isUser = msg.role == "user",
                                timestamp = msg.timestamp,
                                responseTimeMs = msg.responseTimeMs
                            )
                        }
                        
                        // Recalcular tokens
                        val totalTokens = tokenManager?.calculateConversationTokens(messageHistory) ?: 0
                        tokenManager?.setTotalTokens(totalTokens)
                        _tokenStats.value = tokenManager?.getTokenStats()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error cargando conversación", e)
                    messageHistory.clear()
                    _messages.value = emptyList()
                }
            }
        }
    }

    private fun saveConversation() {
        database?.let { db ->
            viewModelScope.launch {
                try {
                    val messagesArray = JSONArray()
                    messageHistory.forEach { msg ->
                        val msgObj = JSONObject()
                        msgObj.put("id", msg.id)
                        msgObj.put("role", msg.role)
                        msgObj.put("content", msg.content)
                        msgObj.put("timestamp", msg.timestamp)
                        msg.responseTimeMs?.let { msgObj.put("responseTimeMs", it) }
                        messagesArray.put(msgObj)
                    }

                    val title = messageHistory.firstOrNull { it.role == "user" }?.content?.take(30)?.replace("\n", " ") ?: "Nueva conversación"

                    val conversation = ConversationEntity(
                        id = conversationId,
                        title = title,
                        messagesJson = messagesArray.toString(),
                        updatedAt = System.currentTimeMillis()
                    )
                    db.conversationDao().insert(conversation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * Inicializa el modelo de IA
     */
    suspend fun initializeModel() {
        val selectedModel = modelPersistence?.getSelectedModel()
        
        AppLogger.i(TAG, "Inicializando modelo - modelo guardado: $selectedModel")
        android.util.Log.i(TAG, "Inicializando modelo - modelo guardado: $selectedModel")
        
        // If no model is explicitly selected, try to recover from installed models
        val modelToInitialize = if (selectedModel.isNullOrBlank()) {
            // No model selected, try to recover from installed models
            val installedModels = modelPersistence?.getInstalledModels() ?: emptySet()
            AppLogger.i(TAG, "No modelo seleccionado explícitamente, modelos instalados: $installedModels")
            android.util.Log.i(TAG, "No modelo seleccionado explícitamente, modelos instalados: $installedModels")
            
            // If only one model is installed, automatically select it
            if (installedModels.size == 1) {
                val onlyModel = installedModels.first()
                AppLogger.i(TAG, "Solo un modelo instalado, seleccionando automáticamente: $onlyModel")
                android.util.Log.i(TAG, "Solo un modelo instalado, seleccionando automáticamente: $onlyModel")
                modelPersistence?.saveSelectedModel(onlyModel)
                onlyModel
            } else {
                // Try to find a suitable default model in order of preference
                val defaultModel = when {
                    installedModels.contains(LlamaCppRepository.MODEL_GEMMA_2B) -> LlamaCppRepository.MODEL_GEMMA_2B
                    installedModels.contains(LlamaCppRepository.MODEL_QWEN_1_5B) -> LlamaCppRepository.MODEL_QWEN_1_5B
                    installedModels.contains(LlamaCppRepository.MODEL_QWEN_0_5B) -> LlamaCppRepository.MODEL_QWEN_0_5B
                    installedModels.contains(LlamaCppRepository.MODEL_QWEN_3B) -> LlamaCppRepository.MODEL_QWEN_3B
                    installedModels.contains(LlamaCppRepository.MODEL_LLAMA_3_2_3B) -> LlamaCppRepository.MODEL_LLAMA_3_2_3B
                    installedModels.contains(LlamaCppRepository.MODEL_LLAMA_3_8B) -> LlamaCppRepository.MODEL_LLAMA_3_8B
                    installedModels.contains(LlamaCppRepository.MODEL_MISTRAL_7B) -> LlamaCppRepository.MODEL_MISTRAL_7B
                    installedModels.contains(LlamaCppRepository.MODEL_PHI_3_MINI) -> LlamaCppRepository.MODEL_PHI_3_MINI
                    else -> null
                }
                
                defaultModel?.let { model ->
                    AppLogger.i(TAG, "Recuperando modelo instalado como predeterminado: $model")
                    android.util.Log.i(TAG, "Recuperando modelo instalado como predeterminado: $model")
                    // Save this model as the selected model for future sessions
                    modelPersistence?.saveSelectedModel(model)
                    model
                } ?: return@initializeModel
            }
        } else {
            selectedModel
        }
        
        val totalRamGb = getDeviceRamGb()
        tokenManager?.setMaxTokens(modelToInitialize, totalRamGb)
        _tokenStats.value = tokenManager?.getTokenStats()

        // Warning de RAM
        if ((modelToInitialize.contains("mistral", ignoreCase = true) || modelToInitialize.contains("llama-3", ignoreCase = true))) {
            if (totalRamGb <= 8) {
                _ramWarning.value = "Atención: su dispositivo tiene ${totalRamGb}GB de RAM, lo cual es insuficiente para correr este modelo suavemente. Podría ralentizarse considerablemente."
            } else {
                _ramWarning.value = null
            }
        } else {
            _ramWarning.value = null
        }
        
        _isInitializing.value = true
        _initializationError.value = null
        
        try {
            val contextLength = modelSettingsManager?.contextLength?.value ?: ModelSettingsManager.DEFAULT_CONTEXT_LENGTH
            val success = llamaRepository.initialize(modelToInitialize, maxContextLength = contextLength)
            
            if (success) {
                _isModelInitialized.value = true
                _initializationError.value = null
                modelPersistence?.markModelInitialized()
                android.util.Log.i("ChatViewModel", "Modelo $modelToInitialize inicializado correctamente")
                AppLogger.i(TAG, "Modelo $modelToInitialize inicializado correctamente")
            } else {
                _isModelInitialized.value = false
                _initializationError.value = "No se pudo inicializar el modelo '$modelToInitialize'. Verifica que esté descargado correctamente."
                android.util.Log.e("ChatViewModel", "Error al inicializar modelo $modelToInitialize")
                AppLogger.e(TAG, "Error al inicializar modelo $modelToInitialize")
                // Do NOT clear the model on failure; keep the selected model so user can retry later
            }
        } catch (e: Throwable) {
            _isModelInitialized.value = false
            _initializationError.value = "Error al cargar el modelo: ${e.message}"
            android.util.Log.e("ChatViewModel", "Excepción al inicializar modelo", e)
            AppLogger.e(TAG, "Excepción al inicializar modelo: ${e.message}", e)
            // Do NOT clear the model on failure; keep the selected model so user can retry later
        } finally {
            _isInitializing.value = false
        }
    }

    fun sendMessage(content: String, isIncognito: Boolean = false) {
        if (content.isBlank()) return
        
        // Verificar que el modelo esté inicializado
        if (!_isModelInitialized.value) {
            _uiState.value = ChatUiState.Error(_initializationError.value ?: "Modelo no inicializado")
            return
        }
        
        tokenManager?.let { manager ->
            if (manager.isNearLimit()) {
                _uiState.value = ChatUiState.Error("Límite de tokens alcanzado. Reinicia la conversación.")
                return
            }
            manager.addTokens(content)
            _tokenStats.value = manager.getTokenStats()
            _showTokenWarning.value = manager.shouldShowWarning.value
        }

        viewModelScope.launch {
            addMessage(content = content, isUser = true)

            messageHistory.add(
                ChatMessage(
                    role = "user",
                    content = content
                )
            )

            _uiState.value = ChatUiState.Loading

            try {
                val startTime = System.currentTimeMillis()

                var streamingMessageId: String? = null
                var currentAssistantResponse = ""

                // Mapping messageHistory to HistoryMessage for the repository
                // Limitar a los últimos 6 mensajes (3 intercambios) para evitar conversaciones largas que confundan al modelo
                val repositoryHistory = messageHistory.takeLast(6).map { 
                    com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.HistoryMessage(
                        role = it.role,
                        content = it.content
                    )
                }

                val maxTokensSetting = modelSettingsManager?.maxTokens?.value ?: 1024
                val contextLength = modelSettingsManager?.contextLength?.value ?: ModelSettingsManager.DEFAULT_CONTEXT_LENGTH
                val maxTokens = maxTokensSetting.coerceIn(16, (contextLength - 256).coerceAtLeast(16))
                val temperature = modelSettingsManager?.temperature?.value ?: 0.7f
                val topP = modelSettingsManager?.topP?.value ?: 0.9f
                val topK = modelSettingsManager?.topK?.value ?: 40

                val responseCharLimit = maxTokensSetting * TokenWarningManager.CHARS_PER_TOKEN

                llamaRepository.generateResponse(
                    prompt = content,
                    history = repositoryHistory,
                    maxTokens = maxTokens,
                    temperature = temperature,
                    topP = topP,
                    topK = topK,
                    responseCharLimit = responseCharLimit
                ).collect { responseResult ->
                    when (responseResult) {
                        is com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.GenerationResult.Generating -> {
                            val token = responseResult.partial
                            currentAssistantResponse += token
                            
                            val cleanedPartial = cleanContent(currentAssistantResponse)
                            
                            if (streamingMessageId == null) {
                                // First token received, create the message
                                val msg = addMessage(content = cleanedPartial, isUser = false)
                                streamingMessageId = msg.id
                                _uiState.value = ChatUiState.Streaming(cleanedPartial)
                            } else {
                                // Subsequent tokens, update existing message
                                updateLastMessage(cleanedPartial)
                                _uiState.value = ChatUiState.Streaming(cleanedPartial)
                            }
                        }
                        is com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.GenerationResult.Success -> {
                            val responseTime = System.currentTimeMillis() - startTime
                            val baseResponse = cleanContent(responseResult.response)
                            val finalResponse = baseResponse + appSignature
    
                            if (streamingMessageId == null) {
                                addMessage(
                                    content = finalResponse,
                                    isUser = false,
                                    responseTimeMs = responseTime
                                )
                            } else {
                                updateLastMessage(finalResponse, responseTime)
                            }
    
                            messageHistory.add(
                                ChatMessage(
                                    role = "assistant",
                                    content = finalResponse,
                                    responseTimeMs = responseTime
                                )
                            )
    
                            tokenManager?.let { manager ->
                                manager.addTokens(finalResponse)
                                _tokenStats.value = manager.getTokenStats()
                                _showTokenWarning.value = manager.shouldShowWarning.value
                            }
    
                            if (_ttsEnabled.value) {
                                ttsManager?.speak(finalResponse)
                            }
    
                            saveConversation()
                            _uiState.value = ChatUiState.Idle
                        }
                        is com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.GenerationResult.Error -> {
                            _uiState.value = ChatUiState.Error(responseResult.message)
                        }
                    }
                }

            } catch (e: Exception) {
                _uiState.value = ChatUiState.Error("Error: ${e.message}")
            }
        }
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        _isRecording.value = true
        audioRecorder?.startRecording { text ->
            if (text.isNotBlank()) {
                _transcribedText.value = text
            }
            _isRecording.value = false
        }
    }

    fun clearTranscribedText() {
        _transcribedText.value = null
    }

    private fun checkDeviceRam() {
        // La RAM se comprueba dinámicamente en getDeviceRamGb
    }

    private fun getDeviceRamGb(): Int {
        return context?.let { ctx ->
            val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            (memoryInfo.totalMem / (1024 * 1024 * 1024)).toInt()
        } ?: 0
    }

    private fun stopRecording() {
        _isRecording.value = false
        audioRecorder?.stopRecording()
    }

    fun toggleTts() {
        _ttsEnabled.value = !_ttsEnabled.value
        if (_ttsEnabled.value) {
            ttsManager?.initialize()
        } else {
            ttsManager?.stop()
        }
    }

    fun stopTts() {
        ttsManager?.stop()
    }

    fun clearChat() {
        _messages.value = emptyList()
        messageHistory.clear()
        tokenManager?.resetCounter()
        _tokenStats.value = tokenManager?.getTokenStats()
        _showTokenWarning.value = false
        startNewChat()
    }

    fun dismissTokenWarning() {
        _showTokenWarning.value = false
        tokenManager?.markWarningShown()
    }

    private fun addMessage(
        content: String,
        isUser: Boolean,
        responseTimeMs: Long? = null
    ): ChatMessageDisplay {
        val message = ChatMessageDisplay(
            id = UUID.randomUUID().toString(),
            content = content,
            isUser = isUser,
            timestamp = System.currentTimeMillis(),
            responseTimeMs = responseTimeMs
        )
        _messages.value = _messages.value + message
        
        // Check context accumulation after adding message
        checkContextAccumulation()
        
        return message
    }

    private fun cleanContent(content: String): String {
        val truncated = truncateAtStopTokens(content)
        return truncated
            .replace("<|im_end|>", "")
            .replace("<|im_start|>", "")
            .replace("<|eot_id|>", "")
            .replace("<|start_header_id|>", "")
            .replace("<|end_header_id|>", "")
            .replace("<end_of_turn>", "")
            .replace("<start_of_turn>", "")
            .replace("</s>", "")
            .replace("<|endoftext|>", "")
            .replace("<|bos|>", "")
            .replace("<|pad|>", "")
            .replace("️", "")  // Remove emoji modifiers that might confuse
            .trim()
    }

    private fun truncateAtStopTokens(content: String): String {
        val stopTokens = listOf(
            "<|eot_id|>",
            "<|start_header_id|>user",
            "<|im_start|>user",
            "<|end|>",
            "[INST]",
            "<start_of_turn>user"
        )
        var cutIndex = content.length
        for (token in stopTokens) {
            val index = content.indexOf(token)
            if (index >= 0 && index < cutIndex) {
                cutIndex = index
            }
        }
        return content.substring(0, cutIndex)
    }

    private fun applyResponseLengthLimit(
        response: String,
        maxTokens: Int,
        reservedChars: Int
    ): String {
        if (maxTokens <= 0) return response
        val maxChars = (maxTokens * TokenWarningManager.CHARS_PER_TOKEN) - reservedChars
        if (maxChars <= 0) return ""
        return if (response.length <= maxChars) {
            response
        } else {
            response.take(maxChars).trimEnd()
        }
    }

    private fun updateLastMessage(content: String, responseTimeMs: Long? = null) {
        val currentMessages = _messages.value
        if (currentMessages.isNotEmpty()) {
            val lastMessage = currentMessages.last()
            if (!lastMessage.isUser) {
                val updatedMessage = lastMessage.copy(
                    content = content,
                    responseTimeMs = responseTimeMs
                )
                _messages.value = currentMessages.dropLast(1) + updatedMessage
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        saveConversation()
        ttsManager?.shutdown()
        audioRecorder?.release()
    }

    fun showContextWarning(message: String) {
        _contextWarningMessage.value = message
        _contextWarningVisible.value = true
    }

    fun dismissContextWarning() {
        _contextWarningVisible.value = false
    }

    private fun checkContextAccumulation() {
        val messageCount = _messages.value.size
        val userMessages = _messages.value.count { it.isUser }
        
        // Mostrar advertencia si hay más de 5 mensajes o más de 3 respuestas del usuario
        if (messageCount > 10 || userMessages > 3) {
            val remainingTokens = _tokenStats.value?.remainingTokens ?: 0
            val maxTokens = _tokenStats.value?.maxTokens ?: 0
            
            if (remainingTokens < maxTokens * 0.3) { // Si quedan menos del 30% de tokens
                showContextWarning(
                    "⚠️ Contexto acumulado: ${messageCount} mensajes. " +
                    "Este modelo tiene límite de contexto. " +
                    "Quedan ${remainingTokens} tokens disponibles."
                )
            }
        }
    }
}

data class ChatMessageDisplay(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val responseTimeMs: Long? = null
)
