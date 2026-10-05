package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.BookEntity
import com.example.ui.components.AudioPlayerSheet
import com.example.ui.components.AudiobookExportDialog
import com.example.ui.components.BackgroundDownloadBanner
import com.example.ui.components.BackupRestoreDialog
import com.example.ui.components.BookCard
import com.example.ui.components.EBookSimpleListItem
import com.example.ui.components.GeminiApiKeyDialog
import com.example.ui.components.GoogleAccountSheet
import com.example.ui.components.ImportBookDialog
import com.example.ui.components.LibraryMiniPlayerBar
import com.example.ui.components.MemoryDiagnosticsDialog
import com.example.ui.components.NegativeWordsDialog
import com.example.ui.components.VoiceSettingsSheet
import com.example.viewmodel.ReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: ReaderViewModel,
    onOpenReader: (Long) -> Unit,
    onReadAloudBook: (Long) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val books by viewModel.allBooks.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val taskState by viewModel.downloadTaskState.collectAsStateWithLifecycle()
    val googleUser by viewModel.googleUser.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val memoryStats by viewModel.memoryStats.collectAsStateWithLifecycle()
    val memoryLogs by viewModel.memoryLogs.collectAsStateWithLifecycle()

    val pagedBooks = viewModel.pagedBooks.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val recentBook by viewModel.mostRecentBook.collectAsStateWithLifecycle()

    val activeMiniPlayerBook by viewModel.activeMiniPlayerBook.collectAsStateWithLifecycle()
    val isTtsPlaying by viewModel.isTtsPlaying.collectAsStateWithLifecycle()
    val isTtsBuffering by viewModel.isTtsBuffering.collectAsStateWithLifecycle()
    val activeTtsEngine by viewModel.activeTtsEngine.collectAsStateWithLifecycle()

    var isSimpleListView by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showGoogleSheet by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var bookForVoiceSettings by remember { mutableStateOf<BookEntity?>(null) }
    var bookForAudiobookExport by remember { mutableStateOf<BookEntity?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var bookForCoverUpload by remember { mutableStateOf<BookEntity?>(null) }
    var bookForNegativeWords by remember { mutableStateOf<BookEntity?>(null) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showAudioPlayerSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    // Document Picker Launcher for EPUB, TXT, MD, HTML
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = queryFileName(context, uri) ?: "Livro.epub"
            viewModel.importBookFile(uri, fileName)
        }
    }

    // Photo Picker for Book Cover
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && bookForCoverUpload != null) {
            viewModel.uploadBookCover(bookForCoverUpload!!.id, uri)
            bookForCoverUpload = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Ícone oficial do aplicativo VoxReader com identidade visual própria
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_brand_logo),
                            contentDescription = "Ícone do aplicativo VoxReader",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "VoxReader",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Leitor de eBooks & IA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    // 1. Ícone da Memória Usada
                    IconButton(
                        onClick = { showMemoryDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("btn_memory_diagnostics")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Diagnóstico de Memória: ${memoryStats.usedHeapMb}MB",
                            tint = if (memoryStats.heapUsagePercent >= 80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 2. Ícone do Backup JSON
                    IconButton(
                        onClick = { showBackupDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("btn_backup_restore_topbar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = "Backup JSON",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 3. Ícone do Login com Google
                    IconButton(
                        onClick = { showGoogleSheet = true },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("btn_google_account_topbar")
                    ) {
                        Icon(
                            imageVector = if (googleUser != null) Icons.Default.CloudDone else Icons.Default.CloudSync,
                            contentDescription = if (googleUser != null) "Conta Google: ${googleUser!!.displayName}" else "Login Google",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showImportDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_import_book")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Importar Livro"
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            activeMiniPlayerBook?.let { activeBook ->
                LibraryMiniPlayerBar(
                    book = activeBook,
                    isPlaying = isTtsPlaying,
                    isBuffering = isTtsBuffering,
                    activeEngine = activeTtsEngine,
                    onTogglePlayPause = { viewModel.toggleMiniPlayerSpeech() },
                    onClick = { showAudioPlayerSheet = true }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if ((taskState.isActive || taskState.isComplete) && !showExportDialog) {
                BackgroundDownloadBanner(
                    taskState = taskState,
                    onOpenDetails = {
                        bookForAudiobookExport = books.find { it.title == taskState.bookTitle } ?: books.firstOrNull()
                        showExportDialog = true
                    },
                    onCancelTask = { viewModel.cancelAudiobookTask() },
                    onDismissComplete = { viewModel.dismissAudiobookTask() }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Pesquisar título ou autor...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_search_books")
                )
            }

            // Google Cloud Sync Banner / Status Badge
            if (googleUser == null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGoogleSheet = true }
                            .testTag("banner_google_backup"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Salvar biblioteca no Google",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Guarde livros, status e progresso de leitura na nuvem.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { showGoogleSheet = true },
                                modifier = Modifier.testTag("btn_connect_google_banner")
                            ) {
                                Text("Entrar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                            .clickable { showGoogleSheet = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("badge_google_synced"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Nuvem Google: ${googleUser!!.email}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = if (syncStatus.isSyncing) "Sincronizando..." else "Gerenciar",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Continue Reading Hero Card (if books exist)
            val heroBook = recentBook
            if (heroBook != null && searchQuery.isBlank() && selectedFilter == "Todos") {
                item {
                    Text(
                        text = "Continuar Ouvindo / Lendo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenReader(heroBook.id) }
                            .testTag("card_continue_reading"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(heroBook.coverGradientStart),
                                            Color(heroBook.coverGradientEnd)
                                        )
                                    )
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = heroBook.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${heroBook.author} • Cap. ${heroBook.currentChapterIndex + 1} de ${heroBook.totalChapters}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${(heroBook.readingProgress * 100).toInt()}% concluído",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = { onReadAloudBook(heroBook.id) },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .testTag("btn_hero_read_aloud")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = "Ouvir agora",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filterOptions = listOf("Todos", "Favoritos", "EPUB", "PDF", "TXT/MD")
                    items(filterOptions) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { viewModel.setSelectedFilter(filter) },
                            label = { Text(filter) },
                            modifier = Modifier.testTag("chip_filter_$filter")
                        )
                    }
                }
            }

            // Book List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Sua Biblioteca (${pagedBooks.itemCount})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "Paging 3",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { isSimpleListView = !isSimpleListView },
                        modifier = Modifier.testTag("btn_toggle_view_mode")
                    ) {
                        Icon(
                            imageVector = if (isSimpleListView) Icons.Default.ViewAgenda else Icons.Default.ViewList,
                            contentDescription = if (isSimpleListView) "Modo Cards" else "Modo Lista Simples",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (pagedBooks.loadState.refresh is LoadState.Loading && pagedBooks.itemCount == 0) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Carregando livros da biblioteca...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (pagedBooks.itemCount == 0) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.LibraryBooks,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Nenhum livro encontrado.",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Toque no botão '+' abaixo para importar um EPUB ou TXT.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    count = pagedBooks.itemCount,
                    key = { index -> pagedBooks[index]?.id ?: index }
                ) { index ->
                    val book = pagedBooks[index] ?: return@items
                    if (isSimpleListView) {
                        EBookSimpleListItem(
                            book = book,
                            onClick = { onOpenReader(book.id) },
                            onReadAloudClick = { onReadAloudBook(book.id) },
                            onVoiceSettingsClick = { bookForVoiceSettings = book },
                            onUpdateVoiceSettings = { engine, voiceId, speed, pitch ->
                                viewModel.updateBookVoiceSettingsDirectly(book.id, engine, voiceId, speed, pitch)
                            },
                            onCycleSpeed = { viewModel.cycleBookPlaybackSpeed(book) },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        BookCard(
                            book = book,
                            onClick = { onOpenReader(book.id) },
                            onReadAloudClick = { onReadAloudBook(book.id) },
                            onVoiceSettingsClick = { bookForVoiceSettings = book },
                            onAudiobookExportClick = {
                                bookForAudiobookExport = book
                                showExportDialog = true
                            },
                            onUploadCoverClick = {
                                bookForCoverUpload = book
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onNegativeWordsClick = { bookForNegativeWords = book },
                            onClearAudioCacheClick = { viewModel.deleteBookAudio(book.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(book) },
                            onDeleteClick = { viewModel.deleteBook(book) }
                        )
                    }
                }

                if (pagedBooks.loadState.append is LoadState.Loading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

    // Import Dialog
    if (showImportDialog) {
        ImportBookDialog(
            onDismiss = { showImportDialog = false },
            onPickFileClick = {
                filePickerLauncher.launch(
                    arrayOf(
                        "application/epub+zip",
                        "application/pdf",
                        "text/plain",
                        "text/html",
                        "text/markdown",
                        "*/*"
                    )
                )
            },
            onCreateManualBook = { title, author, content ->
                viewModel.createManualBook(title, author, content)
            }
        )
    }

    // Negative Words Filter Dialog
    bookForNegativeWords?.let { targetBook ->
        NegativeWordsDialog(
            book = targetBook,
            onSave = { words, skipPages, clearCache ->
                viewModel.updateNegativeWordsFilter(targetBook.id, words, skipPages, clearCache)
                bookForNegativeWords = null
            },
            onDismiss = { bookForNegativeWords = null }
        )
    }

    // Voice & Speed Customization Sheet for selected book
    bookForVoiceSettings?.let { currentBookSetting ->
        VoiceSettingsSheet(
            book = currentBookSetting,
            onDismiss = { bookForVoiceSettings = null },
            onSaveSettings = { engine, voiceId, speed, pitch ->
                viewModel.updateBookVoiceSettingsDirectly(currentBookSetting.id, engine, voiceId, speed, pitch)
                bookForVoiceSettings = null
            },
            onTestVoice = { engine, voiceId, speed, pitch ->
                viewModel.testVoice(engine, voiceId, speed, pitch)
            }
        )
    }

    // Audiobook Pre-caching and Export Dialog
    val targetExportBook = bookForAudiobookExport ?: books.find { it.title == taskState.bookTitle }
    if (showExportDialog && targetExportBook != null) {
        AudiobookExportDialog(
            book = targetExportBook,
            currentChapter = null,
            taskState = taskState,
            onDismiss = {
                showExportDialog = false
            },
            onPreloadChapter = { /* Chapter preload done from reader */ },
            onPreloadBook = { exportToDownloads ->
                viewModel.preloadEntireBook(exportToDownloads, targetExportBook)
            },
            onCancelTask = {
                viewModel.cancelAudiobookTask()
                showExportDialog = false
            }
        )
    }

    // Google Account & Cloud Sync Sheet
    if (showGoogleSheet) {
        GoogleAccountSheet(
            user = googleUser,
            syncStatus = syncStatus,
            onDismiss = { showGoogleSheet = false },
            onSignInClick = { activity ->
                viewModel.signInWithGoogle(activity)
            },
            onQuickSignIn = { email, name ->
                viewModel.signInDevAccount(email, name)
            },
            onSignOutClick = {
                viewModel.signOutGoogle()
            },
            onSyncNowClick = {
                viewModel.syncLibraryWithCloud()
            },
            onRestoreClick = {
                viewModel.restoreLibraryFromCloud()
            }
        )
    }

    // Memory Diagnostics & LeakCanary Dialog
    if (showMemoryDialog) {
        MemoryDiagnosticsDialog(
            stats = memoryStats,
            logs = memoryLogs,
            onDismiss = { showMemoryDialog = false },
            onForceGc = { viewModel.forceGarbageCollection() },
            onClearLogs = { viewModel.clearMemoryLogs() }
        )
    }

    // Local JSON Backup & Restore Dialog
    if (showBackupDialog) {
        BackupRestoreDialog(
            viewModel = viewModel,
            onDismiss = { showBackupDialog = false }
        )
    }

    // Full Audio Player Modal with animated book cover transitions
    if (showAudioPlayerSheet && activeMiniPlayerBook != null) {
        val activeBook = activeMiniPlayerBook!!
        val activeChapterTitle by viewModel.ttsManager.activeChapterTitle.collectAsStateWithLifecycle()
        val activeParaIdx by viewModel.ttsManager.activeParagraphIndex.collectAsStateWithLifecycle()
        val ttsStatusMsg by viewModel.ttsManager.statusMessage.collectAsStateWithLifecycle()
        val recentSegments by viewModel.recentSegments.collectAsStateWithLifecycle()
        val chapterLabel = activeChapterTitle ?: "Capítulo ${activeBook.currentChapterIndex + 1}"
        val progressText = if (activeParaIdx >= 0) "Parágrafo ${activeParaIdx + 1}" else "Iniciando leitura"

        AudioPlayerSheet(
            book = activeBook,
            isPlaying = isTtsPlaying,
            isBuffering = isTtsBuffering,
            activeEngine = activeTtsEngine,
            currentChapterTitle = chapterLabel,
            paragraphProgressText = progressText,
            progressFraction = activeBook.readingProgress,
            statusMessage = ttsStatusMsg,
            recentSegments = recentSegments,
            onTogglePlayPause = { viewModel.toggleMiniPlayerSpeech() },
            onSkipNext = { viewModel.skipNextParagraph() },
            onSkipPrevious = { viewModel.skipPreviousParagraph() },
            onRewind = { viewModel.rewindSpeech() },
            onReplaySegment = { viewModel.replaySegment(it) },
            onOpenReader = {
                showAudioPlayerSheet = false
                onOpenReader(activeBook.id)
            },
            onOpenVoiceSettings = {
                bookForVoiceSettings = activeBook
            },
            onDismiss = { showAudioPlayerSheet = false }
        )
    }
}

private fun queryFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
