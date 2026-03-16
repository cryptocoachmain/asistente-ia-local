package com.dnklabs.asistenteialocal.ui.screens.chat

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Gestiona el Text-to-Speech (TTS) para leer respuestas de la IA
 */
class TextToSpeechManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onComplete: (() -> Unit)? = null

    fun initialize() {
        if (isInitialized) return

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                configureTts()
            }
        }
    }

    private fun configureTts() {
        tts?.apply {
            // Configurar idioma español
            val result = setLanguage(Locale("es", "ES"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback a inglés si español no está disponible
                setLanguage(Locale.US)
            }

            // Configurar velocidad y tono
            setSpeechRate(1.0f) // Velocidad normal
            setPitch(1.0f)      // Tono normal

            // Listener para saber cuando termina de hablar
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    onComplete?.invoke()
                }
                override fun onError(utteranceId: String?) {}
            })
        }
    }

    /**
     * Lee un texto en voz alta
     */
    fun speak(text: String, utteranceId: String = "msg_${System.currentTimeMillis()}") {
        if (!isInitialized) {
            initialize()
            return
        }

        // Detener cualquier lectura anterior
        stop()

        // Leer el nuevo texto
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Establece callback para cuando termina de hablar
     */
    fun setOnCompleteListener(listener: () -> Unit) {
        onComplete = listener
    }

    /**
     * Detiene la lectura actual
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Libera recursos
     */
    fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    /**
     * Verifica si el TTS está listo
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Obtiene lista de voces disponibles
     */
    fun getAvailableVoices(): List<android.speech.tts.Voice>? {
        return tts?.voices?.toList()
    }
}
