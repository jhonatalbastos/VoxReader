package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import com.example.util.AppTheme
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ReaderFont
import com.example.model.ReaderTheme
import com.example.model.VoiceCatalog
import com.example.ui.components.BackupRestoreDialog
import com.example.ui.components.GeminiApiKeyDialog
import com.example.ui.components.MemoryDiagnosticsDialog
import com.example.util.AppSettingsManager
import com.example.util.CoverFitMode
import com.example.util.MediaProgressMode
import com.example.util.PlaybackNotificationMode
import com.example.viewmodel.ReaderViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ReaderViewModel,
    onBackToLibrary: () -> Unit
) {
    BackHandler { onBackToLibrary() }

    val context = LocalContext.current
    val settingsManager = remember { AppSettingsManager.getInstance(context) }

    val playbackMode by settingsManager.playbackMode.collectAsStateWithLifecycle()
    val autoPlaybackMode by settingsManager.autoPlaybackMode.collectAsStateWithLifecycle()
    val autoResumeLast by settingsManager.autoResumeLast.collectAsStateWithLifecycle()
    val appTheme by settingsManager.appTheme.collectAsStateWithLifecycle()
    val defaultTheme by settingsManager.defaultTheme.collectAsStateWithLifecycle()
    val defaultFont by settingsManager.defaultFont.collectAsStateWithLifecycle()
    val defaultFontSize by settingsManager.defaultFontSize.collectAsStateWithLifecycle()
    val defaultLineSpacing by settingsManager.defaultLineSpacing.collectAsStateWithLifecycle()
    val defaultVoiceEngine by settingsManager.defaultVoiceEngine.collectAsStateWithLifecycle()
    val defaultVoiceId by settingsManager.defaultVoiceId.collectAsStateWithLifecycle()
    val defaultVoiceSpeed by settingsManager.defaultVoiceSpeed.collectAsStateWithLifecycle()
    val defaultVoicePitch by settingsManager.defaultVoicePitch.collectAsStateWithLifecycle()
    val mediaProgressMode by settingsManager.mediaProgressMode.collectAsStateWithLifecycle()
    val coverFitMode by settingsManager.coverFitMode.collectAsStateWithLifecycle()
    val notificationShowImages by settingsManager.notificationShowImages.collectAsStateWithLifecycle()
    val notificationImageDurationSec by settingsManager.notificationImageDurationSec.collectAsStateWithLifecycle()

    val googleUser by viewModel.googleUser.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val memoryStats by viewModel.memoryStats.collectAsStateWithLifecycle()
    val memoryLogs by viewModel.memoryLogs.collectAsStateWithLifecycle()
    val apiKeys by viewModel.apiKeys.collectAsStateWithLifecycle()

    var showMemoryDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showGeminiKeysDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ajustes & Configurações",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackToLibrary,
                        modifier = Modifier.testTag("btn_back_from_settings")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar para Biblioteca"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. MODO DE REPRODUÇÃO TTS & CONTROLES DE MÍDIA
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Estilo de Notificação & Player TTS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Como o sistema Android reconhece a leitura",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Opção Player de Música
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (playbackMode == PlaybackNotificationMode.MUSIC_PLAYER) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { settingsManager.setPlaybackMode(PlaybackNotificationMode.MUSIC_PLAYER) }
                            .testTag("opt_mode_music_player")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = playbackMode == PlaybackNotificationMode.MUSIC_PLAYER,
                                onClick = { settingsManager.setPlaybackMode(PlaybackNotificationMode.MUSIC_PLAYER) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Modo Player de Música",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Recomendado", fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Trata a narração como áudio nativo. Exibe a capa do livro na tela de bloqueio, controles multimídia e carrossel de mídia do Android.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Opção Clássico
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (playbackMode == PlaybackNotificationMode.CLASSIC) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { settingsManager.setPlaybackMode(PlaybackNotificationMode.CLASSIC) }
                            .testTag("opt_mode_classic")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = playbackMode == PlaybackNotificationMode.CLASSIC,
                                onClick = { settingsManager.setPlaybackMode(PlaybackNotificationMode.CLASSIC) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Modo Clássico",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Notificação com o texto e o parágrafo lidos atualizados em tempo real conforme a voz narra.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 1.1 ENQUADRAMENTO DA CAPA DO LIVRO NO PLAYER
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Crop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Enquadramento da Capa no Player",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Evita que a capa do livro fique achatada no formato quadrado do player",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Opção: Fundo Desfocado (Mantém proporção)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (coverFitMode == CoverFitMode.AMBIENT_BLUR_FIT) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { settingsManager.setCoverFitMode(CoverFitMode.AMBIENT_BLUR_FIT) }
                                .testTag("opt_cover_ambient_blur")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = coverFitMode == CoverFitMode.AMBIENT_BLUR_FIT,
                                    onClick = { settingsManager.setCoverFitMode(CoverFitMode.AMBIENT_BLUR_FIT) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Enquadrar Capa com Fundo Desfocado",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Padrão", fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Mantém a proporção vertical original do livro sem achatar, com elegante fundo atmosférico suave e sombra 3D.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Opção: Recorte Centralizado 1:1
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (coverFitMode == CoverFitMode.CENTER_CROP) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { settingsManager.setCoverFitMode(CoverFitMode.CENTER_CROP) }
                                .testTag("opt_cover_center_crop")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = coverFitMode == CoverFitMode.CENTER_CROP,
                                    onClick = { settingsManager.setCoverFitMode(CoverFitMode.CENTER_CROP) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Recorte Centralizado 1:1",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Preenche o quadrado recortando suavemente as bordas laterais sem distorcer o livro.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 1.2 CONTADOR & BARRA DE PROGRESSO DA MÍDIA
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Contador e Barra de Progresso",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Formato de exibição do tempo no reprodutor de mídia",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Opção 1: Tempo Estimado (Preferência/Padrão)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (mediaProgressMode == MediaProgressMode.ESTIMATED_TIME) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { settingsManager.setMediaProgressMode(MediaProgressMode.ESTIMATED_TIME) }
                                .testTag("opt_progress_estimated_time")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = mediaProgressMode == MediaProgressMode.ESTIMATED_TIME,
                                    onClick = { settingsManager.setMediaProgressMode(MediaProgressMode.ESTIMATED_TIME) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Tempo Estimado da Leitura (min:seg)",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Opção 1 (Padrão)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Calcula a duração estimada do capítulo com base nas palavras e na velocidade da voz narrada (ex: 03:20 / 14:45).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Opção 2: Progresso Percentual
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (mediaProgressMode == MediaProgressMode.PERCENTAGE) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { settingsManager.setMediaProgressMode(MediaProgressMode.PERCENTAGE) }
                                .testTag("opt_progress_percentage")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = mediaProgressMode == MediaProgressMode.PERCENTAGE,
                                    onClick = { settingsManager.setMediaProgressMode(MediaProgressMode.PERCENTAGE) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Barra de Progresso Percentual",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Opção 2", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Exibe a barra de reprodução e contador em percentual do capítulo (0% a 100%), como no app.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 1.3 ILUSTRAÇÕES DO LIVRO NAS NOTIFICAÇÕES
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Exibir Ilustrações na Notificação",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Quando a narração passa por uma imagem no capítulo, exibe a ilustração na notificação do player",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = notificationShowImages,
                                onCheckedChange = { settingsManager.setNotificationShowImages(it) },
                                modifier = Modifier.testTag("switch_notification_images")
                            )
                        }

                        if (notificationShowImages) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Tempo de exibição da imagem:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "$notificationImageDurationSec segundos",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(5, 8, 12, 15, 20).forEach { sec ->
                                            FilterChip(
                                                selected = notificationImageDurationSec == sec,
                                                onClick = { settingsManager.setNotificationImageDurationSec(sec) },
                                                label = { Text("${sec}s") },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Reading Reminders
            var showReadingReminderDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Lembrete de Leitura",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notificar após tempo de leitura diário", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Incentiva o hábito de leitura regular.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { showReadingReminderDialog = true }) {
                            Text("Configurar")
                        }
                    }
                }
            }
            if (showReadingReminderDialog) {
                com.example.ui.components.ReadingReminderDialog(
                    onDismiss = { showReadingReminderDialog = false },
                    onSaveReminder = {
                        // TODO save logic here
                    }
                )
            }

            // 2. ANDROID AUTO (CARRO)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Android Auto",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Integração segura com a central multimídia do veículo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Início Rápido Automático no Carro
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Início rápido do último livro", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Oferece no menu do carro atalho prioritário para continuar a leitura de onde você parou.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = autoResumeLast,
                            onCheckedChange = { settingsManager.setAutoResumeLast(it) }
                        )
                    }

                    // Modo de reprodução no Android Auto
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Estilo de reprodução no carro:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = autoPlaybackMode == PlaybackNotificationMode.MUSIC_PLAYER,
                                onClick = { settingsManager.setAutoPlaybackMode(PlaybackNotificationMode.MUSIC_PLAYER) },
                                label = { Text("Player de Música") }
                            )
                            FilterChip(
                                selected = autoPlaybackMode == PlaybackNotificationMode.CLASSIC,
                                onClick = { settingsManager.setAutoPlaybackMode(PlaybackNotificationMode.CLASSIC) },
                                label = { Text("Clássico") }
                            )
                        }
                    }
                }
            }

            // 3. CONTA GOOGLE & SINCRONIZAÇÃO EM NUVEM
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Conta Google & Sincronização",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Backup em nuvem com o Google Drive",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (googleUser != null) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = googleUser!!.displayName.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(googleUser!!.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(googleUser!!.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Última sincronização:", style = MaterialTheme.typography.bodySmall)
                                    val lastSyncStr = syncStatus.lastSyncTime?.let {
                                        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(it))
                                    } ?: "Agora há pouco"
                                    Text(lastSyncStr, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.syncLibraryWithCloud() },
                                modifier = Modifier.weight(1f),
                                enabled = !syncStatus.isSyncing
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sincronizar")
                            }
                            OutlinedButton(
                                onClick = { viewModel.signOutGoogle() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Sair da Conta")
                            }
                        }
                    } else {
                        Text(
                            text = "Conecte sua conta Google para sincronizar o progresso de leitura entre dispositivos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val act = context as? android.app.Activity
                                    if (act != null) viewModel.signInWithGoogle(act)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Conectar Google")
                            }
                            OutlinedButton(
                                onClick = { viewModel.signInDevAccount("leitor@gmail.com", "Usuário Google") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Login Rápido")
                            }
                        }
                    }
                }
            }

            // 4. BACKUP LOCAL (JSON)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Backup Local em Arquivo JSON",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Exporte histórico de leitura, vozes e velocidades para arquivo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { showBackupDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_open_backup_manager")
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gerenciar Backup & Restauração JSON")
                    }
                }
            }

            // 5. TEMA DO APLICATIVO (ALINHADO COM OS TEMAS DOS LIVROS)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().testTag("card_app_theme_settings")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Tema do Aplicativo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Personalize o visual e as cores do app, alinhado aos temas dos livros",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Lista de temas visuais com amostras de cor no estilo do leitor
                    val themeVisuals = listOf(
                        Triple(AppTheme.BOOK_PAPER, "Papel Livro", Triple(Color(0xFFF7F4EA), Color(0xFF2C241B), "Aconchegante e quente como páginas de livro")),
                        Triple(AppTheme.SEPIA, "Sépia Clássico", Triple(Color(0xFFFBF0D9), Color(0xFF433422), "Tons dourados vintage para descanso visual")),
                        Triple(AppTheme.LIGHT, "Branco Limpo", Triple(Color(0xFFFFFFFF), Color(0xFF1E293B), "Branco tradicional de alta nitidez e contraste")),
                        Triple(AppTheme.DARK, "Noturno", Triple(Color(0xFF1E293B), Color(0xFFF8FAFC), "Tons escuros equilibrados para pouca luz")),
                        Triple(AppTheme.AMOLED, "Preto Puro (AMOLED)", Triple(Color(0xFF000000), Color(0xFFF1F5F9), "Preto absoluto para máxima economia de energia")),
                        Triple(AppTheme.SYSTEM, "Padrão do Sistema", Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Acompanha automaticamente o tema do Android"))
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        themeVisuals.forEach { (theme, name, details) ->
                            val (bgColor, textColor, desc) = details
                            val isSelected = appTheme == theme

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { settingsManager.setAppTheme(theme) }
                                    .testTag("opt_theme_${theme.name}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Amostra de cor circular 'Aa' idêntica à do leitor
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(bgColor)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.6f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Aa",
                                            color = textColor,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = name,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.primary)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Ativo",
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { settingsManager.setAppTheme(theme) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. DIAGRAMAÇÃO & APARÊNCIA PADRÃO DOS LIVROS
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Diagramação & Leitura Padrão",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Preferências visuais aplicadas a novos livros",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Tema Padrão do Leitor com amostras visuais de cor
                    Text("Tema Padrão dos Livros:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val bookThemeSwatches = listOf(
                            Triple(ReaderTheme.BOOK_PAPER, "Papel", Color(0xFFF7F4EA) to Color(0xFF2C241B)),
                            Triple(ReaderTheme.SEPIA, "Sépia", Color(0xFFFBF0D9) to Color(0xFF433422)),
                            Triple(ReaderTheme.LIGHT, "Branco", Color(0xFFFFFFFF) to Color(0xFF1E293B)),
                            Triple(ReaderTheme.DARK, "Noturno", Color(0xFF1E293B) to Color(0xFFE2E8F0)),
                            Triple(ReaderTheme.AMOLED, "AMOLED", Color(0xFF000000) to Color(0xFFE0E0E0))
                        )

                        bookThemeSwatches.forEach { (bTheme, label, colors) ->
                            val isSelected = defaultTheme == bTheme
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { settingsManager.setDefaultTheme(bTheme) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(colors.first)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aa",
                                        color = colors.second,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Tipografia Padrão
                    Text("Fonte Padrão:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReaderFont.entries.forEach { font ->
                            FilterChip(
                                selected = defaultFont == font,
                                onClick = { settingsManager.setDefaultFont(font) },
                                label = { Text(font.displayName, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Tamanho da Fonte Padrão
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tamanho da fonte:", style = MaterialTheme.typography.bodySmall)
                            Text("${defaultFontSize.toInt()} sp", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = defaultFontSize,
                            onValueChange = { settingsManager.setDefaultFontSize(it) },
                            valueRange = 12f..28f,
                            steps = 7
                        )
                    }

                    // Espaçamento entre Linhas
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Espaçamento de linhas:", style = MaterialTheme.typography.bodySmall)
                            Text(String.format(Locale.getDefault(), "%.1fx", defaultLineSpacing), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = defaultLineSpacing,
                            onValueChange = { settingsManager.setDefaultLineSpacing(it) },
                            valueRange = 1.0f..2.4f,
                            steps = 6
                        )
                    }
                }
            }

            // 7. VOZ NEURAL & LEITURA COM IA (EDGE TTS & GEMINI 3.5 IA)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Voz Neural & Leitura com IA",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Escolha o motor de leitura padrão (Edge TTS ou Gemini 3.5)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Motor Padrão Selector
                    Text("Motor Padrão do Aplicativo:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { settingsManager.setDefaultVoiceEngine("EDGE_TTS") }
                                .testTag("opt_default_engine_edge"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (defaultVoiceEngine == "EDGE_TTS")
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = if (defaultVoiceEngine == "EDGE_TTS") 2.dp else 1.dp,
                                color = if (defaultVoiceEngine == "EDGE_TTS") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = defaultVoiceEngine == "EDGE_TTS",
                                        onClick = { settingsManager.setDefaultVoiceEngine("EDGE_TTS") }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edge TTS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Padrão Inicial", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Rápido, gratuito e direto. Sem chaves de API.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { settingsManager.setDefaultVoiceEngine("GEMINI_TTS") }
                                .testTag("opt_default_engine_gemini"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (defaultVoiceEngine == "GEMINI_TTS")
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = if (defaultVoiceEngine == "GEMINI_TTS") 2.dp else 1.dp,
                                color = if (defaultVoiceEngine == "GEMINI_TTS") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = defaultVoiceEngine == "GEMINI_TTS",
                                        onClick = { settingsManager.setDefaultVoiceEngine("GEMINI_TTS") }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Gemini 3.5", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF8B5CF6).copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("IA Expressiva", fontSize = 9.sp, color = Color(0xFF8B5CF6), fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Entonação e riqueza de IA pelo Google AI Studio.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Seletor de Voz para o motor padrão
                    if (defaultVoiceEngine == "GEMINI_TTS") {
                        Text(
                            text = "Voz Padrão do Gemini 3.5:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val popularGeminiVoices = listOf("Puck", "Kore", "Aoede", "Charon")
                            popularGeminiVoices.forEach { vName ->
                                FilterChip(
                                    selected = defaultVoiceId == vName,
                                    onClick = { settingsManager.setDefaultVoiceId(vName) },
                                    label = { Text("Gemini $vName", fontSize = 11.sp) }
                                )
                            }
                        }

                        // Botão de Gerenciamento de Chaves de API Gemini
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Chaves de API do Google AI Studio",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    val activeKeys = apiKeys.count { it.isActive }
                                    Text(
                                        text = if (activeKeys > 0) "$activeKeys chave(s) configurada(s)" else "Nenhuma chave inserida (usando padrão)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (activeKeys > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { showGeminiKeysDialog = true },
                                    modifier = Modifier.testTag("btn_manage_gemini_keys_settings")
                                ) {
                                    Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Chaves API")
                                }
                            }
                        }
                    } else {
                        // Edge TTS Default Voices
                        Text("Voz Padrão do Edge TTS:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val defaultEdgeVoices = listOf(
                                "pt-BR-FranciscaNeural" to "Francisca",
                                "pt-BR-AntonioNeural" to "Antônio",
                                "pt-BR-ThalitaNeural" to "Thalita",
                                "pt-PT-DuarteNeural" to "Duarte"
                            )
                            defaultEdgeVoices.forEach { (vId, label) ->
                                FilterChip(
                                    selected = defaultVoiceId == vId,
                                    onClick = { settingsManager.setDefaultVoiceId(vId) },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    Text(
                        text = "💡 O Edge TTS é mantido como o motor padrão global. Você pode escolher o Gemini 3.5 aqui para ser o padrão ou alterar a voz de qualquer livro individualmente no menu de voz do leitor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )

                    // Velocidade Padrão
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Velocidade Padrão:", style = MaterialTheme.typography.bodySmall)
                            Text(String.format(Locale.getDefault(), "%.2fx", defaultVoiceSpeed), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = defaultVoiceSpeed,
                            onValueChange = { settingsManager.setDefaultVoiceSpeed(it) },
                            valueRange = 0.5f..2.5f,
                            steps = 7
                        )
                    }

                    // Tom Padrão
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tom da Voz Padrão:", style = MaterialTheme.typography.bodySmall)
                            Text(String.format(Locale.getDefault(), "%.2fx", defaultVoicePitch), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = defaultVoicePitch,
                            onValueChange = { settingsManager.setDefaultVoicePitch(it) },
                            valueRange = 0.8f..1.2f,
                            steps = 4
                        )
                    }
                }
            }

            // 7. INFORMAÇÕES DA MEMÓRIA & LEAKCANARY
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = if (memoryStats.heapUsagePercent >= 80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Informações de Memória & Diagnóstico",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Monitoramento contínuo de Heap e detecção de vazamentos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Heap Utilizado:", style = MaterialTheme.typography.bodySmall)
                                Text("${memoryStats.usedHeapMb} MB de ${memoryStats.maxHeapMb} MB (${memoryStats.heapUsagePercent}%)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("RAM Livre do Sistema:", style = MaterialTheme.typography.bodySmall)
                                Text("${memoryStats.systemAvailMb} MB de ${memoryStats.systemTotalMb} MB", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Status de Memória:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (memoryStats.isLowMemory) "Memória Crítica" else "Normal / Otimizado",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (memoryStats.isLowMemory) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.forceGarbageCollection() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Liberar Memória (GC)")
                        }
                        OutlinedButton(
                            onClick = { showMemoryDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ver Logs")
                        }
                    }
                }
            }

            // 8. RODAPÉ INFORMATIVO
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "VoxReader v2.1 • Edição Especial Audiolivro",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Compatível com Android Auto • MediaSession • Capa de Álbum na Tela de Bloqueio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    if (showBackupDialog) {
        BackupRestoreDialog(
            viewModel = viewModel,
            onDismiss = { showBackupDialog = false }
        )
    }

    if (showMemoryDialog) {
        MemoryDiagnosticsDialog(
            stats = memoryStats,
            logs = memoryLogs,
            onDismiss = { showMemoryDialog = false },
            onForceGc = { viewModel.forceGarbageCollection() },
            onClearLogs = { viewModel.clearMemoryLogs() }
        )
    }

    if (showGeminiKeysDialog) {
        GeminiApiKeyDialog(
            apiKeys = apiKeys,
            onDismiss = { showGeminiKeysDialog = false },
            onToggleKey = { viewModel.toggleGeminiApiKey(it) },
            onDeleteKey = { viewModel.deleteGeminiApiKey(it) },
            onAddKey = { key, label -> viewModel.addGeminiApiKey(key, label) },
            onTestKey = { apiKey -> viewModel.testGeminiApiKey(apiKey) }
        )
    }
}
