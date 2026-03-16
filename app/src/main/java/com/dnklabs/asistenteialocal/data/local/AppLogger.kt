package com.dnklabs.asistenteialocal.data.local

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Logger que escribe tanto en logcat como en un archivo en Documents/AILocal
 */
object AppLogger {
    private const val DEFAULT_TAG = "AsistenteIA"
    private const val LOG_DIR = "AILocal"
    private const val LOG_FILE = "app.log"
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5MB max
    
    private var logFile: File? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    
    /**
     * Inicializa el logger con el contexto de la aplicación
     * Debe llamarse desde Application.onCreate()
     */
    fun init(context: Context) {
        try {
            val documentsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ usa context.getExternalFilesDir que no necesita permisos
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            } else {
                // Para versiones anteriores
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), LOG_DIR)
            }
            
            documentsDir?.let { dir ->
                if (!dir.exists()) dir.mkdirs()
                logFile = File(dir, LOG_FILE)
                
                // Si el archivo es muy grande, hacer backup y crear nuevo
                if (logFile!!.exists() && logFile!!.length() > MAX_LOG_SIZE) {
                    val backupName = "app_${fileDateFormat.format(Date())}.log"
                    val backupFile = File(dir, backupName)
                    logFile!!.renameTo(backupFile)
                    logFile = File(dir, LOG_FILE)
                }
                
                // Escribir header de nueva sesión
                writeToFile("\n========== NUEVA SESIÓN: ${dateFormat.format(Date())} ==========\n")
            }
        } catch (e: Exception) {
            Log.e(DEFAULT_TAG, "Error inicializando AppLogger: ${e.message}")
        }
    }
    
    private fun writeToFile(message: String) {
        logFile?.let { file ->
            executor.execute {
                try {
                    FileWriter(file, true).use { writer ->
                        writer.write(message)
                    }
                } catch (e: Exception) {
                    Log.e(DEFAULT_TAG, "Error escribiendo al log: ${e.message}")
                }
            }
        }
    }
    
    private fun formatMessage(level: String, tag: String, message: String): String {
        return "${dateFormat.format(Date())} $level/$tag: $message\n"
    }
    
    fun d(tag: String = DEFAULT_TAG, message: String) {
        Log.d(tag, message)
        writeToFile(formatMessage("D", tag, message))
    }
    
    fun d(tag: String = DEFAULT_TAG, message: String, throwable: Throwable) {
        Log.d(tag, message, throwable)
        writeToFile(formatMessage("D", tag, "$message\n${getStackTrace(throwable)}"))
    }
    
    fun i(tag: String = DEFAULT_TAG, message: String) {
        Log.i(tag, message)
        writeToFile(formatMessage("I", tag, message))
    }
    
    fun i(tag: String = DEFAULT_TAG, message: String, throwable: Throwable) {
        Log.i(tag, message, throwable)
        writeToFile(formatMessage("I", tag, "$message\n${getStackTrace(throwable)}"))
    }
    
    fun w(tag: String = DEFAULT_TAG, message: String) {
        Log.w(tag, message)
        writeToFile(formatMessage("W", tag, message))
    }
    
    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable) {
        Log.w(tag, message, throwable)
        writeToFile(formatMessage("W", tag, "$message\n${getStackTrace(throwable)}"))
    }
    
    fun e(tag: String = DEFAULT_TAG, message: String) {
        Log.e(tag, message)
        writeToFile(formatMessage("E", tag, message))
    }
    
    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
        writeToFile(formatMessage("E", tag, "$message\n${getStackTrace(throwable)}"))
    }
    
    fun v(tag: String = DEFAULT_TAG, message: String) {
        Log.v(tag, message)
        writeToFile(formatMessage("V", tag, message))
    }
    
    fun v(tag: String = DEFAULT_TAG, message: String, throwable: Throwable) {
        Log.v(tag, message, throwable)
        writeToFile(formatMessage("V", tag, "$message\n${getStackTrace(throwable)}"))
    }
    
    private fun getStackTrace(t: Throwable): String {
        val sw = PrintWriter(StringWriter())
        t.printStackTrace(sw)
        return sw.toString()
    }
    
    /**
     * Obtiene la ruta del archivo de log
     */
    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }
    
    /**
     * Obtiene el contenido del log
     */
    fun getLogContent(): String? {
        return try {
            logFile?.readText()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Borra todo el contenido del log
     */
    fun clearLog() {
        executor.execute {
            try {
                logFile?.let { file ->
                    FileWriter(file, false).use { writer ->
                        writer.write("")
                    }
                }
                writeToFile("========== LOG BORRADO: ${dateFormat.format(Date())} ==========\n")
            } catch (e: Exception) {
                Log.e(DEFAULT_TAG, "Error borrando log: ${e.message}")
            }
        }
    }
    
    /**
     * Borra el archivo de log completamente (elimina el archivo)
     */
    fun deleteLogFile() {
        executor.execute {
            try {
                logFile?.delete()
                // Recrear archivo vacío
                logFile?.createNewFile()
                writeToFile("========== LOG ELIMINADO Y RECREADO: ${dateFormat.format(Date())} ==========\n")
            } catch (e: Exception) {
                Log.e(DEFAULT_TAG, "Error eliminando archivo de log: ${e.message}")
            }
        }
    }
}
