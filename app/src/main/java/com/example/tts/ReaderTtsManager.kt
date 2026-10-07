package com.example.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.model.ReadSegment
import com.example.service.ReaderMediaService
import com.example.util.TextSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val TAG = "ReaderTtsManager"

enum class TtsEngineType {
    EDGE_NEURAL,
    LOCAL_ANDROID
}

class ReaderTtsManager(
    private val context: Context
) {
    companion object {
        @Volatile
        private var instance: ReaderTtsManager? = null

        fun getExistingInstance(): ReaderTtsManager? = instance
    }

    private val edgeTtsClient = EdgeTtsClient()
    val cacheManager = TtsCacheManager(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var mediaPlayer: MediaPlayer? = null
    private var localTts: TextToSpeech? = null
    private var isLocalTtsReady = false

    private var playbackJob: Job? = null
    private var prefetchJob: Job? = null

    private var currentBookId: Long = 0
    private var currentChapterIndex: Int = 0
    private var currentParagraphs: List<String> = emptyList()
    private var currentIndex: Int = 0
    private var currentVoiceEngine: String = "EDGE_TTS"
    private var currentVoiceId: String = "pt-BR-FranciscaNeural"
    private var currentSpeed: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentNegativeWords: String = ""
    private var currentSkipPageNumbers: Boolean = true
    private var currentBookTitle: String = "VoxReader"
    private var currentChapterTitle: String = "Capítulo"
    private var onProgressCallback: ((paragraphIndex: Int, isChapterEnd: Boolean) -> Unit)? = null

    // Reactive State
    private val _isReading = MutableStateFlow(false)
    val isReading: StateFlow<Boolean> = _isReading.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _activeParagraphIndex = MutableStateFlow(-1)
    val activeParagraphIndex: StateFlow<Int> = _activeParagraphIndex.asStateFlow()

    private val _activeEngine = MutableStateFlow(TtsEngineType.EDGE_NEURAL)
    val activeEngine: StateFlow<TtsEngineType> = _activeEngine.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _activeBookId = MutableStateFlow<Long?>(null)
    val activeBookId: StateFlow<Long?> = _activeBookId.asStateFlow()

    private val _activeBookTitle = MutableStateFlow<String?>(null)
    val activeBookTitle: StateFlow<String?> = _activeBookTitle.asStateFlow()

    private val _activeChapterTitle = MutableStateFlow<String?>(null)
    val activeChapterTitle: StateFlow<String?> = _activeChapterTitle.asStateFlow()

    // Log of the last 10 read segments for instant Rewind / Re-listen
    private val _recentSegments = MutableStateFlow<List<ReadSegment>>(emptyList())
    val recentSegments: StateFlow<List<ReadSegment>> = _recentSegments.asStateFlow()

    init {
        instance = this
        initLocalTts()
    }

    private fun initLocalTts() {
        localTts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isLocalTtsReady = true
                val locale = Locale.forLanguageTag("pt-BR")
                val result = localTts?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    localTts?.setLanguage(Locale.getDefault())
                }
            }
        }
        localTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isPlaying.value = true
                _isBuffering.value = false
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    advanceToNextParagraph()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isPlaying.value = false
                _isBuffering.value = false
            }
        })
    }

    /**
     * Start or continue reading aloud from the given paragraph index.
     */
    fun startReading(
        bookId: Long,
        chapterIndex: Int,
        paragraphs: List<String>,
        startIndex: Int,
        voiceEngine: String,
        voiceId: String,
        speed: Float,
        pitch: Float,
        negativeWords: String = "",
        skipPageNumbers: Boolean = true,
        bookTitle: String = "VoxReader",
        chapterTitle: String = "Capítulo",
        onProgress: (paragraphIndex: Int, isChapterEnd: Boolean) -> Unit
    ) {
        if (_isReading.value && currentBookId == bookId && currentChapterIndex == chapterIndex && currentIndex == startIndex && mediaPlayer != null) {
            resume()
            return
        }

        stopPlaybackOnly()

        currentBookId = bookId
        currentChapterIndex = chapterIndex
        currentParagraphs = paragraphs
        currentIndex = startIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        currentVoiceEngine = voiceEngine
        currentVoiceId = voiceId
        currentSpeed = speed
        currentPitch = pitch
        currentNegativeWords = negativeWords
        currentSkipPageNumbers = skipPageNumbers
        currentBookTitle = bookTitle
        currentChapterTitle = chapterTitle
        _activeBookId.value = bookId
        _activeBookTitle.value = bookTitle
        _activeChapterTitle.value = chapterTitle
        onProgressCallback = onProgress

        recalculateChapterTimeEstimates()

        _isReading.value = true
        _activeParagraphIndex.value = currentIndex

        readCurrentParagraph()
    }

    private fun readCurrentParagraph() {
        if (currentIndex < 0 || currentIndex >= currentParagraphs.size) {
            _isReading.value = false
            _isPlaying.value = false
            _isBuffering.value = false
            _activeParagraphIndex.value = -1
            onProgressCallback?.invoke(currentIndex, true)
            return
        }

        val rawText = currentParagraphs[currentIndex].trim()
        val sanitizedText = TextSanitizer.cleanForSpeech(rawText, currentNegativeWords, currentSkipPageNumbers)

        if (sanitizedText.isBlank()) {
            currentIndex++
            readCurrentParagraph()
            return
        }

        _activeParagraphIndex.value = currentIndex
        updateMediaNotification()
        onProgressCallback?.invoke(currentIndex, false)

        // Log into the last 10 read segments (newest first)
        val segment = ReadSegment(
            bookId = currentBookId,
            chapterIndex = currentChapterIndex,
            paragraphIndex = currentIndex,
            chapterTitle = currentChapterTitle,
            text = sanitizedText
        )
        _recentSegments.value = (listOf(segment) + _recentSegments.value.filterNot {
            it.bookId == currentBookId && it.chapterIndex == currentChapterIndex && it.paragraphIndex == currentIndex
        }).take(10)

        // Check if current paragraph is already cached!
        val cachedFile = cacheManager.getCachedFile(
            bookId = currentBookId,
            chapterIndex = currentChapterIndex,
            paragraphIndex = currentIndex,
            engine = currentVoiceEngine,
            voiceId = currentVoiceId,
            speed = currentSpeed
        )

        if (cachedFile != null) {
            _isBuffering.value = false
            _statusMessage.value = null
            _activeEngine.value = TtsEngineType.EDGE_NEURAL
            playAudioFile(cachedFile)
            triggerLookaheadPrefetch(currentIndex + 1)
            return
        }

        // Needs synthesis via Edge TTS HD
        _isBuffering.value = true
        _statusMessage.value = "Carregando fala neural HD..."

        playbackJob?.cancel()
        playbackJob = scope.launch {
            try {
                val audioBytes = withContext(Dispatchers.IO) {
                    edgeTtsClient.synthesizeToMp3(
                        text = sanitizedText,
                        voiceId = currentVoiceId,
                        speed = currentSpeed,
                        pitch = currentPitch
                    )
                }

                if (audioBytes.isEmpty()) {
                    throw RuntimeException("Áudio retornado vazio pelo sintetizador.")
                }

                val savedFile = withContext(Dispatchers.IO) {
                    cacheManager.saveAudio(
                        bookId = currentBookId,
                        chapterIndex = currentChapterIndex,
                        paragraphIndex = currentIndex,
                        engine = "EDGE_TTS",
                        voiceId = currentVoiceId,
                        speed = currentSpeed,
                        bytes = audioBytes
                    )
                }

                _isBuffering.value = false
                _statusMessage.value = null
                _activeEngine.value = TtsEngineType.EDGE_NEURAL
                playAudioFile(savedFile)
                triggerLookaheadPrefetch(currentIndex + 1)

            } catch (e: Exception) {
                Log.w(TAG, "Síntese Edge TTS falhou: ${e.message}", e)
                _isBuffering.value = false
                _statusMessage.value = "Sem conexão: usando voz local do sistema Android."
                _activeEngine.value = TtsEngineType.LOCAL_ANDROID
                playWithLocalTts(sanitizedText)
            }
        }
    }

    /**
     * Proactively pre-fetches subsequent paragraphs into the disk cache.
     */
    private fun triggerLookaheadPrefetch(nextStartIndex: Int) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            val maxLookahead = 2
            for (offset in 0 until maxLookahead) {
                val targetIndex = nextStartIndex + offset
                if (targetIndex >= currentParagraphs.size) break

                val isAlreadyCached = cacheManager.isCached(
                    bookId = currentBookId,
                    chapterIndex = currentChapterIndex,
                    paragraphIndex = targetIndex,
                    engine = currentVoiceEngine,
                    voiceId = currentVoiceId,
                    speed = currentSpeed
                )

                if (!isAlreadyCached) {
                    val raw = currentParagraphs[targetIndex].trim()
                    val textToPreload = TextSanitizer.cleanForSpeech(raw, currentNegativeWords, currentSkipPageNumbers)

                    if (textToPreload.isNotBlank()) {
                        try {
                            val audioBytes = edgeTtsClient.synthesizeToMp3(
                                text = textToPreload,
                                voiceId = currentVoiceId,
                                speed = currentSpeed,
                                pitch = currentPitch
                            )

                            if (audioBytes.isNotEmpty()) {
                                cacheManager.saveAudio(
                                    bookId = currentBookId,
                                    chapterIndex = currentChapterIndex,
                                    paragraphIndex = targetIndex,
                                    engine = "EDGE_TTS",
                                    voiceId = currentVoiceId,
                                    speed = currentSpeed,
                                    bytes = audioBytes
                                )
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Prefetch paragraph $targetIndex skipped: ${e.message}")
                            break
                        }
                    }
                }
            }
        }
    }

    private fun playAudioFile(file: File) {
        try {
            releaseMediaPlayer()
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(context.applicationContext, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    advanceToNextParagraph()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    try { mp.reset() } catch (_: Exception) {}
                    scope.launch { advanceToNextParagraph() }
                    true
                }
                start()
            }
            _isPlaying.value = true
            _isBuffering.value = false
            updateMediaNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao reproduzir arquivo de áudio", e)
            val currentRaw = currentParagraphs.getOrNull(currentIndex) ?: ""
            playWithLocalTts(TextSanitizer.cleanForSpeech(currentRaw, currentNegativeWords, currentSkipPageNumbers))
        }
    }

    private fun playWithLocalTts(text: String) {
        _activeEngine.value = TtsEngineType.LOCAL_ANDROID
        _isBuffering.value = false
        _isPlaying.value = true

        localTts?.let { tts ->
            tts.setSpeechRate(currentSpeed)
            tts.setPitch(currentPitch)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vox_p_${currentIndex}")
        } ?: run {
            advanceToNextParagraph()
        }
    }

    private fun advanceToNextParagraph() {
        if (!_isReading.value) return
        currentIndex++
        if (currentIndex < currentParagraphs.size) {
            readCurrentParagraph()
        } else {
            // End of chapter
            _isReading.value = false
            _isPlaying.value = false
            _isBuffering.value = false
            _activeParagraphIndex.value = -1
            onProgressCallback?.invoke(currentIndex, true)
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
        localTts?.stop()
        _isPlaying.value = false
        updateMediaNotification()
    }

    fun resume() {
        mediaPlayer?.let {
            it.start()
            _isPlaying.value = true
            _isReading.value = true
            updateMediaNotification()
        } ?: run {
            if (currentIndex in currentParagraphs.indices) {
                _isReading.value = true
                readCurrentParagraph()
            }
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            resume()
        }
    }

    fun skipNext() {
        if (currentIndex < currentParagraphs.size - 1) {
            currentIndex++
            readCurrentParagraph()
        }
    }

    fun skipPrevious() {
        if (currentIndex > 0) {
            currentIndex--
            readCurrentParagraph()
        }
    }

    /**
     * Rewind the audio narration to replay a confusing sentence or return to the previous segment.
     * Uses the playback position and recent segments history.
     */
    fun rewind() {
        if (!_isReading.value) return
        val pos = try { mediaPlayer?.currentPosition ?: 0 } catch (_: Exception) { 0 }
        if (pos > 2500 || currentIndex <= 0) {
            // Replay the current sentence from the beginning
            readCurrentParagraph()
        } else {
            // Jump back to the previous sentence/paragraph
            currentIndex--
            readCurrentParagraph()
        }
    }

    /**
     * Replay a specific sentence from the last 10 read segments.
     */
    fun replaySegment(segment: ReadSegment) {
        if (segment.bookId != currentBookId) return
        currentIndex = segment.paragraphIndex.coerceIn(0, (currentParagraphs.size - 1).coerceAtLeast(0))
        _isReading.value = true
        readCurrentParagraph()
    }

    fun updateSettingsOnTheFly(voiceEngine: String, voiceId: String, speed: Float, pitch: Float) {
        val engineChanged = currentVoiceEngine != voiceEngine
        val voiceChanged = currentVoiceId != voiceId
        val speedChanged = kotlin.math.abs(currentSpeed - speed) > 0.05f
        val pitchChanged = kotlin.math.abs(currentPitch - pitch) > 0.05f

        currentVoiceEngine = voiceEngine
        currentVoiceId = voiceId
        currentSpeed = speed
        currentPitch = pitch

        if (_isReading.value && (engineChanged || voiceChanged || speedChanged || pitchChanged)) {
            readCurrentParagraph()
        }
    }

    fun updateNegativeWordsFilter(negativeWords: String, skipPageNumbers: Boolean) {
        currentNegativeWords = negativeWords
        currentSkipPageNumbers = skipPageNumbers
    }

    fun deleteParagraphAudio(paragraphIndex: Int): Boolean {
        return cacheManager.deleteParagraphCache(currentBookId, currentChapterIndex, paragraphIndex)
    }

    fun deleteChapterAudio(): Int {
        return cacheManager.deleteChapterCache(currentBookId, currentChapterIndex)
    }

    fun deleteBookAudio(): Int {
        return cacheManager.deleteBookCache(currentBookId)
    }

    /**
     * Preview sample voice speech directly from Edge TTS HD.
     */
     fun testVoice(
        engine: String = "EDGE_TTS",
        voiceId: String,
        speed: Float,
        pitch: Float,
        sampleText: String = "Olá! Esta é uma demonstração de narração com voz neural em alta definição."
    ) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            try {
                _statusMessage.value = "Gerando amostra de voz ($voiceId)..."
                _isBuffering.value = true
                val bytes = withContext(Dispatchers.IO) {
                    edgeTtsClient.synthesizeToMp3(sampleText, voiceId, speed, pitch)
                }
                _statusMessage.value = null
                val sampleFile = withContext(Dispatchers.IO) {
                    val file = File.createTempFile("sample_voice_", ".mp3", context.cacheDir)
                    file.outputStream().use { it.write(bytes) }
                    file
                }
                playAudioFile(sampleFile)
            } catch (e: Exception) {
                Log.e(TAG, "Falha no teste de voz: ${e.message}", e)
                _statusMessage.value = "Erro no teste: ${e.localizedMessage}"
                _isBuffering.value = false
                playWithLocalTts(sampleText)
            }
        }
    }

    fun stopPlaybackOnly() {
        playbackJob?.cancel()
        playbackJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        releaseMediaPlayer()
        localTts?.stop()
        _isPlaying.value = false
        _isBuffering.value = false
    }

    fun stop() {
        stopPlaybackOnly()
        _isReading.value = false
        _activeParagraphIndex.value = -1
        _statusMessage.value = null
        ReaderMediaService.stopService(context)
    }

    private var paragraphCumulativeTimesMs: List<Long> = emptyList()
    private var totalEstimatedChapterDurationMs: Long = 0L

    fun recalculateChapterTimeEstimates() {
        val wps = (140.0f * currentSpeed.coerceAtLeast(0.5f)) / 60.0f
        val cumulative = mutableListOf<Long>()
        var accMs = 0L
        for (paragraph in currentParagraphs) {
            val spoken = extractSpokenText(paragraph)
            val wordCount = spoken.split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)
            cumulative.add(accMs)
            val durationSec = (wordCount / wps).coerceAtLeast(1.0f)
            accMs += (durationSec * 1000L).toLong()
        }
        paragraphCumulativeTimesMs = cumulative
        totalEstimatedChapterDurationMs = accMs.coerceAtLeast(10_000L)
    }

    fun extractImagePath(paragraph: String): String? {
        val imgMatch = Regex("""\[IMG:(.*?)\]""").find(paragraph)
        if (imgMatch != null) {
            return imgMatch.groupValues[1].substringBefore('|').trim()
        }
        val mdMatch = Regex("""!\[.*?\]\((.*?)\)""").find(paragraph)
        if (mdMatch != null) {
            val path = mdMatch.groupValues[1].trim()
            if (!path.startsWith("http://") && !path.startsWith("https://")) {
                return path
            }
        }
        return null
    }

    fun extractSpokenText(paragraph: String): String {
        return paragraph
            .replace(Regex("""\[IMG:(.*?)\]""")) { match ->
                val content = match.groupValues[1]
                if (content.contains('|')) {
                    content.substringAfter('|').trim()
                } else ""
            }
            .replace(Regex("""!\[(.*?)\]\(.*?\)"""), "$1")
            .trim()
    }

    fun seekToFraction(fraction: Float) {
        if (currentParagraphs.isEmpty()) return
        val targetIndex = ((currentParagraphs.size - 1) * fraction.coerceIn(0f, 1f)).toInt()
        jumpToParagraph(targetIndex)
    }

    fun seekToTimeMs(targetMs: Long) {
        if (currentParagraphs.isEmpty() || paragraphCumulativeTimesMs.isEmpty()) return
        var foundIndex = 0
        for (i in paragraphCumulativeTimesMs.indices) {
            if (paragraphCumulativeTimesMs[i] <= targetMs) {
                foundIndex = i
            } else {
                break
            }
        }
        jumpToParagraph(foundIndex)
    }

    fun jumpToParagraph(targetIndex: Int) {
        if (targetIndex in currentParagraphs.indices) {
            currentIndex = targetIndex
            readCurrentParagraph()
        }
    }

    private fun updateMediaNotification() {
        if (!_isReading.value) return
        val rawParagraph = currentParagraphs.getOrNull(currentIndex) ?: ""
        val snippet = extractSpokenText(rawParagraph).ifBlank { currentChapterTitle }
        val imagePath = extractImagePath(rawParagraph)

        val baseElapsedMs = paragraphCumulativeTimesMs.getOrNull(currentIndex) ?: 0L
        val subPosMs = try {
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.currentPosition?.toLong() ?: 0L else 0L
        } catch (_: Exception) { 0L }
        val elapsedMs = (baseElapsedMs + subPosMs).coerceIn(0L, totalEstimatedChapterDurationMs.coerceAtLeast(10_000L))

        ReaderMediaService.startOrUpdate(
            context = context,
            bookTitle = currentBookTitle,
            chapterTitle = currentChapterTitle,
            paragraphIndex = currentIndex,
            totalParagraphs = currentParagraphs.size,
            isPlaying = _isPlaying.value,
            snippet = snippet,
            bookId = _activeBookId.value ?: -1L,
            author = "",
            activeImagePath = imagePath,
            elapsedTimeMs = elapsedMs,
            totalDurationMs = totalEstimatedChapterDurationMs,
            readingSpeed = currentSpeed
        )
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun release() {
        stop()
        try {
            localTts?.shutdown()
        } catch (_: Exception) {}
        localTts = null
    }
}
