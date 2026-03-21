package com.dnklabs.asistenteialocal.data.repository

import android.content.Context
import android.util.Log
import com.dnklabs.asistenteialocal.data.local.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.Channels

/**
 * Repositorio para gestionar modelos LlamaCpp (llama.cpp)
 * Soporta modelos Qwen para procesamiento local de IA
 */
class LlamaCppRepository(private val context: Context) {

companion object {
        private const val TAG = "LlamaCppRepository"
        private const val MODELS_DIR = "models"
        
        // Modelos Qwen soportados
        const val MODEL_QWEN_0_5B = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        const val MODEL_QWEN_1_5B = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        const val MODEL_QWEN_3B = "qwen2.5-3b-instruct-q4_k_m.gguf"
        
        // Nuevos modelos recomendados
        const val MODEL_GEMMA_2B = "gemma-2-2b-it-Q4_K_M.gguf"
        const val MODEL_PHI_3_MINI = "Phi-3-mini-4k-instruct-q4.gguf"
        const val MODEL_MISTRAL_7B = "Mistral-7B-Instruct-v0.3-Q4_K_M.gguf"
        const val MODEL_LLAMA_3_8B = "Meta-Llama-3-8B-Instruct-Q4_K_M.gguf"
        const val MODEL_LLAMA_3_2_3B = "Llama-3.2-3B-Instruct-Q4_K_M.gguf"
        
        // === QWEN 3.5 MODELS (Marzo 2026) - bartowski ===
        const val MODEL_QWEN35_0_8B = "Qwen_Qwen3.5-0.8B-Q4_K_M.gguf"
        const val MODEL_QWEN35_2B = "Qwen_Qwen3.5-2B-Q4_K_M.gguf"
        const val MODEL_QWEN35_4B = "Qwen_Qwen3.5-4B-Q4_K_M.gguf"
        
        // URLs de descarga (Hugging Face)
        private val MODEL_URLS = mapOf(
            MODEL_QWEN_0_5B to "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            MODEL_QWEN_1_5B to "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            MODEL_QWEN_3B to "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            MODEL_GEMMA_2B to "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            MODEL_PHI_3_MINI to "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            MODEL_MISTRAL_7B to "https://huggingface.co/bartowski/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf",
            MODEL_LLAMA_3_8B to "https://huggingface.co/bartowski/Meta-Llama-3-8B-Instruct-GGUF/resolve/main/Meta-Llama-3-8B-Instruct-Q4_K_M.gguf",
            MODEL_LLAMA_3_2_3B to "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            // Qwen 3.5 - bartowski (Marzo 2026)
            MODEL_QWEN35_0_8B to "https://huggingface.co/bartowski/Qwen_Qwen3.5-0.8B-GGUF/resolve/main/Qwen_Qwen3.5-0.8B-Q4_K_M.gguf?download=true",
            MODEL_QWEN35_2B to "https://huggingface.co/bartowski/Qwen_Qwen3.5-2B-GGUF/resolve/main/Qwen_Qwen3.5-2B-Q4_K_M.gguf?download=true",
            MODEL_QWEN35_4B to "https://huggingface.co/bartowski/Qwen_Qwen3.5-4B-GGUF/resolve/main/Qwen_Qwen3.5-4B-Q4_K_M.gguf?download=true"
        )
        
        // Tamaños aproximados en bytes
        private val MODEL_SIZES = mapOf(
            MODEL_QWEN_0_5B to 401_000_000L,
            MODEL_QWEN_1_5B to 994_000_000L,
            MODEL_QWEN_3B to 1_910_000_000L,
            MODEL_GEMMA_2B to 1_700_000_000L,
            MODEL_PHI_3_MINI to 2_390_000_000L,
            MODEL_MISTRAL_7B to 4_370_000_000L,
            MODEL_LLAMA_3_8B to 4_920_000_000L,
            MODEL_LLAMA_3_2_3B to 1_950_000_000L,
            // Qwen 3.5 - bartowski (Marzo 2026)
            MODEL_QWEN35_0_8B to 580_000_000L,
            MODEL_QWEN35_2B to 1_400_000_000L,
            MODEL_QWEN35_4B to 2_800_000_000L
        )
        
        // Nombres legibles
        private val MODEL_DISPLAY_NAMES = mapOf(
            MODEL_QWEN_0_5B to "Qwen 2.5 0.5B",
            MODEL_QWEN_1_5B to "Qwen 2.5 1.5B",
            MODEL_QWEN_3B to "Qwen 2.5 3B",
            MODEL_GEMMA_2B to "Gemma 2 2B (Google)",
            MODEL_PHI_3_MINI to "Phi-3 Mini 4K (Microsoft)",
            MODEL_MISTRAL_7B to "Mistral 7B v0.3",
            MODEL_LLAMA_3_8B to "Llama 3 8B (Meta)",
            MODEL_LLAMA_3_2_3B to "Llama 3.2 3B (Potente Meta)",
            // Qwen 3.5 (Marzo 2026)
            MODEL_QWEN35_0_8B to "Qwen3.5 0.8B (Ultra Rápido)",
            MODEL_QWEN35_2B to "Qwen3.5 2B (Equilibrado)",
            MODEL_QWEN35_4B to "Qwen3.5 4B (Máxima Calidad)"
        )
    }
    
