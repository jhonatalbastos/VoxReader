package com.example

import com.example.data.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioPlayerTransitionTest {

    @Test
    fun testBookEntityForAudioPlayerHasValidProperties() {
        val book1 = BookEntity(
            id = 1L,
            title = "Dom Casmurro",
            author = "Machado de Assis",
            path = "/fake/dom_casmurro.epub",
            format = "EPUB",
            coverGradientStart = 0xFF1E3A8A,
            coverGradientEnd = 0xFF0284C7,
            progress = 0.42f
        )

        val book2 = BookEntity(
            id = 2L,
            title = "Memórias Póstumas",
            author = "Machado de Assis",
            path = "/fake/memorias.epub",
            format = "EPUB",
            coverGradientStart = 0xFF14532D,
            coverGradientEnd = 0xFF16A34A,
            progress = 0.15f
        )

        assertNotNull(book1)
        assertNotNull(book2)
        assertEquals("Dom Casmurro", book1.title)
        assertEquals("Memórias Póstumas", book2.title)
        assertEquals(0.42f, book1.readingProgress)
    }
}
