package com.dnklabs.asistenteialocal.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestiona los parámetros de configuración del modelo LLM
 * 
 * Parámetros disponibles:
 * - Temperature: Controla la creatividad/aleatoriedad (0.0 - 2.0)
 * - Max Tokens: Longitud máxima de respuesta (50 - 10000)
 * - Top P: Muestreo nucleus (0.0 - 1.0)
 * - Top K: Número de tokens más probables a considerar (1 - 100)
 * - Repeat Penalty: Penalización por repeticiones (1.0 - 2.0)
 * - Context Length: Tamaño del contexto/memoria (512 - 8192)
 */
class ModelSettingsManager(context: Context) {

    companion object {
        const val PREFS_NAME = "model_settings"
        
        // Keys
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_MAX_TOKENS = "max_tokens"
        const val KEY_TOP_P = "top_p"
        const val KEY_TOP_K = "top_k"
        const val KEY_REPEAT_PENALTY = "repeat_penalty"
        const val KEY_CONTEXT_LENGTH = "context_length"
        
        // Valores por defecto
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_MAX_TOKENS = 1024
        const val DEFAULT_TOP_P = 0.9f
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_REPEAT_PENALTY = 1.1f
        const val DEFAULT_CONTEXT_LENGTH = 2048
        
        // Rangos válidos
        const val MIN_TEMPERATURE = 0.0f
        const val MAX_TEMPERATURE = 2.0f
        const val MIN_MAX_TOKENS = 50
        const val MAX_MAX_TOKENS = 10000
        const val MIN_TOP_P = 0.0f
        const val MAX_TOP_P = 1.0f
        const val MIN_TOP_K = 1
        const val MAX_TOP_K = 100
        const val MIN_REPEAT_PENALTY = 1.0f
        const val MAX_REPEAT_PENALTY = 2.0f
        const val MIN_CONTEXT_LENGTH = 512
        const val MAX_CONTEXT_LENGTH = 8192
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _temperature = MutableStateFlow(
        prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE)
    )
    val temperature: StateFlow<Float> = _temperature.asStateFlow()
    
    private val _maxTokens = MutableStateFlow(
        prefs.getInt(KEY_MAX_TOKENS, DEFAULT_MAX_TOKENS)
    )
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()
    
    private val _topP = MutableStateFlow(
        prefs.getFloat(KEY_TOP_P, DEFAULT_TOP_P)
    )
    val topP: StateFlow<Float> = _topP.asStateFlow()
    
    private val _topK = MutableStateFlow(
        prefs.getInt(KEY_TOP_K, DEFAULT_TOP_K)
    )
    val topK: StateFlow<Int> = _topK.asStateFlow()
    
    private val _repeatPenalty = MutableStateFlow(
        prefs.getFloat(KEY_REPEAT_PENALTY, DEFAULT_REPEAT_PENALTY)
    )
    val repeatPenalty: StateFlow<Float> = _repeatPenalty.asStateFlow()
    
    private val _contextLength = MutableStateFlow(
        prefs.getInt(KEY_CONTEXT_LENGTH, DEFAULT_CONTEXT_LENGTH)
    )
    val contextLength: StateFlow<Int> = _contextLength.asStateFlow()
    
