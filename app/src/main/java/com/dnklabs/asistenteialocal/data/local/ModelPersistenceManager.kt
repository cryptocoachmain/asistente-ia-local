package com.dnklabs.asistenteialocal.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.HashSet
import java.util.Set

/**
 * Gestiona la persistencia del modelo seleccionado y modelos instalados
 */
class ModelPersistenceManager(context: Context) {

    companion object {
        private const val TAG = "ModelPersistence"
        private const val PREFS_NAME = "model_persistence"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_MODEL_INITIALIZED = "model_initialized"
        private const val KEY_INSTALLED_MODELS = "installed_models"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _selectedModel = MutableStateFlow<String?>(prefs.getString(KEY_SELECTED_MODEL, null))
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    init {
        val savedModel = getSelectedModel()
        val installed = getInstalledModels()
        Log.d(TAG, "ModelPersistenceManager init - modelo guardado: $savedModel, modelos instalados: $installed")
    }

    /**
     * Guarda el modelo seleccionado
     * @return true si se guardó exitosamente
     */
    fun saveSelectedModel(modelName: String): Boolean {
        Log.d(TAG, "Guardando modelo seleccionado: $modelName")
        _selectedModel.value = modelName
        val result = prefs.edit()
            .putString(KEY_SELECTED_MODEL, modelName)
            .putBoolean(KEY_MODEL_INITIALIZED, false) // Reset initialization flag
            .commit()
        Log.d(TAG, "Modelo guardado exitosamente: $result")
        return result
    }

    /**
     * Obtiene el modelo seleccionado
     */
    fun getSelectedModel(): String? {
        val model = prefs.getString(KEY_SELECTED_MODEL, null)
        Log.d(TAG, "Obteniendo modelo seleccionado: $model")
        return model
    }

    /**
     * Verifica si hay un modelo configurado
     */
    fun hasModelConfigured(): Boolean {
        val configured = !prefs.getString(KEY_SELECTED_MODEL, null).isNullOrBlank()
        Log.d(TAG, "Verificando si hay modelo configurado: $configured")
        return configured
    }

    /**
     * Marca el modelo como inicializado
     */
    fun markModelInitialized() {
        Log.d(TAG, "Marcando modelo como inicializado")
        val result = prefs.edit()
            .putBoolean(KEY_MODEL_INITIALIZED, true)
            .commit()
        Log.d(TAG, "Modelo marcado como inicializado: $result")
    }

    /**
     * Verifica si el modelo fue inicializado previamente
     */
    fun wasModelInitialized(): Boolean {
        val initialized = prefs.getBoolean(KEY_MODEL_INITIALIZED, false)
        Log.d(TAG, "Verificando si el modelo fue inicializado previamente: $initialized")
        return initialized
    }

    /**
     * Limpia la configuración del modelo
     */
    fun clearModel() {
        Log.d(TAG, "Limpiando modelo seleccionado")
        _selectedModel.value = null
        val result = prefs.edit()
            .remove(KEY_SELECTED_MODEL)
            .remove(KEY_MODEL_INITIALIZED)
            .commit()
        Log.d(TAG, "Modelo limpiado: $result")
    }

    /**
     * Guarda el conjunto de modelos instalados
     * @return true si se guardó exitosamente
     */
    fun saveInstalledModels(installedModels: kotlin.collections.Set<String>): Boolean {
        Log.d(TAG, "Guardando modelos instalados: $installedModels")
        // Convert Set to JSON string to avoid SharedPreferences type issues
        val json = org.json.JSONArray(installedModels.toList()).toString()
        val result = prefs.edit()
            .putString(KEY_INSTALLED_MODELS, json)
            .commit()
        Log.d(TAG, "Modelos instalados guardados exitosamente: $result")
        return result
    }

    /**
     * Obtiene el conjunto de modelos instalados
     * Si no hay ninguno guardado, devuelve un conjunto vacío
     */
    fun getInstalledModels(): kotlin.collections.Set<String> {
        val json = prefs.getString(KEY_INSTALLED_MODELS, null)
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).map { array.getString(it) }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing installed models", e)
            emptySet()
        }
    }
}