package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.BookEntity
import com.example.data.BookRepository
import com.example.tts.ReaderTtsManager
import com.example.util.AppSettingsManager
import com.example.util.CoverArtHelper
import com.example.util.CoverFitMode
import com.example.util.MediaProgressMode
import com.example.util.PlaybackNotificationMode
import com.example.widget.ReaderAppWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "ReaderMediaService"
private const val MEDIA_ROOT_ID = "voxreader_media_root"
private const val CATEGORY_RESUME_LAST = "category_resume_last"
private const val CATEGORY_LIBRARY = "category_library"
private const val CATEGORY_FAVORITES = "category_favorites"
private const val CATEGORY_MODE = "category_mode"

class ReaderMediaService : MediaBrowserServiceCompat() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var mediaSession: MediaSessionCompat

    companion object {
        const val CHANNEL_ID = "reader_playback_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_PLAY = "com.example.service.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_PLAY_PAUSE = "com.example.service.ACTION_PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.example.service.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.example.service.ACTION_NEXT"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_UPDATE_STATE = "com.example.service.ACTION_UPDATE_STATE"
        const val ACTION_PLAY_BOOK = "com.example.service.ACTION_PLAY_BOOK"

        const val EXTRA_BOOK_TITLE = "EXTRA_BOOK_TITLE"
        const val EXTRA_CHAPTER_TITLE = "EXTRA_CHAPTER_TITLE"
        const val EXTRA_PARAGRAPH_INDEX = "EXTRA_PARAGRAPH_INDEX"
        const val EXTRA_TOTAL_PARAGRAPHS = "EXTRA_TOTAL_PARAGRAPHS"
        const val EXTRA_IS_PLAYING = "EXTRA_IS_PLAYING"
        const val EXTRA_SNIPPET = "EXTRA_SNIPPET"
        const val EXTRA_BOOK_ID = "EXTRA_BOOK_ID"
        const val EXTRA_AUTHOR = "EXTRA_AUTHOR"
        const val EXTRA_ACTIVE_IMAGE_PATH = "EXTRA_ACTIVE_IMAGE_PATH"
        const val EXTRA_ELAPSED_TIME_MS = "EXTRA_ELAPSED_TIME_MS"
        const val EXTRA_TOTAL_DURATION_MS = "EXTRA_TOTAL_DURATION_MS"
        const val EXTRA_READING_SPEED = "EXTRA_READING_SPEED"

        @Volatile
        private var activeInstance: ReaderMediaService? = null

        val isServiceActive: Boolean
            get() = activeInstance != null

        fun startOrUpdate(
            context: Context,
            bookTitle: String,
            chapterTitle: String,
            paragraphIndex: Int,
            totalParagraphs: Int,
            isPlaying: Boolean,
            snippet: String = "",
            bookId: Long = -1L,
            author: String = "",
            activeImagePath: String? = null,
            elapsedTimeMs: Long = 0L,
            totalDurationMs: Long = 0L,
            readingSpeed: Float = 1.0f
        ) {
            val current = activeInstance
            if (current != null) {
                current.updateStateDirectly(
                    bookTitle = bookTitle,
                    chapterTitle = chapterTitle,
                    paragraphIndex = paragraphIndex,
                    totalParagraphs = totalParagraphs,
                    isPlaying = isPlaying,
                    snippet = snippet,
                    bookId = bookId,
                    author = author,
                    activeImagePath = activeImagePath,
                    elapsedTimeMs = elapsedTimeMs,
                    totalDurationMs = totalDurationMs,
                    readingSpeed = readingSpeed
                )
                return
            }

            val intent = Intent(context, ReaderMediaService::class.java).apply {
                action = ACTION_UPDATE_STATE
                putExtra(EXTRA_BOOK_TITLE, bookTitle)
                putExtra(EXTRA_CHAPTER_TITLE, chapterTitle)
                putExtra(EXTRA_PARAGRAPH_INDEX, paragraphIndex)
                putExtra(EXTRA_TOTAL_PARAGRAPHS, totalParagraphs)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_SNIPPET, snippet)
                putExtra(EXTRA_BOOK_ID, bookId)
                putExtra(EXTRA_AUTHOR, author)
                putExtra(EXTRA_ACTIVE_IMAGE_PATH, activeImagePath)
                putExtra(EXTRA_ELAPSED_TIME_MS, elapsedTimeMs)
                putExtra(EXTRA_TOTAL_DURATION_MS, totalDurationMs)
                putExtra(EXTRA_READING_SPEED, readingSpeed)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível iniciar ReaderMediaService: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            val current = activeInstance
            if (current != null) {
                current.handleStop()
                return
            }
            val intent = Intent(context, ReaderMediaService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(NOTIFICATION_ID)
            }
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val existing = notificationManager?.getNotificationChannel(CHANNEL_ID)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Reprodução de Áudio e Narração",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Controles de reprodução de áudio e leitura em voz alta"
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                    notificationManager?.createNotificationChannel(channel)
                }
            }
        }
    }

    private var currentBookTitle: String = "VoxReader"
    private var currentChapterTitle: String = "Capítulo"
    private var currentAuthor: String = ""
    private var currentBookId: Long = -1L
    private var currentParagraphIndex: Int = 0
    private var currentTotalParagraphs: Int = 1
    private var isCurrentlyPlaying: Boolean = false
    private var currentSnippet: String = ""

    private var currentActiveImagePath: String? = null
    private var currentActiveImageBitmap: Bitmap? = null
    private var currentElapsedTimeMs: Long = 0L
    private var currentTotalDurationMs: Long = 0L
    private var currentReadingSpeed: Float = 1.0f
    private var imageRevertJob: Job? = null

    private var cachedCoverBookId: Long = -1L
    private var cachedCoverBitmap: Bitmap? = null
    private var cachedCoverFitMode: CoverFitMode? = null

    private var wakeLock: PowerManager.WakeLock? = null

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlayFromSearch(query: String?, extras: android.os.Bundle?) {
            playRecentOrFirstBook()
        }

        override fun onPlay() {
            val tts = ReaderTtsManager.getExistingInstance()
            if (tts != null && tts.isReading.value) {
                tts.resume()
                ReaderAppWidgetProvider.updateAllWidgets(applicationContext)
            } else {
                playRecentOrFirstBook()
            }
        }

        override fun onPause() {
            ReaderTtsManager.getExistingInstance()?.pause()
            ReaderAppWidgetProvider.updateAllWidgets(applicationContext)
        }

        override fun onSkipToNext() {
            ReaderTtsManager.getExistingInstance()?.skipNext()
            ReaderAppWidgetProvider.updateAllWidgets(applicationContext)
        }

        override fun onSkipToPrevious() {
            ReaderTtsManager.getExistingInstance()?.skipPrevious()
            ReaderAppWidgetProvider.updateAllWidgets(applicationContext)
        }

        override fun onSeekTo(pos: Long) {
            val tts = ReaderTtsManager.getExistingInstance() ?: return
            val mode = AppSettingsManager.getInstance(applicationContext).mediaProgressMode.value
            if (mode == MediaProgressMode.PERCENTAGE) {
                val fraction = (pos.toFloat() / 100_000f).coerceIn(0f, 1f)
                tts.seekToFraction(fraction)
            } else {
                tts.seekToTimeMs(pos)
            }
        }

        override fun onStop() {
            handleStop()
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            if (mediaId == null) return
            when {
                mediaId == "action_set_mode_player" -> {
                    AppSettingsManager.getInstance(applicationContext).setPlaybackMode(PlaybackNotificationMode.MUSIC_PLAYER)
                    notifyChildrenChanged(CATEGORY_MODE)
                    updateNotificationDirectly()
                }
                mediaId == "action_set_mode_classic" -> {
                    AppSettingsManager.getInstance(applicationContext).setPlaybackMode(PlaybackNotificationMode.CLASSIC)
                    notifyChildrenChanged(CATEGORY_MODE)
                    updateNotificationDirectly()
                }
                mediaId.startsWith("book_") -> {
                    val bookId = mediaId.removePrefix("book_").toLongOrNull() ?: -1L
                    if (bookId > 0) {
                        playBookFromService(bookId)
                    }
                }
                else -> {
                    playRecentOrFirstBook()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        ensureChannel(this)

        // Initialize Android MediaSessionCompat
        mediaSession = MediaSessionCompat(this, "VoxReaderMediaService").apply {
            setCallback(mediaSessionCallback)
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }
        sessionToken = mediaSession.sessionToken

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VoxReader:ReaderMediaServiceWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock não pôde ser criado: ${e.message}")
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        when (parentId) {
            MEDIA_ROOT_ID -> {
                val items = mutableListOf<MediaBrowserCompat.MediaItem>()
                val isMusicMode = AppSettingsManager.getInstance(this).playbackMode.value == PlaybackNotificationMode.MUSIC_PLAYER
                val modeTitle = if (isMusicMode) "Modo: Player de Música" else "Modo: Clássico"

                items.add(createBrowsableItem(CATEGORY_RESUME_LAST, "▶ Continuar Último Livro", "Início rápido do livro recente"))
                items.add(createBrowsableItem(CATEGORY_LIBRARY, "📚 Biblioteca de Livros", "Todos os seus livros disponíveis"))
                items.add(createBrowsableItem(CATEGORY_FAVORITES, "⭐ Favoritos", "Livros marcados como favoritos"))
                items.add(createBrowsableItem(CATEGORY_MODE, "⚙️ $modeTitle", "Alternar modo de reprodução"))
                result.sendResult(items)
            }

            CATEGORY_RESUME_LAST -> {
                result.detach()
                serviceScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val book = db.bookDao().getRecentBooksSync(1).firstOrNull()
                    val items = mutableListOf<MediaBrowserCompat.MediaItem>()
                    if (book != null) {
                        val desc = MediaDescriptionCompat.Builder()
                            .setMediaId("book_${book.id}")
                            .setTitle("▶ ${book.title}")
                            .setSubtitle("${book.author} • Cap. ${book.currentChapterIndex + 1}")
                            .setDescription("${(book.readingProgress * 100).toInt()}% concluído")
                            .build()
                        items.add(MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                    }
                    result.sendResult(items)
                }
            }

            CATEGORY_LIBRARY -> {
                result.detach()
                serviceScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val books = db.bookDao().getAllBooksSync()
                    val items = books.map { book ->
                        val desc = MediaDescriptionCompat.Builder()
                            .setMediaId("book_${book.id}")
                            .setTitle(book.title)
                            .setSubtitle("${book.author} • Cap. ${book.currentChapterIndex + 1}")
                            .setDescription("${(book.readingProgress * 100).toInt()}% lido")
                            .setIconUri(book.coverImagePath?.let { Uri.fromFile(File(it)) })
                            .build()
                        MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
                    }.toMutableList()
                    result.sendResult(items)
                }
            }

            CATEGORY_FAVORITES -> {
                result.detach()
                serviceScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val books = db.bookDao().getAllBooksSync().filter { it.isFavorite }
                    val items = books.map { book ->
                        val desc = MediaDescriptionCompat.Builder()
                            .setMediaId("book_${book.id}")
                            .setTitle(book.title)
                            .setSubtitle("${book.author} • Cap. ${book.currentChapterIndex + 1}")
                            .build()
                        MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
                    }.toMutableList()
                    result.sendResult(items)
                }
            }

            CATEGORY_MODE -> {
                val items = mutableListOf<MediaBrowserCompat.MediaItem>()
                val descPlayer = MediaDescriptionCompat.Builder()
                    .setMediaId("action_set_mode_player")
                    .setTitle("Ativar Modo Player de Música")
                    .setSubtitle("Controles e capa do álbum na tela de bloqueio")
                    .build()
                val descClassic = MediaDescriptionCompat.Builder()
                    .setMediaId("action_set_mode_classic")
                    .setTitle("Ativar Modo Clássico")
                    .setSubtitle("Notificação com texto descritivo atualizado")
                    .build()
                items.add(MediaBrowserCompat.MediaItem(descPlayer, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                items.add(MediaBrowserCompat.MediaItem(descClassic, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                result.sendResult(items)
            }

            else -> {
                result.sendResult(mutableListOf())
            }
        }
    }

    private fun createBrowsableItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    fun updateStateDirectly(
        bookTitle: String,
        chapterTitle: String,
        paragraphIndex: Int,
        totalParagraphs: Int,
        isPlaying: Boolean,
        snippet: String,
        bookId: Long = -1L,
        author: String = "",
        activeImagePath: String? = null,
        elapsedTimeMs: Long = 0L,
        totalDurationMs: Long = 0L,
        readingSpeed: Float = 1.0f
    ) {
        currentBookTitle = bookTitle
        currentChapterTitle = chapterTitle
        currentParagraphIndex = paragraphIndex
        currentTotalParagraphs = totalParagraphs
        isCurrentlyPlaying = isPlaying
        currentSnippet = snippet
        if (bookId > 0) currentBookId = bookId
        if (author.isNotBlank()) currentAuthor = author
        currentElapsedTimeMs = elapsedTimeMs
        currentTotalDurationMs = totalDurationMs
        currentReadingSpeed = readingSpeed

        handleIllustrationImage(activeImagePath)

        manageWakeLock(isPlaying)
        updateNotificationDirectly()
    }

    private fun handleIllustrationImage(imagePath: String?) {
        val showImages = AppSettingsManager.getInstance(this).notificationShowImages.value
        if (!showImages || imagePath.isNullOrBlank()) {
            if (currentActiveImagePath != null) {
                imageRevertJob?.cancel()
                imageRevertJob = null
                currentActiveImagePath = null
                currentActiveImageBitmap = null
            }
            return
        }

        if (imagePath != currentActiveImagePath) {
            val file = File(imagePath)
            if (file.exists() && file.length() > 0) {
                try {
                    val rawBmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (rawBmp != null) {
                        val fitMode = AppSettingsManager.getInstance(this).coverFitMode.value
                        currentActiveImageBitmap = CoverArtHelper.formatBookCoverForSquareArtwork(rawBmp, fitMode)
                        currentActiveImagePath = imagePath

                        val durationSec = AppSettingsManager.getInstance(this).notificationImageDurationSec.value
                        imageRevertJob?.cancel()
                        imageRevertJob = serviceScope.launch {
                            delay(durationSec * 1000L)
                            currentActiveImageBitmap = null
                            currentActiveImagePath = null
                            updateNotificationDirectly()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Falha ao carregar imagem para notificação: ${e.message}")
                }
            }
        }
    }

    private fun updateNotificationDirectly() {
        try {
            val notification = buildMediaNotification()
            notificationManager.notify(NOTIFICATION_ID, notification)
            ReaderAppWidgetProvider.updateAllWidgets(this)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar notificação diretamente", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_UPDATE_STATE

        // Start foreground immediately to comply with 5s timeout
        try {
            val notification = buildMediaNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar foreground no onStartCommand", e)
        }

        when (action) {
            ACTION_UPDATE_STATE -> {
                currentBookTitle = intent?.getStringExtra(EXTRA_BOOK_TITLE) ?: currentBookTitle
                currentChapterTitle = intent?.getStringExtra(EXTRA_CHAPTER_TITLE) ?: currentChapterTitle
                currentParagraphIndex = intent?.getIntExtra(EXTRA_PARAGRAPH_INDEX, currentParagraphIndex) ?: currentParagraphIndex
                currentTotalParagraphs = intent?.getIntExtra(EXTRA_TOTAL_PARAGRAPHS, currentTotalParagraphs) ?: currentTotalParagraphs
                isCurrentlyPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, isCurrentlyPlaying) ?: isCurrentlyPlaying
                currentSnippet = intent?.getStringExtra(EXTRA_SNIPPET) ?: currentSnippet
                val extraBookId = intent?.getLongExtra(EXTRA_BOOK_ID, -1L) ?: -1L
                if (extraBookId > 0) currentBookId = extraBookId
                val extraAuthor = intent?.getStringExtra(EXTRA_AUTHOR) ?: ""
                if (extraAuthor.isNotBlank()) currentAuthor = extraAuthor
                val extraImagePath = intent?.getStringExtra(EXTRA_ACTIVE_IMAGE_PATH)
                currentElapsedTimeMs = intent?.getLongExtra(EXTRA_ELAPSED_TIME_MS, currentElapsedTimeMs) ?: currentElapsedTimeMs
                currentTotalDurationMs = intent?.getLongExtra(EXTRA_TOTAL_DURATION_MS, currentTotalDurationMs) ?: currentTotalDurationMs
                currentReadingSpeed = intent?.getFloatExtra(EXTRA_READING_SPEED, currentReadingSpeed) ?: currentReadingSpeed

                handleIllustrationImage(extraImagePath)

                manageWakeLock(isCurrentlyPlaying)
                updateNotificationDirectly()
            }

            ACTION_PLAY_BOOK -> {
                val bookId = intent?.getLongExtra(EXTRA_BOOK_ID, -1L) ?: -1L
                if (bookId > 0) {
                    playBookFromService(bookId)
                }
            }

            ACTION_PLAY_PAUSE -> {
                ReaderTtsManager.getExistingInstance()?.togglePlayPause()
                ReaderAppWidgetProvider.updateAllWidgets(this)
            }

            ACTION_PLAY -> {
                ReaderTtsManager.getExistingInstance()?.resume()
                ReaderAppWidgetProvider.updateAllWidgets(this)
            }

            ACTION_PAUSE -> {
                ReaderTtsManager.getExistingInstance()?.pause()
                ReaderAppWidgetProvider.updateAllWidgets(this)
            }

            ACTION_NEXT -> {
                ReaderTtsManager.getExistingInstance()?.skipNext()
                ReaderAppWidgetProvider.updateAllWidgets(this)
            }

            ACTION_PREVIOUS -> {
                ReaderTtsManager.getExistingInstance()?.skipPrevious()
                ReaderAppWidgetProvider.updateAllWidgets(this)
            }

            ACTION_STOP -> {
                handleStop()
            }
        }

        return START_NOT_STICKY
    }

    private fun playRecentOrFirstBook() {
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val recent = db.bookDao().getRecentBooksSync(1).firstOrNull()
            if (recent != null) {
                playBookFromService(recent.id)
            }
        }
    }

    private fun playBookFromService(bookId: Long) {
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val book = db.bookDao().getBookByIdSync(bookId) ?: return@launch
            val chapters: List<com.example.data.ChapterEntity> = db.bookDao().getChaptersForBookSync(bookId)
            if (chapters.isEmpty()) return@launch

            val chapterIdx = book.currentChapterIndex.coerceIn(0, chapters.size - 1)
            val chapter: com.example.data.ChapterEntity = chapters[chapterIdx]
            val paragraphs = chapter.content.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
            if (paragraphs.isEmpty()) return@launch

            val startIdx = book.currentParagraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))

            currentBookId = book.id
            currentBookTitle = book.title
            currentAuthor = book.author
            currentChapterTitle = chapter.title

            val ttsManager = ReaderTtsManager.getExistingInstance() ?: run {
                val repo = BookRepository(db.bookDao())
                val apiKeyMgr = com.example.tts.GeminiApiKeyManager(repo)
                val geminiClient = com.example.tts.GeminiTtsClient(apiKeyMgr)
                ReaderTtsManager(applicationContext, geminiClient)
            }

            val vEngine = if (book.voiceEngine.isBlank()) "EDGE_TTS" else book.voiceEngine
            val vId = if (book.voiceId.isBlank()) "pt-BR-FranciscaNeural" else book.voiceId

            withContext(Dispatchers.Main) {
                ttsManager.startReading(
                    bookId = book.id,
                    chapterIndex = chapterIdx,
                    paragraphs = paragraphs,
                    startIndex = startIdx,
                    voiceEngine = vEngine,
                    voiceId = vId,
                    speed = book.voiceSpeed,
                    pitch = book.voicePitch,
                    negativeWords = book.negativeWords,
                    skipPageNumbers = book.skipPageNumbers,
                    bookTitle = book.title,
                    chapterTitle = chapter.title
                ) { activeIdx: Int, isEnd: Boolean ->
                    if (isEnd) {
                        if (chapterIdx < chapters.size - 1) {
                            playBookFromService(bookId)
                        }
                    } else {
                        serviceScope.launch(Dispatchers.IO) {
                            val progress = if (paragraphs.isNotEmpty()) activeIdx.toFloat() / paragraphs.size else 0f
                            db.bookDao().updateBookProgress(book.id, chapterIdx, activeIdx, progress)
                        }
                    }
                }
                ReaderAppWidgetProvider.updateAllWidgets(applicationContext)
            }
        }
    }

    private fun manageWakeLock(shouldHold: Boolean) {
        try {
            if (shouldHold) {
                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire(45 * 60 * 1000L) // 45-minute timeout guard
                }
            } else {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro no controle do WakeLock: ${e.message}")
        }
    }

    fun handleStop() {
        manageWakeLock(false)
        ReaderTtsManager.getExistingInstance()?.stopPlaybackOnly()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        ReaderAppWidgetProvider.updateAllWidgets(this)
        stopSelf()
    }

    private fun getCoverBitmap(bookId: Long, bookTitle: String, author: String): Bitmap {
        val activeIllustration = currentActiveImageBitmap
        if (activeIllustration != null) {
            return activeIllustration
        }

        val fitMode = AppSettingsManager.getInstance(this).coverFitMode.value
        if (cachedCoverBookId == bookId && cachedCoverBitmap != null && cachedCoverFitMode == fitMode) {
            return cachedCoverBitmap!!
        }

        val bitmap = try {
            val db = AppDatabase.getDatabase(this)
            val book = if (bookId > 0) {
                runBlocking(Dispatchers.IO) { db.bookDao().getBookByIdSync(bookId) }
            } else null

            if (book != null) {
                if (currentAuthor.isBlank()) currentAuthor = book.author
                CoverArtHelper.getOrGenerateCoverBitmap(this, book, fitMode = fitMode)
            } else {
                CoverArtHelper.generateStyledArtwork(bookTitle, author)
            }
        } catch (_: Exception) {
            CoverArtHelper.generateStyledArtwork(bookTitle, author)
        }
        cachedCoverBookId = bookId
        cachedCoverBitmap = bitmap
        cachedCoverFitMode = fitMode
        return bitmap
    }

    private fun formatTime(posMs: Long, durMs: Long): String {
        fun toMinSec(ms: Long): String {
            val totalSec = (ms / 1000L).coerceAtLeast(0L)
            val min = totalSec / 60L
            val sec = totalSec % 60L
            return if (min >= 60) {
                val hr = min / 60L
                val remMin = min % 60L
                String.format("%d:%02d:%02d", hr, remMin, sec)
            } else {
                String.format("%02d:%02d", min, sec)
            }
        }
        return "${toMinSec(posMs)} / ${toMinSec(durMs)}"
    }

    private fun buildMediaNotification(): Notification {
        ensureChannel(this)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (currentBookId > 0) {
                putExtra("EXTRA_OPEN_BOOK_ID", currentBookId)
            }
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, ReaderMediaService::class.java).apply { action = ACTION_PREVIOUS }
        val prevPendingIntent = PendingIntent.getService(
            this, 101, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, ReaderMediaService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePendingIntent = PendingIntent.getService(
            this, 102, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, ReaderMediaService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(
            this, 103, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ReaderMediaService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 104, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isCurrentlyPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseLabel = if (isCurrentlyPlaying) "Pausar" else "Reproduzir"

        val progressMode = AppSettingsManager.getInstance(this).mediaProgressMode.value

        val (durationMs, positionMs, speedForState) = when (progressMode) {
            MediaProgressMode.ESTIMATED_TIME -> {
                val dur = if (currentTotalDurationMs > 0L) {
                    currentTotalDurationMs
                } else {
                    (currentTotalParagraphs * 25_000L).coerceAtLeast(10_000L)
                }
                val pos = currentElapsedTimeMs.coerceIn(0L, dur)
                val spd = if (isCurrentlyPlaying) currentReadingSpeed else 0f
                Triple(dur, pos, spd)
            }
            MediaProgressMode.PERCENTAGE -> {
                val dur = 100_000L // 100 seconds scale = 100%
                val pct = if (currentTotalParagraphs > 0) {
                    ((currentParagraphIndex.toFloat() / currentTotalParagraphs.coerceAtLeast(1)) * 100f).coerceIn(0f, 100f)
                } else 0f
                val pos = (pct * 1000L).toLong()
                Triple(dur, pos, 0f)
            }
        }

        // Update MediaSession state
        val stateActions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(stateActions)
            .setState(
                if (isCurrentlyPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                positionMs,
                speedForState
            )
            .build()
        mediaSession.setPlaybackState(playbackState)

        val coverBitmap = getCoverBitmap(currentBookId, currentBookTitle, currentAuthor)

        // Update MediaSession metadata
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentChapterTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentAuthor.ifBlank { "VoxReader" })
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentBookTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, currentBookTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "${currentAuthor.ifBlank { "VoxReader" }} • $currentChapterTitle")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, currentSnippet)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, coverBitmap)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, coverBitmap)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
            .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, currentTotalParagraphs.toLong().coerceAtLeast(1L))
            .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, (currentParagraphIndex + 1).toLong())
            .build()
        mediaSession.setMetadata(metadata)

        val isMusicPlayerMode = AppSettingsManager.getInstance(this).playbackMode.value == PlaybackNotificationMode.MUSIC_PLAYER

        val percentInt = if (currentTotalParagraphs > 0) {
            ((currentParagraphIndex.toFloat() / currentTotalParagraphs) * 100).toInt()
        } else 0
        val timeFormatted = formatTime(positionMs, durationMs)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setOngoing(isCurrentlyPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)

        if (isMusicPlayerMode) {
            // MODO PLAYER DE MÚSICA (MediaStyle + Capa do Livro na Tela de Bloqueio + Carrossel de Mídia)
            val mediaStyle = MediaNotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(stopPendingIntent)

            val subTextLabel = if (progressMode == MediaProgressMode.PERCENTAGE) {
                "$percentInt% • Parágrafo ${currentParagraphIndex + 1}/$currentTotalParagraphs"
            } else {
                "$timeFormatted • ${percentInt}%"
            }

            builder.setStyle(mediaStyle)
                .setContentTitle(currentBookTitle)
                .setContentText("${if (currentAuthor.isNotBlank()) "$currentAuthor • " else ""}$currentChapterTitle")
                .setSubText(subTextLabel)
                .setLargeIcon(coverBitmap)
                .addAction(android.R.drawable.ic_media_previous, "Anterior", prevPendingIntent)
                .addAction(playPauseIcon, playPauseLabel, playPausePendingIntent)
                .addAction(android.R.drawable.ic_media_next, "Próximo", nextPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Parar", stopPendingIntent)
        } else {
            // MODO CLÁSSICO (Notificação detalhada com texto do parágrafo atualizado em tempo real)
            val progressText = if (currentTotalParagraphs > 1) {
                if (progressMode == MediaProgressMode.PERCENTAGE) {
                    "$currentChapterTitle • Parágrafo ${currentParagraphIndex + 1}/$currentTotalParagraphs ($percentInt%)"
                } else {
                    "$currentChapterTitle • $timeFormatted ($percentInt%)"
                }
            } else {
                currentChapterTitle
            }
            val bigText = if (currentSnippet.isNotBlank()) currentSnippet else progressText

            builder.setContentTitle(currentBookTitle)
                .setContentText(progressText)
                .setSubText("Voz Neural Edge TTS")
                .setLargeIcon(coverBitmap)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .addAction(android.R.drawable.ic_media_previous, "Anterior", prevPendingIntent)
                .addAction(playPauseIcon, playPauseLabel, playPausePendingIntent)
                .addAction(android.R.drawable.ic_media_next, "Próximo", nextPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Parar", stopPendingIntent)
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        activeInstance = null
        serviceScope.cancel()
        imageRevertJob?.cancel()
        imageRevertJob = null
        currentActiveImageBitmap = null
        currentActiveImagePath = null
        mediaSession.isActive = false
        mediaSession.release()
        cachedCoverBitmap = null
        cachedCoverBookId = -1L
        manageWakeLock(false)
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        ReaderAppWidgetProvider.updateAllWidgets(this)
    }
}
