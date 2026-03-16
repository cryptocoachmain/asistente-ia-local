package com.dnklabs.asistenteialocal.ui.screens.license

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dnklabs.asistenteialocal.data.local.LicenseManager
import com.dnklabs.asistenteialocal.data.repository.UpdateInfo
import com.dnklabs.asistenteialocal.data.repository.UpdateRepository
import kotlinx.coroutines.launch

@Composable
fun LicenseExpiredScreen(
    onUpdateAvailable: (UpdateInfo) -> Unit = {},
    onRetryCheck: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    val licenseManager = remember { LicenseManager(context) }
    
    val expiryDate = licenseManager.getExpiryDateFormatted()
    val daysRemaining = licenseManager.getDaysRemaining()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icono de advertencia
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Título
                Text(
                    text = "LICENCIA EXPIRADA",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Mensaje de vencimiento
                Text(
                    text = "Tu licencia de uso expiró el",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = expiryDate,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                HorizontalDivider()
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Información
                Text(
                    text = "Por favor, descarga la nueva versión de la app o contacta con soporte ante cualquier problema:",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Email de contacto
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:dnkautomatizaciones@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, "Renovación de licencia - Asistente IA Local")
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "dnkautomatizaciones@gmail.com",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Botón de descargar actualización
                Button(
                    onClick = {
                        scope.launch {
                            isCheckingUpdate = true
                            updateError = null
                            
                            try {
                                val updateRepo = UpdateRepository(context)
                                val currentVersion = context.packageManager
                                    .getPackageInfo(context.packageName, 0).versionName
                                
                                val updateInfo = updateRepo.checkForUpdate(currentVersion ?: "1.0.0")
                                
                                if (updateInfo != null) {
                                    onUpdateAvailable(updateInfo)
                                } else {
                                    updateError = "No se encontró una nueva versión. Por favor, descarga manualmente desde GitHub."
                                }
                            } catch (e: Exception) {
                                updateError = "Error al buscar actualizaciones: ${e.message}"
                            } finally {
                                isCheckingUpdate = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isCheckingUpdate
                ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = "Buscar actualización",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Error message
                if (updateError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = updateError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Botón manual
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://github.com/cryptocoachmain/asistente-ia-local/releases")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Abrir GitHub manualmente")
                }
            }
        }
    }
}
