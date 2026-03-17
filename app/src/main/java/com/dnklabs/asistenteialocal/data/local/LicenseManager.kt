package com.dnklabs.asistenteialocal.data.local

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Gestiona la verificación de licencia de la aplicación.
 * La licencia es válida hasta el 30 de abril de 2026.
 */
class LicenseManager(context: Context) {

    companion object {
        // Fecha de vencimiento: 30 de abril de 2026
        private const val EXPIRY_DATE_STRING = "30/04/2026"
        private const val DATE_FORMAT = "dd/MM/yyyy"
        
        private const val PREFS_NAME = "license_prefs"
        private const val KEY_FIRST_USE_DATE = "first_use_date"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_LICENSE_KEY = "license_key"
        
        // Contraseñas válidas
        private val VALID_KEYS = setOf("mañolandia", "DNKLABS", "dnklabsautomatizaciones")
        
        // Fecha de expiry como timestamp
        private val EXPIRY_DATE: Date by lazy {
            SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).parse(EXPIRY_DATE_STRING) 
                ?: throw IllegalStateException("Invalid date format")
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Verifica si la licencia actual es válida
     */
    fun isLicenseValid(): Boolean {
        val currentDate = Date()
        return currentDate.before(EXPIRY_DATE) || currentDate == EXPIRY_DATE
    }
    
    /**
     * Verifica si la contraseña introducida es válida
     */
    fun isValidKey(key: String): Boolean {
        return key.trim().lowercase() in VALID_KEYS
    }
    
    /**
     * Guarda la licencia introducida
     */
    fun saveLicenseKey(key: String): Boolean {
        if (!isValidKey(key)) return false
        
        val editor = prefs.edit()
        editor.putString(KEY_LICENSE_KEY, key.trim())
        editor.apply()
        return true
    }
    
    /**
     * Verifica si ya se ha introducido una licencia
     */
    fun hasLicenseKey(): Boolean {
        return prefs.contains(KEY_LICENSE_KEY)
    }
    
    /**
     * Obtiene la licencia guardada
     */
    fun getSavedLicenseKey(): String? {
        return prefs.getString(KEY_LICENSE_KEY, null)
    }
    
    /**
     * Obtiene la fecha de vencimiento en formato legible
     */
    fun getExpiryDateFormatted(): String {
        val formatter = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es", "ES"))
        return formatter.format(EXPIRY_DATE)
    }
    
    /**
     * Obtiene los días restantes hasta el vencimiento
     * Retorna 0 si ya expiró
     */
    fun getDaysRemaining(): Int {
        val currentDate = Date()
        if (currentDate.after(EXPIRY_DATE)) {
            return 0
        }
        
        val diffMillis = EXPIRY_DATE.time - currentDate.time
        return TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
    }
    
    /**
     * Obtiene los días restantes como texto legible
     */
    fun getDaysRemainingText(): String {
        val days = getDaysRemaining()
        return when {
            days <= 0 -> "Expirada"
            days == 1 -> "1 día restante"
            days < 30 -> "$days días restantes"
            days < 365 -> "${days / 30} meses restantes"
            else -> "${days / 365} años restantes"
        }
    }
    
    /**
     * Registra la primera vez que se usa la app (anti-manipulación)
     */
    fun registerFirstUse() {
        if (!prefs.contains(KEY_FIRST_USE_DATE)) {
            val editor = prefs.edit()
            editor.putLong(KEY_FIRST_USE_DATE, System.currentTimeMillis())
            editor.putString(KEY_INSTALLATION_ID, generateInstallationId())
            editor.apply()
        }
    }
    
    /**
     * Obtiene la fecha del primer uso
     */
    fun getFirstUseDate(): Long {
        return prefs.getLong(KEY_FIRST_USE_DATE, 0L)
    }
    
    /**
     * Verifica si es el primer uso de la app
     */
    fun isFirstUse(): Boolean {
        return getFirstUseDate() == 0L
    }
    
    /**
     * Genera un ID único de instalación
     */
    private fun generateInstallationId(): String {
        return java.util.UUID.randomUUID().toString()
    }
    
    /**
     * Obtiene el estado de la licencia para debugging
     */
    fun getLicenseStatus(): LicenseStatus {
        return LicenseStatus(
            isValid = isLicenseValid(),
            expiryDate = getExpiryDateFormatted(),
            daysRemaining = getDaysRemaining(),
            firstUseRegistered = !isFirstUse(),
            hasLicenseKey = hasLicenseKey()
        )
    }
}

/**
 * Estado de la licencia
 */
data class LicenseStatus(
    val isValid: Boolean,
    val expiryDate: String,
    val daysRemaining: Int,
    val firstUseRegistered: Boolean,
    val hasLicenseKey: Boolean = false
)
