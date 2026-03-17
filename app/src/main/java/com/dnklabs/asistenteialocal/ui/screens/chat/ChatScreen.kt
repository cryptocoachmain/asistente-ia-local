package com.dnklabs.asistenteialocal.ui.screens.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dnklabs.asistenteialocal.data.local.TokenStats
import com.dnklabs.asistenteialocal.data.local.TokenWarningManager
import com.dnklabs.asistenteialocal.data.local.ModelSettingsManager
import com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository
import com.dnklabs.asistenteialocal.ui.screens.ocr.OCRViewModel
import com.dnklabs.asistenteialocal.ui.components.PermissionRequestDialog
import com.dnklabs.asistenteialocal.ui.components.AppPermissions
import com.dnklabs.asistenteialocal.ui.theme.LocalPrivacyGreen
import kotlinx.coroutines.launch
import com.dnklabs.asistenteialocal.data.ocr.OCRService
import com.dnklabs.asistenteialocal.data.repository.UpdateRepository
import com.dnklabs.asistenteialocal.data.repository.UpdateInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String? = null,
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToOCR: () -> Unit = {}
) {
    val context = LocalContext.current
    
    val modelPersistence = remember { com.dnklabs.asistenteialocal.data.local.ModelPersistenceManager(context) }
    val modelSettingsManager = remember { ModelSettingsManager(context) }
    
    val viewModel: ChatViewModel = viewModel {
        ChatViewModel(
            llamaRepository = LlamaCppRepository(context),
            ttsManager = TextToSpeechManager(context),
            audioRecorder = AudioRecorder(context),
            tokenManager = TokenWarningManager(context),
            modelPersistence = modelPersistence,
            modelSettingsManager = modelSettingsManager,
            context = context
        )
    }
    
    val ocrViewModel: OCRViewModel = viewModel {
        OCRViewModel(context)
    }
    
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val ttsEnabled by viewModel.ttsEnabled.collectAsState()
    val tokenStats by viewModel.tokenStats.collectAsState()
    val showTokenWarning by viewModel.showTokenWarning.collectAsState()
    val selectedModel by modelPersistence.selectedModel.collectAsState()
    
    val isModelInitialized by viewModel.isModelInitialized.collectAsState()
    val isInitializing by viewModel.isInitializing.collectAsState()
    val initializationError by viewModel.initializationError.collectAsState()
    
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // File attachment state
    var attachedFileContext by remember { mutableStateOf<FileAttachment?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showTokenLimitDialog by remember { mutableStateOf(false) }
    var pendingFileText by remember { mutableStateOf("") }
    var pendingFileTokens by remember { mutableStateOf(0) }
    
    // OCR state
    val ocrState by ocrViewModel.uiState.collectAsState()
    val recognizedText by ocrViewModel.recognizedText.collectAsState()
    
    val transcribedText by viewModel.transcribedText.collectAsState()
    val ramWarning by viewModel.ramWarning.collectAsState()

    // Version update status
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var isUpdateAvailable by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    // Check for updates on first composition
    LaunchedEffect(Unit) {
        isCheckingUpdate = true
        try {
            val updateRepo = UpdateRepository(context)
            val currentVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            } catch (e: Exception) {
                "1.0.0"
            }
            val updateInfo = updateRepo.checkForUpdate(currentVersion)
            if (updateInfo != null) {
                latestVersion = updateInfo.versionName
                isUpdateAvailable = true
            } else {
                isUpdateAvailable = false
            }
        } catch (e: Exception) {
            // Silently fail - don't bother user
            isUpdateAvailable = false
        } finally {
            isCheckingUpdate = false
        }
    }

    val promptText = remember(messageText, attachedFileContext) {
        buildString {
            if (messageText.isNotBlank()) {
                append(messageText)
            }
            attachedFileContext?.let {
                if (isNotEmpty()) append("\n\n")
                append("[Contexto de archivo: ${it.text}]")
            }
        }
    }
    val promptCharCount = promptText.length
    val maxPromptChars = (tokenStats?.maxTokens ?: TokenWarningManager.DEFAULT_MAX_TOKENS) *
        TokenWarningManager.CHARS_PER_TOKEN
    val isPromptOverLimit = promptCharCount > maxPromptChars

    LaunchedEffect(transcribedText) {
        transcribedText?.let {
            messageText = it
            viewModel.clearTranscribedText()
        }
    }

    LaunchedEffect(ramWarning) {
        ramWarning?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(selectedModel, isInitializing) {
        if (!selectedModel.isNullOrBlank() && !isInitializing) {
            viewModel.initializeModel()
        }
    }
    
    // Microphone permission dialog state
    var showMicPermissionDialog by remember { mutableStateOf(false) }
    
    // Image, PDF, and Camera permission dialog states
    var showImagePermissionDialog by remember { mutableStateOf(false) }
    var showPdfPermissionDialog by remember { mutableStateOf(false) }
    var showCameraPermissionDialog by remember { mutableStateOf(false) }
    
    // Storage permission dialog state
    var showStoragePermissionDialog by remember { mutableStateOf(false) }
    
    // Request audio permission
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleRecording()
        } else {
            showMicPermissionDialog = true
        }
    }
    
    LaunchedEffect(chatId) {
        if (chatId != null) {
            viewModel.loadConversation(chatId)
        } else {
            viewModel.startNewChat()
        }
    }

    // Camera launcher - MUST be declared before cameraPermissionLauncher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let { bmp ->
            processBitmapWithTokenCheck(
                context = context,
                bitmap = bmp,
                tokenManager = null,
                ocrViewModel = ocrViewModel,
                onProcessingStart = { /* Show loading */ },
                onTokenLimitExceeded = { text, tokens ->
                    pendingFileText = text
                    pendingFileTokens = tokens
                    showTokenLimitDialog = true
                },
                onSuccess = { text, type ->
                    attachedFileContext = FileAttachment(
                        text = text,
                        uri = null,
                        type = type,
                        name = "Foto de cámara",
                        bitmap = bmp
                    )
                }
            )
        }
    }

    // Camera permission launcher - references cameraLauncher, so must come AFTER
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            showCameraPermissionDialog = true
        }
    }

    // Image picker launcher for gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            processFileWithTokenCheck(
                context = context,
                fileUri = imageUri,
                fileType = FileType.IMAGE,
                tokenManager = null,
                ocrViewModel = ocrViewModel,
                onProcessingStart = { /* Show loading */ },
                onTokenLimitExceeded = { text, tokens ->
                    pendingFileText = text
                    pendingFileTokens = tokens
                    showTokenLimitDialog = true
                },
                onSuccess = { text, uri, type ->
                    attachedFileContext = FileAttachment(
                        text = text,
                        uri = uri,
                        type = type,
                        name = "Imagen seleccionada"
                    )
                }
            )
        }
    }
    
    // PDF picker launcher - using OpenDocument for better Android 11+ compatibility
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { pdfUri ->
            // Take persistable permission for future access
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(pdfUri, takeFlags)
            } catch (e: Exception) {
                // Ignore if cannot persist
            }
            
            processFileWithTokenCheck(
                context = context,
                fileUri = pdfUri,
                fileType = FileType.PDF,
                tokenManager = null,
                ocrViewModel = ocrViewModel,
                onProcessingStart = { /* Show loading */ },
                onTokenLimitExceeded = { text, tokens ->
                    pendingFileText = text
                    pendingFileTokens = tokens
                    showTokenLimitDialog = true
                },
                onSuccess = { text, uri, type ->
                    attachedFileContext = FileAttachment(
                        text = text,
                        uri = uri,
                        type = type,
                        name = getFileName(context, pdfUri) ?: "Documento PDF"
                    )
                }
            )
        }
    }
    
    // Storage permission launcher
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showAttachmentMenu = true
        } else {
            showStoragePermissionDialog = true
        }
    }
    
    // Image permission launcher
    val imagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            galleryLauncher.launch("image/*")
        } else {
            showImagePermissionDialog = true
        }
    }
    
    // Watch for OCR completion and place text in input field
    LaunchedEffect(ocrState, recognizedText) {
        when (val state = ocrState) {
            is com.dnklabs.asistenteialocal.ui.screens.ocr.OcrUiState.Success -> {
                // Place extracted text in the message input field
                if (recognizedText.isNotBlank()) {
                    messageText = recognizedText
                    Toast.makeText(context, "Texto extraído: ${recognizedText.length} caracteres", Toast.LENGTH_SHORT).show()
                }
            }
            is com.dnklabs.asistenteialocal.ui.screens.ocr.OcrUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }
            else -> { /* Do nothing */ }
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    
    // Token limit dialog
    if (showTokenLimitDialog) {
        TokenLimitDialog(
            extractedText = pendingFileText,
            addedTokens = pendingFileTokens,
            remainingTokens = tokenStats?.remainingTokens ?: 0,
            onDismiss = { showTokenLimitDialog = false },
            onSendPartial = {
                // Send only first half
                val halfLength = pendingFileText.length / 2
                val partialText = pendingFileText.substring(0, halfLength)
                attachedFileContext = FileAttachment(
                    text = partialText,
                    uri = null,
                    type = FileType.TEXT,
                    name = "Texto parcial"
                )
                showTokenLimitDialog = false
            },
            onCancel = {
                showTokenLimitDialog = false
                pendingFileText = ""
                pendingFileTokens = 0
            },
            onClearAndSend = {
                viewModel.clearChat()
                attachedFileContext = FileAttachment(
                    text = pendingFileText,
                    uri = null,
                    type = FileType.TEXT,
                    name = "Texto completo"
                )
                showTokenLimitDialog = false
            }
        )
    }
    
    // Microphone permission dialog
    if (showMicPermissionDialog) {
        PermissionRequestDialog(
            permissionInfo = AppPermissions.MicrophonePermissionInfo(),
            onDismiss = { showMicPermissionDialog = false },
            onPermissionGranted = {
                showMicPermissionDialog = false
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )
    }
    
    // Camera permission dialog
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

    // Image permission dialog
    if (showImagePermissionDialog) {
        PermissionRequestDialog(
            permissionInfo = AppPermissions.MediaImagesPermissionInfo(),
            onDismiss = { showImagePermissionDialog = false },
            onPermissionGranted = {
                showImagePermissionDialog = false
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    imagePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    imagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        )
    }

    // PDF permission dialog - now using OpenDocument so no permission needed
    // This dialog is kept for backwards compatibility but should rarely show
    if (showPdfPermissionDialog) {
        PermissionRequestDialog(
            permissionInfo = AppPermissions.StoragePermissionInfo(),
            onDismiss = { showPdfPermissionDialog = false },
            onPermissionGranted = {
                showPdfPermissionDialog = false
                // OpenDocument doesn't need permissions, just open directly
                pdfLauncher.launch(arrayOf("application/pdf"))
            }
        )
    }

    // Storage permission dialog
    if (showStoragePermissionDialog) {
        StoragePermissionDialog(
            onDismiss = { showStoragePermissionDialog = false },
            onOpenSettings = {
                showStoragePermissionDialog = false
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onRequestPermission = {
                showStoragePermissionDialog = false
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    storagePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "ASISTENTE IA LOCAL",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "DNK Labs (Diego Martinez)",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "100% Offline • v1.3.4",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = LocalPrivacyGreen
                            )
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.dp,
                                    color = LocalPrivacyGreen
                                )
                            } else if (isUpdateAvailable && latestVersion != null) {
                                Text(
                                    text = "• Nueva Versión $latestVersion",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = Color(0xFFE53935),
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "• Actualizado",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = Color(0xFF43A047),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                actions = {
                    // TTS Toggle
                    IconButton(onClick = { viewModel.toggleTts() }) {
                        Icon(
                            imageVector = if (ttsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = if (ttsEnabled) "TTS activado" else "TTS desactivado",
                            tint = if (ttsEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                    
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "Historial")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    // Recording indicator
                    if (isRecording) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Grabando...",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    
                    // Attached file preview
                    AnimatedVisibility(
                        visible = attachedFileContext != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        attachedFileContext?.let { attachment ->
                            AttachedFilePreview(
                                attachment = attachment,
                                onRemove = { attachedFileContext = null },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    
                    // Input area - reorganized for better space
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Left side buttons group (Attachment + Voice)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            // Clip/Attachment Button
                            Box {
                                IconButton(
                                    onClick = {
                                        when {
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED -> {
                                                showAttachmentMenu = true
                                            }
                                            else -> {
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                    storagePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                                                } else {
                                                    storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Adjuntar archivo",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                // Attachment menu dropdown
                                DropdownMenu(
                                    expanded = showAttachmentMenu,
                                    onDismissRequest = { showAttachmentMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("📷 Cámara") },
                                        onClick = {
                                            showAttachmentMenu = false
                                            when {
                                                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                                                    cameraLauncher.launch(null)
                                                }
                                                else -> {
                                                    showCameraPermissionDialog = true
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("🖼️ Imagen") },
                                        onClick = {
                                            showAttachmentMenu = false
                                            when {
                                                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                                                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED -> {
                                                    galleryLauncher.launch("image/*")
                                                }
                                                else -> {
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                        imagePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                                                    } else {
                                                        imagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                                    }
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Image, contentDescription = null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("📄 PDF") },
                                        onClick = {
                                            showAttachmentMenu = false
                                            // Use OpenDocument which doesn't require permissions
                                            pdfLauncher.launch(arrayOf("application/pdf"))
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                        }
                                    )
                                }
                            }
                            
                            // Voice/Mic Button
                            IconButton(
                                onClick = {
                                    when {
                                        isRecording -> viewModel.toggleRecording()
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                                            viewModel.toggleRecording()
                                        }
                                        else -> {
                                            showMicPermissionDialog = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRecording) MaterialTheme.colorScheme.error 
                                        else MaterialTheme.colorScheme.primaryContainer
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (isRecording) "Detener" else "Grabar voz",
                                    tint = if (isRecording) MaterialTheme.colorScheme.onError 
                                           else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        
                        // Text Input - now takes most of the space
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            placeholder = { Text("Escribe un mensaje...") },
                            maxLines = 5,
                            enabled = uiState !is ChatUiState.Loading && !isRecording,
                            shape = RoundedCornerShape(24.dp),
                            singleLine = false
                        )
                        
                        // Send Button - always visible but disabled when empty
                        IconButton(
                            onClick = {
                                if (messageText.isNotBlank() || attachedFileContext != null) {
                                    val fullMessage = buildString {
                                        if (messageText.isNotBlank()) {
                                            append(messageText)
                                        }
                                        attachedFileContext?.let {
                                            if (isNotEmpty()) append("\n\n")
                                            append("[Contexto de archivo: ${it.text}]")
                                        }
                                    }
                                    viewModel.sendMessage(fullMessage)
                                    messageText = ""
                                    attachedFileContext = null
                                }
                            },
                            enabled = (messageText.isNotBlank() || attachedFileContext != null) && 
                                      uiState !is ChatUiState.Loading && !isRecording,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(start = 4.dp)
                                .clip(CircleShape)
                                .background(
                                    if ((messageText.isNotBlank() || attachedFileContext != null) && 
                                        uiState !is ChatUiState.Loading) 
                                        MaterialTheme.colorScheme.primary 
                                    else Color.Gray.copy(alpha = 0.5f)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar",
                                tint = if ((messageText.isNotBlank() || attachedFileContext != null) && 
                                           uiState !is ChatUiState.Loading) 
                                           MaterialTheme.colorScheme.onPrimary 
                                       else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Prompt: $promptCharCount / $maxPromptChars caracteres",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPromptOverLimit) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (messages.isEmpty()) {
                // Empty state - centered welcome message
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Inicia una conversación",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Tu IA local está lista para ayudarte",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "📎 Adjunta imágenes, fotos o PDFs\n🎤 Usa el micrófono para hablar\n📄 Escanea documentos desde el menú",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )
                        
                        // Model initialization status
                        Spacer(modifier = Modifier.height(24.dp))
                        when {
                            isInitializing -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "Inicializando modelo de IA...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            !isModelInitialized && initializationError != null -> {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Error de inicialización",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            initializationError ?: "Error desconocido",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        viewModel.initializeModel()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Text("Reintentar")
                                            }
                                            OutlinedButton(
                                                onClick = onNavigateToSettings
                                            ) {
                                                Text("Configuración")
                                            }
                                        }
                                    }
                                }
                            }
                            isModelInitialized -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Modelo listo",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Token usage indicator - positioned at top for empty state too
                tokenStats?.let { stats ->
                    TokenUsageIndicator(
                        tokenStats = stats,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                    )
                }
            } else {
                // Messages list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, 
                        end = 16.dp, 
                        top = 56.dp, // Space for token indicator
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        val context = LocalContext.current
                        MessageBubble(
                            message = message,
                            isLastMessage = message == messages.last(),
                            modelName = selectedModel ?: "Qwen",
                            onShare = { viewModel.shareMessage(context, message.content) },
                            onCopy = { viewModel.copyToClipboard(context, message.content) }
                        )
                    }
                    
                    // Loading indicator
                    if (uiState is ChatUiState.Loading) {
                        val userMessageCount = messages.count { it.isUser }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(end = 64.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Pensando...", style = MaterialTheme.typography.bodyMedium)
                                            if (userMessageCount > 1) {
                                                Text(
                                                    "Puede tardar más al cargar el contexto del chat",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Token usage indicator - floating at top for messages list
                tokenStats?.let { stats ->
                    TokenUsageIndicator(
                        tokenStats = stats,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }

            // Token warning banner
            AnimatedVisibility(
                visible = showTokenWarning,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TokenWarningBanner(
                    onDismiss = { viewModel.dismissTokenWarning() },
                    onClearContext = { viewModel.clearChat() },
                    modifier = Modifier.padding(
                        top = if (messages.isEmpty()) 60.dp else 60.dp, 
                        start = 16.dp, 
                        end = 16.dp
                    )
                )
            }

            // Context accumulation warning banner
            val contextWarningVisible by viewModel.contextWarningVisible.collectAsState()
            val contextWarningMessage by viewModel.contextWarningMessage.collectAsState()
            
            AnimatedVisibility(
                visible = contextWarningVisible,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = if (messages.isEmpty()) 60.dp else 60.dp, 
                            start = 16.dp, 
                            end = 16.dp
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = contextWarningMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissContextWarning() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Error message
            AnimatedVisibility(
                visible = uiState is ChatUiState.Error,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it }
            ) {
                if (uiState is ChatUiState.Error) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = (uiState as ChatUiState.Error).message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // OCR Processing overlay
            if (ocrState is com.dnklabs.asistenteialocal.ui.screens.ocr.OcrUiState.Processing) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Procesando archivo...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Extrayendo texto con OCR",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachedFilePreview(
    attachment: FileAttachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail for images
            when (attachment.type) {
                FileType.IMAGE, FileType.CAMERA -> {
                    attachment.bitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } ?: attachment.uri?.let { uri ->
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
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } ?: Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } ?: Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                FileType.PDF -> {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                FileType.TEXT -> {
                    Icon(
                        imageVector = Icons.Default.TextSnippet,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${attachment.text.length} caracteres",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Quitar adjunto",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun TokenLimitDialog(
    extractedText: String,
    addedTokens: Int,
    remainingTokens: Int,
    onDismiss: () -> Unit,
    onSendPartial: () -> Unit,
    onCancel: () -> Unit,
    onClearAndSend: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("Archivo muy grande")
        },
        text = {
            Column {
                Text(
                    "El archivo es muy grande y excede el límite de tokens disponibles.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            "Tokens añadidos: $addedTokens",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Restantes: $remainingTokens",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (remainingTokens < 0) MaterialTheme.colorScheme.error 
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "¿Qué deseas hacer?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSendPartial,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enviar solo una parte (primera mitad)")
                }
                OutlinedButton(
                    onClick = onClearAndSend,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Limpiar contexto y enviar")
                }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar")
                }
            }
        },
        dismissButton = null
    )
}

@Composable
 private fun MessageBubble(
    message: ChatMessageDisplay,
    isLastMessage: Boolean,
    modelName: String = "",
    onShare: () -> Unit = {},
    onCopy: () -> Unit = {}
) {
    val isUser = message.isUser
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Mostrar modelo a la izquierda (solo para mensajes del asistente y solo en el primer mensaje)
        if (!isUser && isLastMessage && modelName.isNotBlank()) {
            Row(
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ModelTraining,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Modelo: $modelName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .padding(horizontal = if (isUser) 0.dp else 0.dp)
                    .widthIn(max = 300.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Get colors outside remember to avoid composable context issues
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    
                    // Make URLs clickable in the message
                    val annotatedContent = remember(message.content, isUser) {
                        val text = message.content
                        // Simple URL detector; covers http and https
                        val urlRegex = "(https?://[^\\s]+)".toRegex()
                        buildAnnotatedString {
                            var lastIndex = 0
                            for (match in urlRegex.findAll(text)) {
                                val start = match.range.first
                                val end = match.range.last + 1
                                if (start > lastIndex) {
                                    append(text.substring(lastIndex, start))
                                }
                                val url = match.value
                                // Mark this range as a URL annotation
                                pushStringAnnotation(tag = "URL", annotation = url)
                                withStyle(style = SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline)) {
                                    append(url)
                                }
                                pop()
                                lastIndex = end
                            }
                            if (lastIndex < text.length) {
                                append(text.substring(lastIndex))
                            }
                        }
                    }
                    val context = LocalContext.current
                    ClickableText(
                        text = annotatedContent,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = textColor
                        ),
                        onClick = { offset ->
                            annotatedContent.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                    context.startActivity(intent)
                                }
                        }
                    )
                    
                    if (!isUser && message.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (message.responseTimeMs != null) {
                                Text(
                                    text = formatResponseTime(message.responseTimeMs),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                IconButton(
                                    onClick = onCopy,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copiar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onShare,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Compartir",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    } else if (!isUser && message.responseTimeMs != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatResponseTime(message.responseTimeMs),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenUsageIndicator(
    tokenStats: TokenStats,
    modifier: Modifier = Modifier
) {
    val percentage = if (tokenStats.maxTokens > 0) {
        tokenStats.currentTokens.toFloat() / tokenStats.maxTokens.toFloat()
    } else 0f
    
    val progressColor = when {
        percentage < 0.5f -> MaterialTheme.colorScheme.primary
        percentage < 0.7f -> Color(0xFFFFA726) // Orange
        percentage < 0.85f -> Color(0xFFFF7043) // Deep Orange
        else -> MaterialTheme.colorScheme.error
    }
    
    AnimatedVisibility(
        visible = tokenStats.maxTokens > 0,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${tokenStats.currentTokens} / ${tokenStats.maxTokens}",
                        style = MaterialTheme.typography.bodySmall,
                        color = progressColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { percentage.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )

                if (percentage > 0.85f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Memoria casi llena - considera limpiar el contexto",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun StoragePermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermission: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Permiso de Almacenamiento",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Necesitamos acceso a tu almacenamiento para seleccionar imágenes y PDFs.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Permiso denegado",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Para usar archivos adjuntos, debes otorgar el permiso desde la configuración de Android.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onRequestPermission) {
                Text("Solicitar nuevamente")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenSettings) {
                    Text("Ir a ajustes")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}

@Composable
private fun TokenWarningBanner(
    onDismiss: () -> Unit,
    onClearContext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Memoria de Contexto Llena",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Has alcanzado el limite de tokens del modelo. Para continuar conversando, limpia el contexto actual.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onClearContext,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Limpiar Contexto")
            }
        }
    }
}

// Data classes and helper functions

private fun formatResponseTime(ms: Long): String {
    val totalSeconds = ms / 1000
    if (totalSeconds < 1) return "${ms}ms"
    if (totalSeconds < 60) return "${totalSeconds}s"
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (seconds == 0L) "${minutes}m" else "${minutes}m ${seconds}s"
}

enum class FileType {
    IMAGE, CAMERA, PDF, TEXT
}

data class FileAttachment(
    val text: String,
    val uri: Uri?,
    val type: FileType,
    val name: String,
    val bitmap: android.graphics.Bitmap? = null
)

private fun processFileWithTokenCheck(
    context: android.content.Context,
    fileUri: Uri,
    fileType: FileType,
    tokenManager: TokenWarningManager?,
    ocrViewModel: OCRViewModel,
    onProcessingStart: () -> Unit,
    onTokenLimitExceeded: (String, Int) -> Unit,
    onSuccess: (String, Uri?, FileType) -> Unit
) {
    onProcessingStart()
    
    // Calcular límite de caracteres basado en tokens disponibles (aprox 4 caracteres por token)
    val maxCharacters = when (tokenManager) {
        null -> 10000 // Default if no token manager
        else -> {
            val stats = tokenManager.getTokenStats()
            val availableTokens = (stats.maxTokens - stats.currentTokens).coerceAtLeast(0)
            (availableTokens * 4).coerceIn(1000, 50000) // Mínimo 1000, máximo 50000 caracteres
        }
    }
    
    when (fileType) {
        FileType.IMAGE -> {
            ocrViewModel.processImage(context, fileUri)
        }
        FileType.PDF -> {
            // Extraer todas las páginas hasta el límite de caracteres
            ocrViewModel.processPdf(context, fileUri, maxCharacters)
        }
        else -> { /* Handle other types */ }
    }
}

private fun processBitmapWithTokenCheck(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    tokenManager: TokenWarningManager?,
    ocrViewModel: OCRViewModel,
    onProcessingStart: () -> Unit,
    onTokenLimitExceeded: (String, Int) -> Unit,
    onSuccess: (String, FileType) -> Unit
) {
    onProcessingStart()
    ocrViewModel.processBitmap(bitmap)
}

private fun getFileName(context: android.content.Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
