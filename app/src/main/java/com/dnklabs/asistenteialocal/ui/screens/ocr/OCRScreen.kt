package com.dnklabs.asistenteialocal.ui.screens.ocr

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dnklabs.asistenteialocal.ui.theme.LocalPrivacyGreen
import com.dnklabs.asistenteialocal.ui.components.PermissionRequestDialog
import com.dnklabs.asistenteialocal.ui.components.AppPermissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OCRScreen(
    viewModel: OCRViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
    onTextRecognized: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val wordCount by viewModel.wordCount.collectAsState()

    // Dialog state for permission requests
    var showCameraPermissionDialog by remember { mutableStateOf(false) }
    var showMediaPermissionDialog by remember { mutableStateOf(false) }
    var showPdfPermissionDialog by remember { mutableStateOf(false) }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.processImage(context, it) }
    }

    // PDF picker launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.processPdf(context, it) }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCameraPermissionDialog = false
            // Open camera - would need camera launcher, currently using gallery picker
            imagePickerLauncher.launch("image/*")
        } else {
            showCameraPermissionDialog = false
            Toast.makeText(context, "Permiso de cámara requerido", Toast.LENGTH_SHORT).show()
        }
    }

    // Media/Storage permission launcher for images
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showMediaPermissionDialog = false
            imagePickerLauncher.launch("image/*")
        } else {
            showMediaPermissionDialog = false
            Toast.makeText(context, "Permiso de almacenamiento requerido", Toast.LENGTH_SHORT).show()
        }
    }

    // Storage permission launcher for PDFs
    val pdfPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showPdfPermissionDialog = false
            pdfPickerLauncher.launch("application/pdf")
        } else {
            showPdfPermissionDialog = false
            Toast.makeText(context, "Permiso de almacenamiento requerido", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("OCR - Escaneo de texto")
                        Text(
                            text = "100% Offline con ML Kit",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalPrivacyGreen
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    if (recognizedText.isNotBlank()) {
                        IconButton(
                            onClick = { 
                                onTextRecognized(recognizedText)
                                Toast.makeText(context, "Texto enviado al chat", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Usar en chat")
                        }
                        IconButton(onClick = { viewModel.clearText() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                        }
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
            when (val state = uiState) {
                is OcrUiState.Idle -> {
                    IdleContent(
                        onSelectImage = {
                            val hasStoragePermission = when {
                                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU -> {
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                                }
                                else -> {
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                                }
                            }
                            if (hasStoragePermission) {
                                imagePickerLauncher.launch("image/*")
                            } else {
                                showMediaPermissionDialog = true
                            }
                        },
                        onSelectPdf = {
                            val hasStoragePermission = when {
                                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU -> {
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                                }
                                else -> {
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                                }
                            }
                            if (hasStoragePermission) {
                                pdfPickerLauncher.launch("application/pdf")
                            } else {
                                showPdfPermissionDialog = true
                            }
                        }
                    )
                }
                
                is OcrUiState.Processing -> {
                    ProcessingContent()
                }
                
                is OcrUiState.Success -> {
                    SuccessContent(
                        text = recognizedText,
                        wordCount = wordCount,
                        imageUri = state.imageUri,
                        onUseText = { 
                            onTextRecognized(recognizedText)
                            onNavigateBack()
                        },
                        onCopyToClipboard = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("OCR Text", recognizedText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Texto copiado", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                
                is OcrUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.clearText() }
                    )
                }
            }

            // Permission dialogs
            if (showMediaPermissionDialog) {
                PermissionRequestDialog(
                    permissionInfo = AppPermissions.MediaImagesPermissionInfo(),
                    onDismiss = { showMediaPermissionDialog = false },
                    onPermissionGranted = {
                        showMediaPermissionDialog = false
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            mediaPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                        } else {
                            mediaPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                )
            }

            if (showPdfPermissionDialog) {
                PermissionRequestDialog(
                    permissionInfo = AppPermissions.StoragePermissionInfo(),
                    onDismiss = { showPdfPermissionDialog = false },
                    onPermissionGranted = {
                        showPdfPermissionDialog = false
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            pdfPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                        } else {
                            pdfPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                )
            }

            if (showCameraPermissionDialog) {
                PermissionRequestDialog(
                    permissionInfo = AppPermissions.CameraPermissionInfo(),
                    onDismiss = { showCameraPermissionDialog = false },
                    onPermissionGranted = {
                        showCameraPermissionDialog = false
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }
        }
    }
}

@Composable
private fun IdleContent(
    onSelectImage: () -> Unit,
    onSelectPdf: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Image option
        Surface(
            onClick = onSelectImage,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Column {
                    Text(
                        text = "Escanear imagen",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Selecciona una foto de tu galería",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // PDF option
        Surface(
            onClick = onSelectPdf,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Column {
                    Text(
                        text = "Extraer de PDF",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Selecciona un documento PDF",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "El procesamiento se realiza 100% en tu dispositivo",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProcessingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Procesando...",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "ML Kit está analizando el texto localmente",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SuccessContent(
    text: String,
    wordCount: Int,
    imageUri: Uri?,
    onUseText: () -> Unit,
    onCopyToClipboard: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Stats card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    value = wordCount.toString(),
                    label = "Palabras"
                )
                StatItem(
                    value = text.length.toString(),
                    label = "Caracteres"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Image preview (if available)
        imageUri?.let { uri ->
            val context = LocalContext.current
            val bitmap = remember(uri) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                        android.graphics.ImageDecoder.decodeBitmap(source)
                    } else {
                        @Suppress("DEPRECATION")
                        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Imagen escaneada",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        // Text content
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Texto reconocido:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCopyToClipboard,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copiar")
            }
            
            Button(
                onClick = onUseText,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Usar en chat")
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Intentar de nuevo")
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
        )
    }
}
