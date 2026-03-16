package com.dnklabs.asistenteialocal.ui.screens.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Gestiona el reconocimiento de voz mediante SpeechRecognizer de Android.
 */
class AudioRecorder(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false
    private var onTranscriptionComplete: ((String) -> Unit)? = null

    /**
     * Verifica si tiene permisos de grabación
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Inicia el reconocimiento de voz
     */
    fun startRecording(onComplete: (String) -> Unit) {
        if (!hasPermission()) {
            onComplete("Error: Permiso de grabación no concedido")
            return
        }

        if (isRecording) return

        onTranscriptionComplete = onComplete
        isRecording = true

        // Ejecutar siempre en el hilo principal
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            setupRecognizer()
        }
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onTranscriptionComplete?.invoke("Error: Reconocimiento de voz no disponible en este dispositivo")
            isRecording = false
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isRecording = false
            }

            override fun onError(error: Int) {
                isRecording = false
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                    SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permisos insuficientes"
                    SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo de espera de red"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No se encontró coincidencia"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Servicio ocupado"
                    SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz"
                    else -> "Error desconocido"
                }
                // Si es NO_MATCH simplemente devolvemos vacío para no molestar al usuario con un error explícito
                if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                    onTranscriptionComplete?.invoke("")
                } else {
                    onTranscriptionComplete?.invoke("Error: $message")
                }
                release()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onTranscriptionComplete?.invoke(matches[0])
                } else {
                    onTranscriptionComplete?.invoke("")
                }
                release()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Podríamos mostrar resultados parciales si quisiéramos tiempo real
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    /**
     * Detiene el reconocimiento
     */
    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        speechRecognizer?.stopListening()
    }

    /**
     * Cancela el reconocimiento
     */
    fun cancelRecording() {
        isRecording = false
        speechRecognizer?.cancel()
        release()
    }

    /**
     * Libera recursos
     */
    fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isRecording = false
    }

    fun isRecording(): Boolean = isRecording
}
