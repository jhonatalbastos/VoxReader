package com.example

import com.example.tts.EdgeTtsClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeTtsLiveTest {

    @Test
    fun `sec-ms-gec generates valid 64-char uppercase hex hash`() {
        val client = EdgeTtsClient()
        val gec = client.generateSecMsGec()
        assertNotNull(gec)
        assertEquals(64, gec.length)
        assertTrue(gec.all { it in '0'..'9' || it in 'A'..'F' })
    }

    @Test
    fun `edge tts connects and synthesizes audio`() = runBlocking {
        val client = EdgeTtsClient()
        val audioBytes = client.synthesizeToMp3(
            text = "Olá! Teste de leitura neural com Edge TTS.",
            voiceId = "pt-BR-FranciscaNeural",
            speed = 1.0f,
            pitch = 1.0f
        )
        assertNotNull(audioBytes)
        assertTrue("Audio bytes should be greater than 1000 bytes", audioBytes.size > 1000)
    }

    private fun assertEquals(expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
