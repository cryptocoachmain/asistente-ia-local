package com.dnklabs.asistenteialocal.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestiona el conteo y advertencias de tokens
 * Los modelos locales (Qwen 2.5) tienen límites de contexto:
 * - 0.5B: ~32K tokens
 * - 1.5B: ~32K tokens  
 * - 3B: ~32K tokens
 */
class TokenWarningManager(context: Context) {

    companion object {
        const val PREFS_NAME = "token_settings"
        const val KEY_WARNING_THRESHOLD = "warning_threshold"
        const val KEY_CURRENT_TOKENS = "current_tokens"
        const val KEY_MAX_TOKENS = "max_tokens"
        const val KEY_LAST_WARNING_TIME = "last_warning_time"
        
        // Límites típicos de contexto para modelos Qwen 2.5
        const val DEFAULT_MAX_TOKENS = 32000
        const val DEFAULT_WARNING_THRESHOLD = 0.85f // 85% del límite
        const val WARNING_COOLDOWN_MS = 60000 // 1 minuto entre advertencias
        
        // Aproximaciones: ~4 caracteres = 1 token (muy aproximado)
        const val CHARS_PER_TOKEN = 4
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _currentTokens = MutableStateFlow(prefs.getInt(KEY_CURRENT_TOKENS, 0))
    val currentTokens: StateFlow<Int> = _currentTokens.asStateFlow()
    
    private val _maxTokens = MutableStateFlow(prefs.getInt(KEY_MAX_TOKENS, DEFAULT_MAX_TOKENS))
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()
    
    private val _warningThreshold = MutableStateFlow(
        prefs.getFloat(KEY_WARNING_THRESHOLD, DEFAULT_WARNING_THRESHOLD)
    )
    val warningThreshold: StateFlow<Float> = _warningThreshold.asStateFlow()
    
    private val _usagePercentage = MutableStateFlow(0f)
    val usagePercentage: StateFlow<Float> = _usagePercentage.asStateFlow()
    
    private val _shouldShowWarning = MutableStateFlow(false)
    val shouldShowWarning: StateFlow<Boolean> = _shouldShowWarning.asStateFlow()
    
    /**
     * Estima tokens a partir de texto
     * Fórmula aproximada: caracteres / 4
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        // Contar palabras y caracteres para mejor estimación
        val wordCount = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val charCount = text.length
        
        // Fórmula híbrida: promedio entre conteo por caracteres y por palabras
        val byChars = charCount / CHARS_PER_TOKEN
        val byWords = wordCount * 1.3f // Palabras en español ~1.3 tokens promedio
        
        return ((byChars + byWords) / 2).toInt()
    }
    
    /**
     * Añade tokens al contador actual
     */
    fun addTokens(text: String) {
        val tokens = estimateTokens(text)
        val newTotal = _currentTokens.value + tokens
        _currentTokens.value = newTotal
        updateUsagePercentage()
        
        // Guardar en preferencias
        prefs.edit().putInt(KEY_CURRENT_TOKENS, newTotal).apply()
        
        // Verificar si debe mostrar advertencia
        checkWarningThreshold()
    }
    
    /**
     * Calcula tokens de una conversación completa (historial)
     */
    fun calculateConversationTokens(messages: List<ChatMessage>): Int {
        return messages.sumOf { estimateTokens(it.content) }
    }
    
    /**
     * Estima tokens restantes disponibles
     */
    fun getRemainingTokens(): Int {
        return maxOf(0, _maxTokens.value - _currentTokens.value)
    }
    
    /**
     * Verifica si está cerca del límite (para deshabilitar envío)
     */
    fun isNearLimit(): Boolean {
        return _usagePercentage.value >= 0.95f // 95% del límite
    }
    
    /**
     * Actualiza el umbral de advertencia
     */
    fun setWarningThreshold(percentage: Float) {
        val clampedPercentage = percentage.coerceIn(0.5f, 0.95f)
        _warningThreshold.value = clampedPercentage
        prefs.edit().putFloat(KEY_WARNING_THRESHOLD, clampedPercentage).apply()
    }
    
    /**
     * Actualiza el límite máximo de tokens según el modelo
     */
    fun setMaxTokens(modelName: String, totalRamGb: Int? = null) {
        val modelLimit = resolveModelContextTokens(modelName)
        val ramLimit = totalRamGb?.let { resolveRamBasedLimit(it) } ?: modelLimit
        val maxTokens = minOf(modelLimit, ramLimit).coerceAtLeast(1024)
        _maxTokens.value = maxTokens
        prefs.edit().putInt(KEY_MAX_TOKENS, maxTokens).apply()
        updateUsagePercentage()
    }
    
    /**
     * Reinicia el contador de tokens (nueva conversación)
     */
    fun resetCounter() {
        _currentTokens.value = 0
        _usagePercentage.value = 0f
        _shouldShowWarning.value = false
        prefs.edit().putInt(KEY_CURRENT_TOKENS, 0).apply()
    }

    /**
     * Establece el total de tokens (para cuando se carga un historial)
     */
    fun setTotalTokens(tokens: Int) {
        _currentTokens.value = tokens
        updateUsagePercentage()
        prefs.edit().putInt(KEY_CURRENT_TOKENS, tokens).apply()
        checkWarningThreshold()
    }
    
    /**
     * Marca que la advertencia fue mostrada
     */
    fun markWarningShown() {
        _shouldShowWarning.value = false
        prefs.edit().putLong(KEY_LAST_WARNING_TIME, System.currentTimeMillis()).apply()
    }
    
    /**
     * Obtiene mensaje de advertencia según el porcentaje
     */
    fun getWarningMessage(): String {
        val percentage = (_usagePercentage.value * 100).toInt()
        val remaining = getRemainingTokens()
        
        return when {
            percentage >= 95 -> "⚠️ ¡Límite crítico! Solo quedan ~$remaining tokens. Guarda o reinicia la conversación."
            percentage >= 85 -> "⚡ Atención: Has usado el $percentage% de tokens (~$remaining restantes)"
            percentage >= 75 -> "📊 $percentage% de tokens utilizados"
            else -> ""
        }
    }
    
    /**
     * Obtiene color según el nivel de uso
     */
    fun getUsageColor(): String {
        return when {
            _usagePercentage.value >= 0.95f -> "#FF0000" // Rojo
            _usagePercentage.value >= 0.85f -> "#FFA500" // Naranja
            _usagePercentage.value >= 0.75f -> "#FFFF00" // Amarillo
            else -> "#00FF00" // Verde
        }
    }
    
    private fun updateUsagePercentage() {
        val percentage = if (_maxTokens.value > 0) {
            _currentTokens.value.toFloat() / _maxTokens.value.toFloat()
        } else {
            0f
        }
        _usagePercentage.value = percentage.coerceIn(0f, 1f)
    }
    
    private fun checkWarningThreshold() {
        val percentage = _usagePercentage.value
        val threshold = _warningThreshold.value
        
        // Solo mostrar si supera el umbral y no se ha mostrado recientemente
        if (percentage >= threshold) {
            val lastWarning = prefs.getLong(KEY_LAST_WARNING_TIME, 0)
            val timeSinceLastWarning = System.currentTimeMillis() - lastWarning
            
            if (timeSinceLastWarning > WARNING_COOLDOWN_MS) {
                _shouldShowWarning.value = true
            }
        }
    }
    
    /**
     * Obtiene estadísticas detalladas
     */
    fun getTokenStats(): TokenStats {
        return TokenStats(
            currentTokens = _currentTokens.value,
            maxTokens = _maxTokens.value,
            remainingTokens = getRemainingTokens(),
            usagePercentage = _usagePercentage.value,
            warningThreshold = _warningThreshold.value,
            isNearLimit = isNearLimit()
        )
    }

    private fun resolveModelContextTokens(modelName: String): Int {
        val normalized = modelName.lowercase()
        return when {
            normalized.contains("qwen2.5-0.5b") -> 32768
            normalized.contains("qwen2.5-1.5b") -> 32768
            normalized.contains("qwen2.5-3b") -> 32768
            normalized.contains("gemma-2") -> 8192
            normalized.contains("phi-3") && normalized.contains("4k") -> 4096
            normalized.contains("mistral-7b") -> 8192
            normalized.contains("llama-3") -> 8192
            else -> DEFAULT_MAX_TOKENS
        }
    }

    private fun resolveRamBasedLimit(totalRamGb: Int): Int {
        return when {
            totalRamGb <= 0 -> DEFAULT_MAX_TOKENS
            totalRamGb <= 3 -> 2048
            totalRamGb <= 4 -> 4096
            totalRamGb <= 6 -> 8192
            totalRamGb <= 8 -> 16384
            totalRamGb <= 12 -> 24576
            else -> 32768
        }
    }
}

/**
 * Estadísticas de uso de tokens
 */
data class TokenStats(
    val currentTokens: Int,
    val maxTokens: Int,
    val remainingTokens: Int,
    val usagePercentage: Float,
    val warningThreshold: Float,
    val isNearLimit: Boolean
)
