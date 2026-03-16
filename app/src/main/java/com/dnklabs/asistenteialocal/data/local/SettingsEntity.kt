package com.dnklabs.asistenteialocal.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val selectedModelId: String = "default",
    val ttsEnabled: Boolean = false,
    val ttsLanguage: String = "es-ES",
    val ttsSpeed: Float = 1.0f,
    val darkMode: Boolean = false,
    val apiBaseUrl: String = "http://localhost:8080",
    val systemPrompt: String = ""
)
