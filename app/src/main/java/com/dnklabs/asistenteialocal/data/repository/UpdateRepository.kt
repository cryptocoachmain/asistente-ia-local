package com.dnklabs.asistenteialocal.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
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
        private const val APK_MIME = "application/vnd.android.package-archive"
    }

    private fun findActivityContext(ctx: Context): Context {
        var context = ctx
        while (context is android.content.ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return ctx
    }

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
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
    }

    suspend fun downloadAndInstall(updateInfo: UpdateInfo) = withContext(Dispatchers.IO) {
        val activityContext = findActivityContext(context)
        
        try {
            val url = URL(updateInfo.downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
            }

            val apkFile = File(context.cacheDir, updateInfo.assetName)
            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    input.copyTo(output)
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            withContext(Dispatchers.Main) {
                try {
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, APK_MIME)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    activityContext.startActivity(installIntent)
                } catch (e: Exception) {
                    // Si falla, intentar abrir configuración de-apps desconcidas
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            activityContext.startActivity(settingsIntent)
                        } else {
                            val settingsIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            activityContext.startActivity(settingsIntent)
                        }
                    } catch (e2: Exception) {
                        Toast.makeText(
                            context,
                            "Error al instalar. Por favor descarga el APK manualmente.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Error al descargar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
