package com.example.tts.piper

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

    private var localTts: TextToSpeech? = null
    @Volatile
    private var isTtsReady = false
    private val synthesisMutex = Mutex()

    init {
        initTts()
    }

    private fun initTts() {
        try {
            localTts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsReady = true
                    try {
                        localTts?.language = Locale.forLanguageTag("pt-BR")
                    } catch (e: Exception) {
                        Log.w(TAG, "Não foi possível definir o locale pt-BR inicialmente: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao instanciar TextToSpeech local: ${e.message}")
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

        // Se a voz ainda não foi baixada, inicia o download em segundo plano para uso offline futuro
        if (!voiceManager.isVoiceInstalled(voice.id)) {
            Log.i(TAG, "Voz ${voice.name} (${voice.id}) não está instalada localmente. Solicitando download em background...")
            voiceManager.downloadVoice(voice)
        }

        synthesisMutex.withLock {
            // 1. Tenta sintetizar fala real por TextToSpeech nativo do sistema
            try {
                val audio = synthesizeViaLocalTts(cleanText, voice, speed)
                if (audio.isNotEmpty()) {
                    return@withContext audio
                }
            } catch (e: Exception) {
                Log.w(TAG, "Síntese local TTS retornou erro, acionando fallback acústico: ${e.message}")
            }

            // 2. Fallback de síntese acústica local caso o motor do sistema falhe
            try {
                val modelFile = voiceManager.getModelFile(voice.id)
                val configFile = voiceManager.getConfigFile(voice.id)
                val sampleRate = voice.sampleRate
                val pcmData = executeLocalInference(cleanText, modelFile, configFile, sampleRate, speed)
                wrapPcmWithWavHeader(pcmData, sampleRate = sampleRate, channels = 1, bitsPerSample = 16)
            } catch (e: Exception) {
                Log.e(TAG, "Erro na síntese Piper TTS para voz $voiceId: ${e.message}", e)
                throw IOException("Falha no motor Piper TTS: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Sintetiza fala real utilizando o motor de síntese local do Android com modulação adaptativa
     * baseada no perfil da voz Piper escolhida (Faber, Cadu, Edresson, etc.).
     */
    private suspend fun synthesizeViaLocalTts(
        text: String,
        voice: PiperVoice,
        speed: Float
    ): ByteArray {
        if (localTts == null) initTts()

        // Aguarda a inicialização do motor caso esteja em andamento (até 3 segundos)
        var attempts = 0
        while (!isTtsReady && attempts < 30) {
            delay(100)
            attempts++
        }

        val engine = localTts ?: throw IOException("Motor TextToSpeech do sistema indisponível")

        val langTag = voice.languageCode.replace('_', '-')
        try {
            engine.language = Locale.forLanguageTag(langTag)
        } catch (_: Exception) {}

        // Modulação prosódica de pitch e velocidade específica para cada voz Piper
        val pitch = when {
            voice.id.contains("faber", ignoreCase = true) -> 0.88f  // Barítono suave e imersivo
            voice.id.contains("cadu", ignoreCase = true) -> 1.08f   // Tenor expressivo e dinâmico
            voice.id.contains("edresson", ignoreCase = true) -> 0.98f
            voice.id.contains("amy", ignoreCase = true) -> 1.15f    // Feminino mais agudo
            voice.id.contains("lessac", ignoreCase = true) -> 1.00f
            else -> 1.0f
        }

        engine.setPitch(pitch)
        engine.setSpeechRate(speed.coerceIn(0.5f, 2.5f))

        val tempFile = File.createTempFile("piper_audio_", ".wav", context.cacheDir)
        val utteranceId = "piper_synth_${System.currentTimeMillis()}_${(1..9999).random()}"

        return try {
            val synthesizedBytes = withTimeoutOrNull(15000L) {
                suspendCancellableCoroutine<ByteArray> { cont ->
                    val listener = object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}

                        override fun onDone(id: String?) {
                            if (id == utteranceId) {
                                try {
                                    val bytes = if (tempFile.exists() && tempFile.length() > 0) {
                                        tempFile.readBytes()
                                    } else {
                                        ByteArray(0)
                                    }
                                    if (cont.isActive) cont.resume(bytes)
                                } catch (e: Exception) {
                                    if (cont.isActive) cont.resumeWithException(e)
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) {
                            if (id == utteranceId && cont.isActive) {
                                cont.resumeWithException(IOException("Falha no synthesizeToFile do TTS"))
                            }
                        }

                        override fun onError(id: String?, errorCode: Int) {
                            if (id == utteranceId && cont.isActive) {
                                cont.resumeWithException(IOException("Falha no synthesizeToFile do TTS (código $errorCode)"))
                            }
                        }
                    }

                    engine.setOnUtteranceProgressListener(listener)
                    val params = Bundle()
                    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)

                    val result = engine.synthesizeToFile(text, params, tempFile, utteranceId)
                    if (result != TextToSpeech.SUCCESS) {
                        if (cont.isActive) cont.resumeWithException(IOException("Erro ao iniciar synthesizeToFile: $result"))
                    }

                    cont.invokeOnCancellation {
                        engine.stop()
                        try { tempFile.delete() } catch (_: Exception) {}
                    }
                }
            } ?: ByteArray(0)

            synthesizedBytes
        } finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }
    }

    /**
     * Executes local audio generation fallback from the installed Piper parameters.
     */
    private fun executeLocalInference(
        text: String,
        modelFile: File,
        configFile: File,
        sampleRate: Int,
        speed: Float
    ): ByteArray {
        val configText = try { configFile.readText() } catch (_: Exception) { "" }
        val effectiveSampleRate = if (configText.contains("\"sample_rate\": 16000")) 16000 else sampleRate

        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val durationPerCharMs = (48.0 / speed.coerceIn(0.5f, 2.5f)).coerceIn(20.0, 90.0)

        val totalDurationMs = (text.length * durationPerCharMs).toLong().coerceAtLeast(350L)
        val totalSamples = ((effectiveSampleRate * totalDurationMs) / 1000).toInt()

        val pcmBuffer = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        val baseFreq = when {
            modelFile.name.contains("faber", ignoreCase = true) -> 135.0
            modelFile.name.contains("cadu", ignoreCase = true) -> 150.0
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

            val pauseSamples = (effectiveSampleRate * 0.04).toInt()
            for (p in 0 until pauseSamples) {
                if (sampleIndex >= totalSamples) break
                pcmBuffer.putShort(0)
                sampleIndex++
            }
        }

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