    private val modelsDir: File by lazy {
        File(context.filesDir, MODELS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }
    
    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress
    
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading
    
    private var currentModel: String? = null
    private var llamaContext: Long = 0
    private var maxContextTokens: Int = 2048
    
    /**
     * Verifica si el modelo está inicializado y listo para usar
     */
    val isInitialized: Boolean
        get() = llamaContext != 0L && currentModel != null
    
    /**
     * Verifica si un modelo está instalado localmente
     */
    fun isModelInstalled(modelName: String): Boolean {
        val modelFile = File(modelsDir, modelName)
        val exists = modelFile.exists()
        val validSize = exists && modelFile.length() > (MODEL_SIZES[modelName]?.times(0.9)?.toLong() ?: 0L)
        return exists && validSize
    }
    
    /**
     * Obtiene información de todos los modelos disponibles
     */
    fun getAvailableModels(): List<ModelInfo> {
        return listOf(
            ModelInfo(
                id = MODEL_QWEN_0_5B,
                name = MODEL_DISPLAY_NAMES[MODEL_QWEN_0_5B] ?: MODEL_QWEN_0_5B,
                size = MODEL_SIZES[MODEL_QWEN_0_5B] ?: 0,
                ramRequiredMB = 400,
                isInstalled = isModelInstalled(MODEL_QWEN_0_5B),
                description = "Muy rápida. Ideal para dispositivos con poco espacio.",
                performance = "Básica"
            ),
            ModelInfo(
                id = MODEL_QWEN_1_5B,
                name = MODEL_DISPLAY_NAMES[MODEL_QWEN_1_5B] ?: MODEL_QWEN_1_5B,
                size = MODEL_SIZES[MODEL_QWEN_1_5B] ?: 0,
                ramRequiredMB = 1000,
                isInstalled = isModelInstalled(MODEL_QWEN_1_5B),
                description = "Equilibrio entre velocidad y calidad.",
                performance = "Buena"
            ),
            ModelInfo(
                id = MODEL_GEMMA_2B,
                name = MODEL_DISPLAY_NAMES[MODEL_GEMMA_2B] ?: MODEL_GEMMA_2B,
                size = MODEL_SIZES[MODEL_GEMMA_2B] ?: 0,
                ramRequiredMB = 1800,
                isInstalled = isModelInstalled(MODEL_GEMMA_2B),
                description = "Extremadamente rápido. Ideal si buscas velocidad por encima de la profundidad técnica.",
                performance = "Excelente"
            ),
            ModelInfo(
                id = MODEL_QWEN_3B,
                name = MODEL_DISPLAY_NAMES[MODEL_QWEN_3B] ?: MODEL_QWEN_3B,
                size = MODEL_SIZES[MODEL_QWEN_3B] ?: 0,
                ramRequiredMB = 2000,
                isInstalled = isModelInstalled(MODEL_QWEN_3B),
                description = "Máxima precisión para respuestas detalladas.",
                performance = "Alta"
            ),
            ModelInfo(
                id = MODEL_LLAMA_3_2_3B,
                name = MODEL_DISPLAY_NAMES[MODEL_LLAMA_3_2_3B] ?: MODEL_LLAMA_3_2_3B,
                size = MODEL_SIZES[MODEL_LLAMA_3_2_3B] ?: 0,
                ramRequiredMB = 2100,
                isInstalled = isModelInstalled(MODEL_LLAMA_3_2_3B),
                description = "Modelo potente y eficiente de Meta. Buen equilibrio entre tamaño y capacidad de razonamiento.",
                performance = "Alta"
            ),
            ModelInfo(
                id = MODEL_PHI_3_MINI,
                name = MODEL_DISPLAY_NAMES[MODEL_PHI_3_MINI] ?: MODEL_PHI_3_MINI,
                size = MODEL_SIZES[MODEL_PHI_3_MINI] ?: 0,
                ramRequiredMB = 2600,
                isInstalled = isModelInstalled(MODEL_PHI_3_MINI),
                description = "Modelo pequeño (3.8B) diseñado para ser eficiente. Sorprende por lo bien que razona y su buen nivel de español.",
                performance = "Alta"
            ),
            ModelInfo(
                id = MODEL_MISTRAL_7B,
                name = MODEL_DISPLAY_NAMES[MODEL_MISTRAL_7B] ?: MODEL_MISTRAL_7B,
                size = MODEL_SIZES[MODEL_MISTRAL_7B] ?: 0,
                ramRequiredMB = 4800,
                isInstalled = isModelInstalled(MODEL_MISTRAL_7B),
                description = "Solo gama alta (16GB RAM mín). Sigue instrucciones de forma muy precisa en castellano.",
                performance = "Superior"
            ),
            ModelInfo(
                id = MODEL_LLAMA_3_8B,
                name = MODEL_DISPLAY_NAMES[MODEL_LLAMA_3_8B] ?: MODEL_LLAMA_3_8B,
                size = MODEL_SIZES[MODEL_LLAMA_3_8B] ?: 0,
                ramRequiredMB = 5500,
                isInstalled = isModelInstalled(MODEL_LLAMA_3_8B),
                description = "Solo gama alta (16GB RAM mín). El estándar de oro actual. Excelente comprensión.",
                performance = "Referencia"
            ),
            // === QWEN 3.5 MODELS (Marzo 2026) - bartowski ===
            ModelInfo(
                id = MODEL_QWEN35_0_8B,
                name = MODEL_DISPLAY_NAMES[MODEL_QWEN35_0_8B] ?: MODEL_QWEN35_0_8B,
                size = MODEL_SIZES[MODEL_QWEN35_0_8B] ?: 0,
                ramRequiredMB = 700,
                isInstalled = isModelInstalled(MODEL_QWEN35_0_8B),
                description = "⭐ NUEVO: Qwen3.5 ultra rápido. Mejora respecto a Qwen 2.5 con mejor razonamiento.",
                performance = "Buena"
            ),
            ModelInfo(
                id = MODEL_QWEN35_2B,
                name = MODEL_DISPLAY_NAMES[MODEL_QWEN35_2B] ?: MODEL_QWEN35_2B,
                size = MODEL_SIZES[MODEL_QWEN35_2B] ?: 0,
                ramRequiredMB = 1500,
                isInstalled = isModelInstalled(MODEL_QWEN35_2B),
                description = "⭐ NUEVO: Qwen3.5 equilibrado. Excelente para uso diario con gran mejora en español.",
                performance = "Muy Alta"
            ),
            ModelInfo(
                id = MODEL_QWEN35_4B,
                name = MODEL_DISPLAY_NAMES[MODEL_QWEN35_4B] ?: MODEL_QWEN35_4B,
                size = MODEL_SIZES[MODEL_QWEN35_4B] ?: 0,
                ramRequiredMB = 2800,
                isInstalled = isModelInstalled(MODEL_QWEN35_4B),
                description = "⭐ NUEVO: Qwen3.5 máxima calidad. El mejor Qwen hasta la fecha. Gran razonamiento.",
                performance = "Excelente"
            )
        )
    }
    
    /**
     * Descarga un modelo desde Hugging Face
     * Emite progreso de 0 a 100
     */
    fun downloadModel(modelName: String): Flow<DownloadStatus> = flow {
        val url = MODEL_URLS[modelName] ?: throw IllegalArgumentException("Unknown model: $modelName")
        val modelFile = File(modelsDir, modelName)
        val tmpFile = File(modelFile.absolutePath + ".tmp")
        
        AppLogger.i(TAG, "Iniciando descarga de modelo: $modelName")
        
        try {
            emit(DownloadStatus.Preparing)
            AppLogger.i(TAG, "Preparando descarga: $url")
            
            // Eliminar archivo temporal si existe
            if (tmpFile.exists()) tmpFile.delete()
            
            var currentUrl = url
            var connection: HttpURLConnection
            var responseCode: Int
            var redirects = 0
            
            // Manejo robusto de redirecciones (HuggingFace hace varias)
            do {
                val urlObj = URL(currentUrl)
                connection = urlObj.openConnection() as HttpURLConnection
                connection.apply {
                    connectTimeout = 20000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    setRequestProperty("Accept-Encoding", "identity") // Prevenir compresión gzip
                }
                
                responseCode = connection.responseCode
                AppLogger.d(TAG, "Conectando a: $currentUrl -> Código: $responseCode")
                Log.d(TAG, "Conectando a: $currentUrl -> Código: $responseCode")
                
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location")
                    if (location == null) {
                        AppLogger.e(TAG, "Redirección detectada pero 'Location' es nulo")
                        Log.e(TAG, "Redirección detectada pero 'Location' es nulo")
                        emit(DownloadStatus.Error("Error de redirección"))
                        return@flow
                    }
                    currentUrl = if (location.startsWith("http")) location 
                                else URL(URL(currentUrl), location).toString()
                    connection.disconnect()
                    redirects++
                } else {
                    break
                }
            } while (redirects < 10)
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                AppLogger.e(TAG, "Error del servidor: $responseCode para URL: $currentUrl")
                Log.e(TAG, "Error del servidor: $responseCode para URL: $currentUrl")
                emit(DownloadStatus.Error("Error del servidor ($responseCode)."))
                return@flow
            }
            
            val fileLength = connection.contentLengthLong
            AppLogger.i(TAG, "Tamaño del archivo detectado: $fileLength bytes")
            Log.d(TAG, "Tamaño del archivo detectado: $fileLength bytes")
            
            connection.inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(128 * 1024) // Buffer más grande (128KB)
                    var total: Long = 0
                    var count: Int
                    var lastUpdateTime = 0L
                    
                    while (input.read(buffer).also { count = it } != -1) {
                        total += count
                        output.write(buffer, 0, count)
                        
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime > 800) { // Actualizar cada 800ms
                            val progress = if (fileLength > 0) {
                                (total * 100 / fileLength).toInt()
                            } else {
                                -1 // Progreso indeterminado si no hay fileLength
                            }
                            AppLogger.v(TAG, "Descargando: $progress% ($total/$fileLength)")
                            Log.v(TAG, "Descargando: $progress% ($total/$fileLength)")
                            emit(DownloadStatus.Progress(progress, total, fileLength))
                            lastUpdateTime = now
                        }
                    }
                }
            }
            
            connection.disconnect()
            
            // Renombrar archivo temporal al final
            if (tmpFile.renameTo(modelFile)) {
                AppLogger.i(TAG, "Modelo descargado correctamente: ${modelFile.absolutePath}")
                Log.i(TAG, "Model downloaded successfully: ${modelFile.absolutePath}")
                emit(DownloadStatus.Success)
            } else {
                AppLogger.e(TAG, "Error al renombrar archivo temporal")
                Log.e(TAG, "Failed to rename temp file")
                emit(DownloadStatus.Error("Error al guardar el archivo"))
            }
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error en descarga: ${e.message}", e)
            Log.e(TAG, "Download error", e)
            emit(DownloadStatus.Error(e.message ?: "Error desconocido"))
            if (tmpFile.exists()) tmpFile.delete()
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Inicializa el modelo para generación de texto
     * @param modelName Nombre del modelo a cargar
     * @param maxContextLength Longitud máxima del contexto (default 2048)
     * @return true si se inicializó correctamente
     */
    suspend fun initialize(modelName: String, maxContextLength: Int = 2048): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                AppLogger.i(TAG, "Inicializando modelo: $modelName")
                
                if (!isModelInstalled(modelName)) {
                    AppLogger.e(TAG, "Modelo no instalado: $modelName")
                    Log.e(TAG, "Modelo no instalado: $modelName")
                    return@withContext false
                }
                
                val modelPath = File(modelsDir, modelName).absolutePath
                AppLogger.i(TAG, "Ruta del modelo: $modelPath")
                
                val nThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
                AppLogger.i(TAG, "Threads a usar: $nThreads")
                
                val effectiveContext = maxContextLength.coerceAtLeast(512)
                maxContextTokens = effectiveContext
                llamaContext = loadModel(modelPath, nThreads, effectiveContext)
                currentModel = modelName
                
                if (llamaContext != 0L) {
                    AppLogger.i(TAG, "Modelo $modelName inicializado correctamente")
                    Log.i(TAG, "Modelo $modelName inicializado correctamente")
                    true
                } else {
                    AppLogger.e(TAG, "Error inicializando modelo - loadModel devolvió 0")
                    Log.e(TAG, "Error inicializando modelo")
                    false
                }
            } catch (e: Throwable) {
                AppLogger.e(TAG, "Error inicializando modelo: ${e.message}", e)
                Log.e(TAG, "Error inicializando modelo: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Genera una respuesta a partir de un prompt
     * @param prompt Texto de entrada
     * @param maxTokens Número máximo de tokens a generar
     * @param temperature Temperatura para sampling (0.0 - 1.0)
     * @param callback Callback para recibir tokens generados
     * @return Flow con los tokens generados
     */
    fun generateResponse(
        prompt: String,
        history: List<HistoryMessage> = emptyList(),
        maxTokens: Int = 1024,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40,
        responseCharLimit: Int? = null
    ): Flow<GenerationResult> = callbackFlow {
        AppLogger.i(TAG, "generateResponse llamado - modelo: $currentModel, contexto: ${if (llamaContext != 0L) "OK" else "NO INICIALIZADO"}")
        
        if (llamaContext == 0L) {
            val errorMsg = if (currentModel == null) {
                "Modelo no inicializado. Por favor, descarga un modelo desde Configuración."
            } else {
                "El modelo '$currentModel' no se pudo cargar. Intenta reiniciar la aplicación."
            }
            AppLogger.e(TAG, errorMsg)
            trySend(GenerationResult.Error(errorMsg))
            close()
            return@callbackFlow
        }
        
        try {
            val fullPrompt = formatPrompt(prompt, history, responseCharLimit)
            val promptEstimate = estimateTokensByChars(fullPrompt.length)
            val availableTokens = (maxContextTokens - promptEstimate - 32).coerceAtLeast(16)
            val safeMaxTokens = maxTokens.coerceAtMost(availableTokens)
            AppLogger.d(TAG, "Generando respuesta para prompt (longitud: ${fullPrompt.length})")
            Log.d(TAG, "Generando respuesta para prompt (longitud: ${fullPrompt.length})")
            
            val response = withContext(Dispatchers.Default) {
                generateTextStream(
                    llamaContext,
                    fullPrompt,
                    safeMaxTokens,
                    temperature,
                    topK,
                    topP,
                    object : TokenListener {
                        override fun onToken(token: String) {
                            trySend(GenerationResult.Generating(token))
                        }
                    }
                )
            }
            
            if (response.startsWith("Error:") || response.startsWith("Crash:")) {
                AppLogger.e(TAG, "Error en generación: $response")
                trySend(GenerationResult.Error(response))
            } else {
                AppLogger.i(TAG, "Generación completada con éxito")
                trySend(GenerationResult.Success(response.trim()))
            }
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error generando respuesta: ${e.message}", e)
            Log.e(TAG, "Error generando respuesta: ${e.message}", e)
            trySend(GenerationResult.Error(e.message ?: "Error desconocido"))
        }
        
        close()
        awaitClose { }
    }.flowOn(Dispatchers.Default)
    
    /**
     * Libera recursos del modelo cargado
     */
    fun release() {
        if (llamaContext != 0L) {
            releaseModel(llamaContext)
            llamaContext = 0
            currentModel = null
        }
    }
    
    /**
     * Elimina un modelo descargado
     */
    fun deleteModel(modelName: String): Boolean {
        val modelFile = File(modelsDir, modelName)
        return if (modelFile.exists()) {
            // No eliminar si está cargado
            if (currentModel == modelName) {
                release()
            }
            modelFile.delete()
        } else {
            false
        }
    }
    
    /**
     * Obtiene espacio libre en disco
     */
    fun getFreeSpace(): Long {
        return context.filesDir.freeSpace
    }
    
    /**
     * Data class para representar mensajes en el historial para el repositorio
     */
    data class HistoryMessage(
        val role: String,
        val content: String
    )

    /**
     * Formatea el prompt según el modelo y el historial de conversación
     */
    private fun formatPrompt(
        prompt: String,
        history: List<HistoryMessage> = emptyList(),
        responseCharLimit: Int? = null
    ): String {
        val model = currentModel?.lowercase() ?: ""
        val limitHint = responseCharLimit?.let { "Responde en menos de $it caracteres." } ?: ""

        return when {
            model.contains("phi-3") -> formatPhi3Prompt(prompt, history, limitHint)
            model.contains("llama-3") -> formatLlama3Prompt(prompt, history, limitHint)
            model.contains("gemma") -> formatGemmaPrompt(prompt, history, limitHint)
            model.contains("mistral") -> formatMistralPrompt(prompt, history, limitHint)
            model.contains("qwen3.5") || model.contains("qwen_3.5") -> formatQwen35Prompt(prompt, history, limitHint)
            else -> formatChatMLPrompt(prompt, history, limitHint) // Qwen 2.5 and others using ChatML
        }
    }
    
    // === QWEN 3.5 PROMPT FORMAT (Marzo 2026) ===
    private fun formatQwen35Prompt(
        prompt: String,
        history: List<HistoryMessage>,
        limitHint: String
    ): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n")
        sb.append("You are a helpful assistant. Responde SIEMPRE en ESPAÑOL. ")
        if (limitHint.isNotBlank()) sb.append(limitHint).append(" ")
        sb.append("<|im_end|>\n")

        for (msg in history) {
            val role = if (msg.role == "user") "user" else "assistant"
            sb.append("<|im_start|>$role\n${msg.content}<|im_end|>\n")
        }

        sb.append("<|im_start|>user\n$prompt<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun formatChatMLPrompt(
        prompt: String,
        history: List<HistoryMessage>,
        limitHint: String
    ): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\nEres un asistente de IA ?til que responde SIEMPRE en ESPA?OL. No incluyas razonamientos previos en ingl?s ni simulaciones de conversaci?n. Responde directamente a la pregunta.")
        if (limitHint.isNotBlank()) sb.append(" ").append(limitHint)
        sb.append("<|im_end|>\n")

        for (msg in history) {
            val role = if (msg.role == "user") "user" else "assistant"
            sb.append("<|im_start|>$role\n${msg.content}<|im_end|>\n")
        }

        sb.append("<|im_start|>user\n$prompt<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun formatPhi3Prompt(
        prompt: String,
        history: List<HistoryMessage>,
        limitHint: String
    ): String {
        val sb = StringBuilder()
        sb.append("<|system|>\nResponde SIEMPRE en ESPA?OL. Sin razonamientos en ingl?s. Respuesta directa.")
        if (limitHint.isNotBlank()) sb.append(" ").append(limitHint)
        sb.append("<|end|>\n")

        for (msg in history) {
            val role = if (msg.role == "user") "user" else "assistant"
            sb.append("<|$role|>\n${msg.content}<|end|>\n")
        }

        sb.append("<|user|>\n$prompt<|end|>\n")
        sb.append("<|assistant|>\n")
        return sb.toString()
    }

    private fun formatLlama3Prompt(
        prompt: String,
        history: List<HistoryMessage>,
        limitHint: String
    ): String {
        val sb = StringBuilder()
        sb.append("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\nResponde SIEMPRE en ESPANOL. No simules dialogo ni agregues nuevos turnos. Responde solo como asistente, sin prefijos como \"Usuario:\" o \"Asistente:\".")
        if (limitHint.isNotBlank()) sb.append(" ").append(limitHint)
        sb.append("<|eot_id|>")

        for (msg in history) {
            val role = if (msg.role == "user") "user" else "assistant"
            sb.append("<|start_header_id|>$role<|end_header_id|>\n\n${msg.content}<|eot_id|>")
        }

        sb.append("<|start_header_id|>user<|end_header_id|>\n\n$prompt<|eot_id|>")
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }

    private fun formatMistralPrompt(
        prompt: String,
        history: List<HistoryMessage>,
        limitHint: String
    ): String {
        val sb = StringBuilder()
        // Mistral utiliza <s> e </s>
        sb.append("<s>[INST] Responde SIEMPRE en ESPA?OL. Sin simulaciones.")
        if (limitHint.isNotBlank()) sb.append(" ").append(limitHint)
        sb.append(" [/INST] Entendido, responder? solo en espa?ol.</s>")

        for (msg in history) {
            if (msg.role == "user") {
                sb.append("[INST] ${msg.content} [/INST]")
            } else {
                sb.append(" ${msg.content} </s>")
            }
        }

        sb.append("[INST] $prompt [/INST]")
        return sb.toString()
    }

    private fun formatGemmaPrompt(
        prompt: String,
        history: List<HistoryMessage>,
        limitHint: String
    ): String {
        val sb = StringBuilder()
        sb.append("<start_of_turn>user\nResponde SIEMPRE en ESPA?OL.")
        if (limitHint.isNotBlank()) sb.append(" ").append(limitHint)
        sb.append("<end_of_turn>\n<start_of_turn>assistant\nEntendido.<end_of_turn>\n")

        for (msg in history) {
            val role = if (msg.role == "user") "user" else "assistant"
            sb.append("<start_of_turn>$role\n${msg.content}<end_of_turn>\n")
        }

        sb.append("<start_of_turn>user\n$prompt<end_of_turn>\n")
        sb.append("<start_of_turn>assistant\n")
        return sb.toString()
    }

    private fun estimateTokensByChars(charCount: Int): Int {
        return (charCount / 4).coerceAtLeast(1)
    }

    interface TokenListener {
        fun onToken(token: String)
    }

    // Native methods - implementados en jni-bridge.cpp
    private external fun loadModel(modelPath: String, nThreads: Int, maxContextLength: Int): Long
    private external fun generateText(
        contextPtr: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float
    ): String

    private external fun generateTextStream(
        contextPtr: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        listener: TokenListener
    ): String

    private external fun releaseModel(contextPtr: Long)
    
    data class ModelInfo(
        val id: String,
        val name: String,
        val size: Long,
        val ramRequiredMB: Int,
        val isInstalled: Boolean,
        val description: String,
        val performance: String
    )
    
    sealed class DownloadStatus {
        data object Preparing : DownloadStatus()
        data class Progress(val percent: Int, val downloaded: Long, val total: Long) : DownloadStatus()
        data object Success : DownloadStatus()
        data class Error(val message: String) : DownloadStatus()
    }
    
    sealed class GenerationResult {
        data class Generating(val partial: String) : GenerationResult()
        data class Success(val response: String) : GenerationResult()
        data class Error(val message: String) : GenerationResult()
    }
}
