package com.dnklabs.asistenteialocal.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dnklabs.asistenteialocal.data.local.AppLogger
import java.security.SecureRandom
import java.security.MessageDigest

/**
 * Gestiona la seguridad de la aplicación:
 * - PIN de acceso
 * - Encriptación de datos sensibles
 * - Verificación de integridad
 */
class SecurityManager(private val context: Context) {
    
    companion object {
        private const val PREFS_FILE = "security_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_SALT = "salt"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val MAX_PIN_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 5 * 60 * 1000 // 5 minutos
    }
    
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private val encryptedPrefs: EncryptedSharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("security_plain", Context.MODE_PRIVATE)
    }
    
    /**
     * Verifica si es el primer lanzamiento (no hay PIN configurado)
     */
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }
    
    /**
     * Marca que ya se completó la configuración inicial
     */
    fun markSetupComplete() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }
    
    /**
     * Verifica si hay PIN configurado
     */
    fun isPinConfigured(): Boolean {
        return encryptedPrefs.contains(KEY_PIN_HASH)
    }
    
    /**
     * Configura un nuevo PIN
     * @return true si se configuró correctamente
     */
    fun setupPin(pin: String): Boolean {
        if (pin.length !in 4..6) {
            return false
        }
        
        val salt = generateSalt()
        val pinHash = hashPin(pin, salt)
        
        encryptedPrefs.edit()
            .putString(KEY_PIN_HASH, pinHash)
            .putString(KEY_SALT, salt)
            .apply()
        
        // Resetear contador de intentos
        prefs.edit().remove("pin_attempts").remove("lockout_end").apply()
        
        AppLogger.i("SecurityManager", "PIN configurado correctamente")
        return true
    }
    
    /**
     * Verifica si el PIN introducido es correcto
     * @return true si el PIN es válido
     */
    fun verifyPin(pin: String): Boolean {
        // Verificar si está bloqueado
        if (isLockedOut()) {
            return false
        }
        
        val storedHash = encryptedPrefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = encryptedPrefs.getString(KEY_SALT, null) ?: return false
        
        val inputHash = hashPin(pin, salt)
        
        if (inputHash == storedHash) {
            // PIN correcto - resetear intentos
            prefs.edit().remove("pin_attempts").apply()
            return true
        } else {
            // PIN incorrecto - incrementar contador
            val attempts = prefs.getInt("pin_attempts", 0) + 1
            prefs.edit().putInt("pin_attempts", attempts).apply()
            
            if (attempts >= MAX_PIN_ATTEMPTS) {
                // Bloquear por 5 minutos
                val lockoutEnd = System.currentTimeMillis() + LOCKOUT_DURATION_MS
                prefs.edit().putLong("lockout_end", lockoutEnd).apply()
                AppLogger.w("SecurityManager", "PIN bloqueado por $MAX_PIN_ATTEMPTS intentos fallidos")
            }
            
            return false
        }
    }
    
    /**
     * Verifica si el acceso está bloqueado por intentos fallidos
     */
    fun isLockedOut(): Boolean {
        val lockoutEnd = prefs.getLong("lockout_end", 0)
        return if (lockoutEnd > System.currentTimeMillis()) {
            true
        } else {
            if (lockoutEnd > 0) {
                // Desbloquear automáticamente
                prefs.edit().remove("lockout_end").remove("pin_attempts").apply()
            }
            false
        }
    }
    
    /**
     * Obtiene tiempo restante de bloqueo en segundos
     */
    fun getLockoutRemainingSeconds(): Int {
        val lockoutEnd = prefs.getLong("lockout_end", 0)
        val remaining = lockoutEnd - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 1000).toInt() else 0
    }
    
    /**
     * Obtiene número de intentos restantes antes del bloqueo
     */
    fun getRemainingAttempts(): Int {
        val attempts = prefs.getInt("pin_attempts", 0)
        return MAX_PIN_ATTEMPTS - attempts
    }
    
    /**
     * Cambia el PIN (requiere PIN actual)
     * @return true si se cambió correctamente
     */
    fun changePin(currentPin: String, newPin: String): Boolean {
        if (!verifyPin(currentPin)) {
            return false
        }
        return setupPin(newPin)
    }
    
    /**
     * Genera una clave para encriptar la base de datos
     * Basada en el PIN + salt
     */
    fun getDatabaseEncryptionKey(): String {
        val salt = encryptedPrefs.getString(KEY_SALT, "") ?: ""
        val pinHash = encryptedPrefs.getString(KEY_PIN_HASH, "") ?: ""
        return hashPin(pinHash, salt + "_db_key")
    }
    
    /**
     * Genera salt aleatorio
     */
    private fun generateSalt(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Hashea el PIN con SHA-256 + salt
     */
    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = (pin + salt).toByteArray()
        val hash = digest.digest(input)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Elimina toda la configuración de seguridad (para reset)
     */
    fun clearSecurity() {
        encryptedPrefs.edit().clear().apply()
        prefs.edit().clear().apply()
        AppLogger.i("SecurityManager", "Seguridad reiniciada")
    }
}