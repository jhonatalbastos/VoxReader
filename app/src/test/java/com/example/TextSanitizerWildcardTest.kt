package com.example

import com.example.util.TextSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSanitizerWildcardTest {

    @Test
    fun testPartWildcardFourDigitsRemovesPartNumbers() {
        val rawText = "Início do capítulo part0024 e continuação da história."
        val sanitized = TextSanitizer.cleanForSpeech(
            text = rawText,
            negativeWords = "part****",
            skipPageNumbers = false
        )

        assertFalse(sanitized.contains("part0024"))
        assertEquals("Início do capítulo e continuação da história.", sanitized)
    }

    @Test
    fun testPartWildcardMatches0001And9999() {
        val rawText1 = "Arquivo part0001 carregado."
        val rawText2 = "Arquivo part9999 carregado."

        val sanitized1 = TextSanitizer.cleanForSpeech(rawText1, "part****", false)
        val sanitized2 = TextSanitizer.cleanForSpeech(rawText2, "part****", false)

        assertEquals("Arquivo carregado.", sanitized1)
        assertEquals("Arquivo carregado.", sanitized2)
    }

    @Test
    fun testPartWildcardPreservesNormalWordsLikeParticipar() {
        val rawText = "Todos devem participar da reunião part0024 amanhã."
        val sanitized = TextSanitizer.cleanForSpeech(
            text = rawText,
            negativeWords = "part****",
            skipPageNumbers = false
        )

        // "participar" must NOT be corrupted because it has 6 characters after 'part', not 4
        assertTrue(sanitized.contains("participar"))
        assertFalse(sanitized.contains("part0024"))
        assertEquals("Todos devem participar da reunião amanhã.", sanitized)
    }

    @Test
    fun testSingleAsteriskWildcardMatchesAnyLength() {
        val rawText = "capítulo part1 e part0024 e partABC"
        val sanitized = TextSanitizer.cleanForSpeech(
            text = rawText,
            negativeWords = "part*",
            skipPageNumbers = false
        )

        assertFalse(sanitized.contains("part1"))
        assertFalse(sanitized.contains("part0024"))
        assertFalse(sanitized.contains("partABC"))
        assertEquals("capítulo e e", sanitized)
    }

    @Test
    fun testMultipleTermsSeparatedByCommaAndNewline() {
        val rawText = "Texto com part0012 e depois [nota 5] e página 44 no rodapé."
        val negativeWords = "part****, [nota *]"
        val sanitized = TextSanitizer.cleanForSpeech(
            text = rawText,
            negativeWords = negativeWords,
            skipPageNumbers = true
        )

        assertFalse(sanitized.contains("part0012"))
        assertFalse(sanitized.contains("nota 5"))
        assertFalse(sanitized.contains("página 44"))
    }
}
