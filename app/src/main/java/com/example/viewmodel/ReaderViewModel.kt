package com.example.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import com.example.data.AppDatabase
import com.example.data.BookEntity
import com.example.data.BookmarkEntity
import com.example.data.BookRepository
import com.example.data.ChapterEntity
import com.example.data.GeminiApiKeyEntity
import com.example.model.GoogleUser
import com.example.model.ReadSegment
import com.example.model.ReaderFont
import com.example.model.ReaderSettings
import com.example.model.ReaderTheme
import com.example.model.SyncStatus
import com.example.model.VoiceCatalog
import com.example.parser.EbookParser
import com.example.parser.SampleLibrary
import com.example.service.GoogleAuthManager
import com.example.service.GoogleCloudSyncService
import com.example.util.AppSettingsManager
import com.example.util.AppTheme
import com.example.util.LocalBackupManager
import com.example.util.MemoryProfiler
import com.example.util.MemoryStats
import com.example.tts.AudiobookDownloadManager
import com.example.tts.AudiobookTaskState
import com.example.tts.EdgeTtsClient
import com.example.tts.GeminiApiKeyManager
import com.example.tts.GeminiTtsClient
import com.example.tts.GeminiVoiceCatalog
import com.example.tts.ReaderTtsManager
import com.example.tts.TtsEngineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "ReaderViewModel"

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BookRepository
    val apiKeyManager: GeminiApiKeyManager
    val geminiTtsClient: GeminiTtsClient
    val ttsManager: ReaderTtsManager
    val downloadManager: AudiobookDownloadManager
    val googleAuthManager: GoogleAuthManager
    val googleCloudSyncService: GoogleCloudSyncService
    val memoryProfiler: MemoryProfiler

    val memoryStats: StateFlow<MemoryStats>
    val memoryLogs: StateFlow<List<String>>

    val googleUser: StateFlow<GoogleUser?>
    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    val downloadTaskState: StateFlow<AudiobookTaskState>
    val allBooks: StateFlow<List<BookEntity>>
    val mostRecentBook: StateFlow<BookEntity?>
    val activeMiniPlayerBook: StateFlow<BookEntity?>
    val favoriteBooks: StateFlow<List<BookEntity>>
    val apiKeys: StateFlow<List<GeminiApiKeyEntity>>

    val isTtsReading: StateFlow<Boolean>
    val isTtsPlaying: StateFlow<Boolean>
    val isTtsBuffering: StateFlow<Boolean>
    val activeTtsEngine: StateFlow<TtsEngineType>
    val recentSegments: StateFlow<List<ReadSegment>>

    val appSettingsManager: AppSettingsManager
    val appTheme: StateFlow<AppTheme>
    val defaultVoiceEngine: StateFlow<String>

    // Current Book State
    private val _currentBook = MutableStateFlow<BookEntity?>(null)
    val currentBook: StateFlow<BookEntity?> = _currentBook.asStateFlow()

    private val _chapters = MutableStateFlow<List<ChapterEntity>>(emptyList())
    val chapters: StateFlow<List<ChapterEntity>> = _chapters.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkEntity>> = _bookmarks.asStateFlow()

    private val _readerSettings = MutableStateFlow(ReaderSettings())
    val readerSettings: StateFlow<ReaderSettings> = _readerSettings.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Paging 3 Search & Filter Controls
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow("Todos")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedFilter(filter: String) {
        _selectedFilter.value = filter
    }

    val pagedBooks: kotlinx.coroutines.flow.Flow<PagingData<BookEntity>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BookRepository(database.bookDao())

        apiKeyManager = GeminiApiKeyManager(repository)
        geminiTtsClient = GeminiTtsClient(apiKeyManager)

        ttsManager = ReaderTtsManager(application.applicationContext, geminiTtsClient)
        downloadManager = AudiobookDownloadManager.getInstance(
            application.applicationContext,
            EdgeTtsClient(),
            geminiTtsClient,
            ttsManager.cacheManager
        )

        downloadTaskState = downloadManager.taskState

        googleAuthManager = GoogleAuthManager(application.applicationContext)
        googleCloudSyncService = GoogleCloudSyncService(application.applicationContext)
        memoryProfiler = MemoryProfiler.getInstance(application.applicationContext)
        memoryStats = memoryProfiler.memoryStats
        memoryLogs = memoryProfiler.memoryLogs
        googleUser = googleAuthManager.currentUser
        _syncStatus.value = googleCloudSyncService.getLastSyncStatus()

        appSettingsManager = AppSettingsManager.getInstance(application.applicationContext)
        appTheme = appSettingsManager.appTheme
        defaultVoiceEngine = appSettingsManager.defaultVoiceEngine

        @OptIn(ExperimentalCoroutinesApi::class)
        pagedBooks = combine(
            _searchQuery,
            _selectedFilter
        ) { query, filter ->
            query to filter
        }.flatMapLatest { (query, filter) ->
            repository.getPagedBooks(query, filter)
        }.cachedIn(viewModelScope)

        allBooks = repository.allBooks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        mostRecentBook = repository.mostRecentBook.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        isTtsReading = ttsManager.isReading
        isTtsPlaying = ttsManager.isPlaying
        isTtsBuffering = ttsManager.isBuffering
        activeTtsEngine = ttsManager.activeEngine
        recentSegments = ttsManager.recentSegments

        activeMiniPlayerBook = combine(
            _currentBook,
            mostRecentBook,
            ttsManager.activeBookId
        ) { current, recent, activeId ->
            when {
                activeId != null && current?.id == activeId -> current
                activeId != null && recent?.id == activeId -> recent
                current != null -> current
                else -> recent
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        favoriteBooks = repository.favoriteBooks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        apiKeys = repository.allApiKeys.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Seed sample classic books on first run if database is empty
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.allBooks.first()
            if (existing.isEmpty()) {
                val samples = SampleLibrary.getSampleBooks()
                samples.forEach { (book, chapters) ->
                    repository.insertBookWithChapters(book, chapters)
                }
            }

            // Seed preloaded Gemini API keys with account labels
            val currentKeys = repository.getActiveApiKeysSync().map { it.apiKey.trim() }
            val defaultKeys = listOf(
                "AIzaSyDeKpzW0GlEHV17Y2nyQ0dWTSG3IdTU02U" to "jhonatalbastos@gmail.com",
                "AIzaSyARIIyuYY3oyw5731OPUQTayzOzrZZEhzQ" to "canalbiblianarradaoficial@gmail.com",
                "AIzaSyBy3Qp1K2oDR2T3JRgRDfN-nrfrqyP5aiQ" to "bizarricesgrok@gmail.com",
                "AIzaSyBls4S1TgJdzKhlWN4tC7r9T4-qdg9BwSw" to "luzdapalavratv@gmail.com",
                "AIzaSyBEf0-raol6qdN90ZJcNQdq1trVMyy1i_U" to "receitasfecd@gmail.com",
                "AIzaSyANgf1fNcSl4lAuEE5HM0hS9ziT3hUIXpY" to "jhonatabastos20260410@gmail.com"
            )
            defaultKeys.forEach { (key, label) ->
                if (!currentKeys.contains(key)) {
                    repository.insertApiKey(
                        GeminiApiKeyEntity(
                            apiKey = key,
                            label = label,
                            isActive = true
                        )
                    )
                }
            }
        }
    }

    fun openBook(bookId: Long) {
        viewModelScope.launch {
            val book = repository.getBookByIdSync(bookId) ?: return@launch
            val isGemini = book.voiceEngine == "GEMINI_TTS"
            val isValidGemini = isGemini && GeminiVoiceCatalog.VOICES.any { it.name == book.voiceId }
            val isValidEdge = book.voiceEngine == "EDGE_TTS" && VoiceCatalog.VOICES.any { it.id == book.voiceId }

            val normalizedBook = if (!isValidGemini && !isValidEdge) {
                if (isGemini) {
                    val fixed = book.copy(voiceEngine = "GEMINI_TTS", voiceId = "Puck")
                    repository.updateVoiceSettings(bookId, "GEMINI_TTS", "Puck", fixed.voiceSpeed, fixed.voicePitch)
                    fixed
                } else {
                    val fixed = book.copy(voiceEngine = "EDGE_TTS", voiceId = "pt-BR-FranciscaNeural")
                    repository.updateVoiceSettings(bookId, "EDGE_TTS", "pt-BR-FranciscaNeural", fixed.voiceSpeed, fixed.voicePitch)
                    fixed
                }
            } else {
                book
            }
            _currentBook.value = normalizedBook
            _currentChapterIndex.value = normalizedBook.currentChapterIndex

            // Restore reading preferences (visual and font) saved in Room for this book
            val restoredTheme = try {
                ReaderTheme.valueOf(normalizedBook.readerTheme)
            } catch (_: Exception) {
                ReaderTheme.BOOK_PAPER
            }
            val restoredFont = try {
                ReaderFont.valueOf(normalizedBook.readerFont)
            } catch (_: Exception) {
                ReaderFont.SERIF
            }
            _readerSettings.value = ReaderSettings(
                fontSizeSp = normalizedBook.fontSizeSp,
                lineSpacingMultiplier = normalizedBook.lineSpacing,
                theme = restoredTheme,
                font = restoredFont
            )

            val bookChapters = repository.getChaptersSync(bookId)
            _chapters.value = bookChapters

            ttsManager.updateNegativeWordsFilter(normalizedBook.negativeWords, normalizedBook.skipPageNumbers)

            // Observe bookmarks
            launch {
                repository.getBookmarks(bookId).collect {
                    _bookmarks.value = it
                }
            }

            // Update timestamp
            repository.updateBook(normalizedBook.copy(lastReadTimestamp = System.currentTimeMillis()))
        }
    }

    fun closeBook() {
        ttsManager.stop()
        _currentBook.value = null
        _chapters.value = emptyList()
        _bookmarks.value = emptyList()
    }

    fun selectChapter(index: Int) {
        val chapterList = _chapters.value
        if (index in chapterList.indices) {
            ttsManager.stop()
            _currentChapterIndex.value = index
            saveProgress(index, 0)
        }
    }

    fun nextChapter() {
        val nextIdx = _currentChapterIndex.value + 1
        if (nextIdx < _chapters.value.size) {
            selectChapter(nextIdx)
        }
    }

    fun previousChapter() {
        val prevIdx = _currentChapterIndex.value - 1
        if (prevIdx >= 0) {
            selectChapter(prevIdx)
        }
    }

    fun getCurrentChapterParagraphs(): List<String> {
        val chapter = _chapters.value.getOrNull(_currentChapterIndex.value) ?: return emptyList()
        return chapter.content.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
    }

    // ==========================================
    // TTS Controls (Edge TTS & Gemini AI Studio)
    // ==========================================

    fun startReadAloud(fromParagraphIndex: Int? = null) {
        val book = _currentBook.value ?: return
        val paragraphs = getCurrentChapterParagraphs()
        if (paragraphs.isEmpty()) return

        // If resuming while paused, simply resume
        if (ttsManager.isReading.value && !ttsManager.isPlaying.value && fromParagraphIndex == null) {
            ttsManager.resume()
            return
        }

        // Resume from current saved position if not specified
        val startIndex = fromParagraphIndex ?: book.currentParagraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        val chapter = _chapters.value.getOrNull(_currentChapterIndex.value)
        val chapterTitle = chapter?.title ?: "Capítulo ${_currentChapterIndex.value + 1}"

        ttsManager.startReading(
            bookId = book.id,
            chapterIndex = _currentChapterIndex.value,
            paragraphs = paragraphs,
            startIndex = startIndex,
            voiceEngine = book.voiceEngine,
            voiceId = book.voiceId,
            speed = book.voiceSpeed,
            pitch = book.voicePitch,
            negativeWords = book.negativeWords,
            skipPageNumbers = book.skipPageNumbers,
            bookTitle = book.title,
            chapterTitle = chapterTitle
        ) { activeIndex, isChapterEnd ->
            if (isChapterEnd) {
                // Auto advance chapter if available
                if (_currentChapterIndex.value < _chapters.value.size - 1) {
                    val nextChapterIdx = _currentChapterIndex.value + 1
                    selectChapter(nextChapterIdx)
                    startReadAloud(0)
                }
            } else {
                saveProgress(_currentChapterIndex.value, activeIndex)
            }
        }
    }

    fun pauseReadAloud() {
        ttsManager.pause()
        val activeIdx = ttsManager.activeParagraphIndex.value
        if (activeIdx >= 0) {
            saveProgress(_currentChapterIndex.value, activeIdx)
        }
    }

    fun resumeReadAloud() {
        ttsManager.resume()
    }

    fun stopReadAloud() {
        val activeIdx = ttsManager.activeParagraphIndex.value
        if (activeIdx >= 0) {
            saveProgress(_currentChapterIndex.value, activeIdx)
        }
        ttsManager.stop()
    }

    fun skipNextParagraph() {
        ttsManager.skipNext()
    }

    fun skipPreviousParagraph() {
        ttsManager.skipPrevious()
    }

    /**
     * Rewind the current narration: re-reads the confusing sentence or jumps back to previous segment.
     */
    fun rewindSpeech() {
        ttsManager.rewind()
    }

    /**
     * Re-listen to a specific segment from the last 10 read segments log.
     */
    fun replaySegment(segment: ReadSegment) {
        ttsManager.replaySegment(segment)
    }

    fun setAppTheme(theme: AppTheme) {
        appSettingsManager.setAppTheme(theme)
    }

    fun setDefaultVoiceEngine(engine: String) {
        appSettingsManager.setDefaultVoiceEngine(engine)
    }

    fun toggleMiniPlayerSpeech() {
        if (ttsManager.isPlaying.value) {
            pauseReadAloud()
        } else if (ttsManager.isReading.value) {
            resumeReadAloud()
        } else {
            val targetBook = activeMiniPlayerBook.value ?: return
            quickPlayActiveBook(targetBook.id, forceEdgeTts = false)
        }
    }

    fun quickPlayActiveBook(bookId: Long, forceEdgeTts: Boolean = false) {
        viewModelScope.launch {
            if (_currentBook.value?.id != bookId || _chapters.value.isEmpty()) {
                withContext(Dispatchers.IO) {
                    val book = repository.getBookByIdSync(bookId) ?: return@withContext
                    val bookToUse = if (forceEdgeTts && book.voiceEngine != "EDGE_TTS") {
                        val updated = book.copy(voiceEngine = "EDGE_TTS", voiceId = book.voiceId.ifBlank { "pt-BR-FranciscaNeural" })
                        repository.updateBook(updated)
                        updated
                    } else book
                    val bookChapters = repository.getChaptersSync(bookId)
                    withContext(Dispatchers.Main) {
                        _currentBook.value = bookToUse
                        _currentChapterIndex.value = bookToUse.currentChapterIndex
                        _chapters.value = bookChapters
                        startReadAloud()
                    }
                }
            } else {
                if (forceEdgeTts && _currentBook.value?.voiceEngine != "EDGE_TTS") {
                    updateBookVoiceSettings("EDGE_TTS", "pt-BR-FranciscaNeural", 1.0f, 1.0f)
                }
                startReadAloud()
            }
        }
    }

    fun updateBookVoiceSettings(
        voiceEngine: String,
        voiceId: String,
        speed: Float,
        pitch: Float
    ) {
        val book = _currentBook.value ?: return
        val updated = book.copy(
            voiceEngine = voiceEngine,
            voiceId = voiceId,
            voiceSpeed = speed,
            voicePitch = pitch
        )
        _currentBook.value = updated

        ttsManager.updateSettingsOnTheFly(voiceEngine, voiceId, speed, pitch)

        viewModelScope.launch(Dispatchers.IO) {
            repository.updateVoiceSettings(book.id, voiceEngine, voiceId, speed, pitch)
        }
    }

    fun updateBookVoiceSettingsDirectly(
        bookId: Long,
        voiceEngine: String,
        voiceId: String,
        speed: Float,
        pitch: Float = 1.0f
    ) {
        if (_currentBook.value?.id == bookId) {
            _currentBook.value = _currentBook.value?.copy(
                voiceEngine = voiceEngine,
                voiceId = voiceId,
                voiceSpeed = speed,
                voicePitch = pitch
            )
            ttsManager.updateSettingsOnTheFly(voiceEngine, voiceId, speed, pitch)
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateVoiceSettings(bookId, voiceEngine, voiceId, speed, pitch)
        }
    }

    fun cycleBookPlaybackSpeed(book: BookEntity) {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val currentIdx = speeds.indexOfFirst { kotlin.math.abs(it - book.voiceSpeed) < 0.05f }
        val nextSpeed = if (currentIdx >= 0 && currentIdx < speeds.size - 1) {
            speeds[currentIdx + 1]
        } else {
            speeds[0]
        }
        updateBookVoiceSettingsDirectly(
            bookId = book.id,
            voiceEngine = book.voiceEngine,
            voiceId = book.voiceId,
            speed = nextSpeed,
            pitch = book.voicePitch
        )
    }

    fun testVoice(engine: String, voiceId: String, speed: Float, pitch: Float) {
        ttsManager.testVoice(engine, voiceId, speed, pitch)
    }

    private fun saveProgress(chapterIndex: Int, paragraphIndex: Int) {
        val book = _currentBook.value ?: return
        val totalChapters = _chapters.value.size.coerceAtLeast(1)
        val progress = ((chapterIndex.toFloat() + (paragraphIndex.toFloat() / 30f).coerceAtMost(0.99f)) / totalChapters).coerceIn(0f, 1f)

        val updated = book.copy(
            currentChapterIndex = chapterIndex,
            currentParagraphIndex = paragraphIndex,
            progress = progress,
            readingProgress = progress,
            lastReadTimestamp = System.currentTimeMillis()
        )
        _currentBook.value = updated

        viewModelScope.launch(Dispatchers.IO) {
            repository.updateProgress(book.id, chapterIndex, paragraphIndex, progress)
        }
    }

    // ==========================================
    // Negative Words & Editing Content
    // ==========================================

    fun updateNegativeWordsFilter(
        bookId: Long,
        negativeWords: String,
        skipPageNumbers: Boolean,
        clearCache: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateNegativeWords(bookId, negativeWords, skipPageNumbers)
            if (_currentBook.value?.id == bookId) {
                _currentBook.value = _currentBook.value?.copy(
                    negativeWords = negativeWords,
                    skipPageNumbers = skipPageNumbers
                )
                ttsManager.updateNegativeWordsFilter(negativeWords, skipPageNumbers)
            }
            if (clearCache) {
                ttsManager.cacheManager.deleteBookCache(bookId)
                _userMessage.value = "Filtro salvo e cache de áudio limpo!"
            } else {
                _userMessage.value = "Filtro salvo com sucesso!"
            }
        }
    }

    fun renameChapter(chapterId: Long, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateChapterTitle(chapterId, newTitle)
            _currentBook.value?.id?.let { bId ->
                _chapters.value = repository.getChaptersSync(bId)
            }
            _userMessage.value = "Capítulo renomeado!"
        }
    }

    fun updateChapterContent(chapterId: Long, newTitle: String, newContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val bookId = _currentBook.value?.id ?: 0L
            val chIndex = _currentChapterIndex.value
            repository.updateChapterTitle(chapterId, newTitle)
            repository.updateChapterContent(chapterId, newContent)
            if (bookId > 0) {
                _chapters.value = repository.getChaptersSync(bookId)
                // Clear cache for this chapter so it synthesizes with updated content
                ttsManager.cacheManager.deleteChapterCache(bookId, chIndex)
            }
            _userMessage.value = "Conteúdo do capítulo atualizado!"
        }
    }

    fun updateParagraph(paragraphIndex: Int, newText: String) {
        val chapter = _chapters.value.getOrNull(_currentChapterIndex.value) ?: return
        val currentPars = getCurrentChapterParagraphs().toMutableList()
        if (paragraphIndex in currentPars.indices) {
            currentPars[paragraphIndex] = newText
            val newContent = currentPars.joinToString("\n\n")

            viewModelScope.launch(Dispatchers.IO) {
                repository.updateChapterContent(chapter.id, newContent)
                _currentBook.value?.id?.let { bId ->
                    _chapters.value = repository.getChaptersSync(bId)
                    // Clear cache for this specific paragraph
                    ttsManager.cacheManager.deleteParagraphCache(bId, _currentChapterIndex.value, paragraphIndex)
                }
                _userMessage.value = "Parágrafo atualizado!"
            }
        }
    }

    // ==========================================
    // Local JSON Backup & Restore
    // ==========================================

    fun exportBackupToJsonUri(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val books = repository.getAllBooksSync()
            val result = LocalBackupManager.writeBackupToUri(getApplication(), uri, books)
            withContext(Dispatchers.Main) {
                result.onSuccess { count ->
                    _userMessage.value = "Backup local de $count livros exportado com sucesso!"
                    onResult(true, "Backup exportado com sucesso ($count livros)!")
                }.onFailure { err ->
                    _userMessage.value = "Falha ao exportar backup: ${err.message}"
                    onResult(false, err.message ?: "Erro desconhecido")
                }
            }
        }
    }

    fun shareBackupJson(context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val books = repository.getAllBooksSync()
            val result = LocalBackupManager.createShareableBackupFile(context, books)
            withContext(Dispatchers.Main) {
                result.onSuccess { file ->
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "VoxReader Backup (${books.size} livros)")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Salvar ou Enviar Backup JSON"))
                    onResult(true, "Arquivo gerado com sucesso!")
                }.onFailure { err ->
                    _userMessage.value = "Erro ao gerar arquivo de compartilhamento: ${err.message}"
                    onResult(false, err.message ?: "Erro desconhecido")
                }
            }
        }
    }

    fun restoreBackupFromJsonUri(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = LocalBackupManager.restoreBackupFromUri(
                context = getApplication(),
                uri = uri,
                bookDao = repository.bookDao
            )
            withContext(Dispatchers.Main) {
                result.onSuccess { count ->
                    _userMessage.value = "Backup restaurado com sucesso! $count livros atualizados."
                    onResult(true, "$count livros atualizados com seu progresso e configurações!")
                }.onFailure { err ->
                    _userMessage.value = "Falha ao restaurar backup: ${err.message}"
                    onResult(false, err.message ?: "Erro desconhecido")
                }
            }
        }
    }

    fun deleteParagraphAudio(paragraphIndex: Int) {
        val book = _currentBook.value ?: return
        val deleted = ttsManager.cacheManager.deleteParagraphCache(book.id, _currentChapterIndex.value, paragraphIndex)
        _userMessage.value = if (deleted) "Áudio em cache deste parágrafo excluído." else "Nenhum áudio em cache encontrado para este parágrafo."
    }

    fun deleteCurrentChapterAudio() {
        val book = _currentBook.value ?: return
        val count = ttsManager.cacheManager.deleteChapterCache(book.id, _currentChapterIndex.value)
        _userMessage.value = "$count áudio(s) em cache deste capítulo foram excluídos."
    }

    fun deleteBookAudio(bookId: Long) {
        val count = ttsManager.cacheManager.deleteBookCache(bookId)
        _userMessage.value = "$count áudio(s) em cache deste livro foram excluídos."
    }

    // ==========================================
    // Book Cover Upload
    // ==========================================

    fun uploadBookCover(bookId: Long, uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
                val coversDir = File(context.filesDir, "covers")
                if (!coversDir.exists()) coversDir.mkdirs()

                val coverFile = File(coversDir, "cover_${bookId}_${System.currentTimeMillis()}.jpg")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(coverFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    repository.updateBookCover(bookId, coverFile.absolutePath)
                }

                if (_currentBook.value?.id == bookId) {
                    _currentBook.value = _currentBook.value?.copy(coverImagePath = coverFile.absolutePath)
                }
                _userMessage.value = "Capa do livro atualizada com sucesso!"
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao salvar capa do livro", e)
                _userMessage.value = "Erro ao salvar capa: ${e.localizedMessage}"
            }
        }
    }

    // ==========================================
    // Gemini API Keys Management (Round-Robin)
    // ==========================================

    fun addGeminiApiKey(apiKey: String, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertApiKey(
                GeminiApiKeyEntity(
                    apiKey = apiKey.trim(),
                    label = label.trim()
                )
            )
            _userMessage.value = "Chave da API Google AI Studio adicionada!"
        }
    }

    fun toggleGeminiApiKey(key: GeminiApiKeyEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateApiKey(key)
        }
    }

    suspend fun testGeminiApiKey(apiKey: String): Result<String> {
        return ttsManager.geminiTtsClient.testApiKey(apiKey)
    }

    fun deleteGeminiApiKey(keyId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteApiKeyById(keyId)
            _userMessage.value = "Chave de API removida."
        }
    }

    // ==========================================
    // Audiobook Pre-caching and Export
    // ==========================================

    fun preloadCurrentChapter(exportToDownloads: Boolean, targetChapter: ChapterEntity? = null) {
        val book = _currentBook.value ?: return
        val chapter = targetChapter ?: _chapters.value.getOrNull(_currentChapterIndex.value) ?: return
        downloadManager.processChapter(book, chapter, exportToDownloads) { uri ->
            if (uri != null) {
                _userMessage.value = "Áudio salvo em Downloads com sucesso!"
            }
        }
    }

    fun preloadEntireBook(exportToDownloads: Boolean, targetBook: BookEntity? = null) {
        val book = targetBook ?: _currentBook.value ?: return
        viewModelScope.launch {
            val bookChapters = if (targetBook != null && (targetBook.id != _currentBook.value?.id || _chapters.value.isEmpty())) {
                repository.getChaptersSync(targetBook.id)
            } else {
                _chapters.value.ifEmpty { repository.getChaptersSync(book.id) }
            }

            if (bookChapters.isEmpty()) {
                _userMessage.value = "Nenhum capítulo encontrado para pré-carregar."
                return@launch
            }

            downloadManager.processEntireBook(book, bookChapters, exportToDownloads) { uri ->
                if (uri != null) {
                    _userMessage.value = "Audiolivro completo salvo em Downloads!"
                }
            }
        }
    }

    fun cancelAudiobookTask() {
        downloadManager.cancelTask()
    }

    fun dismissAudiobookTask() {
        downloadManager.dismissTask()
    }

    // ==========================================
    // Bookmarks
    // ==========================================

    fun addBookmark(paragraphIndex: Int, snippet: String, note: String = "") {
        val book = _currentBook.value ?: return
        val chapterIdx = _currentChapterIndex.value
        val bookmark = BookmarkEntity(
            bookId = book.id,
            chapterIndex = chapterIdx,
            paragraphIndex = paragraphIndex,
            snippet = snippet.take(120),
            note = note
        )
        viewModelScope.launch(Dispatchers.IO) {
            repository.addBookmark(bookmark)
            _userMessage.value = "Marcador salvo!"
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeBookmark(bookmarkId)
        }
    }

    // ==========================================
    // Library Management & Import
    // ==========================================

    fun importBookFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            try {
                _userMessage.value = "Importando e processando livro..."
                val context = getApplication<Application>().applicationContext
                val parsed = withContext(Dispatchers.IO) {
                    EbookParser.parseUri(context, uri, fileName)
                }

                val paletteColors = listOf(
                    Pair(0xFF0F172A, 0xFF334155),
                    Pair(0xFF1E1B4B, 0xFF4338CA),
                    Pair(0xFF701A75, 0xFF9D174D),
                    Pair(0xFF064E3B, 0xFF047857),
                    Pair(0xFF7C2D12, 0xFFEA580C),
                    Pair(0xFF164E63, 0xFF0284C7)
                )
                val randomColors = paletteColors.random()

                val defaultEngine = appSettingsManager.defaultVoiceEngine.value
                val defaultVoice = if (defaultEngine == "GEMINI_TTS") "Puck" else appSettingsManager.defaultVoiceId.value

                val newBook = BookEntity(
                    title = parsed.title,
                    author = parsed.author,
                    path = uri.toString(),
                    progress = 0f,
                    format = parsed.format,
                    coverImagePath = parsed.coverImagePath,
                    coverGradientStart = randomColors.first,
                    coverGradientEnd = randomColors.second,
                    totalChapters = parsed.chapters.size,
                    currentChapterIndex = 0,
                    voiceEngine = defaultEngine,
                    voiceId = defaultVoice,
                    voiceSpeed = appSettingsManager.defaultVoiceSpeed.value,
                    voicePitch = appSettingsManager.defaultVoicePitch.value
                )

                val bookId = withContext(Dispatchers.IO) {
                    repository.insertBookWithChapters(newBook, parsed.chapters)
                }

                _userMessage.value = "Livro \"${parsed.title}\" importado com sucesso!"
                openBook(bookId)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao importar livro", e)
                _userMessage.value = "Erro ao importar livro: ${e.localizedMessage}"
            }
        }
    }

    fun createManualBook(title: String, author: String, textContent: String) {
        viewModelScope.launch {
            try {
                val chapters = textContent.split(Regex("""(?m)^(?=[Cc]ap[ií]tulo\s+\d+|[Cc]hapter\s+\d+|#+\s+)"""))
                    .mapIndexed { idx, content ->
                        ChapterEntity(
                            bookId = 0,
                            chapterIndex = idx,
                            title = "Capítulo ${idx + 1}",
                            content = content.trim()
                        )
                    }

                val defaultEngine = appSettingsManager.defaultVoiceEngine.value
                val defaultVoice = if (defaultEngine == "GEMINI_TTS") "Puck" else appSettingsManager.defaultVoiceId.value

                val book = BookEntity(
                    title = title.ifBlank { "Livro Personalizado" },
                    author = author.ifBlank { "Autor" },
                    path = "internal://manual/${System.currentTimeMillis()}",
                    progress = 0f,
                    format = "TXT",
                    coverGradientStart = 0xFF1E3A8A,
                    coverGradientEnd = 0xFF2563EB,
                    totalChapters = chapters.size,
                    voiceEngine = defaultEngine,
                    voiceId = defaultVoice,
                    voiceSpeed = appSettingsManager.defaultVoiceSpeed.value
                )

                val bookId = withContext(Dispatchers.IO) {
                    repository.insertBookWithChapters(book, chapters)
                }
                _userMessage.value = "Livro criado com sucesso!"
                openBook(bookId)
            } catch (e: Exception) {
                _userMessage.value = "Erro ao criar livro: ${e.message}"
            }
        }
    }

    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateBook(book.copy(isFavorite = !book.isFavorite))
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_currentBook.value?.id == book.id) {
                closeBook()
            }
            ttsManager.cacheManager.deleteBookCache(book.id)
            repository.deleteBook(book)
            _userMessage.value = "Livro removido da biblioteca."
        }
    }

    fun updateReaderSettings(settings: ReaderSettings) {
        _readerSettings.value = settings
        _currentBook.value?.let { book ->
            val updated = book.copy(
                fontSizeSp = settings.fontSizeSp,
                lineSpacing = settings.lineSpacingMultiplier,
                readerTheme = settings.theme.name,
                readerFont = settings.font.name
            )
            _currentBook.value = updated
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateVisualPreferences(
                    bookId = book.id,
                    fontSizeSp = settings.fontSizeSp,
                    lineSpacing = settings.lineSpacingMultiplier,
                    theme = settings.theme.name,
                    font = settings.font.name
                )
            }
        }
    }

    // ==========================================
    // Google Sign-In & Cloud Sync
    // ==========================================

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _syncStatus.value = _syncStatus.value.copy(isSyncing = true, errorMessage = null)
            val result = googleAuthManager.signInWithGoogle(activity)
            result.onSuccess { user ->
                _userMessage.value = "Conectado ao Google como ${user.displayName}!"
                syncLibraryWithCloud()
            }.onFailure { err ->
                _syncStatus.value = _syncStatus.value.copy(isSyncing = false)
                _userMessage.value = "Login cancelado ou não concluído."
            }
        }
    }

    fun signInDevAccount(email: String = "jhonatalbastos@gmail.com", name: String = "Jhonata Bastos") {
        viewModelScope.launch {
            val user = googleAuthManager.signInDirectly(email, name)
            _userMessage.value = "Conectado ao Google como ${user.displayName}!"
            syncLibraryWithCloud()
        }
    }

    fun signOutGoogle() {
        googleAuthManager.signOut()
        _syncStatus.value = SyncStatus()
        _userMessage.value = "Desconectado da Conta Google."
    }

    fun syncLibraryWithCloud() {
        val user = googleUser.value ?: return
        viewModelScope.launch {
            _syncStatus.value = _syncStatus.value.copy(isSyncing = true, errorMessage = null)
            val currentBooks = repository.getAllBooksSync()
            val result = googleCloudSyncService.syncLibraryToCloud(user, currentBooks)
            result.onSuccess { status ->
                _syncStatus.value = status
                _userMessage.value = "Sincronizado com o Google! (${status.syncedBooksCount} livros salvos)"
            }.onFailure { error ->
                _syncStatus.value = _syncStatus.value.copy(
                    isSyncing = false,
                    errorMessage = error.message
                )
                _userMessage.value = "Erro na sincronização: ${error.message}"
            }
        }
    }

    fun restoreLibraryFromCloud() {
        val user = googleUser.value ?: return
        viewModelScope.launch {
            _syncStatus.value = _syncStatus.value.copy(isSyncing = true, errorMessage = null)
            val result = googleCloudSyncService.restoreLibraryFromCloud(user, repository)
            result.onSuccess { count ->
                _syncStatus.value = googleCloudSyncService.getLastSyncStatus()
                _userMessage.value = "$count livros e progressos restaurados do Google!"
            }.onFailure { error ->
                _syncStatus.value = _syncStatus.value.copy(isSyncing = false, errorMessage = error.message)
                _userMessage.value = "Erro ao restaurar: ${error.message}"
            }
        }
    }

    fun forceGarbageCollection() {
        memoryProfiler.forceGarbageCollection()
        _userMessage.value = "Coleta de lixo (GC) executada com sucesso!"
    }

    fun clearMemoryLogs() {
        memoryProfiler.clearLogs()
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.release()
    }
}
