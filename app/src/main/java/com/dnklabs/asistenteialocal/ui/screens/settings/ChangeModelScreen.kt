package com.dnklabs.asistenteialocal.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.app.ActivityManager
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dnklabs.asistenteialocal.data.local.ModelPersistenceManager
import com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository
import com.dnklabs.asistenteialocal.data.local.ModelSettingsManager
import com.dnklabs.asistenteialocal.ui.screens.onboarding.ModelOption
import com.dnklabs.asistenteialocal.ui.screens.onboarding.ModelInfo
import com.dnklabs.asistenteialocal.ui.screens.onboarding.toModelFileName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeModelScreen(
    onNavigateBack: () -> Unit = {},
    onModelSelected: (ModelOption) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Use ModelPersistenceManager for consistency
    val modelPersistence = remember { ModelPersistenceManager(context) }
    val modelSettingsManager = remember { ModelSettingsManager(context) }
    val llamaRepository = remember { LlamaCppRepository(context) }
    
    // Estado para forzar recomposición después de desinstalar
    var refreshKey by remember { mutableIntStateOf(0) }
    
    // Obtener modelo actual guardado desde ModelPersistenceManager
    var currentModel by remember { mutableStateOf(modelPersistence.getSelectedModel() ?: com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.MODEL_QWEN_1_5B) }
    // Obtener modelos instalados desde ModelPersistenceManager
    var installedModels by remember { mutableStateOf(modelPersistence.getInstalledModels().toSet()) }
    
    var selectedModel by remember { mutableStateOf<ModelOption?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showUninstallDialog by remember { mutableStateOf(false) }
    var modelToUninstall by remember { mutableStateOf<ModelOption?>(null) }
    
    // Estados para download progress
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    
    // Available models - dynamic from repository
    val availableModelsInfo = remember { llamaRepository.getAvailableModels() }
    
    // Get device RAM info
    val activityManager = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager }
    val memoryInfo = remember { ActivityManager.MemoryInfo() }
    activityManager.getMemoryInfo(memoryInfo)
    val totalRamMB = (memoryInfo.totalMem / (1024 * 1024)).toInt()
    val availableRamMB = (memoryInfo.availMem / (1024 * 1024)).toInt()
    
    // Función para descargar modelo real
    fun startDownload(model: ModelOption) {
        scope.launch {
            isDownloading = true
            showDownloadDialog = true
            downloadProgress = 0f
            
            val modelFileName = model.toModelFileName()
            
            try {
                llamaRepository.downloadModel(modelFileName).collect { status ->
                    when (status) {
                        is com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.DownloadStatus.Preparing -> {
                            downloadProgress = 0f
                        }
                        is com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.DownloadStatus.Progress -> {
                            downloadProgress = status.percent / 100f
                        }
                        is com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.DownloadStatus.Success -> {
                            downloadProgress = 1f
                            
                            // Guardar como instalado en ModelPersistenceManager
                            val newInstalled = installedModels.toMutableSet()
                            newInstalled.add(model.name) // Guardamos el nombre del modelo (ej: "QWEN_1_5B")
                            modelPersistence.saveInstalledModels(newInstalled.toSet())
                            
                            // Actualizar el modelo actual
                            modelPersistence.saveSelectedModel(modelFileName)
                            currentModel = modelFileName
                            
                            isDownloading = false
                            showDownloadDialog = false
                            onModelSelected(model)
                        }
                        is com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.DownloadStatus.Error -> {
                            isDownloading = false
                            // Mostrar error pero mantener diálogo abierto
                            android.util.Log.e("ChangeModel", "Error descargando: ${status.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                isDownloading = false
                android.util.Log.e("ChangeModel", "Excepción descargando modelo", e)
            }
        }
    }
    
    // Función para desinstalar modelo
    fun uninstallModel(model: ModelOption) {
        scope.launch {
            val modelFileName = model.toModelFileName()
            
            // Delete the actual model file
            val deleted = llamaRepository.deleteModel(modelFileName)
            
            if (deleted) {
                val newInstalled = installedModels.toMutableSet()
                newInstalled.remove(model.name)
                
                modelPersistence.saveInstalledModels(newInstalled.toSet())
                
                // Si el modelo desinstalado era el actual, limpiar
                val currentSelectedModel = modelPersistence.getSelectedModel()
                if (currentSelectedModel == modelFileName) {
                    modelPersistence.clearModel()
                    llamaRepository.release()
                }
                
                refreshKey++ // Forzar recomposición
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cambiar Modelo") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // RAM Status Info
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Memoria del Sistema",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "RAM Total: ${totalRamMB / 1024} GB | Disponible: ${availableRamMB / 1024} GB",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (memoryInfo.lowMemory) {
                            Text(
                                text = "⚠️ Sistema con poca memoria libre",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Modelos disponibles",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Leyenda mejorada
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Leyenda:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Descargado",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "En uso",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFF9800).copy(alpha = 0.2f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "No descargado",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF44336).copy(alpha = 0.2f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFF44336),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "RAM baja",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(availableModelsInfo) { info ->
                    val modelOption = ModelOption.values().find { it.toModelFileName() == info.id } ?: ModelOption.QWEN_1_5B
                    val modelFileName = info.id
                    val isInstalled = info.isInstalled
                    val selectedModelName = modelPersistence.getSelectedModel()
                    val isCurrent = selectedModelName == modelFileName
                    
                    ModelCard(
                        modelOption = modelOption,
                        modelInfo = info,
                        isInstalled = isInstalled,
                        isCurrent = isCurrent,
                        deviceTotalRamMB = totalRamMB,
                        onClick = {
                            selectedModel = modelOption
                            if (!isInstalled) {
                                showConfirmDialog = true
                            } else {
                                // Si ya está instalado, solo cambiar al modelo
                                // IMPORTANTE: guardar el nombre del archivo del modelo, no el enum
                                modelPersistence.saveSelectedModel(modelFileName)
                                currentModel = modelFileName
                                scope.launch {
                                    llamaRepository.release()
                                    val contextLength = modelSettingsManager.contextLength.value
                                    val success = llamaRepository.initialize(modelFileName, maxContextLength = contextLength)
                                    if (success) {
                                        modelPersistence.markModelInitialized()
                                    }
                                }
                                
                                onModelSelected(modelOption)
                            }
                        },
                        onDeleteClick = if (isInstalled) {
                            {
                                modelToUninstall = modelOption
                                showUninstallDialog = true
                            }
                        } else null
                    )
                }
            }
        }
    }
    
    // Confirmation dialog para descargar
    if (showConfirmDialog && selectedModel != null) {
        val modelInfo = availableModelsInfo.find { it.id == selectedModel!!.toModelFileName() }!!
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Descargar modelo") },
            text = {
                Column {
                    Text("¿Deseas descargar ${modelInfo.name}?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tamaño: ${String.format("%.2f", modelInfo.size / (1024.0 * 1024.0))} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "RAM necesaria: ${String.format("%.1f", modelInfo.ramRequiredMB / 1024.0)} GB",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (modelInfo.ramRequiredMB > totalRamMB) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (modelInfo.ramRequiredMB > totalRamMB) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ Este modelo requiere más RAM de la instalada físicamente. Funcionará muy lento usando RAM virtual o podría cerrarse inesperadamente.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ La descarga puede tardar varios minutos según tu conexión.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        startDownload(selectedModel!!)
                    }
                ) {
                    Text("Descargar")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmDialog = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    // Download progress dialog
    if (showDownloadDialog && selectedModel != null) {
        val modelInfo = availableModelsInfo.find { it.id == selectedModel!!.toModelFileName() }!!
        val modelSizeMB = (modelInfo.size / (1024.0 * 1024.0)).toInt()
        val downloadedMB = (downloadProgress * modelSizeMB).toInt()
        
        AlertDialog(
            onDismissRequest = { /* No permitir cerrar */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Descargando modelo") },
            text = {
                Column {
                    Text("Descargando ${modelInfo.name}...")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        val modelSizeMB = (modelInfo.size / (1024.0 * 1024.0)).toInt()
                        Text(
                            text = "$downloadedMB MB de $modelSizeMB MB",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    if (downloadProgress < 1f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Por favor, espera... No cierres la app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            confirmButton = {
                if (downloadProgress >= 1f) {
                    Button(onClick = { showDownloadDialog = false }) {
                        Text("Aceptar")
                    }
                } else {
                    TextButton(
                        onClick = { /* No se puede cancelar */ },
                        enabled = false
                    ) {
                        Text("Descargando...")
                    }
                }
            }
        )
    }
    
    // Uninstall confirmation dialog
    if (showUninstallDialog && modelToUninstall != null) {
        val info = availableModelsInfo.find { it.id == modelToUninstall!!.toModelFileName() }!!
        AlertDialog(
            onDismissRequest = { 
                showUninstallDialog = false
                modelToUninstall = null
            },
            title = { Text("¿Desinstalar modelo?") },
            text = {
                val sizeMB = String.format("%.1f", info.size / (1024.0 * 1024.0))
                Text("¿Estás seguro de que quieres desinstalar ${info.name}? Esto liberará $sizeMB MB de espacio.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        uninstallModel(modelToUninstall!!)
                        showUninstallDialog = false
                        modelToUninstall = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Desinstalar")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { 
                        showUninstallDialog = false
                        modelToUninstall = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun ModelCard(
    modelOption: ModelOption,
    modelInfo: com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.ModelInfo,
    isInstalled: Boolean,
    isCurrent: Boolean,
    deviceTotalRamMB: Int,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null
) {
    val hasEnoughRam = modelInfo.ramRequiredMB <= deviceTotalRamMB
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCurrent -> MaterialTheme.colorScheme.primaryContainer
                isInstalled && hasEnoughRam -> MaterialTheme.colorScheme.surface
                isInstalled && !hasEnoughRam -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = if (isCurrent) {
            CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon más grande y visible
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isCurrent -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "En uso",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(24.dp)
                            )
                        }
                    }
                    isInstalled -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Descargado",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(24.dp)
                            )
                        }
                    }
                    else -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFF9800).copy(alpha = 0.15f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "No descargado",
                                tint = Color(0xFFFF9800),
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(24.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = modelInfo.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                }
                
                // Badge de estado más visible
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when {
                        isCurrent -> MaterialTheme.colorScheme.primary
                        isInstalled -> Color(0xFF4CAF50)
                        else -> Color(0xFFFF9800)
                    }.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = when {
                            isCurrent -> "✓ EN USO"
                            isInstalled -> "✓ Descargado"
                            else -> "⬇ Descargar"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isInstalled -> Color(0xFF4CAF50)
                            else -> Color(0xFFFF9800)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = modelInfo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Info de RAM con icono y color según compatibilidad
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasEnoughRam) Icons.Default.Memory else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (hasEnoughRam) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (hasEnoughRam) {
                            "✓ Compatible (${String.format("%.1f", modelInfo.ramRequiredMB / 1024.0)} GB)"
                        } else {
                            "⚠️ RAM insuficiente (${String.format("%.1f", modelInfo.ramRequiredMB / 1024.0)} GB req.)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasEnoughRam) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontWeight = if (!hasEnoughRam) FontWeight.Bold else FontWeight.Normal
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val sizeMB = String.format("%.1f", modelInfo.size / (1024.0 * 1024.0))
                    Text(
                        text = "$sizeMB MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = modelInfo.performance,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Action buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isCurrent) {
                    // Mostrar indicador de uso activo
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "En uso",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }
                } else if (isInstalled) {
                    FilledIconButton(
                        onClick = onClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Activar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = onClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFFFF9800)
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Descargar",
                            tint = Color.White
                        )
                    }
                }
                
                // Delete button (only for installed models)
                if (isInstalled && onDeleteClick != null) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Desinstalar",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
