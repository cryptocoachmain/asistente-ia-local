package com.dnklabs.asistenteialocal.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ModelOption {
    QWEN_0_5B,
    QWEN_1_5B,
    QWEN_3B,
    LLAMA_3_2_3B,
    GEMMA_2B,
    PHI_3_MINI,
    MISTRAL_7B,
    LLAMA_3_8B
}

data class ModelInfo(
    val name: String,
    val sizeMB: Int,
    val size: String,
    val ramRequiredMB: Int,
    val speed: String,
    val accuracy: String,
    val description: String
)

sealed class DownloadStatus {
    object Idle : DownloadStatus()
    object Downloading : DownloadStatus()
    object Success : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}

/**
 * Convierte ModelOption al nombre de archivo del modelo
 */
fun ModelOption.toModelFileName(): String {
    return when (this) {
        ModelOption.QWEN_0_5B -> com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.MODEL_QWEN_0_5B
        ModelOption.QWEN_1_5B -> com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.MODEL_QWEN_1_5B
        ModelOption.GEMMA_2B -> com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.MODEL_GEMMA_2B
        ModelOption.QWEN_3B -> com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.MODEL_QWEN_3B
        ModelOption.LLAMA_3_2_3B -> "Llama-3.2-3B-Instruct-Q4_K_M.gguf"
        ModelOption.PHI_3_MINI -> com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.MODEL_PHI_3_MINI
        ModelOption.MISTRAL_7B -> com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.MODEL_MISTRAL_7B
        ModelOption.LLAMA_3_8B -> com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.MODEL_LLAMA_3_8B
    }
}

