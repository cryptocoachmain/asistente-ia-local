package com.dnklabs.asistenteialocal.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad para almacenar conversaciones encriptadas
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    
    val timestamp: Long = System.currentTimeMillis(),
    
    val updatedAt: Long = System.currentTimeMillis(),
    
    val title: String = "", // Título auto-generado (primeros 30 chars de primera pregunta)
    
    val isIncognito: Boolean = false, // true = no guardar en historial
    
    val messagesJson: String = "" // JSON de List<ChatMessage>
)

/**
 * Mensaje individual del chat (usado para serializar a JSON)
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user" o "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val responseTimeMs: Long? = null // Tiempo de respuesta de la IA
)