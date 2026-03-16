package com.dnklabs.asistenteialocal.ui.screens.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Estados de la UI para OCR
 */
sealed class OcrUiState {
    object Idle : OcrUiState()
    object Processing : OcrUiState()
    data class Success(val text: String, val imageUri: Uri?) : OcrUiState()
    data class Error(val message: String) : OcrUiState()
}

/**
 * ViewModel para gestionar OCR con ML Kit
 */
class OCRViewModel(private val context: Context? = null) : ViewModel() {

    init {
        // Initialize PDFBox for Android
        context?.let {
            PDFBoxResourceLoader.init(it)
        }
    }

    private val _uiState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _wordCount = MutableStateFlow(0)
    val wordCount: StateFlow<Int> = _wordCount.asStateFlow()

    // ML Kit Text Recognizer (on-device, no requiere internet)
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Procesa una imagen para extraer texto
     */
    fun processImage(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = OcrUiState.Processing

            try {
                val bitmap = loadBitmapFromUri(context, imageUri)
                if (bitmap == null) {
                    _uiState.value = OcrUiState.Error("No se pudo cargar la imagen")
                    return@launch
                }

                val image = InputImage.fromBitmap(bitmap, 0)
                val result = textRecognizer.process(image).await()

                val text = result.text
                _recognizedText.value = text
                _wordCount.value = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size

                if (text.isBlank()) {
                    _uiState.value = OcrUiState.Error("No se detectó texto en la imagen")
                } else {
                    _uiState.value = OcrUiState.Success(text, imageUri)
                }

            } catch (e: Exception) {
                Log.e("OCR", "Error processing image", e)
                _uiState.value = OcrUiState.Error("Error: ${e.message}")
            }
        }
    }

    /**
     * Procesa un Bitmap directamente (desde cámara)
     */
    fun processBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = OcrUiState.Processing

            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val result = textRecognizer.process(image).await()

                val text = result.text
                _recognizedText.value = text
                _wordCount.value = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size

                if (text.isBlank()) {
                    _uiState.value = OcrUiState.Error("No se detectó texto")
                } else {
                    _uiState.value = OcrUiState.Success(text, null)
                }

            } catch (e: Exception) {
                Log.e("OCR", "Error processing bitmap", e)
                _uiState.value = OcrUiState.Error("Error: ${e.message}")
            }
        }
    }

    /**
     * Procesa un archivo PDF
     * @param context Contexto de la aplicación
     * @param pdfUri URI del archivo PDF
     * @param maxCharacters Límite máximo de caracteres a extraer (default: 10000)
     */
    fun processPdf(context: Context, pdfUri: Uri, maxCharacters: Int = 10000) {
        viewModelScope.launch {
            _uiState.value = OcrUiState.Processing

            try {
                // Extraer texto del PDF usando PDFBox - todas las páginas hasta límite
                val text = extractTextFromPdf(context, pdfUri, maxCharacters)

                _recognizedText.value = text
                _wordCount.value = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size

                if (text.isBlank()) {
                    _uiState.value = OcrUiState.Error("No se pudo extraer texto del PDF")
                } else {
                    _uiState.value = OcrUiState.Success(text, pdfUri)
                }

            } catch (e: Exception) {
                Log.e("OCR", "Error processing PDF", e)
                _uiState.value = OcrUiState.Error("Error PDF: ${e.message}")
            }
        }
    }

    /**
     * Limpia el texto reconocido
     */
    fun clearText() {
        _recognizedText.value = ""
        _wordCount.value = 0
        _uiState.value = OcrUiState.Idle
    }

    /**
     * Obtiene bloques de texto organizados (para mostrar enriquecido)
     */
    fun getTextBlocks(context: Context, imageUri: Uri, onResult: (List<TextBlock>) -> Unit) {
        viewModelScope.launch {
            try {
                val bitmap = loadBitmapFromUri(context, imageUri) ?: return@launch
                val image = InputImage.fromBitmap(bitmap, 0)
                val result = textRecognizer.process(image).await()

                val blocks = result.textBlocks.map { block ->
                    TextBlock(
                        text = block.text,
                        boundingBox = block.boundingBox,
                        lines = block.lines.map { it.text }
                    )
                }

                onResult(blocks)
            } catch (e: Exception) {
                Log.e("OCR", "Error getting text blocks", e)
                onResult(emptyList())
            }
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            Log.e("OCR", "Error loading bitmap", e)
            null
        }
    }

    private fun extractTextFromPdf(context: Context, pdfUri: Uri, maxCharacters: Int = 10000): String {
        return try {
            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                // Usar PDFBox para extraer texto (inicializado en init)
                PDDocument.load(inputStream).use { document ->
                    val pdfStripper = PDFTextStripper()
                    pdfStripper.sortByPosition = true
                    
                    val totalPages = document.numberOfPages
                    val accumulatedText = StringBuilder()
                    
                    // Extraer página por página hasta alcanzar el límite
                    for (pageNum in 1..totalPages) {
                        pdfStripper.startPage = pageNum
                        pdfStripper.endPage = pageNum
                        
                        val pageText = pdfStripper.getText(document).trim()
                        
                        // Verificar si agregar esta página excedería el límite
                        if (accumulatedText.length + pageText.length > maxCharacters) {
                            // Agregar solo lo que cabe del texto restante
                            val remainingSpace = maxCharacters - accumulatedText.length
                            if (remainingSpace > 100) { // Agregar al menos 100 caracteres si hay espacio
                                accumulatedText.append("\n").append(pageText.take(remainingSpace))
                            }
                            break
                        }
                        
                        if (pageText.isNotBlank()) {
                            if (accumulatedText.isNotEmpty()) {
                                accumulatedText.append("\n\n")
                            }
                            accumulatedText.append(pageText)
                        }
                        
                        Log.d("OCR", "Extracted page $pageNum/$totalPages: ${pageText.length} chars, total: ${accumulatedText.length}")
                    }
                    
                    accumulatedText.toString()
                }
            } ?: ""
        } catch (e: Exception) {
            Log.e("OCR", "Error extracting PDF text", e)
            ""
        }
    }

    override fun onCleared() {
        super.onCleared()
        textRecognizer.close()
    }
}

/**
 * Representa un bloque de texto detectado
 */
data class TextBlock(
    val text: String,
    val boundingBox: android.graphics.Rect?,
    val lines: List<String>
)
