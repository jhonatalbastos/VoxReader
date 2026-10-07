package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import com.example.data.ChapterEntity
import com.example.model.ReaderFont
import com.example.model.ReaderSettings
import com.example.model.ReaderTheme
import com.example.ui.components.AudioPlayerSheet
import com.example.ui.components.AudiobookExportDialog
import com.example.ui.components.BackgroundDownloadBanner
import com.example.ui.components.ChromeStyleScrollbar
import com.example.ui.components.EditChapterDialog
import com.example.ui.components.EditParagraphDialog
import com.example.ui.components.NegativeWordsDialog
import com.example.ui.components.ReaderAppearanceSheet
import com.example.ui.components.TableOfContentsSheet
import com.example.ui.components.TtsPlaybackBar
import com.example.ui.components.VoiceSettingsSheet
import com.example.ui.theme.AmoledBackground
import com.example.ui.theme.AmoledText
import com.example.ui.theme.DarkReaderBackground
import com.example.ui.theme.DarkReaderText
import com.example.ui.theme.SepiaBackground
import com.example.ui.theme.SepiaText
import com.example.viewmodel.ReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBackToLibrary: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val book by viewModel.currentBook.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val readerSettings by viewModel.readerSettings.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val isReading by viewModel.ttsManager.isReading.collectAsStateWithLifecycle()
    val isPlaying by viewModel.ttsManager.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by viewModel.ttsManager.isBuffering.collectAsStateWithLifecycle()
    val activeParagraphIndex by viewModel.ttsManager.activeParagraphIndex.collectAsStateWithLifecycle()
    val activeEngine by viewModel.ttsManager.activeEngine.collectAsStateWithLifecycle()
    val statusMessage by viewModel.ttsManager.statusMessage.collectAsStateWithLifecycle()
    val taskState by viewModel.downloadTaskState.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showVoiceSettingsSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showNegativeWordsDialog by remember { mutableStateOf(false) }
    var showEditChapterDialog by remember { mutableStateOf(false) }
    var showAudioPlayerSheet by remember { mutableStateOf(false) }
    var paragraphToEdit by remember { mutableStateOf<Pair<Int, String>?>(null) }

    val listState = rememberLazyListState()
    val paragraphs = viewModel.getCurrentChapterParagraphs()
    val currentChapter = chapters.getOrNull(currentChapterIndex)
    val snackbarHostState = remember { SnackbarHostState() }

    val chapterReadPercent by remember(listState.firstVisibleItemIndex, activeParagraphIndex, paragraphs.size, isReading) {
        derivedStateOf {
            val total = paragraphs.size.coerceAtLeast(1)
            val currentPos = if (isReading && activeParagraphIndex >= 0) {
                activeParagraphIndex + 1
            } else {
                (listState.firstVisibleItemIndex - 1).coerceAtLeast(0) + 1
            }
            ((currentPos.toFloat() / total) * 100).toInt().coerceIn(0, 100)
        }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    // Auto scroll list when active paragraph changes in TTS
    LaunchedEffect(activeParagraphIndex) {
        if (activeParagraphIndex in paragraphs.indices) {
            listState.animateScrollToItem(activeParagraphIndex)
        }
    }

    // Colors according to chosen reader theme
    val (readerBgColor, readerTextColor, readerHighlightColor) = when (readerSettings.theme) {
        ReaderTheme.BOOK_PAPER -> Triple(Color(0xFFF7F4EA), Color(0xFF2C241B), Color(0xFFFFECB3))
        ReaderTheme.LIGHT -> Triple(Color(0xFFFFFFFF), Color(0xFF1E293B), Color(0xFFDBEAFE))
        ReaderTheme.SEPIA -> Triple(SepiaBackground, SepiaText, Color(0xFFFFE082))
        ReaderTheme.DARK -> Triple(DarkReaderBackground, DarkReaderText, Color(0xFF1E3A8A))
        ReaderTheme.AMOLED -> Triple(AmoledBackground, AmoledText, Color(0xFF263238))
    }

    val readerFontFamily = when (readerSettings.font) {
        ReaderFont.SERIF -> FontFamily.Serif
        ReaderFont.SANS -> FontFamily.SansSerif
        ReaderFont.MONO -> FontFamily.Monospace
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = readerBgColor.copy(alpha = 0.98f),
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(bottom = 4.dp)
                    ) {
                        // Linha Superior 1: Botão Voltar + Título Completo do Livro e Subtítulo do Capítulo
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.stopReadAloud()
                                    onBackToLibrary()
                                },
                                modifier = Modifier.testTag("btn_reader_back")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar à Biblioteca",
                                    tint = readerTextColor
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = book?.title ?: "Leitor",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = readerTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${currentChapter?.title ?: "Capítulo ${currentChapterIndex + 1}"} • $chapterReadPercent% lido",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Linha Inferior 2: Barra de Ações com Ícones distribuídos uniformemente
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quick Voice & Speed settings
                            IconButton(
                                onClick = { showVoiceSettingsSheet = true },
                                modifier = Modifier.testTag("btn_reader_voice_settings")
                            ) {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = "Voz e Narração", tint = MaterialTheme.colorScheme.primary)
                            }

                            // Filter Negative Words
                            IconButton(
                                onClick = { showNegativeWordsDialog = true },
                                modifier = Modifier.testTag("btn_reader_negative_words")
                            ) {
                                Icon(Icons.Default.Block, contentDescription = "Filtro de Palavras Omitidas", tint = readerTextColor)
                            }

                            // Edit Chapter
                            IconButton(
                                onClick = { showEditChapterDialog = true },
                                modifier = Modifier.testTag("btn_reader_edit_chapter")
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = "Editar Capítulo", tint = readerTextColor)
                            }

                            // Pre-cache and Export MP3
                            IconButton(
                                onClick = { showExportDialog = true },
                                modifier = Modifier.testTag("btn_reader_export_audio")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = "Pré-carregar e Baixar MP3", tint = MaterialTheme.colorScheme.primary)
                            }

                            // Table of Contents
                            IconButton(
                                onClick = { showTocSheet = true },
                                modifier = Modifier.testTag("btn_reader_toc")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Índice de Capítulos", tint = readerTextColor)
                            }

                            // Appearance Settings
                            IconButton(
                                onClick = { showAppearanceSheet = true },
                                modifier = Modifier.testTag("btn_reader_appearance")
                            ) {
                                Icon(Icons.Default.FormatSize, contentDescription = "Diagramação e Aparência", tint = readerTextColor)
                            }

                            // General Settings
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("btn_reader_settings")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Configurações Gerais", tint = readerTextColor)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column {
                if (!showExportDialog) {
                    BackgroundDownloadBanner(
                        taskState = taskState,
                        onOpenDetails = { showExportDialog = true },
                        onCancelTask = { viewModel.cancelAudiobookTask() },
                        onDismissComplete = { viewModel.dismissAudiobookTask() }
                    )
                }

                // Floating Playback Bar
                book?.let { currentBook ->
                TtsPlaybackBar(
                    isVisible = isReading,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    voiceId = currentBook.voiceId,
                    speed = currentBook.voiceSpeed,
                    engineType = activeEngine,
                    statusMessage = statusMessage,
                    paragraphProgressText = "Parágrafo ${activeParagraphIndex + 1}/${paragraphs.size.coerceAtLeast(1)}",
                    onPlayPause = {
                        if (isPlaying) viewModel.pauseReadAloud() else viewModel.resumeReadAloud()
                    },
                    onSkipNext = { viewModel.skipNextParagraph() },
                    onSkipPrevious = { viewModel.skipPreviousParagraph() },
                    onStop = { viewModel.stopReadAloud() },
                    onOpenVoiceSettings = { showVoiceSettingsSheet = true },
                    onOpenAudioPlayer = { showAudioPlayerSheet = true },
                    onRewind = { viewModel.rewindSpeech() }
                )
            }
        }
    },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = readerBgColor
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clickable { showControls = !showControls }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = readerSettings.horizontalMarginDp.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Chapter Header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentChapter?.title ?: "Capítulo ${currentChapterIndex + 1}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = readerTextColor,
                            textAlign = TextAlign.Center,
                            fontFamily = readerFontFamily
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick Read Aloud trigger banner if not reading
                        if (!isReading) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.startReadAloud(null) }
                                    .testTag("banner_read_aloud_chapter")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if ((book?.currentParagraphIndex ?: 0) > 0) "Continuar leitura em voz alta" else "Ler capítulo em voz alta",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val voiceDisplayName = VoiceCatalog.findById(book?.voiceId ?: "").displayName.substringBefore(" (")
                                        val engineVoiceDesc = "Edge Neural HD • $voiceDisplayName (${String.format("%.1fx", book?.voiceSpeed ?: 1.0f)})"
                                        Text(
                                            text = engineVoiceDesc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { viewModel.startReadAloud(null) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(if ((book?.currentParagraphIndex ?: 0) > 0) "Continuar" else "Iniciar")
                                    }
                                }
                            }
                        }
                    }
                }

                // Chapter Paragraphs with Active Spoken Highlight & Book Diagramming
                itemsIndexed(paragraphs) { index, paragraph ->
                    val isSpokenNow = isReading && index == activeParagraphIndex
                    val hasBookmark = bookmarks.any { it.chapterIndex == currentChapterIndex && it.paragraphIndex == index }

                    ParagraphItem(
                        text = paragraph,
                        index = index,
                        isSpoken = isSpokenNow,
                        hasBookmark = hasBookmark,
                        textColor = readerTextColor,
                        highlightColor = readerHighlightColor,
                        fontSizeSp = readerSettings.fontSizeSp,
                        lineSpacingMultiplier = readerSettings.lineSpacingMultiplier,
                        isJustified = readerSettings.isJustified,
                        hasFirstLineIndent = readerSettings.hasFirstLineIndent,
                        fontFamily = readerFontFamily,
                        onReadFromHere = {
                            viewModel.startReadAloud(index)
                        },
                        onEditParagraph = {
                            paragraphToEdit = Pair(index, paragraph)
                        },
                        onDeleteAudioCache = {
                            viewModel.deleteParagraphAudio(index)
                        },
                        onToggleBookmark = {
                            viewModel.addBookmark(index, paragraph)
                        }
                    )
                }

                // Chapter Navigation Footer
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.previousChapter() },
                            enabled = currentChapterIndex > 0,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_prev_chapter")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cap. Anterior")
                        }

                        Text(
                            text = "${currentChapterIndex + 1} / ${chapters.size.coerceAtLeast(1)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = readerTextColor.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = { viewModel.nextChapter() },
                            enabled = currentChapterIndex < chapters.size - 1,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_next_chapter")
                        ) {
                            Text("Próximo Cap.")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                        }
                    }
                }
            }

            // Top discrete reading progress line
            LinearProgressIndicator(
                progress = { (chapterReadPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )

            // Chrome-style vertical scrollbar with live percentage badge and drag support
            ChromeStyleScrollbar(
                listState = listState,
                totalParagraphs = paragraphs.size,
                currentParagraphIndex = activeParagraphIndex,
                isReadingAloud = isReading,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }

    // Modal Sheets and Dialogs
    if (showTocSheet) {
        TableOfContentsSheet(
            chapters = chapters,
            currentChapterIndex = currentChapterIndex,
            bookmarks = bookmarks,
            onSelectChapter = { viewModel.selectChapter(it) },
            onRenameChapter = { chapterId, newTitle ->
                viewModel.renameChapter(chapterId, newTitle)
            },
            onSelectBookmark = { bookmark ->
                viewModel.selectChapter(bookmark.chapterIndex)
                showTocSheet = false
            },
            onDeleteBookmark = { viewModel.deleteBookmark(it) },
            onDismiss = { showTocSheet = false }
        )
    }

    if (showAppearanceSheet) {
        ReaderAppearanceSheet(
            settings = readerSettings,
            onSettingsChanged = { viewModel.updateReaderSettings(it) },
            onDismiss = { showAppearanceSheet = false }
        )
    }

    if (showVoiceSettingsSheet && book != null) {
        VoiceSettingsSheet(
            book = book!!,
            onDismiss = { showVoiceSettingsSheet = false },
            onSaveSettings = { engine, voiceId, speed, pitch ->
                viewModel.updateBookVoiceSettings(engine, voiceId, speed, pitch)
            },
            onTestVoice = { engine, voiceId, speed, pitch ->
                viewModel.testVoice(engine, voiceId, speed, pitch)
            }
        )
    }

    if (showNegativeWordsDialog && book != null) {
        NegativeWordsDialog(
            book = book!!,
            onSave = { words, skipPages, clearCache ->
                viewModel.updateNegativeWordsFilter(book!!.id, words, skipPages, clearCache)
            },
            onDismiss = { showNegativeWordsDialog = false }
        )
    }

    if (showEditChapterDialog && currentChapter != null) {
        EditChapterDialog(
            chapter = currentChapter,
            onSave = { newTitle, newContent ->
                viewModel.updateChapterContent(currentChapter.id, newTitle, newContent)
            },
            onDismiss = { showEditChapterDialog = false }
        )
    }

    paragraphToEdit?.let { (pIndex, pText) ->
        EditParagraphDialog(
            paragraphIndex = pIndex,
            initialText = pText,
            onSave = { updatedText ->
                viewModel.updateParagraph(pIndex, updatedText)
                paragraphToEdit = null
            },
            onDismiss = { paragraphToEdit = null }
        )
    }

    if (showExportDialog && book != null) {
        AudiobookExportDialog(
            book = book!!,
            currentChapter = currentChapter,
            taskState = taskState,
            onDismiss = {
                showExportDialog = false
            },
            onPreloadChapter = { exportToDownloads ->
                viewModel.preloadCurrentChapter(exportToDownloads, currentChapter)
            },
            onPreloadBook = { exportToDownloads ->
                viewModel.preloadEntireBook(exportToDownloads)
            },
            onCancelTask = {
                viewModel.cancelAudiobookTask()
                showExportDialog = false
            }
        )
    }

    // Full Audio Player Modal with animated book cover transitions
    if (showAudioPlayerSheet && book != null) {
        val activeBook = book!!
        val activeChapterTitle by viewModel.ttsManager.activeChapterTitle.collectAsStateWithLifecycle()
        val recentSegments by viewModel.recentSegments.collectAsStateWithLifecycle()
        val chapterLabel = activeChapterTitle ?: (currentChapter?.title ?: "Capítulo ${currentChapterIndex + 1}")
        val progressText = if (activeParagraphIndex >= 0) {
            "Parágrafo ${activeParagraphIndex + 1} de ${paragraphs.size.coerceAtLeast(1)}"
        } else {
            "Iniciando leitura"
        }

        AudioPlayerSheet(
            book = activeBook,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            activeEngine = activeEngine,
            currentChapterTitle = chapterLabel,
            paragraphProgressText = progressText,
            progressFraction = activeBook.readingProgress,
            statusMessage = statusMessage,
            recentSegments = recentSegments,
            onTogglePlayPause = {
                if (isPlaying) viewModel.pauseReadAloud() else viewModel.resumeReadAloud()
            },
            onSkipNext = { viewModel.skipNextParagraph() },
            onSkipPrevious = { viewModel.skipPreviousParagraph() },
            onRewind = { viewModel.rewindSpeech() },
            onReplaySegment = { viewModel.replaySegment(it) },
            onOpenReader = {
                showAudioPlayerSheet = false
            },
            onOpenVoiceSettings = {
                showVoiceSettingsSheet = true
            },
            onDismiss = { showAudioPlayerSheet = false }
        )
    }
}

@Composable
private fun ParagraphItem(
    text: String,
    index: Int,
    isSpoken: Boolean,
    hasBookmark: Boolean,
    textColor: Color,
    highlightColor: Color,
    fontSizeSp: Float,
    lineSpacingMultiplier: Float,
    isJustified: Boolean,
    hasFirstLineIndent: Boolean,
    fontFamily: FontFamily,
    onReadFromHere: () -> Unit,
    onEditParagraph: () -> Unit,
    onDeleteAudioCache: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val imgPath = remember(text) {
        val imgMatch = Regex("""\[IMG:(.*?)\]""").find(text)
        if (imgMatch != null) {
            imgMatch.groupValues[1].substringBefore('|').trim()
        } else {
            val mdMatch = Regex("""!\[.*?\]\((.*?)\)""").find(text)
            if (mdMatch != null) {
                val path = mdMatch.groupValues[1].trim()
                if (!path.startsWith("http://") && !path.startsWith("https://")) path else null
            } else null
        }
    }

    val displayText = remember(text) {
        text.replace(Regex("""\[IMG:(.*?)\]""")) { match ->
            val content = match.groupValues[1]
            if (content.contains('|')) content.substringAfter('|').trim() else ""
        }.replace(Regex("""!\[(.*?)\]\(.*?\)"""), "$1").trim()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSpoken) highlightColor.copy(alpha = 0.5f) else Color.Transparent)
            .border(
                width = if (isSpoken) 1.5.dp else 0.dp,
                color = if (isSpoken) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { showMenu = !showMenu }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("paragraph_$index")
    ) {
        Column {
            if (!imgPath.isNullOrBlank() && File(imgPath).exists()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = File(imgPath),
                        contentDescription = "Ilustração do capítulo",
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .heightIn(max = 320.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            if (displayText.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    if (isSpoken) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp, end = 8.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Lendo em voz alta agora",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    } else if (hasBookmark) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Marcado",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp, end = 6.dp)
                                .size(16.dp)
                        )
                    }

                    Text(
                        text = displayText,
                        color = textColor,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * lineSpacingMultiplier).sp,
                        fontFamily = fontFamily,
                        textAlign = if (isJustified) TextAlign.Justify else TextAlign.Start,
                        style = LocalTextStyle.current.copy(
                            textIndent = if (hasFirstLineIndent) TextIndent(firstLine = 22.sp) else TextIndent.None
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick contextual options when tapping a paragraph
            AnimatedVisibility(visible = showMenu) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Read from here
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            showMenu = false
                            onReadFromHere()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ler daqui",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Edit Paragraph
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            showMenu = false
                            onEditParagraph()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Editar", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Delete Cache for this paragraph
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            showMenu = false
                            onDeleteAudioCache()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpar Áudio", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Bookmark
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            showMenu = false
                            onToggleBookmark()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (hasBookmark) Icons.Default.Bookmark else Icons.Default.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (hasBookmark) "Marcado" else "Marcar",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
