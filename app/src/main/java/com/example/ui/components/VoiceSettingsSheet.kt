package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BookEntity
import com.example.model.EdgeVoice
import com.example.model.VoiceCatalog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsSheet(
    book: BookEntity,
    onDismiss: () -> Unit,
    onSaveSettings: (engine: String, voiceId: String, speed: Float, pitch: Float) -> Unit,
    onTestVoice: (engine: String, voiceId: String, speed: Float, pitch: Float) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedVoiceId by remember {
        val initial = if (VoiceCatalog.VOICES.any { it.id == book.voiceId }) {
            book.voiceId
        } else {
            "pt-BR-FranciscaNeural"
        }
        mutableStateOf(initial)
    }

    var currentSpeed by remember { mutableFloatStateOf(book.voiceSpeed) }
    var currentPitch by remember { mutableFloatStateOf(book.voicePitch) }
    var selectedLanguageFilter by remember { mutableStateOf("Português (BR)") }
    var selectedGenderFilter by remember { mutableStateOf("Todos") }
    var isTestingVoice by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val languages = listOf("Português (BR)", "Português (PT)", "English", "Español")

    val filteredVoices = VoiceCatalog.VOICES.filter { voice ->
        val matchesLang = when (selectedLanguageFilter) {
            "Português (BR)" -> voice.locale.startsWith("pt-BR")
            "Português (PT)" -> voice.locale.startsWith("pt-PT")
            "English" -> voice.locale.startsWith("en")
            "Español" -> voice.locale.startsWith("es")
            else -> true
        }
        val matchesGender = when (selectedGenderFilter) {
            "Feminino" -> voice.gender == "Feminino"
            "Masculino" -> voice.gender == "Masculino"
            else -> true
        }
        matchesLang && matchesGender
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
                        .size(42.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Voz & Narração Neural",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Neural HD",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = "Microsoft Edge Neural • ${book.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Language Tabs
            Text(
                text = "Idioma e Região:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                languages.forEach { lang ->
                    FilterChip(
                        selected = selectedLanguageFilter == lang,
                        onClick = {
                            selectedLanguageFilter = lang
                            // Auto select first voice of this language if current doesn't match
                            val firstInLang = VoiceCatalog.VOICES.firstOrNull {
                                when (lang) {
                                    "Português (BR)" -> it.locale.startsWith("pt-BR")
                                    "Português (PT)" -> it.locale.startsWith("pt-PT")
                                    "English" -> it.locale.startsWith("en")
                                    "Español" -> it.locale.startsWith("es")
                                    else -> true
                                }
                            }
                            if (firstInLang != null && !VoiceCatalog.VOICES.filter {
                                when (lang) {
                                    "Português (BR)" -> it.locale.startsWith("pt-BR")
                                    "Português (PT)" -> it.locale.startsWith("pt-PT")
                                    "English" -> it.locale.startsWith("en")
                                    "Español" -> it.locale.startsWith("es")
                                    else -> true
                                }
                            }.any { it.id == selectedVoiceId }) {
                                selectedVoiceId = firstInLang.id
                            }
                        },
                        label = { Text(lang, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Gender Filter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gênero:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf("Todos", "Feminino", "Masculino").forEach { gender ->
                    FilterChip(
                        selected = selectedGenderFilter == gender,
                        onClick = { selectedGenderFilter = gender },
                        label = { Text(gender, fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Voice Cards Carousel
            Text(
                text = "Selecione o Narrador:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filteredVoices, key = { it.id }) { voice ->
                    val isSelected = selectedVoiceId == voice.id
                    Surface(
                        selected = isSelected,
                        onClick = { selectedVoiceId = voice.id },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.width(150.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = voice.displayName.substringBefore(" ("),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            Text(
                                text = voice.displayName.substringAfter(" (", "").substringBefore(")"),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = voice.description,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                lineHeight = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Highlight Box: Natural Expressivity
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎙️ Áudio neural de alta fidelidade com carregamento instantâneo e entonação natural para diálogos e leitura fluida.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Speed Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Velocidade de Leitura",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = String.format("%.2fx", currentSpeed),
                    style = MaterialTheme.typography.labelLarge,
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

            // Pitch Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tom da Voz (Pitch)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val pitchPercent = ((currentPitch - 1.0f) * 100).toInt()
                    Text(
                        text = if (pitchPercent == 0) "Padrão" else if (pitchPercent > 0) "+$pitchPercent%" else "$pitchPercent%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (currentPitch != 1.0f) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Redefinir",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable { currentPitch = 1.0f }
                        )
                    }
                }
            }
            Slider(
                value = currentPitch,
                onValueChange = { currentPitch = it },
                valueRange = 0.5f..1.5f,
                steps = 19,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("slider_voice_pitch")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Testing voice indicator
            if (isTestingVoice) {
                Text(
                    text = "Sintetizando amostra via Edge TTS...",
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
                        onTestVoice("EDGE_TTS", selectedVoiceId, currentSpeed, currentPitch)
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
                        onSaveSettings("EDGE_TTS", selectedVoiceId, currentSpeed, currentPitch)
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
