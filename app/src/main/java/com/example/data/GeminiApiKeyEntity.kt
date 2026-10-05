package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gemini_api_keys")
data class GeminiApiKeyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val apiKey: String,
    val label: String = "Chave Gemini",
    val isActive: Boolean = true,
    val callCount: Int = 0,
    val failureCount: Int = 0,
    val lastUsedTimestamp: Long = 0L
)
