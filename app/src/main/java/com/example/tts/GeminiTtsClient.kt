package com.example.tts

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

private const val TAG = "GeminiTtsClient"

data class GeminiVoiceOption(
    val name: String,
    val gender: String,
    val description: String
)

object GeminiVoiceCatalog {
    val VOICES = listOf(
        GeminiVoiceOption("Puck", "Masculino", "Envolvente, expressivo e caloroso"),
        GeminiVoiceOption("Charon", "Masculino", "Grave, firme e ressonante"),
        GeminiVoiceOption("Kore", "Feminino", "Suave, melódica e acolhedora"),
        GeminiVoiceOption("Fenrir", "Masculino", "Autoritário, dramático e marcante"),
        GeminiVoiceOption("Aoede", "Feminino", "Articulada, sofisticada e clara"),
        GeminiVoiceOption("Leda", "Feminino", "Equilibrada, natural e agradável"),
        GeminiVoiceOption("Orus", "Masculino", "Narrativo clássico, firme e fluente"),
        GeminiVoiceOption("Zephyr", "Feminino", "Jovial, brilhante e amigável")
    )

    val DEFAULT_VOICE = VOICES.first()
}

class GeminiTtsClient(private val keyManager: GeminiApiKeyManager) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    /**
     * Synthesizes text into audio bytes using Google AI Studio (Gemini) TTS.
     * Automatically handles chunking for long paragraphs, safety filter bypass for literature,
     * and round-robin fallback across configured API keys.
     */
    suspend fun synthesizeSpeech(
        text: String,
        voiceName: String = "Puck"
    ): ByteArray = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@withContext ByteArray(0)

        val chunks = splitIntoChunks(cleanText, maxChunkSize = 350)
        val pcmStreams = mutableListOf<ByteArray>()

        for (chunk in chunks) {
            val chunkAudio = synthesizeSingleChunkWithRetry(chunk, voiceName)
            pcmStreams.add(extractRawPcm(chunkAudio))
        }

        // Combine all raw PCM chunks and package into a clean WAV file
        val combinedPcm = ByteArrayOutputStream()
        for (pcm in pcmStreams) {
            combinedPcm.write(pcm)
        }

        wrapPcmWithWavHeader(combinedPcm.toByteArray(), sampleRate = 24000, channels = 1, bitsPerSample = 16)
    }

    private suspend fun synthesizeSingleChunkWithRetry(
        chunkText: String,
        voiceName: String
    ): ByteArray {
        val excludedKeys = mutableSetOf<String>()
        var attempts = 0
        val maxAttempts = 6
        var lastError: Exception? = null

        while (attempts < maxAttempts) {
            val apiKey = keyManager.getNextApiKey(excludedKeys)
            if (apiKey.isNullOrBlank()) {
                throw IllegalStateException("Nenhuma chave de API do Google AI Studio disponível. Verifique suas chaves em Configurações.")
            }

            try {
                // Try Gemini TTS models (gemini-2.5-flash-preview-tts / gemini-3.5-flash)
                return executeSynthesis(apiKey, chunkText, voiceName, "gemini-2.5-flash-preview-tts")
            } catch (e: Exception) {
                Log.w(TAG, "Tentativa com gemini-2.5-flash-preview-tts falhou (${apiKey.take(6)}...): ${e.message}. Tentando gemini-3.5-flash...")
                try {
                    return executeSynthesis(apiKey, chunkText, voiceName, "gemini-3.5-flash")
                } catch (e2: Exception) {
                    lastError = e2
                    Log.w(TAG, "Síntese falhou em ambos os modelos Gemini com chave ${apiKey.take(6)}...: ${e2.message}")
                    excludedKeys.add(apiKey)
                    attempts++
                }
            }
        }

        throw lastError ?: IOException("Falha ao sintetizar áudio com Google AI Studio após $attempts tentativas.")
    }

    private fun executeSynthesis(
        apiKey: String,
        text: String,
        voiceName: String,
        modelName: String
    ): ByteArray {
        val safetySettings = JSONArray().apply {
            listOf(
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT"
            ).forEach { cat ->
                put(JSONObject().apply {
                    put("category", cat)
                    put("threshold", "BLOCK_NONE")
                })
            }
        }

        val jsonBody = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "Você é um leitor de audiolivros em português. Narre o texto fornecido pelo usuário de forma natural, expressiva e clara. Não inclua cumprimentos, introduções ou explicações; narre exclusivamente o texto.")
                    })
                })
            })

            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", text)
                        })
                    })
                })
            })

            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().apply {
                    put("AUDIO")
                })
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
                            put("voiceName", voiceName)
                        })
                    })
                })
            })

            put("safetySettings", safetySettings)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseCode = response.code
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw IOException("Google AI Studio ($modelName) HTTP $responseCode: $responseBody")
        }

        val jsonResponse = JSONObject(responseBody)
        val candidates = jsonResponse.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw IOException("Resposta vazia da API Gemini ($modelName)")
        }

        val candidate = candidates.getJSONObject(0)
        val finishReason = candidate.optString("finishReason", "")
        if (finishReason == "SAFETY") {
            Log.w(TAG, "Aviso: Gemini sinalizou filtro de segurança para o trecho.")
        }

        val content = candidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        if (parts == null || parts.length() == 0) {
            throw IOException("Nenhuma parte de áudio na resposta da API Gemini ($modelName). finishReason=$finishReason")
        }

        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val inlineData = part.optJSONObject("inlineData")
            if (inlineData != null) {
                val base64Data = inlineData.optString("data", "")
                if (base64Data.isNotBlank()) {
                    val rawBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    return rawBytes
                }
            }
        }

        throw IOException("Nenhum dado de áudio inline encontrado na resposta Gemini ($modelName).")
    }

    private fun splitIntoChunks(text: String, maxChunkSize: Int = 350): List<String> {
        val trimmed = text.trim()
        if (trimmed.length <= maxChunkSize) return listOf(trimmed)

        val chunks = mutableListOf<String>()
        val sentences = trimmed.split(Regex("(?<=[.!?;\n])\\s+"))
        var currentChunk = StringBuilder()

        for (sentence in sentences) {
            if (currentChunk.isNotEmpty() && (currentChunk.length + sentence.length > maxChunkSize)) {
                chunks.add(currentChunk.toString().trim())
                currentChunk = StringBuilder()
            }
            if (sentence.length > maxChunkSize) {
                val words = sentence.split(" ")
                for (w in words) {
                    if (currentChunk.isNotEmpty() && (currentChunk.length + w.length + 1 > maxChunkSize)) {
                        chunks.add(currentChunk.toString().trim())
                        currentChunk = StringBuilder()
                    }
                    if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                    currentChunk.append(w)
                }
            } else {
                if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                currentChunk.append(sentence)
            }
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        return chunks.filter { it.isNotBlank() }
    }

    private fun extractRawPcm(audioData: ByteArray): ByteArray {
        if (audioData.size > 44 && String(audioData.copyOfRange(0, 4)) == "RIFF") {
            // Strip standard 44-byte WAV header
            return audioData.copyOfRange(44, audioData.size)
        }
        return audioData
    }

    private fun wrapPcmWithWavHeader(
        pcmData: ByteArray,
        sampleRate: Int = 24000,
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
        buffer.putShort((channels * bitsPerSample / 8).toShort()) // BlockAlign
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
