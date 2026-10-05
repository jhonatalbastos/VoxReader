package com.example

import com.example.data.BookEntity
import com.example.util.LocalBackupManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalBackupManagerTest {

    @Test
    fun testBackupJsonCreationContainsAllRequiredFields() {
        val sampleBooks = listOf(
            BookEntity(
                id = 1L,
                title = "Dom Casmurro",
                author = "Machado de Assis",
                totalChapters = 5,
                currentChapterIndex = 2,
                currentParagraphIndex = 14,
                readingProgress = 0.45f,
                lastReadTimestamp = 1727980000000L,
                voiceEngine = "EDGE_TTS",
                voiceId = "pt-BR-FranciscaNeural",
                voiceSpeed = 1.25f,
                voicePitch = 1.05f,
                fontSizeSp = 19f,
                lineSpacing = 1.6f,
                readerTheme = "SEPIA",
                readerFont = "SERIF",
                negativeWords = "part****, pág. *",
                skipPageNumbers = true,
                isFavorite = true
            )
        )

        val jsonStr = LocalBackupManager.createBackupJson(sampleBooks)
        assertNotNull(jsonStr)

        val root = JSONObject(jsonStr)
        assertEquals(1, root.getInt("version"))
        assertEquals("VoxReader", root.getString("appName"))
        assertEquals(1, root.getInt("totalBooks"))

        val booksArray = root.getJSONArray("books")
        assertEquals(1, booksArray.length())

        val bookObj = booksArray.getJSONObject(0)
        assertEquals("Dom Casmurro", bookObj.getString("title"))
        assertEquals("Machado de Assis", bookObj.getString("author"))
        assertEquals(2, bookObj.getInt("currentChapterIndex"))
        assertEquals(14, bookObj.getInt("currentParagraphIndex"))
        assertEquals(0.45, bookObj.getDouble("readingProgress"), 0.001)
        assertEquals("EDGE_TTS", bookObj.getString("voiceEngine"))
        assertEquals("pt-BR-FranciscaNeural", bookObj.getString("voiceId"))
        assertEquals(1.25, bookObj.getDouble("voiceSpeed"), 0.01)
        assertEquals("part****, pág. *", bookObj.getString("negativeWords"))
        assertTrue(bookObj.getBoolean("skipPageNumbers"))
        assertTrue(bookObj.getBoolean("isFavorite"))
    }
}
