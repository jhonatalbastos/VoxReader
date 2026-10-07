package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BookEntity
import com.example.model.VoiceCatalog
import com.example.tts.GeminiVoiceCatalog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsSheet(
    book: BookEntity,
    onDismiss: () -> Unit,
    onSaveSettings: (engine: String, voiceId: String, speed: Float, pitch: Float) -> Unit,
    onTestVoice: (engine: String, voiceId: String, speed: Float, pitch: Float) -> Unit,
    onManageGeminiKeys: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val piperVoiceManager = remember { com.example.tts.piper.PiperVoiceManager.getInstance(context) }
    val installedVoiceIds by piperVoiceManager.installedVoiceIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val downloadProgress by piperVoiceManager.downloadProgress.collectAsStateWithLifecycle(initialValue = emptyMap())

    var showPiperDialogFromSheet by remember { mutableStateOf(false) }

    var selectedEngine by remember {
        mutableStateOf(
            when (book.voiceEngine) {
                "GEMINI_TTS" -> "GEMINI_TTS"
                "PIPER_TTS" -> "PIPER_TTS"
                else -> "EDGE_TTS"
            }
        )
    }
    var selectedVoiceId by remember {
        val initialVoice = when (selectedEngine) {
            "GEMINI_TTS" -> {
                if (GeminiVoiceCatalog.VOICES.any { it.name == book.voiceId }) book.voiceId else "Puck"
            }
            "PIPER_TTS" -> {
                val allVoices = piperVoiceManager.getAllVoices()
                if (allVoices.any { it.id == book.voiceId }) book.voiceId else (piperVoiceManager.installedVoiceIds.value.firstOrNull() ?: com.example.tts.piper.PiperVoiceCatalog.DEFAULT_VOICE.id)
            }
            else -> {
                if (VoiceCatalog.VOICES.any { it.id == book.voiceId }) book.voiceId else "pt-BR-FranciscaNeural"
            }
        }
        mutableStateOf(initialVoice)
    }
    var currentSpeed by remember { mutableFloatStateOf(book.voiceSpeed) }
    var currentPitch by remember { mutableFloatStateOf(book.voicePitch) }
    var selectedLanguageFilter by remember { mutableStateOf("Português (BR)") }
    var isTestingVoice by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val languages = listOf("Português (BR)", "Português (PT)", "English", "Español")

    val filteredEdgeVoices = VoiceCatalog.VOICES.filter {
        when (selectedLanguageFilter) {
            "Português (BR)" -> it.locale.startsWith("pt-BR")
            "Português (PT)" -> it.locale.startsWith("pt-PT")
            "English" -> it.locale.startsWith("en")
            "Español" -> it.locale.startsWith("es")
            else -> true
        }
    }

    if (showPiperDialogFromSheet) {
        PiperVoiceManagerDialog(
            voiceManager = piperVoiceManager,
            selectedVoiceId = selectedVoiceId,
            onSelectVoice = { voiceId ->
                selectedVoiceId = voiceId
                showPiperDialogFromSheet = false
            },
            onTestVoiceSample = { voiceId ->
                onTestVoice("PIPER_TTS", voiceId, currentSpeed, currentPitch)
            },
            onDismiss = { showPiperDialogFromSheet = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("voice_settings_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Voz & Narração Neural",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (selectedEngine) {
                            "GEMINI_TTS" -> "Google Gemini 3.5 • ${book.title}"
                            "PIPER_TTS" -> "Piper TTS (Local & Offline) • ${book.title}"
                            else -> "Microsoft Edge TTS • ${book.title}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Engine Selector: Edge TTS vs Gemini 3.5 vs Piper TTS
            Text(
                text = "Motor de Leitura em Voz Alta:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedEngine == "EDGE_TTS",
                    onClick = {
                        selectedEngine = "EDGE_TTS"
                        if (!VoiceCatalog.VOICES.any { it.id == selectedVoiceId }) {
                            selectedVoiceId = "pt-BR-FranciscaNeural"
                        }
                    },
                    label = { Text("Edge TTS", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedEngine == "GEMINI_TTS",
                    onClick = {
                        selectedEngine = "GEMINI_TTS"
                        if (!GeminiVoiceCatalog.VOICES.any { it.name == selectedVoiceId }) {
                            selectedVoiceId = "Puck"
                        }
                    },
                    label = { Text("Gemini 3.5 (IA)", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedEngine == "PIPER_TTS",
                    onClick = {
                        selectedEngine = "PIPER_TTS"
                        val installed = piperVoiceManager.installedVoiceIds.value
                        if (installed.isNotEmpty() && !installed.contains(selectedVoiceId)) {
                            selectedVoiceId = installed.first()
                        } else if (!piperVoiceManager.getAllVoices().any { it.id == selectedVoiceId }) {
                            selectedVoiceId = com.example.tts.piper.PiperVoiceCatalog.DEFAULT_VOICE.id
                        }
                    },
                    label = { Text("Piper TTS (Local)", fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedEngine == "PIPER_TTS") {
                val availablePiperVoices = (com.example.tts.piper.PiperVoiceCatalog.DEFAULT_BRAZILIAN_VOICES + piperVoiceManager.getAllVoices()).distinctBy { it.id }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vozes Piper (Local/Offline):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = { showPiperDialogFromSheet = true },
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Gerenciar Catálogo", fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(availablePiperVoices, key = { it.id }) { voice ->
                        val isSelected = selectedVoiceId == voice.id
                        val isInstalled = installedVoiceIds.contains(voice.id)
                        val progress = downloadProgress[voice.id]
                        val isDownloading = progress != null

                        Surface(
                            selected = isSelected,
                            onClick = { selectedVoiceId = voice.id },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.width(135.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = voice.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = voice.quality,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                if (isDownloading) {
                                    LinearProgressIndicator(
                                        progress = { progress ?: 0f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp)
                                    )
                                    Text(
                                        text = "${((progress ?: 0f) * 100).toInt()}%",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else if (isInstalled) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("✓ Offline", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            selectedVoiceId = voice.id
                                            piperVoiceManager.downloadVoice(voice)
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("⬇ Baixar", fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (selectedEngine == "GEMINI_TTS") {
                // Card de atalho para gerenciamento de chaves
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onManageGeminiKeys() },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Chaves de API Google AI Studio",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Toque para adicionar, testar ou alternar chaves",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onManageGeminiKeys,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Gerenciar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Gemini Voices Catalog
                Text(
                    text = "Vozes com IA Gemini 3.5 (Google AI Studio):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(GeminiVoiceCatalog.VOICES, key = { it.name }) { voice ->
                        val isSelected = selectedVoiceId == voice.name
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedVoiceId = voice.name }
                                .testTag("gemini_voice_item_${voice.name}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Gemini ${voice.name}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(${voice.gender})",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = voice.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selecionada",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Edge TTS Language Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    languages.forEach { lang ->
                        FilterChip(
                            selected = selectedLanguageFilter == lang,
                            onClick = { selectedLanguageFilter = lang },
                            label = { Text(lang, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Voice List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredEdgeVoices, key = { it.id }) { voice ->
                        val isSelected = selectedVoiceId == voice.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedVoiceId = voice.id }
                                .testTag("voice_item_${voice.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = voice.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(${voice.gender})",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = voice.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selecionada",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            // Speed Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Velocidade de Leitura",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = String.format("%.2fx", currentSpeed),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = currentSpeed,
                onValueChange = { currentSpeed = it },
                valueRange = 0.5f..2.5f,
                steps = 19,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("slider_voice_speed")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Testing voice indicator
            if (isTestingVoice) {
                Text(
                    text = when (selectedEngine) {
                        "GEMINI_TTS" -> "Gerando áudio com a voz selecionada via Google Gemini..."
                        "PIPER_TTS" -> "Gerando áudio com a voz local via Piper TTS..."
                        else -> "Gerando áudio com a voz selecionada via Edge TTS..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        isTestingVoice = true
                        onTestVoice(selectedEngine, selectedVoiceId, currentSpeed, currentPitch)
                        coroutineScope.launch {
                            delay(3000)
                            isTestingVoice = false
                        }
                    },
                    enabled = !isTestingVoice,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_test_voice")
                ) {
                    if (isTestingVoice) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gerando...")
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ouvir Teste")
                    }
                }

                Button(
                    onClick = {
                        onSaveSettings(selectedEngine, selectedVoiceId, currentSpeed, currentPitch)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_save_voice_settings")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salvar Voz")
                }
            }
        }
    }
}
