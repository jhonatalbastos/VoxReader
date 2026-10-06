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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // Caches the confirmed working model for this session to eliminate discovery overhead
    @Volatile
    private var activeWorkingModel: String? = null

    /**
     * Tests a specific API key with direct Google AI Studio models inspection
     * and a short audio synthesis trial.
     * Returns Result.success or Result.failure with human-readable error description.
     */
    suspend fun testApiKey(apiKey: String, voiceName: String = "Puck"): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().replace("\n", "").replace("\r", "")
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Chave da API vazia."))
        }

        // 1. Direct validation via Google AI Studio models catalog endpoint
        val modelsUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=$cleanKey"
        val pingRequest = Request.Builder()
            .url(modelsUrl)
            .get()
            .build()

        val modelsResponse = try {
            httpClient.newCall(pingRequest).execute()
        } catch (e: Exception) {
            return@withContext Result.failure(IOException("Falha de conexão com os servidores da Google: ${e.localizedMessage ?: "Tempo esgotado"}"))
        }

        val responseCode = modelsResponse.code
        val responseBody = modelsResponse.body?.string() ?: ""

        if (!modelsResponse.isSuccessful) {
            val userMsg = try {
                val json = JSONObject(responseBody)
                val err = json.optJSONObject("error")
                val errMsg = err?.optString("message", "") ?: ""
                when {
                    responseCode == 400 && (errMsg.contains("API key not valid", ignoreCase = true) || errMsg.contains("INVALID_ARGUMENT", ignoreCase = true)) ->
                        "Chave de API inválida. Verifique se copiou a chave completa gerada no Google AI Studio (aistudio.google.com)."
                    responseCode == 403 ->
                        "Permissão negada (HTTP 403). Verifique se a Generative Language API está ativada no seu projeto Google Cloud."
                    responseCode == 429 ->
                        "Limite de requisições excedido (HTTP 429 - Quota). A chave é válida, mas aguarde alguns instantes."
                    else -> "Erro no Google AI Studio (HTTP $responseCode): ${errMsg.ifBlank { responseBody.take(120) }}"
                }
            } catch (_: Exception) {
                "Google AI Studio retornou HTTP $responseCode: ${responseBody.take(100)}"
            }
            return@withContext Result.failure(IOException(userMsg))
        }

        // Chave é autêntica e válida na Google AI Studio!
        val availableModelNames = mutableListOf<String>()
        try {
            val json = JSONObject(responseBody)
            val modelsArray = json.optJSONArray("models")
            if (modelsArray != null) {
                for (i in 0 until modelsArray.length()) {
                    val m = modelsArray.optJSONObject(i)
                    val name = m?.optString("name", "")?.removePrefix("models/") ?: ""
                    if (name.isNotBlank()) availableModelNames.add(name)
                }
            }
        } catch (_: Exception) {}

        // 2. Testar síntese real de áudio com os modelos suportados
        val testModels = mutableListOf<String>()
        activeWorkingModel?.let { testModels.add(it) }
        listOf(
            "gemini-3.8-flash-tts",
            "gemini-3.8-flash-lite-tts",
            "gemini-2.5-flash-tts",
            "gemini-2.5-flash",
            "gemini-2.0-flash"
        ).forEach { m ->
            if (!testModels.contains(m)) testModels.add(m)
        }

        for (model in testModels) {
            try {
                val audio = executeSynthesis(cleanKey, "Teste de voz do Google AI Studio.", voiceName, model)
                if (audio.isNotEmpty()) {
                    activeWorkingModel = model
                    return@withContext Result.success("Chave 100% válida e ativa! Áudio sintetizado com sucesso no modelo $model.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Teste de síntese falhou com $model: ${e.message}")
            }
        }

        val modelSummary = if (availableModelNames.isNotEmpty()) "${availableModelNames.size} modelos autorizados" else "acesso liberado"
        Result.success("Chave validada com sucesso no Google AI Studio ($modelSummary).")
    }

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

        // Se o texto for curto (até 450 caracteres), sintetiza direto em uma única requisição rápida
        if (cleanText.length <= 450) {
            val singleAudio = synthesizeSingleChunkWithRetry(cleanText, voiceName)
            return@withContext if (isWav(singleAudio)) singleAudio else wrapPcmWithWavHeader(singleAudio)
        }

        val chunks = splitIntoChunks(cleanText, maxChunkSize = 400)
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

    private fun isWav(audioData: ByteArray): Boolean {
        return audioData.size > 12 &&
            String(audioData.copyOfRange(0, 4)) == "RIFF" &&
            String(audioData.copyOfRange(8, 12)) == "WAVE"
    }

    private suspend fun synthesizeSingleChunkWithRetry(
        chunkText: String,
        voiceName: String
    ): ByteArray {
        val excludedKeys = mutableSetOf<String>()
        var attempts = 0
        val maxAttempts = 3
        var lastError: Exception? = null

        val candidateModels = mutableListOf<String>()
        activeWorkingModel?.let { candidateModels.add(it) }
        listOf(
            "gemini-3.8-flash-tts",
            "gemini-3.8-flash-lite-tts",
            "gemini-2.5-flash-tts",
            "gemini-2.5-flash",
            "gemini-2.0-flash"
        ).forEach { m ->
            if (!candidateModels.contains(m)) candidateModels.add(m)
        }

        while (attempts < maxAttempts) {
            val apiKey = keyManager.getNextApiKey(excludedKeys)
            if (apiKey.isNullOrBlank()) {
                throw IllegalStateException("Nenhuma chave de API do Google AI Studio ativa. Configure sua chave em Configurações > Chaves de API.")
            }

            var keySucceeded = false
            for (modelName in candidateModels) {
                try {
                    val result = executeSynthesis(apiKey, chunkText, voiceName, modelName)
                    if (result.isNotEmpty()) {
                        activeWorkingModel = modelName
                        return result
                    }
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "Tentativa com $modelName falhou (${apiKey.take(6)}...): ${e.message}")
                    if (e.message?.contains("404") == true && activeWorkingModel == modelName) {
                        activeWorkingModel = null
                    }
                    if (e.message?.contains("API key not valid", ignoreCase = true) == true ||
                        e.message?.contains("401") == true ||
                        e.message?.contains("403") == true) {
                        break
                    }
                }
            }

            if (!keySucceeded) {
                excludedKeys.add(apiKey)
                attempts++
            }
        }

        throw lastError ?: IOException("Falha ao sintetizar áudio com Google AI Studio. Verifique sua chave de API ou conexão de rede.")
    }

    private fun executeSynthesis(
        apiKey: String,
        text: String,
        voiceName: String,
        modelName: String
    ): ByteArray {
        val safeVoiceName = if (GeminiVoiceCatalog.VOICES.any { it.name.equals(voiceName, ignoreCase = true) }) {
            voiceName
        } else {
            "Puck"
        }

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
            // OBSERVAÇÃO CRÍTICA: Modelos TTS puros do Gemini (como gemini-3.8-flash-tts) rejeitam
            // `systemInstruction` com HTTP 400. O texto da narração deve ser enviado diretamente em contents.parts[0].text.
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
                            put("voiceName", safeVoiceName)
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
            val userError = try {
                val errObj = JSONObject(responseBody).optJSONObject("error")
                errObj?.optString("message", "") ?: responseBody
            } catch (_: Exception) {
                responseBody
            }
            throw IOException("Google AI Studio ($modelName) HTTP $responseCode: $userError")
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
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val inlineData = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                if (inlineData != null) {
                    val base64Data = inlineData.optString("data", "")
                    if (base64Data.isNotBlank()) {
                        return Base64.decode(base64Data, Base64.DEFAULT)
                    }
                }
            }
        }

        val steps = jsonResponse.optJSONArray("steps")
        if (steps != null) {
            for (i in 0 until steps.length()) {
                val step = steps.optJSONObject(i)
                val stepContent = step?.optJSONArray("content")
                if (stepContent != null) {
                    for (j in 0 until stepContent.length()) {
                        val item = stepContent.optJSONObject(j)
                        val data = item?.optString("data", "") ?: ""
                        if (data.isNotBlank()) {
                            return Base64.decode(data, Base64.DEFAULT)
                        }
                    }
                }
            }
        }

        throw IOException("Nenhum dado de áudio inline encontrado na resposta Gemini ($modelName). finishReason=$finishReason")
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
