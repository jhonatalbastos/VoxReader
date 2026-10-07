package com.example.tts

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "EdgeTtsClient"
private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
private const val WSS_BASE = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
private const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
private const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_FULL_VERSION"
private const val WIN_EPOCH = 11644473600L

class EdgeTtsClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Generates the Sec-MS-GEC token required by Microsoft Edge Read Aloud API.
     */
    fun generateSecMsGec(): String {
        // Current UTC time in seconds
        val nowSeconds = System.currentTimeMillis() / 1000.0
        var ticks = nowSeconds + WIN_EPOCH
        // Round down to nearest 5 minutes (300 seconds)
        ticks -= (ticks % 300)
        // Convert to 100-nanosecond intervals (Windows file time format)
        val fileTimeTicks = (ticks * 10_000_000).toLong()

        val strToHash = "$fileTimeTicks$TRUSTED_CLIENT_TOKEN"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(strToHash.toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun getFormattedTimestamp(): String {
        val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    /**
     * Synthesizes the given text into MP3 audio bytes using Microsoft Edge TTS WebSocket API.
     */
    suspend fun synthesizeToMp3(
        text: String,
        voiceId: String = "pt-BR-FranciscaNeural",
        speed: Float = 1.0f,
        pitch: Float = 1.0f
    ): ByteArray = suspendCancellableCoroutine { continuation ->
        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val secMsGec = generateSecMsGec()

        val url = "$WSS_BASE?TrustedClientToken=$TRUSTED_CLIENT_TOKEN&ConnectionId=$connectionId&Sec-MS-GEC=$secMsGec&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

        val request = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Sec-WebSocket-Version", "13")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
            .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()

        val audioStream = ByteArrayOutputStream()

        val ratePercent = ((speed - 1.0f) * 100).toInt()
        val rateStr = if (ratePercent >= 0) "+$ratePercent%" else "$ratePercent%"

        val pitchPercent = ((pitch - 1.0f) * 100).toInt()
        val pitchStr = if (pitchPercent >= 0) "+$pitchPercent%" else "$pitchPercent%"

        val cleanText = escapeXml(text.trim())
        val timestamp = getFormattedTimestamp()

        val webSocket = client.newWebSocket(request, object : WebSocketListener() {
            private var isTurnEnded = false

            override fun onOpen(webSocket: WebSocket, response: Response) {
                try {
                    // Step 1: Send speech.config (High-Definition 160kbps audio)
                    val configMsg = "X-Timestamp:$timestamp\r\n" +
                            "Content-Type:application/json; charset=utf-8\r\n" +
                            "Path:speech.config\r\n\r\n" +
                            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-160kbitrate-mono-mp3\"}}}}\r\n"
                    webSocket.send(configMsg)

                    // Step 2: Send expressive SSML with natural breathing and dialogue pauses
                    val ssml = buildExpressiveSsml(cleanText, voiceId, pitchStr, rateStr)

                    val ssmlMsg = "X-RequestId:$requestId\r\n" +
                            "Content-Type:application/ssml+xml\r\n" +
                            "X-Timestamp:${timestamp}Z\r\n" +
                            "Path:ssml\r\n\r\n" +
                            ssml

                    webSocket.send(ssmlMsg)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending config or SSML to Edge TTS", e)
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                    webSocket.close(1000, "Error")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    isTurnEnded = true
                    val audioData = audioStream.toByteArray()
                    if (continuation.isActive) {
                        continuation.resume(audioData)
                    }
                    webSocket.close(1000, "Normal closure")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    val rawBytes = bytes.toByteArray()
                    if (rawBytes.size > 2) {
                        val headerLength = ((rawBytes[0].toInt() and 0xFF) shl 8) or (rawBytes[1].toInt() and 0xFF)
                        val totalHeaderLength = 2 + headerLength
                        if (rawBytes.size > totalHeaderLength) {
                            val audioPayloadLength = rawBytes.size - totalHeaderLength
                            audioStream.write(rawBytes, totalHeaderLength, audioPayloadLength)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing Edge TTS audio chunk", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Edge TTS WebSocket failure: ${t.message}, response code=${response?.code}")
                if (continuation.isActive) {
                    continuation.resumeWithException(t)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isTurnEnded && continuation.isActive) {
                    val audioData = audioStream.toByteArray()
                    if (audioData.isNotEmpty()) {
                        continuation.resume(audioData)
                    } else {
                        continuation.resumeWithException(RuntimeException("Edge TTS closed before audio was received: $reason ($code)"))
                    }
                }
            }
        })

        continuation.invokeOnCancellation {
            try {
                webSocket.cancel()
            } catch (_: Exception) {}
        }
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Formata o SSML enriquecido com pausas naturais de respiração e diálogo (<break/>),
     * garantindo expressividade profissional e cadência realista para livros e audiolivros.
     */
    private fun buildExpressiveSsml(
        escapedText: String,
        voiceId: String,
        pitchStr: String,
        rateStr: String
    ): String {
        val lang = if (voiceId.startsWith("pt-")) "pt-BR" else if (voiceId.startsWith("es-")) "es-ES" else "en-US"

        val formattedBody = try {
            generateExpressiveBody(escapedText)
        } catch (_: Exception) {
            escapedText
        }

        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xmlns:mstts='https://www.w3.org/2001/mstts' xml:lang='$lang'>" +
                "<voice name='$voiceId'>" +
                "<prosody pitch='$pitchStr' rate='$rateStr'>$formattedBody</prosody>" +
                "</voice></speak>"
    }

    private fun generateExpressiveBody(escaped: String): String {
        var s = escaped

        // Pausa no início de diálogo (travessões ou hífen de fala)
        s = s.replace(Regex("^([—–\\-])\\s*"), "<break time='180ms'/>$1 ")

        // Pausa natural em travessões internos de diálogo (falas e incisos do narrador)
        s = s.replace(Regex("\\s+([—–])\\s+"), " <break time='140ms'/>$1 ")

        // Pausa expressiva de reticências (hesitação / suspense)
        s = s.replace(Regex("(\\.\\.\\.|…)\\s*"), "<break time='260ms'/> ")

        // Pausa em dois-pontos e ponto-e-vírgula
        s = s.replace(Regex(":\\s+"), ":<break time='160ms'/> ")
        s = s.replace(Regex(";\\s+"), ";<break time='140ms'/> ")

        // Pausa de respiração suave ao final do parágrafo
        return "$s<break time='200ms'/>"
    }
}
