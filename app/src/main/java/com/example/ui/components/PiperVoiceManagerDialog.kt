package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tts.piper.PiperVoice
import com.example.tts.piper.PiperVoiceManager
import kotlinx.coroutines.launch

@Composable
fun PiperVoiceManagerDialog(
    voiceManager: PiperVoiceManager,
    selectedVoiceId: String,
    onSelectVoice: (String) -> Unit,
    onTestVoiceSample: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val installedVoiceIds by voiceManager.installedVoiceIds.collectAsStateWithLifecycle()
    val downloadProgress by voiceManager.downloadProgress.collectAsStateWithLifecycle()
    val allVoices = voiceManager.getAllVoices()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Português (Brasil)") }
    var showAddCustomDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val filteredVoices = allVoices.filter { voice ->
        val matchesSearch = voice.name.contains(searchQuery, ignoreCase = true) ||
                voice.languageDisplayName.contains(searchQuery, ignoreCase = true) ||
                voice.description.contains(searchQuery, ignoreCase = true)

        val matchesTab = when (selectedTab) {
            "Português (Brasil)" -> voice.languageCode == "pt_BR"
            "Instaladas (Offline)" -> installedVoiceIds.contains(voice.id)
            else -> true
        }

        matchesSearch && matchesTab
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.RecordVoiceOver,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Vozes Piper TTS (Local & Offline)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Modelos neurais que rodam 100% no seu aparelho celular. Sem internet, sem gastar dados e sem precisar de chaves de API.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Barra de Busca
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar voz, idioma ou qualidade...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_search_piper_voices")
                )

                // Filtros de Categoria
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Português (Brasil)", "Instaladas (Offline)", "Todas").forEach { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            label = { Text(tab, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                statusMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // Lista de Vozes
                if (filteredVoices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedTab == "Instaladas (Offline)")
                                "Nenhuma voz Piper instalada ainda.\nBaixe uma voz abaixo para usar offline!"
                            else "Nenhuma voz encontrada para a busca.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredVoices, key = { it.id }) { voice ->
                            val isInstalled = installedVoiceIds.contains(voice.id)
                            val isSelected = selectedVoiceId == voice.id
                            val progress = downloadProgress[voice.id]
                            val isDownloading = progress != null

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isInstalled) {
                                            onSelectVoice(voice.id)
                                        }
                                    }
                                    .testTag("piper_voice_card_${voice.id}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = if (isInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Piper ${voice.name}",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "(${voice.gender})",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = "${voice.languageDisplayName} • ${voice.quality} • ~${voice.sizeMb} MB",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        if (isInstalled && isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selecionada",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = voice.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (isDownloading) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Column {
                                            LinearProgressIndicator(
                                                progress = { progress ?: 0f },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Baixando modelo neural... ${((progress ?: 0f) * 100).toInt()}%",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isInstalled) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("✓ Offline Pronto", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    TextButton(
                                                        onClick = { onTestVoiceSample(voice.id) },
                                                        modifier = Modifier.height(28.dp)
                                                    ) {
                                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Ouvir", fontSize = 11.sp)
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        voiceManager.deleteVoice(voice.id)
                                                        statusMessage = "Voz ${voice.name} removida do aparelho."
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Excluir voz",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                                Button(
                                                    onClick = {
                                                        voiceManager.downloadVoice(
                                                            voice = voice,
                                                            onComplete = { success, msg ->
                                                                statusMessage = msg
                                                                if (success) {
                                                                    onSelectVoice(voice.id)
                                                                }
                                                            }
                                                        )
                                                    },
                                                    modifier = Modifier.height(32.dp),
                                                    contentPadding = ButtonDefaults.TextButtonContentPadding
                                                ) {
                                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Baixar (${voice.sizeMb.toInt()} MB)", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Botão para adicionar nova voz customizada
                OutlinedButton(
                    onClick = { showAddCustomDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Adicionar Nova Voz Piper (URL ou Modelo)")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Concluir")
            }
        }
    )

    if (showAddCustomDialog) {
        var customId by remember { mutableStateOf("") }
        var customName by remember { mutableStateOf("") }
        var customModelUrl by remember { mutableStateOf("") }
        var customLang by remember { mutableStateOf("Português (Brasil)") }

        AlertDialog(
            onDismissRequest = { showAddCustomDialog = false },
            title = { Text("Adicionar Nova Voz Piper") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Informe o identificador ou URL do modelo ONNX do repositório Hugging Face ou comunidade Piper.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Nome da Voz (ex: Letícia)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customId,
                        onValueChange = { customId = it },
                        label = { Text("ID do Modelo (ex: pt_BR-leticia-medium)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customModelUrl,
                        onValueChange = { customModelUrl = it },
                        label = { Text("URL do arquivo .onnx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customName.isNotBlank() && customModelUrl.isNotBlank()) {
                            val id = customId.ifBlank { "custom-${customName.lowercase().replace(" ", "_")}" }
                            val newVoice = PiperVoice(
                                id = id,
                                name = customName.trim(),
                                languageCode = "pt_BR",
                                languageDisplayName = customLang,
                                gender = "Neutro",
                                quality = "Personalizada",
                                sampleRate = 22050,
                                modelUrl = customModelUrl.trim(),
                                configUrl = customModelUrl.trim().removeSuffix(".onnx") + ".onnx.json",
                                sizeMb = 50f,
                                description = "Voz Piper personalizada adicionada pelo usuário.",
                                isCustom = true
                            )
                            voiceManager.addCustomVoice(newVoice)
                            showAddCustomDialog = false
                            statusMessage = "Voz ${newVoice.name} adicionada ao catálogo!"
                        }
                    },
                    enabled = customName.isNotBlank() && customModelUrl.isNotBlank()
                ) {
                    Text("Salvar Voz")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
