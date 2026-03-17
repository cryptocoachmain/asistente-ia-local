package com.dnklabs.asistenteialocal.ui.screens.settings

import android.widget.Toast
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.app.ActivityManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.dnklabs.asistenteialocal.data.local.TokenWarningManager
import com.dnklabs.asistenteialocal.data.local.ModelSettingsManager
import com.dnklabs.asistenteialocal.data.local.AppLogger
import com.dnklabs.asistenteialocal.data.repository.UpdateRepository
import com.dnklabs.asistenteialocal.data.repository.UpdateInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onSetPin: (String) -> Unit = {},
    onChangePin: () -> Unit = {},
    onChangeModel: () -> Unit = {}
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenWarningManager(context) }
    val modelSettingsManager = remember { ModelSettingsManager(context) }
    val updateRepository = remember { UpdateRepository(context) }
    val scope = rememberCoroutineScope()
    val currentVersionName = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "1.4.0"
        }.getOrDefault("1.4.0")
    }
    
    // Token threshold slider state
    var warningThreshold by remember { 
        mutableFloatStateOf(tokenManager.warningThreshold.value * 100) 
    }
    
    // Model parameters state
    var temperature by remember {
        mutableFloatStateOf(modelSettingsManager.temperature.value)
    }
    var maxTokens by remember {
        mutableFloatStateOf(modelSettingsManager.maxTokens.value.toFloat())
    }
    var topP by remember {
        mutableFloatStateOf(modelSettingsManager.topP.value)
    }
    var topK by remember {
        mutableFloatStateOf(modelSettingsManager.topK.value.toFloat())
    }
    var repeatPenalty by remember {
        mutableFloatStateOf(modelSettingsManager.repeatPenalty.value)
    }
    var contextLength by remember {
        mutableFloatStateOf(modelSettingsManager.contextLength.value.toFloat())
    }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateStatus by remember { mutableStateOf("") }
    
    var showRamDialog by remember { mutableStateOf(false) }
    var isCleaningRam by remember { mutableStateOf(false) }
    var showCloseAppsDialog by remember { mutableStateOf(false) }
    var isClosingApps by remember { mutableStateOf(false) }
    var showClearLogDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Configuración", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))
            
            // ============================================================
            // 1. COMPROBAR ACTUALIZACIONES - PRIMERA OPCIÓN
            // ============================================================
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Comprobar Actualizaciones",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Estado de la versión actual
                    val currentVersionName = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
                        } catch (e: Exception) {
                            "1.0.0"
                        }
                    }
                    
                    Text(
                        text = "Versión actual: v$currentVersionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Estado de licencia
                    val licenseManager = remember { com.dnklabs.asistenteialocal.data.local.LicenseManager(context) }
                    val isLicenseValid = licenseManager.isLicenseValid()
                    val daysRemaining = licenseManager.getDaysRemainingText()
                    
                    Surface(
                        color = if (isLicenseValid) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isLicenseValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isLicenseValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isLicenseValid) "Licencia activa ($daysRemaining)" else "Licencia expirada",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Botón de actualizar
                    var isCheckingUpdate by remember { mutableStateOf(false) }
                    val updateRepository = remember { UpdateRepository(context) }
                    var localUpdateStatus by remember { mutableStateOf<String?>(null) }
                    val scope = rememberCoroutineScope()
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isCheckingUpdate = true
                                localUpdateStatus = null
                                try {
                                    val updateInfo = updateRepository.checkForUpdate(currentVersionName)
                                    
                                    if (updateInfo != null) {
                                        localUpdateStatus = "Nueva versión ${updateInfo.versionName}. Descargando..."
                                        val success = updateRepository.downloadAndInstall(updateInfo)
                                        if (success) {
                                            localUpdateStatus = "Listo. Busca el APK en Descargas."
                                        }
                                    } else {
                                        localUpdateStatus = "Ya tienes la última versión"
                                    }
                                } catch (e: Exception) {
                                    localUpdateStatus = "Error: ${e.message}"
                                } finally {
                                    isCheckingUpdate = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCheckingUpdate
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buscando...")
                        } else {
                            Icon(Icons.Default.Update, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Comprobar Actualizaciones")
                        }
                    }
                    
                    if (localUpdateStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = localUpdateStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (localUpdateStatus!!.contains("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Security Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onChangePin
            ) {
                ListItem(
                    headlineContent = { Text("Cambiar PIN") },
                    supportingContent = { Text("Modifica tu código de acceso") },
                    leadingContent = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Model Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onChangeModel
            ) {
                ListItem(
                    headlineContent = { Text("Modelo de IA") },
                    supportingContent = { Text("Cambiar o descargar modelo") },
                    leadingContent = {
                        Icon(Icons.Default.ModelTraining, contentDescription = null)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Model Parameters Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Parámetros del Modelo IA",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Text(
                        text = "Ajusta cómo responde la inteligencia artificial",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // =============================================
                    // 1. LONGITUD DE RESPUESTA (Max Tokens)
                    // =============================================
                    ParameterSlider(
                        title = "📝 Longitud de respuesta",
                        value = maxTokens,
                        valueRange = 50f..10000f,
                        steps = 99,
                        valueDisplay = { "${it.toInt()} tokens" },
                        description = modelSettingsManager.getMaxTokensDescription(),
                        lowLabel = "Corto",
                        highLabel = "Largo",
                        lowDescription = "Respuestas breves y directas. Ideal para preguntas rápidas.",
                        highDescription = "Respuestas extensas y detalladas. Usa más recursos.",
                        onValueChange = { maxTokens = it },
                        onValueChangeFinished = { modelSettingsManager.setMaxTokens(maxTokens.toInt()) }
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // =============================================
                    // 2. TEMPERATURA (Creatividad)
                    // =============================================
                    ParameterSlider(
                        title = "🎨 Creatividad",
                        value = temperature,
                        valueRange = 0.0f..2.0f,
                        steps = 19,
                        valueDisplay = { String.format("%.1f", it) },
                        description = modelSettingsManager.getTemperatureDescription(),
                        lowLabel = "Preciso",
                        highLabel = "Creativo",
                        lowDescription = "Respuestas exactas y coherentes. Mejor para tareas técnicas.",
                        highDescription = "Respuestas variadas y originales. Puede generar ideas nuevas.",
                        onValueChange = { temperature = it },
                        onValueChangeFinished = { modelSettingsManager.setTemperature(temperature) }
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // =============================================
                    // 3. TOP P (Diversidad)
                    // =============================================
                    ParameterSlider(
                        title = "🌡️ Diversidad de palabras",
                        value = topP,
                        valueRange = 0.0f..1.0f,
                        steps = 19,
                        valueDisplay = { String.format("%.2f", it) },
                        description = "Muestreo nucleus: vocabulario considerado",
                        lowLabel = "Conservador",
                        highLabel = "Diverso",
                        lowDescription = "Usa solo palabras muy probables. Más predecible.",
                        highDescription = "Considera más opciones. Más variado pero menos preciso.",
                        onValueChange = { topP = it },
                        onValueChangeFinished = { modelSettingsManager.setTopP(topP) }
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // =============================================
                    // 4. TOP K (Selección)
                    // =============================================
                    ParameterSlider(
                        title = "🔢 Tokens a considerar",
                        value = topK,
                        valueRange = 1f..100f,
                        steps = 98,
                        valueDisplay = { "${it.toInt()}" },
                        description = modelSettingsManager.getTopKDescription(),
                        lowLabel = "Selectivo",
                        highLabel = "Amplio",
                        lowDescription = "Solo las palabras más probables. Más coherente.",
                        highDescription = "Más opciones consideradas. Puede perder foco.",
                        onValueChange = { topK = it },
                        onValueChangeFinished = { modelSettingsManager.setTopK(topK.toInt()) }
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // =============================================
                    // 5. REPEAT PENALTY (Repeticiones)
                    // =============================================
                    ParameterSlider(
                        title = "🔁 Evitar repeticiones",
                        value = repeatPenalty,
                        valueRange = 1.0f..2.0f,
                        steps = 19,
                        valueDisplay = { String.format("%.2f", it) },
                        description = modelSettingsManager.getRepeatPenaltyDescription(),
                        lowLabel = "Permite repeticiones",
                        highLabel = "Evita repeticiones",
                        lowDescription = "Puede repetir palabras o frases.",
                        highDescription = "Evita repetir palabras. Más variety but puede sonar robótico.",
                        onValueChange = { repeatPenalty = it },
                        onValueChangeFinished = { modelSettingsManager.setRepeatPenalty(repeatPenalty) }
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // =============================================
                    // 6. CONTEXT LENGTH (Memoria)
                    // =============================================
                    ParameterSlider(
                        title = "🧠 Memoria de conversación",
                        value = contextLength,
                        valueRange = 512f..8192f,
                        steps = 15,
                        valueDisplay = { "${it.toInt()} tokens" },
                        description = modelSettingsManager.getContextLengthDescription(),
                        lowLabel = "Corto",
                        highLabel = "Largo",
                        lowDescription = "Menos memoria de chat. Más rápido, usa menos RAM.",
                        highDescription = "Recuerda más conversación. Usa más RAM, más lento.",
                        onValueChange = { contextLength = it },
                        onValueChangeFinished = { modelSettingsManager.setContextLength(contextLength.toInt()) }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Reset button
                    OutlinedButton(
                        onClick = { 
                            modelSettingsManager.resetToDefaults()
                            temperature = ModelSettingsManager.DEFAULT_TEMPERATURE
                            maxTokens = ModelSettingsManager.DEFAULT_MAX_TOKENS.toFloat()
                            topP = ModelSettingsManager.DEFAULT_TOP_P
                            topK = ModelSettingsManager.DEFAULT_TOP_K.toFloat()
                            repeatPenalty = ModelSettingsManager.DEFAULT_REPEAT_PENALTY
                            contextLength = ModelSettingsManager.DEFAULT_CONTEXT_LENGTH.toFloat()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restaurar valores por defecto")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Token Settings Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DataUsage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Configuración de Tokens",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Umbral de advertencia: ${warningThreshold.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Slider(
                        value = warningThreshold,
                        onValueChange = { warningThreshold = it },
                        onValueChangeFinished = {
                            tokenManager.setWarningThreshold(warningThreshold / 100f)
                        },
                        valueRange = 50f..95f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        text = "Se mostrará una advertencia cuando uses el ${warningThreshold.toInt()}% de los tokens disponibles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Token info
                    val stats = tokenManager.getTokenStats()
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Estadísticas actuales:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Tokens usados: ${stats.currentTokens.toLocaleString()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Límite máximo: ${stats.maxTokens.toLocaleString()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Restantes: ${stats.remainingTokens.toLocaleString()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Uso: ${(stats.usagePercentage * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Reset button
                    OutlinedButton(
                        onClick = { tokenManager.resetCounter() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reiniciar contador de tokens")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            // Permissions Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Permisos",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val lifecycleOwner = LocalLifecycleOwner.current

                    var cameraGranted by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }
                    val cameraPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        cameraGranted = isGranted
                        if (!isGranted) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    }
                    PermissionStatusRow(
                        permissionName = "Cámara",
                        permissionDesc = "Para escanear documentos y texto",
                        isGranted = cameraGranted,
                        onClick = {
                            if (!cameraGranted) {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var micGranted by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }
                    val micPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        micGranted = isGranted
                        if (!isGranted) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    }
                    PermissionStatusRow(
                        permissionName = "Micrófono",
                        permissionDesc = "Para convertir voz a texto",
                        isGranted = micGranted,
                        onClick = {
                            if (!micGranted) {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Storage Permission
                    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_IMAGES
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                    var storageGranted by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                context,
                                storagePermission
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }
                    val storagePermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        storageGranted = isGranted
                        if (!isGranted) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    }

                    var hasAllFilesAccess by remember {
                        mutableStateOf(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                Environment.isExternalStorageManager()
                            } else {
                                false
                            }
                        )
                    }

                    fun refreshPermissions() {
                        cameraGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        micGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        storageGranted = ContextCompat.checkSelfPermission(
                            context,
                            storagePermission
                        ) == PackageManager.PERMISSION_GRANTED
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            hasAllFilesAccess = Environment.isExternalStorageManager()
                        }
                    }

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                refreshPermissions()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }
                    PermissionStatusRow(
                        permissionName = "Almacenamiento",
                        permissionDesc = "Para seleccionar imágenes y PDFs",
                        isGranted = storageGranted,
                        onClick = {
                            if (!storageGranted) {
                                storagePermissionLauncher.launch(storagePermission)
                            } else {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Almacenamiento Total (MANAGE_EXTERNAL_STORAGE) - Solo Android 11+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Acceso total a archivos",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Permite acceso a todos los archivos del dispositivo (Android 11+)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = hasAllFilesAccess,
                                onCheckedChange = { checked ->
                                    if (checked && !hasAllFilesAccess) {
                                        // Intent para Android 11+ - Acceso total a archivos
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // Fallback: abrir configuración de la app
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Actualizaciones Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Actualizaciones",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Estado de licencia
                    val licenseManager = remember { com.dnklabs.asistenteialocal.data.local.LicenseManager(context) }
                    val isLicenseValid = licenseManager.isLicenseValid()
                    val daysRemaining = licenseManager.getDaysRemainingText()
                    
                    Surface(
                        color = if (isLicenseValid) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isLicenseValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isLicenseValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isLicenseValid) "Licencia activa" else "Licencia expirada",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (isLicenseValid) "$daysRemaining" else "Vence: ${licenseManager.getExpiryDateFormatted()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Botón buscar actualizaciones
                    var isCheckingUpdate by remember { mutableStateOf(false) }
                    var updateStatus by remember { mutableStateOf<String?>(null) }
                    
                    val scope = rememberCoroutineScope()
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isCheckingUpdate = true
                                updateStatus = null
                                try {
                                    val updateRepo = UpdateRepository(context)
                                    val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                                    val updateInfo = updateRepo.checkForUpdate(currentVersion ?: "1.0.0")
                                    
                                    if (updateInfo != null) {
                                        val success = updateRepo.downloadAndInstall(updateInfo)
                                        if (success) {
                                            updateStatus = "Listo. Busca el APK en Descargas."
                                        }
                                    } else {
                                        updateStatus = "Ya tienes la última versión"
                                    }
                                } catch (e: Exception) {
                                    updateStatus = "Error: ${e.message}"
                                } finally {
                                    isCheckingUpdate = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCheckingUpdate
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buscando...")
                        } else {
                            Icon(Icons.Default.Update, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buscar actualizaciones")
                        }
                    }
                    
                    if (updateStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = updateStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (updateStatus!!.contains("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Battery Optimization Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Optimización de batería",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                    
                    Text(
                        text = if (isIgnoringBatteryOptimizations) {
                            "La app puede ejecutarse en segundo plano sin restricciones."
                        } else {
                            "El sistema puede cerrar la app cuando está en segundo plano para ahorrar batería."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isIgnoringBatteryOptimizations) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (isIgnoringBatteryOptimizations) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Optimización desactivada",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Desactivar optimización de batería")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // RAM Management Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gestión de Memoria",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Las IAs locales consumen una gran cantidad de memoria RAM (hasta 2GB+). Para un mejor rendimiento, se recomienda cerrar aplicaciones en segundo plano.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val memoryInfo = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memoryInfo)
                    
                    val availableGB = String.format("%.2f", memoryInfo.availMem / (1024.0 * 1024.0 * 1024.0))
                    val totalGB = String.format("%.2f", memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("RAM Disponible", style = MaterialTheme.typography.labelMedium)
                                Text("$availableGB GB / $totalGB GB", style = MaterialTheme.typography.titleSmall)
                            }
                            if (memoryInfo.lowMemory) {
                                Text("¡MEMORIA BAJA!", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { showRamDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        enabled = !isCleaningRam
                    ) {
                        if (isCleaningRam) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onSecondary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Liberando recursos...")
                        } else {
                            Icon(Icons.Default.CleaningServices, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ejecutar Limpieza Profunda")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Esta opciÃ³n cerrarÃ¡ aplicaciones en segundo plano del telÃ©fono para liberar mÃ¡s memoria antes de ejecutar el modelo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showCloseAppsDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isClosingApps
                    ) {
                        if (isClosingApps) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cerrando apps...")
                        } else {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cerrar apps en segundo plano")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // About Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { /* información */ }
            ) {
                ListItem(
                    headlineContent = { Text("Acerca de") },
                    supportingContent = { Text("Versión $currentVersionName • Asistente IA Local") },
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Logs Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Registros (Logs)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Mostrar ruta del log
                    val logPath = AppLogger.getLogFilePath()
                    Text(
                        text = "Ubicación: $logPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { showClearLogDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Borrar todos los registros")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    HorizontalDivider()
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Warning about sending logs
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Los registros NO incluyen tus datos personales, conversaciones ni historial de chats. Solo se envía información técnica interna de la app para diagnosticar problemas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Send log button
                    Button(
                        onClick = { sendLogByEmail(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enviar registros por correo")
                    }
                }
            }
        }
    }
    
    // RAM Cleaning Dialog
    if (showRamDialog) {
        AlertDialog(
            onDismissRequest = { showRamDialog = false },
            title = { Text("¿Limpiar memoria RAM?") },
            text = {
                Text("Esta acción intentará cerrar aplicaciones en segundo plano para liberar memoria para la IA.\n\n⚠️ Aviso: Esto puede cerrar procesos de otras aplicaciones que estés utilizando.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRamDialog = false
                        isCleaningRam = true
                        
                        // Lógica de limpieza mejorada
                        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            // 1. Sugerir al sistema liberar memoria de otras apps
                            try {
                                val runningProcesses = activityManager.runningAppProcesses
                                runningProcesses?.forEach { process ->
                                    if (process.processName != context.packageName) {
                                        activityManager.killBackgroundProcesses(process.processName)
                                    }
                                }
                            } catch (e: Exception) {}

                            // 2. Liberar memoria de la propia app (cachés)
                            System.runFinalization()
                            System.gc()
                            
                            // 3. Feedback visual
                            Toast.makeText(context, "Limpieza completada. Recursos liberados.", Toast.LENGTH_SHORT).show()
                            isCleaningRam = false
                        }, 2000)
                    }
                ) {
                    Text("Limpiar ahora")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRamDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showCloseAppsDialog) {
        AlertDialog(
            onDismissRequest = { showCloseAppsDialog = false },
            title = { Text("¿Cerrar apps en segundo plano?") },
            text = {
                Text("Este botón cerrará procesos de otras aplicaciones que estén en segundo plano para liberar memoria y mejorar el rendimiento del modelo de IA.\n\nPuede interrumpir tareas de otras apps.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseAppsDialog = false
                        isClosingApps = true
                        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                val runningProcesses = activityManager.runningAppProcesses
                                runningProcesses?.forEach { process ->
                                    if (process.processName != context.packageName) {
                                        activityManager.killBackgroundProcesses(process.processName)
                                    }
                                }
                            } catch (_: Exception) {
                            }
                            Toast.makeText(context, "Aplicaciones en segundo plano cerradas.", Toast.LENGTH_SHORT).show()
                            isClosingApps = false
                        }, 1200)
                    }
                ) {
                    Text("Cerrar ahora")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseAppsDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    // Clear Log Dialog
    if (showClearLogDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogDialog = false },
            title = { Text("¿Borrar registros?") },
            text = {
                Text("Esta acción eliminará todos los logs guardados en Documents/AILocal/app.log.\n\nEsta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        AppLogger.clearLog()
                        Toast.makeText(context, "Registros borrados", Toast.LENGTH_SHORT).show()
                        showClearLogDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Borrar todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// Helper function to format numbers with locale
private fun Int.toLocaleString(): String {
    return java.text.NumberFormat.getInstance().format(this)
}

@Composable
private fun PermissionStatusRow(
    permissionName: String,
    permissionDesc: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (isGranted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = permissionName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = permissionDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Componente reutilizable para sliders de parámetros con explicaciones claras
 */
@Composable
private fun ParameterSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueDisplay: (Float) -> String,
    description: String,
    lowLabel: String,
    highLabel: String,
    lowDescription: String,
    highDescription: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Título y valor
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = valueDisplay(value),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Descripción del valor actual
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Slider
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Etiquetas bajo el slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "🔵 $lowLabel",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = lowDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "🔴 $highLabel",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = highDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun sendLogByEmail(context: Context) {
    try {
        val logFilePath = AppLogger.getLogFilePath() ?: run {
            Toast.makeText(context, "No se encontró el archivo de log", Toast.LENGTH_SHORT).show()
            return
        }
        val logFile = File(logFilePath)
        
        if (!logFile.exists() || logFile.length() == 0L) {
            Toast.makeText(context, "No hay registros para enviar", Toast.LENGTH_SHORT).show()
            return
        }
        
        val maxSizeBytes = 3 * 1024 * 1024 // 3 MB
        val logContent: File
        val subject: String
        
        if (logFile.length() > maxSizeBytes) {
            // If log is too large, take last 200 lines
            val lines = logFile.readLines()
            val last200Lines = lines.takeLast(200)
            val tempFile = File(context.cacheDir, "app_log_last_200_lines.txt")
            tempFile.writeText(last200Lines.joinToString("\n"))
            logContent = tempFile
            subject = "Log Asistente IA Local - Últimas 200 líneas"
        } else {
            logContent = logFile
            subject = "Log Asistente IA Local"
        }
        
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "desconocida"
        }
        
        val body = """
            App: Asistente IA Local
            Versión: $versionName
            Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}
            Android: ${Build.VERSION.RELEASE}
            
            ---
            Este correo contiene los registros (logs) de la aplicación para diagnóstico de problemas.
            NO se incluye ningún dato personal, conversación ni historial de chats.
        """.trimIndent()
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logContent
        )
        
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("dnklabsautomatizaciones@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        try {
            context.startActivity(Intent.createChooser(emailIntent, "Enviar correo con..."))
        } catch (e: Exception) {
            Toast.makeText(context, "No hay aplicación de correo instalada", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e("SettingsScreen", "Error sending log", e)
        Toast.makeText(context, "Error al preparar el correo: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
