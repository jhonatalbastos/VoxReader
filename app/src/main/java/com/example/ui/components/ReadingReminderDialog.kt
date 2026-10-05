package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReadingReminderDialog(
    onDismiss: () -> Unit,
    onSaveReminder: (Int) -> Unit
) {
    var selectedMinutes by remember { mutableStateOf(30) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lembrete de Leitura") },
        text = {
            Column {
                Text("Notificar após quantos minutos de leitura diária?")
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = selectedMinutes.toFloat(),
                    onValueChange = { selectedMinutes = it.toInt() },
                    valueRange = 10f..120f,
                    steps = 10
                )
                Text(
                    text = "$selectedMinutes minutos",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSaveReminder(selectedMinutes)
                onDismiss()
            }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
