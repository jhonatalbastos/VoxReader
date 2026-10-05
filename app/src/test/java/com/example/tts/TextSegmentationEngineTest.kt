package com.example.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSegmentationEngineTest {

    @Test
    fun testBasicSentenceSegmentation() {
        val engine = TextSegmentationEngine(targetChunkChars = 50, contextWindowChars = 20)

        val text = "This is a short sentence. This is another one! And a third?"
        val result = engine.createPayloads(text)

        // It should split by punctuation + space.
        // Since targetChunkChars = 50:
        // S1: "This is a short sentence." (25 chars)
        // S2: "This is another one!" (20 chars) -> S1 + S2 = 46 chars (fits in chunk 1)
        // S3: "And a third?" (12 chars) -> new chunk

        assertEquals(2, result.size)
        assertEquals("This is a short sentence. This is another one!", result[0].targetText)
        assertEquals("And a third?", result[1].targetText)
    }

    @Test
    fun testContextWindowGeneration() {
        val engine = TextSegmentationEngine(targetChunkChars = 20, contextWindowChars = 30)

        // Let's create an artificial sentence setup.
        val text = "Sentence one. Sentence two. Sentence three. Sentence four."
        val result = engine.createPayloads(text)

        // Chunk 0: "Sentence one." (13 chars)
        // Chunk 1: "Sentence two." (13 chars)
        // Chunk 2: "Sentence three." (15 chars)
        // Chunk 3: "Sentence four." (14 chars)

        assertEquals(4, result.size)

        // Verify Chunk 0 context
        assertEquals("", result[0].previousContext)
        assertTrue(result[0].nextContext.startsWith("Sentence two. Sentence three."))

        // Verify Chunk 1 context
        assertEquals("Sentence one.", result[1].previousContext)
        assertTrue(result[1].nextContext.startsWith("Sentence three."))

        // Verify Chunk 3 context
        assertEquals("Sentence two. Sentence three.", result[3].previousContext)
        assertEquals("", result[3].nextContext)
    }
}
