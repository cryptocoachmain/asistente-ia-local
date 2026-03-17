package com.dnklabs.asistenteialocal.data.repository

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val assetName: String,
    val downloadUrl: String
)

class UpdateRepository(private val context: Context) {

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/cryptocoachmain/asistente-ia-local/releases/latest"
    }

    private fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Asistente-IA-Local")
            }

            connection.inputStream.use { input ->
                val body = input.bufferedReader().readText()
                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "")
                val versionName = tagName.removePrefix("v")

                if (versionName.isBlank() || versionName == currentVersion) {
                    return@withContext null
                }

                val assets = json.optJSONArray("assets") ?: return@withContext null
                if (assets.length() == 0) return@withContext null

                val asset = assets.getJSONObject(0)
                val assetName = asset.optString("name")
                val downloadUrl = asset.optString("browser_download_url")

                if (assetName.isBlank() || downloadUrl.isBlank()) return@withContext null

                UpdateInfo(
                    versionName = versionName,
                    assetName = assetName,
                    downloadUrl = downloadUrl
                )
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }

    suspend fun downloadAndInstall(updateInfo: UpdateInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(updateInfo.downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                setRequestProperty("User-Agent", "Asistente-IA-Local")
            }

            val apkFileName = "AILocal-${updateInfo.versionName}.apk"
            var apkUri: Uri? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val contentValues = ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, apkFileName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                apkUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                apkUri?.let { uri ->
                    resolver.openOutputStream(uri)?.use { output ->
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }

                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            } else {
                // Legacy storage for older Android
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                
                val apkFile = File(downloadsDir, apkFileName)
                
                connection.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
                apkUri = Uri.fromFile(apkFile)
            }

            withContext(Dispatchers.Main) {
                if (apkUri != null) {
                    try {
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(installIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error al instalar. Busca $apkFileName en Descargas.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "Error al guardar el APK.", Toast.LENGTH_LONG).show()
                }
            }
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            false
        }
    }
}
