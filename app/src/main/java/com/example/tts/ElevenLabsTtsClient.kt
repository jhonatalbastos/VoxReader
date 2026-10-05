package com.example.tts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class WordAlignment(
    val word: String,
    val startSeconds: Float,
    val endSeconds: Float
)

data class TtsSynthesisResult(
    val audioFile: File,
    val alignments: List<WordAlignment>
)

class ElevenLabsTtsClient(private val context: Context) {

    suspend fun synthesizeWithTimestamps(
        apiKey: String,
        voiceId: String,
        text: String,
        previousText: String? = null,
        nextText: String? = null,
        modelId: String = "eleven_multilingual_v2",
        stability: Float = 0.55f,
        similarityBoost: Float = 0.80f,
        style: Float = 0.05f
    ): Result<TtsSynthesisResult> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.elevenlabs.io/v1/text-to-speech/$voiceId/with-timestamps")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("xi-api-key", apiKey)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("text", text)
                put("model_id", modelId)
                if (!previousText.isNullOrEmpty()) put("previous_text", previousText)
                if (!nextText.isNullOrEmpty()) put("next_text", nextText)

                put("voice_settings", JSONObject().apply {
                    put("stability", stability)
                    put("similarity_boost", similarityBoost)
                    put("style", style)
                    put("use_speaker_boost", true)
                })
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseString = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(responseString)

                val audioBase64 = jsonResponse.getString("audio_base64")
                val audioBytes = android.util.Base64.decode(audioBase64, android.util.Base64.DEFAULT)

                val outputFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                outputFile.writeBytes(audioBytes)

                val alignments = mutableListOf<WordAlignment>()
                if (jsonResponse.has("alignment")) {
                    val alignmentObj = jsonResponse.getJSONObject("alignment")
                    val chars = alignmentObj.getJSONArray("characters")
                    val starts = alignmentObj.getJSONArray("character_start_times_seconds")
                    val ends = alignmentObj.getJSONArray("character_end_times_seconds")

                    alignments.addAll(parseCharacterAlignmentsToWords(chars, starts, ends))
                }

                Result.success(TtsSynthesisResult(outputFile, alignments))
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Result.failure(Exception("HTTP ${conn.responseCode}: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseCharacterAlignmentsToWords(
        chars: JSONArray,
        starts: JSONArray,
        ends: JSONArray
    ): List<WordAlignment> {
        val words = mutableListOf<WordAlignment>()
        var currentWord = StringBuilder()
        var wordStart = -1f
        var wordEnd = 0f

        for (i in 0 until chars.length()) {
            val charStr = chars.getString(i)
            val startTime = starts.getDouble(i).toFloat()
            val endTime = ends.getDouble(i).toFloat()

            if (charStr.all { it.isWhitespace() } || i == chars.length() - 1) {
                if (charStr.isNotBlank()) {
                    currentWord.append(charStr)
                    wordEnd = endTime
                }
                if (currentWord.isNotEmpty()) {
                    words.add(
                        WordAlignment(
                            currentWord.toString(),
                            if (wordStart >= 0) wordStart else startTime,
                            wordEnd
                        )
                    )
                    currentWord.clear()
                    wordStart = -1f
                }
            } else {
                if (wordStart < 0) wordStart = startTime
                currentWord.append(charStr)
                wordEnd = endTime
            }
        }
        return words
    }
}
