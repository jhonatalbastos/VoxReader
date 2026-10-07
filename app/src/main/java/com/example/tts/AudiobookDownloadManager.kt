package com.example.tts

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.BookEntity
import com.example.data.ChapterEntity
import com.example.service.AudiobookDownloadService
import com.example.util.TextSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "AudiobookDownloadMgr"

data class AudiobookTaskState(
    val isActive: Boolean = false,
    val bookTitle: String = "",
    val currentChapterTitle: String = "",
    val currentChapterIndex: Int = 0,
    val totalChapters: Int = 0,
    val currentParagraph: Int = 0,
    val totalParagraphsInChapter: Int = 0,
    val progressPercent: Float = 0f,
    val statusMessage: String = "",
    val exportedFileUri: Uri? = null,
    val exportedFileName: String? = null,
    val isComplete: Boolean = false,
    val error: String? = null
)

class AudiobookDownloadManager(
    private val context: Context,
    private val edgeTtsClient: EdgeTtsClient,
    private val cacheManager: TtsCacheManager
) {
    companion object {
        @Volatile
        private var instance: AudiobookDownloadManager? = null

        fun getInstance(
            context: Context,
            edgeTtsClient: EdgeTtsClient,
            cacheManager: TtsCacheManager
        ): AudiobookDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: AudiobookDownloadManager(
                    context.applicationContext,
                    edgeTtsClient,
                    cacheManager
                ).also { instance = it }
            }
        }

        fun getExistingInstance(): AudiobookDownloadManager? = instance
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    private val _taskState = MutableStateFlow(AudiobookTaskState())
    val taskState: StateFlow<AudiobookTaskState> = _taskState.asStateFlow()

    fun dismissTask() {
        if (!_taskState.value.isActive) {
            _taskState.value = AudiobookTaskState()
        }
    }

    fun cancelTask() {
        cancelInternalJob()
        AudiobookDownloadService.stopService(context)
        if (_taskState.value.isActive) {
            _taskState.value = _taskState.value.copy(
                isActive = false,
                statusMessage = "Operação cancelada pelo usuário."
            )
        }
    }

    fun cancelInternalJob() {
        currentJob?.cancel()
        currentJob = null
    }

    fun processChapter(
        book: BookEntity,
        chapter: ChapterEntity,
        exportToDownloads: Boolean,
        onSuccess: (Uri?) -> Unit = {}
    ) {
        cancelInternalJob()

        val paragraphs = chapter.content.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        _taskState.value = AudiobookTaskState(
            isActive = true,
            bookTitle = book.title,
            currentChapterTitle = chapter.title,
            currentChapterIndex = chapter.chapterIndex + 1,
            totalChapters = 1,
            totalParagraphsInChapter = paragraphs.size,
            statusMessage = "Iniciando processamento em segundo plano..."
        )

        AudiobookDownloadService.startService(context, book.title)

        currentJob = scope.launch {
            try {
                val cachedFiles = mutableListOf<File>()
                val engine = book.voiceEngine

                for (idx in paragraphs.indices) {
                    val rawText = paragraphs[idx]
                    val sanitized = TextSanitizer.cleanForSpeech(rawText, book.negativeWords, book.skipPageNumbers)

                    if (sanitized.isBlank()) continue

                    val progressPercent = (idx + 1).toFloat() / paragraphs.size.coerceAtLeast(1)
                    val status = "Sintetizando com Edge TTS HD (${idx + 1}/${paragraphs.size})..."

                    _taskState.value = _taskState.value.copy(
                        currentParagraph = idx + 1,
                        progressPercent = progressPercent,
                        statusMessage = status
                    )

                    AudiobookDownloadService.updateProgress(context, book.title, status, (progressPercent * 100).toInt())

                    val voiceId = if (book.voiceId.isNotBlank()) book.voiceId else "pt-BR-FranciscaNeural"
                    var audioFile = cacheManager.getCachedFile(
                        bookId = book.id,
                        chapterIndex = chapter.chapterIndex,
                        paragraphIndex = idx,
                        engine = "EDGE_TTS",
                        voiceId = voiceId,
                        speed = book.voiceSpeed
                    )

                    if (audioFile == null) {
                        val bytes = edgeTtsClient.synthesizeToMp3(
                            text = sanitized,
                            voiceId = voiceId,
                            speed = book.voiceSpeed,
                            pitch = book.voicePitch
                        )

                        audioFile = cacheManager.saveAudio(
                            bookId = book.id,
                            chapterIndex = chapter.chapterIndex,
                            paragraphIndex = idx,
                            engine = "EDGE_TTS",
                            voiceId = voiceId,
                            speed = book.voiceSpeed,
                            bytes = bytes
                        )
                    }

                    cachedFiles.add(audioFile)
                }

                var downloadUri: Uri? = null
                var exportedName: String? = null

                if (exportToDownloads) {
                    val status = "Consolidando áudio e exportando para Downloads..."
                    _taskState.value = _taskState.value.copy(statusMessage = status)
                    AudiobookDownloadService.updateProgress(context, book.title, status, 98)

                    val cleanBookTitle = book.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                    val cleanChapterTitle = chapter.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                    val outputFileName = "${cleanBookTitle}_Cap${chapter.chapterIndex + 1}_${cleanChapterTitle}.mp3"

                    val mergedFile = cacheManager.mergeAudioFiles(cachedFiles, outputFileName)
                    downloadUri = cacheManager.saveToPublicDownloads(context, mergedFile, outputFileName)
                    exportedName = outputFileName
                }

                val finalMessage = if (exportToDownloads) "Capítulo salvo em Downloads com sucesso!" else "Capítulo 100% pré-carregado no aplicativo!"

                _taskState.value = _taskState.value.copy(
                    isActive = false,
                    isComplete = true,
                    progressPercent = 1.0f,
                    statusMessage = finalMessage,
                    exportedFileUri = downloadUri,
                    exportedFileName = exportedName
                )
                AudiobookDownloadService.completeService(context, finalMessage)
                withContext(Dispatchers.Main) {
                    onSuccess(downloadUri)
                }

            } catch (e: CancellationException) {
                _taskState.value = _taskState.value.copy(
                    isActive = false,
                    statusMessage = "Operação cancelada pelo usuário."
                )
                AudiobookDownloadService.stopService(context)
            } catch (e: Exception) {
                Log.e(TAG, "Erro no processamento do capítulo", e)
                _taskState.value = _taskState.value.copy(
                    isActive = false,
                    error = "Erro: ${e.localizedMessage ?: e.message}"
                )
                AudiobookDownloadService.stopService(context)
            }
        }
    }

    fun processEntireBook(
        book: BookEntity,
        chapters: List<ChapterEntity>,
        exportToDownloads: Boolean,
        onSuccess: (Uri?) -> Unit = {}
    ) {
        cancelInternalJob()

        val totalChapters = chapters.size.coerceAtLeast(1)
        val engine = book.voiceEngine

        _taskState.value = AudiobookTaskState(
            isActive = true,
            bookTitle = book.title,
            currentChapterIndex = 1,
            totalChapters = totalChapters,
            statusMessage = "Iniciando processamento em segundo plano..."
        )

        AudiobookDownloadService.startService(context, book.title)

        currentJob = scope.launch {
            try {
                val allBookAudioFiles = mutableListOf<File>()

                val chaptersWithParagraphs = chapters.map { ch ->
                    Pair(ch, ch.content.split("\n\n").map { it.trim() }.filter { it.isNotBlank() })
                }
                val totalParagraphsInBook = chaptersWithParagraphs.sumOf { it.second.size }.coerceAtLeast(1)
                var globalParagraphCount = 0

                for (chIdx in chaptersWithParagraphs.indices) {
                    val (chapter, paragraphs) = chaptersWithParagraphs[chIdx]

                    _taskState.value = _taskState.value.copy(
                        currentChapterIndex = chIdx + 1,
                        currentChapterTitle = chapter.title,
                        totalParagraphsInChapter = paragraphs.size
                    )

                    for (pIdx in paragraphs.indices) {
                        val rawText = paragraphs[pIdx]
                        val sanitized = TextSanitizer.cleanForSpeech(rawText, book.negativeWords, book.skipPageNumbers)
                        globalParagraphCount++

                        if (sanitized.isBlank()) continue

                        val progressPercent = globalParagraphCount.toFloat() / totalParagraphsInBook
                        val status = "Capítulo ${chIdx + 1}/$totalChapters • Parágrafo ${pIdx + 1}/${paragraphs.size}"

                        _taskState.value = _taskState.value.copy(
                            currentParagraph = pIdx + 1,
                            progressPercent = progressPercent,
                            statusMessage = status
                        )

                        AudiobookDownloadService.updateProgress(context, book.title, status, (progressPercent * 100).toInt())

                        var audioFile = cacheManager.getCachedFile(
                            bookId = book.id,
                            chapterIndex = chapter.chapterIndex,
                            paragraphIndex = pIdx,
                            engine = engine,
                            voiceId = book.voiceId,
                            speed = book.voiceSpeed
                        )

                        if (audioFile == null) {
                            val bytes = edgeTtsClient.synthesizeToMp3(
                                text = sanitized,
                                voiceId = book.voiceId,
                                speed = book.voiceSpeed,
                                pitch = book.voicePitch
                            )

                            audioFile = cacheManager.saveAudio(
                                bookId = book.id,
                                chapterIndex = chapter.chapterIndex,
                                paragraphIndex = pIdx,
                                engine = "EDGE_TTS",
                                voiceId = book.voiceId,
                                speed = book.voiceSpeed,
                                bytes = bytes
                            )
                        }

                        allBookAudioFiles.add(audioFile)
                    }
                }

                var downloadUri: Uri? = null
                var exportedName: String? = null

                if (exportToDownloads) {
                    val status = "Consolidando áudio de todos os capítulos..."
                    _taskState.value = _taskState.value.copy(statusMessage = status)
                    AudiobookDownloadService.updateProgress(context, book.title, status, 98)

                    val cleanBookTitle = book.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                    val outputFileName = "${cleanBookTitle}_Completo_Audiolivro.mp3"

                    val mergedFile = cacheManager.mergeAudioFiles(allBookAudioFiles, outputFileName)
                    downloadUri = cacheManager.saveToPublicDownloads(context, mergedFile, outputFileName)
                    exportedName = outputFileName
                }

                val finalMessage = if (exportToDownloads) "Audiolivro completo salvo em Downloads com sucesso!" else "Livro inteiro 100% pré-carregado no aplicativo!"

                _taskState.value = _taskState.value.copy(
                    isActive = false,
                    isComplete = true,
                    progressPercent = 1.0f,
                    statusMessage = finalMessage,
                    exportedFileUri = downloadUri,
                    exportedFileName = exportedName
                )
                AudiobookDownloadService.completeService(context, finalMessage)
                withContext(Dispatchers.Main) {
                    onSuccess(downloadUri)
                }

            } catch (e: CancellationException) {
                _taskState.value = _taskState.value.copy(
                    isActive = false,
                    statusMessage = "Operação cancelada pelo usuário."
                )
                AudiobookDownloadService.stopService(context)
            } catch (e: Exception) {
                Log.e(TAG, "Erro no pré-carregamento do livro completo", e)
                _taskState.value = _taskState.value.copy(
                    isActive = false,
                    error = "Erro: ${e.localizedMessage ?: e.message}"
                )
                AudiobookDownloadService.stopService(context)
            }
        }
    }
}
