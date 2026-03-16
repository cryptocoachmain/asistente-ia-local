package com.dnklabs.asistenteialocal.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Servicio de OCR para extraer texto de imágenes y PDFs
 * Usa Google ML Kit para el reconocimiento de texto
 */
class OCRService(private val context: Context) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extrae texto de una imagen desde URI
     */
    suspend fun extractTextFromImageUri(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            val resultTask = textRecognizer.process(image)
            
            // Usar suspendCancellableCoroutine para manejar el Task de ML Kit
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                resultTask.addOnSuccessListener { result ->
                    continuation.resume(result.text) { /* onCancellation */ }
                }.addOnFailureListener { e ->
                    android.util.Log.e("OCRService", "Error extracting text from image", e)
                    continuation.resume("") { /* onCancellation */ }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OCRService", "Error extracting text from image URI", e)
            ""
        }
    }

    /**
     * Extrae texto de un archivo PDF (solo primera página)
     */
    suspend fun extractTextFromPdf(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            // Convertir PDF a imagen y luego extraer texto
            val pdfFile = getFileFromUri(uri) ?: return@withContext ""
            
            val fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            val pdfRenderer = PdfRenderer(fileDescriptor)
            
            val text = if (pdfRenderer.pageCount > 0) {
                val page = pdfRenderer.openPage(0)
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                // Extraer texto del bitmap usando ML Kit
                val image = InputImage.fromBitmap(bitmap, 0)
                val resultTask = textRecognizer.process(image)
                
                kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                    resultTask.addOnSuccessListener { result ->
                        continuation.resume(result.text) { /* onCancellation */ }
                    }.addOnFailureListener { e ->
                        android.util.Log.e("OCRService", "Error extracting text from PDF", e)
                        continuation.resume("") { /* onCancellation */ }
                    }
                }
            } else {
                ""
            }
            
            pdfRenderer.close()
            fileDescriptor.close()
            
            text
        } catch (e: Exception) {
            android.util.Log.e("OCRService", "Error extracting text from PDF", e)
            ""
        }
    }

    /**
     * Extrae texto de una imagen desde File
     */
    suspend fun extractTextFromFile(file: File): String = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.fromFile(file)
            val image = InputImage.fromFilePath(context, uri)
            val resultTask = textRecognizer.process(image)
            
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                resultTask.addOnSuccessListener { result ->
                    continuation.resume(result.text) { /* onCancellation */ }
                }.addOnFailureListener { e ->
                    android.util.Log.e("OCRService", "Error extracting text from file", e)
                    continuation.resume("") { /* onCancellation */ }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OCRService", "Error extracting text from file", e)
            ""
        }
    }

    /**
     * Extrae texto de una imagen desde Bitmap
     */
    suspend fun extractTextFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val resultTask = textRecognizer.process(image)
            
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                resultTask.addOnSuccessListener { result ->
                    continuation.resume(result.text) { /* onCancellation */ }
                }.addOnFailureListener { e ->
                    android.util.Log.e("OCRService", "Error extracting text from bitmap", e)
                    continuation.resume("") { /* onCancellation */ }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OCRService", "Error extracting text from bitmap", e)
            ""
        }
    }

    /**
     * Obtiene un archivo desde una URI
     */
    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
            FileOutputStream(tempFile).use { output ->
                inputStream?.copyTo(output)
            }
            inputStream?.close()
            tempFile
        } catch (e: Exception) {
            android.util.Log.e("OCRService", "Error getting file from URI", e)
            null
        }
    }

    /**
     * Libera recursos
     */
    fun close() {
        textRecognizer.close()
    }
}
