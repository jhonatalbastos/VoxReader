package com.example.model

/**
 * Represents an individual text segment or sentence logged during TTS narration.
 * Kept in a rolling list of the last 10 read segments for instant Rewind/Replay.
 */
data class ReadSegment(
    val id: Long = System.nanoTime(),
    val bookId: Long,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val chapterTitle: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val shortSnippet: String
        get() = if (text.length > 90) text.take(90).trimEnd() + "…" else text
}
