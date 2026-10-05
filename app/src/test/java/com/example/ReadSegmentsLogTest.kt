package com.example

import com.example.model.ReadSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadSegmentsLogTest {

    @Test
    fun testReadSegmentLoggingAndRollingWindow() {
        val segments = mutableListOf<ReadSegment>()

        // Simulate reading 15 segments sequentially
        for (i in 1..15) {
            val segment = ReadSegment(
                id = i.toLong(),
                bookId = 100L,
                chapterIndex = 0,
                paragraphIndex = i,
                chapterTitle = "Capítulo 1",
                text = "Esta é a sentença número $i narrada pela voz neural."
            )
            // Prepend newest first and keep at most 10
            segments.add(0, segment)
            if (segments.size > 10) {
                segments.removeAt(segments.size - 1)
            }
        }

        // Must strictly contain the last 10 segments (15 down to 6)
        assertEquals(10, segments.size)
        assertEquals(15, segments.first().paragraphIndex)
        assertEquals(6, segments.last().paragraphIndex)
        assertTrue(segments.first().text.contains("15"))
    }

    @Test
    fun testShortSnippetTruncation() {
        val longText = "Esta é uma sentença extraordinariamente longa que contém muitas palavras complexas e detalhes narrativos que ultrapassam o limite de noventa caracteres para teste."
        val segment = ReadSegment(
            bookId = 1L,
            chapterIndex = 0,
            paragraphIndex = 0,
            chapterTitle = "Intro",
            text = longText
        )

        assertTrue(segment.shortSnippet.length <= 95)
        assertTrue(segment.shortSnippet.endsWith("…"))
    }
}
