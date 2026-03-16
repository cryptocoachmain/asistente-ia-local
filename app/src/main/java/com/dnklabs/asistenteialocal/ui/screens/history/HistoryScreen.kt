package com.dnklabs.asistenteialocal.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dnklabs.asistenteialocal.data.local.AppDatabase
import com.dnklabs.asistenteialocal.data.local.ConversationEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit = {},
    onChatSelected: (String) -> Unit = {},
    onNewChat: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    
    var conversations by remember { mutableStateOf<List<ConversationEntity>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<ConversationEntity?>(null) }
    
    var showEditDialog by remember { mutableStateOf(false) }
    var conversationToEdit by remember { mutableStateOf<ConversationEntity?>(null) }
    var editTitleText by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        database.conversationDao().getAllFlow().collect { items ->
            conversations = items
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = "Historial",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${conversations.size} conversaciones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Nueva conversación",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No hay conversaciones",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tus conversaciones guardadas aparecerán aquí",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onNewChat) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Iniciar nueva conversación")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conversations) { conversation ->
                    ConversationCard(
                        conversation = conversation,
                        onClick = { onChatSelected(conversation.id.toString()) },
                        onEdit = {
                            conversationToEdit = conversation
                            editTitleText = conversation.title
                            showEditDialog = true
                        },
                        onDelete = {
                            conversationToDelete = conversation
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    }
    
    // Dialogo de Edición
    if (showEditDialog && conversationToEdit != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Editar nombre") },
            text = {
                OutlinedTextField(
                    value = editTitleText,
                    onValueChange = { editTitleText = it },
                    label = { Text("Nombre de la conversación") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val titleToSave = editTitleText
                        val idToUpdate = conversationToEdit?.id
                        if (idToUpdate != null) {
                            scope.launch {
                                database.conversationDao().updateTitle(idToUpdate, titleToSave)
                            }
                        }
                        showEditDialog = false
                        conversationToEdit = null
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDeleteDialog && conversationToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Eliminar conversación?") },
            text = { 
                Text("¿Estás seguro de que quieres eliminar esta conversación? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val convToDelete = conversationToDelete
                        if (convToDelete != null) {
                            scope.launch {
                                database.conversationDao().delete(convToDelete)
                            }
                        }
                        showDeleteDialog = false
                        conversationToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun ConversationCard(
    conversation: ConversationEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = conversation.title.ifEmpty { "Nueva conversación" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(conversation.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
