package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String,
    val path: String = "", // File path / URI to the eBook file
    val progress: Float = 0f, // 0.0 to 1.0 (reading progress)
    val format: String = "EPUB", // "EPUB", "TXT", "MD", "HTML"
    val coverImagePath: String? = null, // Local file path to cover image (from EPUB or user upload)
    val coverGradientStart: Long = 0xFF1E3A8A, // Indigo 900
    val coverGradientEnd: Long = 0xFF0284C7,   // Sky 600
    val totalChapters: Int = 1,
    val currentChapterIndex: Int = 0,
    val currentParagraphIndex: Int = 0,
    val readingProgress: Float = progress, // Backwards compatibility for existing views
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,

    // Reading preferences - Speech & Narration (Voice & Speed)
    val voiceEngine: String = "EDGE_TTS",
    val voiceId: String = "pt-BR-FranciscaNeural",
    val voiceSpeed: Float = 1.0f, // 0.5f to 2.5f
    val voicePitch: Float = 1.0f, // 0.8f to 1.2f

    // Reading preferences - Visual & Formatting
    val fontSizeSp: Float = 18f,
    val lineSpacing: Float = 1.5f,
    val readerTheme: String = "BOOK_PAPER",
    val readerFont: String = "SERIF",

    // Negative word list & automatic page number filtering
    val negativeWords: String = "", // Comma or newline separated terms to omit from speech
    val skipPageNumbers: Boolean = true
)
