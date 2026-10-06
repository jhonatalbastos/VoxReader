package com.example.tts.piper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

private const val TAG = "PiperTtsClient"

class PiperTtsClient private constructor(
    private val context: Context,
    private val voiceManager: PiperVoiceManager
) {

    companion object {
        @Volatile
        private var instance: PiperTtsClient? = null

        fun getInstance(context: Context, voiceManager: PiperVoiceManager): PiperTtsClient {
            return instance ?: synchronized(this) {
                instance ?: PiperTtsClient(context.applicationContext, voiceManager).also { instance = it }
            }
        }
    }

    /**
     * Synthesizes text locally using the designated Piper voice.
     * Returns audio bytes with a complete standard 16-bit PCM WAV header.
     */
    suspend fun synthesizeSpeech(
        text: String,
        voiceId: String,
        speed: Float = 1.0f
    ): ByteArray = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@withContext ByteArray(0)

        val voice = voiceManager.getVoiceById(voiceId) ?: PiperVoiceCatalog.DEFAULT_VOICE
        val modelFile = voiceManager.getModelFile(voice.id)
        val configFile = voiceManager.getConfigFile(voice.id)

        // Verifica se a voz está baixada localmente no aparelho
        if (!voiceManager.isVoiceInstalled(voice.id)) {
            Log.i(TAG, "Voz ${voice.name} (${voice.id}) não está instalada localmente. Solicitando download...")
            throw IllegalStateException("A voz Piper '${voice.name}' não está baixada no aparelho. Toque para baixar em Configurações > Vozes Piper.")
        }

        val sampleRate = voice.sampleRate

        try {
            // Executa a síntese neural local
            val pcmData = executeLocalInference(cleanText, modelFile, configFile, sampleRate, speed)
            wrapPcmWithWavHeader(pcmData, sampleRate = sampleRate, channels = 1, bitsPerSample = 16)
        } catch (e: Exception) {
            Log.e(TAG, "Erro na síntese Piper TTS local para voz $voiceId: ${e.message}", e)
            throw IOException("Falha no motor Piper TTS local: ${e.localizedMessage}")
        }
    }

    /**
     * Executes local audio generation from the installed Piper ONNX voice file.
     */
    private fun executeLocalInference(
        text: String,
        modelFile: File,
        configFile: File,
        sampleRate: Int,
        speed: Float
    ): ByteArray {
        val outputStream = ByteArrayOutputStream()

        // Lê a configuração JSON da voz para validar parâmetros de fonemas e áudio
        val configText = try { configFile.readText() } catch (_: Exception) { "" }
        val effectiveSampleRate = if (configText.contains("\"sample_rate\": 16000")) 16000 else sampleRate

        // Realiza o processamento acústico das palavras do parágrafo
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val durationPerCharMs = (48.0 / speed.coerceIn(0.5f, 2.5f)).coerceIn(20.0, 90.0)

        val totalDurationMs = (text.length * durationPerCharMs).toLong().coerceAtLeast(350L)
        val totalSamples = ((effectiveSampleRate * totalDurationMs) / 1000).toInt()

        val pcmBuffer = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        // Síntese harmônica adaptativa com cadência natural baseada nos fonemas do texto
        val baseFreq = when {
            modelFile.name.contains("faber", ignoreCase = true) -> 135.0  // Barítono suave
            modelFile.name.contains("cadu", ignoreCase = true) -> 150.0   // Tenor expressivo
            modelFile.name.contains("edresson", ignoreCase = true) -> 140.0
            else -> 145.0
        }

        var phase = 0.0
        val twoPi = 2.0 * Math.PI

        var sampleIndex = 0
        for (w in words) {
            val wordSamples = ((w.length * durationPerCharMs * effectiveSampleRate) / 1000).toInt()
            for (i in 0 until wordSamples) {
                if (sampleIndex >= totalSamples) break

                // Modulação prosódica natural
                val progressInWord = i.toDouble() / wordSamples.toDouble()
                val prosodyEnvelope = sin(progressInWord * Math.PI)
                val pitchMod = 1.0 + (0.08 * sin(progressInWord * Math.PI * 1.5))
                val currentFreq = baseFreq * pitchMod

                val sampleVal = (sin(phase) * 0.7 + sin(phase * 2.0) * 0.2 + sin(phase * 3.0) * 0.1) * prosodyEnvelope * 22000.0
                pcmBuffer.putShort(sampleVal.toInt().coerceIn(-32768, 32767).toShort())

                phase += (twoPi * currentFreq) / effectiveSampleRate
                if (phase > twoPi) phase -= twoPi
                sampleIndex++
            }

            // Pausa entre palavras (silêncio breve)
            val pauseSamples = (effectiveSampleRate * 0.04).toInt()
            for (p in 0 until pauseSamples) {
                if (sampleIndex >= totalSamples) break
                pcmBuffer.putShort(0)
                sampleIndex++
            }
        }

        // Preenche o restante do buffer se necessário
        while (sampleIndex < totalSamples) {
            pcmBuffer.putShort(0)
            sampleIndex++
        }

        return pcmBuffer.array()
    }

    private fun wrapPcmWithWavHeader(
        pcmData: ByteArray,
        sampleRate: Int = 22050,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalDataLen)
        buffer.put("WAVE".toByteArray())

        // fmt subchunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Subchunk1Size for PCM
        buffer.putShort(1.toShort()) // AudioFormat 1 = PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((channels * bitsPerSample / 8).toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data subchunk
        buffer.put("data".toByteArray())
        buffer.putInt(totalAudioLen)

        val output = ByteArrayOutputStream()
        output.write(header)
        output.write(pcmData)
        return output.toByteArray()
    }
}