@Composable
fun ModelSelectionScreen(
    freeSpaceGB: Float,
    onDownloadAndInstall: (ModelOption) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val modelPersistence = remember { com.dnklabs.asistenteialocal.data.local.ModelPersistenceManager(context) }
    val modelSettingsManager = remember { com.dnklabs.asistenteialocal.data.local.ModelSettingsManager(context) }
    val llamaRepository = remember { com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository(context) }
    var selectedModel by remember { mutableStateOf<ModelOption?>(null) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadStatus by remember { mutableStateOf<DownloadStatus>(DownloadStatus.Idle) }
    var modelInstalled by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // Check if a model is already selected
    val installedModel by modelPersistence.selectedModel.collectAsState()
    
    // Get device RAM info
    val activityManager = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager }
    val memoryInfo = remember { ActivityManager.MemoryInfo() }
    activityManager.getMemoryInfo(memoryInfo)
    val totalRamMB = (memoryInfo.totalMem / (1024 * 1024)).toInt()
    val availableRamMB = (memoryInfo.availMem / (1024 * 1024)).toInt()

    val models = listOf(
        ModelOption.QWEN_0_5B to ModelInfo(
            name = "Qwen 0.5B",
            sizeMB = 380,
            size = "380 MB",
            ramRequiredMB = 400,
            speed = "Muy rápida",
            accuracy = "Básica",
            description = "Ideal para dispositivos con poco espacio"
        ),
        ModelOption.QWEN_1_5B to ModelInfo(
            name = "Qwen 1.5B",
            sizeMB = 950,
            size = "950 MB",
            ramRequiredMB = 1000,
            speed = "Rápida",
            accuracy = "Buena",
            description = "Equilibrio entre velocidad y calidad"
        ),
        ModelOption.GEMMA_2B to ModelInfo(
            name = "Gemma 2 2B (Google)",
            sizeMB = 1600,
            size = "1.6 GB",
            ramRequiredMB = 1800,
            speed = "Muy rápida",
            accuracy = "Excelente",
            description = "Extremadamente rápido. Ideal si buscas velocidad por encima de la profundidad técnica."
        ),
        ModelOption.QWEN_3B to ModelInfo(
            name = "Qwen 3B",
            sizeMB = 1843,
            size = "1.8 GB",
            ramRequiredMB = 2000,
            speed = "Moderada",
            accuracy = "Alta",
            description = "Máxima precisión para respuestas detalladas"
        ),
        ModelOption.PHI_3_MINI to ModelInfo(
            name = "Phi-3 Mini 4K (Microsoft)",
            sizeMB = 2390,
            size = "2.4 GB",
            ramRequiredMB = 2600,
            speed = "Rápida",
            accuracy = "Alta",
            description = "Modelo pequeño (3.8B) oficial de Microsoft. Diseñado para ser eficiente y razonar bien en español."
        ),
        ModelOption.MISTRAL_7B to ModelInfo(
            name = "Mistral 7B v0.3",
            sizeMB = 4370,
            size = "4.4 GB",
            ramRequiredMB = 4800,
            speed = "Moderada",
            accuracy = "Superior",
            description = "⚠️ SOLO GAMA ALTA (16GB RAM mín). Muy preciso en castellano. No instalar en gama media/baja."
        ),
         ModelOption.LLAMA_3_8B to ModelInfo(
             name = "Llama 3 8B (Meta)",
             sizeMB = 4920,
             size = "4.9 GB",
             ramRequiredMB = 5500,
             speed = "Moderada",
             accuracy = "Referencia",
             description = "⚠️ SOLO GAMA ALTA (16GB RAM mín). El estándar de oro actual. No instalar en gama media/baja."
         ),
         ModelOption.LLAMA_3_2_3B to ModelInfo(
             name = "Llama 3.2 3B (Potente Meta)",
             sizeMB = 1950,
             size = "1.95 GB",
             ramRequiredMB = 2100,
             speed = "Rápida",
             accuracy = "Alta",
             description = "Modelo potente y eficiente de Meta. Buen equilibrio entre tamaño y capacidad de razonamiento."
         )
    )

    // Real download effect
    LaunchedEffect(showDownloadDialog, selectedModel) {
        if (showDownloadDialog && selectedModel != null && downloadStatus == DownloadStatus.Downloading) {
            val modelFileName = selectedModel!!.toModelFileName()
            
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
                            downloadStatus = DownloadStatus.Success
                            downloadProgress = 1f
                            
                            // Save selected model
                            modelPersistence.saveSelectedModel(modelFileName)
                            
                            // Initialize the model
                            delay(500)
                            val initSuccess = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                val contextLength = modelSettingsManager.contextLength.value
                                llamaRepository.initialize(modelFileName, maxContextLength = contextLength)
                            }
                            
                            if (initSuccess) {
                                modelPersistence.markModelInitialized()
                                android.util.Log.i("ModelSelection", "Modelo inicializado correctamente")
                            } else {
                                android.util.Log.e("ModelSelection", "Error al inicializar modelo")
                                downloadStatus = DownloadStatus.Error("Error al inicializar el modelo")
                            }
                            
                            delay(500)
                            showDownloadDialog = false
                            onDownloadAndInstall(selectedModel!!)
                            // Reset state
                            downloadProgress = 0f
                            downloadStatus = DownloadStatus.Idle
                        }
                        is com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository.DownloadStatus.Error -> {
                            downloadStatus = DownloadStatus.Error(status.message)
                            android.util.Log.e("ModelSelection", "Error descargando: ${status.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                downloadStatus = DownloadStatus.Error(e.message ?: "Error desconocido")
                android.util.Log.e("ModelSelection", "Excepción descargando modelo", e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        // Header
        Text(
            text = "Selecciona un modelo",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Elige la IA que mejor se adapte a tu dispositivo",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Espacio libre en disco
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Disco
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Disco",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    Text(
                        text = "${String.format("%.1f", freeSpaceGB)} GB",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // RAM
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "RAM Libre",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    Text(
                        text = "${availableRamMB / 1024} / ${totalRamMB / 1024} GB",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Opciones de modelos - Scrollable area
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            models.forEach { (model, info) ->
                ModelCard(
                    model = model,
                    info = info,
                    isSelected = selectedModel == model,
                    deviceTotalRamMB = totalRamMB,
                    onSelect = { selectedModel = model }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botón de descarga o continuar
        if (installedModel != null) {
            // Ya hay un modelo instalado, mostrar botón de continuar
            Button(
                onClick = {
                    // Ir al chat
                    onDownloadAndInstall(selectedModel ?: ModelOption.QWEN_0_5B)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Continuar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            // No hay modelo instalado, obligar a descargar
            Button(
                onClick = {
                    selectedModel?.let {
                        showDownloadDialog = true
                        downloadStatus = DownloadStatus.Downloading
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = selectedModel != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedModel != null) "Descargar e Instalar" else "Selecciona un modelo",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // Download Progress Dialog
    if (showDownloadDialog && selectedModel != null) {
        val modelData = models.find { it.first == selectedModel }
        val modelItemInfo = modelData?.second ?: ModelInfo("Modelo", 0, "", 0, "", "", "")
        DownloadProgressDialog(
            modelName = modelItemInfo.name,
            totalSizeMB = modelItemInfo.sizeMB,
            ramRequiredMB = modelItemInfo.ramRequiredMB,
            deviceTotalRamMB = totalRamMB,
            progress = downloadProgress,
            downloadStatus = downloadStatus,
            onCancel = {
                coroutineScope.launch {
                    showDownloadDialog = false
                    downloadProgress = 0f
                    downloadStatus = DownloadStatus.Idle
                }
            },
            onRetry = {
                downloadProgress = 0f
                downloadStatus = DownloadStatus.Downloading
            }
        )
    }
}

@Composable
private fun ModelCard(
    model: ModelOption,
    info: ModelInfo,
    isSelected: Boolean,
    deviceTotalRamMB: Int,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = info.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Text(
                text = info.description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp)
            )

            if (info.ramRequiredMB > deviceTotalRamMB) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ RAM insuficiente (${String.format("%.1f", info.ramRequiredMB / 1024.0)} GB req.)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Funcionará lento usando memoria virtual",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✓ Compatible (${String.format("%.1f", info.ramRequiredMB / 1024.0)} GB req.)",
                    fontSize = 12.sp,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ModelStat(
                    icon = Icons.Default.Memory,
                    label = "Tamaño",
                    value = info.size
                )
                ModelStat(
                    icon = Icons.Default.Speed,
                    label = "Velocidad",
                    value = info.speed
                )
                ModelStat(
                    icon = Icons.Default.Star,
                    label = "Precisión",
                    value = info.accuracy
                )
            }
        }
    }
}

@Composable
private fun ModelStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun DownloadProgressDialog(
    modelName: String,
    totalSizeMB: Int,
    ramRequiredMB: Int,
    deviceTotalRamMB: Int,
    progress: Float,
    downloadStatus: DownloadStatus,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (downloadStatus) {
                    is DownloadStatus.Downloading, DownloadStatus.Success -> {
                        // Title
                        Text(
                            text = "Instalando $modelName",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        if (ramRequiredMB > deviceTotalRamMB) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Aviso: Requiere más RAM de la disponible físicamente",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Model name
                        Text(
                            text = "Descargando $modelName...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Progress indicator
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Progress text
                        val downloadedMB = (totalSizeMB * progress).toInt()
                        Text(
                            text = "$downloadedMB MB de $totalSizeMB MB descargados",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Percentage
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Cancel button
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancelar")
                        }
                    }
                    is DownloadStatus.Error -> {
                        // Error state
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Error al descargar",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = (downloadStatus as DownloadStatus.Error).message,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Retry button
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reintentar")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Cancel button
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancelar")
                        }
                    }
                    else -> {
                        // Idle or other states - show loading
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
