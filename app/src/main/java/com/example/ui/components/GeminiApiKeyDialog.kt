package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GeminiApiKeyEntity
import kotlinx.coroutines.launch

@Composable
fun GeminiApiKeyDialog(
    apiKeys: List<GeminiApiKeyEntity>,
    onAddKey: (apiKey: String, label: String) -> Unit,
    onToggleKey: (key: GeminiApiKeyEntity) -> Unit,
    onDeleteKey: (keyId: Long) -> Unit,
    onTestKey: suspend (apiKey: String) -> Result<String>,
    onDismiss: () -> Unit
) {
    var newKeyText by remember { mutableStateOf("") }
    var newKeyLabel by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }
    var isTestingNewKey by remember { mutableStateOf(false) }
    var testingKeyId by remember { mutableStateOf<Long?>(null) }
    var testResultFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.VpnKey,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Chaves API Google AI Studio",
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
                    text = "Adicione chaves de API do Google AI Studio para leitura expressiva com vozes Gemini (Puck, Charon, Kore, etc.). O app suporta teste instantâneo de conexão e revezamento automático (Round-Robin).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                testResultFeedback?.let { (success, message) ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                if (isAdding) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Nova Chave de API",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = newKeyText,
                                onValueChange = { newKeyText = it },
                                label = { Text("Chave (ex: AIzaSy...)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_gemini_api_key")
                            )

                            OutlinedTextField(
                                value = newKeyLabel,
                                onValueChange = { newKeyLabel = it },
                                label = { Text("Identificação opcional (ex: Chave Principal)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (newKeyText.isNotBlank()) {
                                            isTestingNewKey = true
                                            testResultFeedback = null
                                            coroutineScope.launch {
                                                val res = onTestKey(newKeyText.trim())
                                                isTestingNewKey = false
                                                testResultFeedback = if (res.isSuccess) {
                                                    Pair(true, "✓ ${res.getOrNull()}")
                                                } else {
                                                    Pair(false, "✗ ${res.exceptionOrNull()?.localizedMessage ?: "Erro na validação"}")
                                                }
                                            }
                                        }
                                    },
                                    enabled = newKeyText.isNotBlank() && !isTestingNewKey,
                                    modifier = Modifier.testTag("btn_test_new_gemini_key")
                                ) {
                                    if (isTestingNewKey) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Testando...", fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Testar", fontSize = 12.sp)
                                    }
                                }

                                Row {
                                    TextButton(onClick = { isAdding = false }) {
                                        Text("Cancelar")
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = {
                                            if (newKeyText.isNotBlank()) {
                                                val label = newKeyLabel.ifBlank { "Chave ${apiKeys.size + 1}" }
                                                onAddKey(newKeyText.trim(), label)
                                                newKeyText = ""
                                                newKeyLabel = ""
                                                isAdding = false
                                            }
                                        },
                                        enabled = newKeyText.isNotBlank(),
                                        modifier = Modifier.testTag("btn_save_gemini_key")
                                    ) {
                                        Text("Salvar")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = { isAdding = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_add_gemini_key"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Adicionar Chave de API")
                    }
                }

                if (apiKeys.isEmpty()) {
                    Text(
                        text = "Nenhuma chave personalizada cadastrada. A chave padrão do ambiente será utilizada se configurada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(apiKeys, key = { it.id }) { key ->
                            val isTesting = testingKeyId == key.id
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (key.isActive)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = if (key.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = key.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${key.apiKey.take(8)}...${key.apiKey.takeLast(4)}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Botão Testar Conexão da Chave
                                    IconButton(
                                        onClick = {
                                            testingKeyId = key.id
                                            testResultFeedback = null
                                            coroutineScope.launch {
                                                val res = onTestKey(key.apiKey)
                                                testingKeyId = null
                                                testResultFeedback = if (res.isSuccess) {
                                                    Pair(true, "✓ ${key.label}: ${res.getOrNull()}")
                                                } else {
                                                    Pair(false, "✗ ${key.label}: ${res.exceptionOrNull()?.localizedMessage ?: "Erro na validação"}")
                                                }
                                            }
                                        },
                                        enabled = !isTesting,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        if (isTesting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Testar chave",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Switch(
                                        checked = key.isActive,
                                        onCheckedChange = { onToggleKey(key.copy(isActive = it)) },
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { onDeleteKey(key.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Excluir chave",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}
