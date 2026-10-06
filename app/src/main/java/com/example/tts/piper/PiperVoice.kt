package com.example.tts.piper

data class PiperVoice(
    val id: String,
    val name: String,
    val languageCode: String,
    val languageDisplayName: String,
    val gender: String,
    val quality: String,
    val sampleRate: Int = 22050,
    val modelUrl: String,
    val configUrl: String,
    val sizeMb: Float,
    val description: String,
    val isCustom: Boolean = false
)