    /**
     * Establece la temperatura del modelo
     * Valores bajos (0.0-0.3): Respuestas más deterministas y coherentes
     * Valores medios (0.4-0.7): Balance entre creatividad y coherencia
     * Valores altos (0.8-2.0): Respuestas más creativas pero menos predecibles
     */
    fun setTemperature(value: Float) {
        val clampedValue = value.coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE)
        _temperature.value = clampedValue
        prefs.edit().putFloat(KEY_TEMPERATURE, clampedValue).apply()
    }
    
    /**
     * Establece el máximo de tokens por respuesta
     * Afecta la longitud máxima de las respuestas generadas
     */
    fun setMaxTokens(value: Int) {
        val clampedValue = value.coerceIn(MIN_MAX_TOKENS, MAX_MAX_TOKENS)
        _maxTokens.value = clampedValue
        prefs.edit().putInt(KEY_MAX_TOKENS, clampedValue).apply()
    }
    
    /**
     * Establece el valor de Top P (nucleus sampling)
     * Valores bajos: Solo considera tokens de alta probabilidad
     * Valores altos: Considera más tokens, genera mayor diversidad
     */
    fun setTopP(value: Float) {
        val clampedValue = value.coerceIn(MIN_TOP_P, MAX_TOP_P)
        _topP.value = clampedValue
        prefs.edit().putFloat(KEY_TOP_P, clampedValue).apply()
    }
    
    /**
     * Establece el valor de Top K
     * Número de tokens más probables que se consideran en cada paso
     * Valores bajos: Solo las palabras más probables
     * Valores altos: Más variedad pero menor coherencia
     */
    fun setTopK(value: Int) {
        val clampedValue = value.coerceIn(MIN_TOP_K, MAX_TOP_K)
        _topK.value = clampedValue
        prefs.edit().putInt(KEY_TOP_K, clampedValue).apply()
    }
    
    /**
     * Establece el valor de Repeat Penalty
     * Penalización por repetir palabras
     * Valores bajos (1.0): Permite repeticiones
     * Valores altos (>1.1): Evita repeticiones
     */
    fun setRepeatPenalty(value: Float) {
        val clampedValue = value.coerceIn(MIN_REPEAT_PENALTY, MAX_REPEAT_PENALTY)
        _repeatPenalty.value = clampedValue
        prefs.edit().putFloat(KEY_REPEAT_PENALTY, clampedValue).apply()
    }
    
    /**
     * Establece la longitud del contexto
     * Cuánta "memoria" de la conversación se mantiene
     * Valores bajos: Menos memoria, más rápido
     * Valores altos: Más contexto, usa más RAM
     */
    fun setContextLength(value: Int) {
        val clampedValue = value.coerceIn(MIN_CONTEXT_LENGTH, MAX_CONTEXT_LENGTH)
        _contextLength.value = clampedValue
        prefs.edit().putInt(KEY_CONTEXT_LENGTH, clampedValue).apply()
    }
    
    /**
     * Restaura los valores por defecto
     */
    fun resetToDefaults() {
        setTemperature(DEFAULT_TEMPERATURE)
        setMaxTokens(DEFAULT_MAX_TOKENS)
        setTopP(DEFAULT_TOP_P)
        setTopK(DEFAULT_TOP_K)
        setRepeatPenalty(DEFAULT_REPEAT_PENALTY)
        setContextLength(DEFAULT_CONTEXT_LENGTH)
    }
    
    /**
     * Obtiene todos los parámetros actuales
     */
    fun getSettings(): ModelSettings {
        return ModelSettings(
            temperature = _temperature.value,
            maxTokens = _maxTokens.value,
            topP = _topP.value,
            topK = _topK.value,
            repeatPenalty = _repeatPenalty.value,
            contextLength = _contextLength.value
        )
    }
    
    /**
     * Obtiene una descripción de la temperatura actual
     */
    fun getTemperatureDescription(): String {
        return when {
            _temperature.value <= 0.3f -> "Muy conservador - Respuestas predecibles"
            _temperature.value <= 0.6f -> "Conservador - Buen balance"
            _temperature.value <= 0.9f -> "Creativo - Más variabilidad"
            else -> "Muy creativo - Respuestas impredecibles"
        }
    }
    
    /**
     * Obtiene una descripción del max tokens actual
     */
    fun getMaxTokensDescription(): String {
        return when {
            _maxTokens.value <= 256 -> "Respuestas cortas y concisas"
            _maxTokens.value <= 1024 -> "Respuestas moderadas"
            _maxTokens.value <= 2048 -> "Respuestas largas y detalladas"
            else -> "Respuestas muy extensas"
        }
    }
    
    /**
     * Obtiene una descripción de Top K actual
     */
    fun getTopKDescription(): String {
        return when {
            _topK.value <= 10 -> "Muy selectivo - Solo palabras muy probables"
            _topK.value <= 40 -> "Balance - Recomendado para mayoría de casos"
            _topK.value <= 80 -> "Diversificado - Más opciones consideradas"
            else -> "Máxima diversidad - Puede perder coherencia"
        }
    }
    
    /**
     * Obtiene una descripción del Repeat Penalty actual
     */
    fun getRepeatPenaltyDescription(): String {
        return when {
            _repeatPenalty.value <= 1.0f -> "Sin penalización - Permite repeticiones"
            _repeatPenalty.value <= 1.1f -> "Penalización leve - Balance óptimo"
            _repeatPenalty.value <= 1.3f -> "Penalización media - Evita repeticiones"
            else -> "Penalización alta - Respuestas muy diversas"
        }
    }
    
    /**
     * Obtiene una descripción de la longitud de contexto actual
     */
    fun getContextLengthDescription(): String {
        return when {
            _contextLength.value <= 1024 -> "Contexto corto - Rápido, menos memoria"
            _contextLength.value <= 2048 -> "Contexto medio - Balance recomendado"
            _contextLength.value <= 4096 -> "Contexto largo - Más memoria de conversación"
            else -> "Contexto muy largo - Máxima memoria (más RAM)"
        }
    }
}

/**
 * Configuración del modelo
 */
data class ModelSettings(
    val temperature: Float,
    val maxTokens: Int,
    val topP: Float,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val contextLength: Int = 2048
)
