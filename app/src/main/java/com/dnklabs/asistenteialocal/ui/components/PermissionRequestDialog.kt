package com.dnklabs.asistenteialocal.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Datos de un permiso para mostrar en el diálogo
 */
data class PermissionInfo(
    val permission: String,
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val rationale: String
)

/**
 * Diálogo de solicitud de permisos con explicación educativa
 */
@Composable
fun PermissionRequestDialog(
    permissionInfo: PermissionInfo,
    onDismiss: () -> Unit,
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit = {}
) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    
    // Verificar estado del permiso
    val permissionStatus = remember {
        ContextCompat.checkSelfPermission(context, permissionInfo.permission)
    }
    
    // Launcher para solicitar permiso
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            // Verificar si fue denegado permanentemente
            val shouldShowRationale = androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                context as android.app.Activity,
                permissionInfo.permission
            )
            if (!shouldShowRationale && permissionStatus == android.content.pm.PackageManager.PERMISSION_DENIED) {
                permanentlyDenied = true
            }
            onPermissionDenied()
        }
    }
    
    // Diálogo principal educativo
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = permissionInfo.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = permissionInfo.title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 300.dp) // Limitar altura máxima
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = permissionInfo.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Explicación detallada
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = permissionInfo.rationale,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (permanentlyDenied) {
                Button(
                    onClick = {
                        // Abrir configuración de la app
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                        onDismiss()
                    }
                ) {
                    Text("Abrir Configuración")
                }
            } else {
                Button(
                    onClick = {
                        permissionLauncher.launch(permissionInfo.permission)
                    }
                ) {
                    Text("Conceder Permiso")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Helper para verificar y solicitar permisos de forma educativa
 */
@Composable
fun CheckAndRequestPermission(
    permission: String,
    permissionInfo: PermissionInfo,
    onPermissionGranted: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    
    val hasPermission = remember {
        ContextCompat.checkSelfPermission(context, permission) == 
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    if (hasPermission) {
        content()
    } else {
        // Mostrar UI que permite abrir el diálogo de permisos
        Box {
            content()
            
            // Overlay que bloquea la funcionalidad hasta tener permiso
            PermissionOverlay(
                permissionInfo = permissionInfo,
                onRequestPermission = { showDialog = true }
            )
        }
        
        if (showDialog) {
            PermissionRequestDialog(
                permissionInfo = permissionInfo,
                onDismiss = { showDialog = false },
                onPermissionGranted = {
                    showDialog = false
                    onPermissionGranted()
                }
            )
        }
    }
}

/**
 * Overlay que muestra por qué se necesita el permiso
 */
@Composable
private fun PermissionOverlay(
    permissionInfo: PermissionInfo,
    onRequestPermission: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = permissionInfo.icon,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = permissionInfo.title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = permissionInfo.description,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Permitir Acceso")
                    }
                }
            }
        }
    }
}

// Objetos predefinidos para permisos comunes
object AppPermissions {
    @Composable
    fun CameraPermissionInfo() = PermissionInfo(
        permission = Manifest.permission.CAMERA,
        title = "Permiso de Cámara",
        description = "Necesitamos acceder a tu cámara para escanear documentos y texto",
        icon = Icons.Default.CameraAlt,
        rationale = "La cámara se utiliza únicamente para:\n• Escanear documentos y convertirlos a texto\n• Tomar fotos de texto para procesar\n• Todo el procesamiento se hace en tu dispositivo, sin subir imágenes a internet"
    )
    
    @Composable
    fun MicrophonePermissionInfo() = PermissionInfo(
        permission = Manifest.permission.RECORD_AUDIO,
        title = "Permiso de Micrófono",
        description = "Necesitamos acceder al micrófono para convertir tu voz a texto",
        icon = Icons.Default.Mic,
        rationale = "El micrófono se utiliza para:\n• Grabar tu voz y convertirla a texto (dictado)\n• Enviar mensajes sin escribir\n• La grabación se procesa localmente y no se almacena"
    )
    
    @Composable
    fun StoragePermissionInfo() = PermissionInfo(
        permission = Manifest.permission.READ_EXTERNAL_STORAGE,
        title = "Permiso de Almacenamiento",
        description = "Necesitamos acceder a tu almacenamiento para seleccionar archivos",
        icon = Icons.Default.FolderOpen,
        rationale = "El acceso al almacenamiento permite:\n• Seleccionar imágenes para extraer texto\n• Elegir archivos PDF para procesar\n• Guardar modelos de IA descargados\n• Solo accedemos a los archivos que tú seleccionas"
    )
    
    @Composable
    fun MediaImagesPermissionInfo() = PermissionInfo(
        permission = Manifest.permission.READ_MEDIA_IMAGES,
        title = "Permiso de Galería",
        description = "Necesitamos acceder a tus imágenes para seleccionar fotos",
        icon = Icons.Default.Image,
        rationale = "El acceso a imágenes permite:\n• Seleccionar fotos de tu galería\n• Extraer texto de imágenes usando OCR\n• Solo procesamos las imágenes que tú eliges\n• El procesamiento es 100% offline"
    )
}
