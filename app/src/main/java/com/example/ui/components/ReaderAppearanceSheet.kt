package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ReaderFont
import com.example.model.ReaderSettings
import com.example.model.ReaderTheme
import com.example.ui.theme.AmoledBackground
import com.example.ui.theme.DarkReaderBackground
import com.example.ui.theme.SepiaBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderAppearanceSheet(
    settings: ReaderSettings,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("appearance_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Diagramação & Aparência",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Theme Options
            Text(
                text = "Tema do Papel / Fundo",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeOptionButton(
                    name = "Papel Livro",
                    bgColor = Color(0xFFF7F4EA),
                    textColor = Color(0xFF2C241B),
                    isSelected = settings.theme == ReaderTheme.BOOK_PAPER,
                    onClick = { onSettingsChanged(settings.copy(theme = ReaderTheme.BOOK_PAPER)) }
                )
                ThemeOptionButton(
                    name = "Sépia",
                    bgColor = SepiaBackground,
                    textColor = Color(0xFF433422),
                    isSelected = settings.theme == ReaderTheme.SEPIA,
                    onClick = { onSettingsChanged(settings.copy(theme = ReaderTheme.SEPIA)) }
                )
                ThemeOptionButton(
                    name = "Branco",
                    bgColor = Color(0xFFFFFFFF),
                    textColor = Color(0xFF1E293B),
                    isSelected = settings.theme == ReaderTheme.LIGHT,
                    onClick = { onSettingsChanged(settings.copy(theme = ReaderTheme.LIGHT)) }
                )
                ThemeOptionButton(
                    name = "Noturno",
                    bgColor = DarkReaderBackground,
                    textColor = Color(0xFFE2E8F0),
                    isSelected = settings.theme == ReaderTheme.DARK,
                    onClick = { onSettingsChanged(settings.copy(theme = ReaderTheme.DARK)) }
                )
                ThemeOptionButton(
                    name = "AMOLED",
                    bgColor = AmoledBackground,
                    textColor = Color(0xFFE0E0E0),
                    isSelected = settings.theme == ReaderTheme.AMOLED,
                    onClick = { onSettingsChanged(settings.copy(theme = ReaderTheme.AMOLED)) }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            // Book Diagramming Options: Justified text & First line indent
            Text(
                text = "Diagramação de Livro Impresso",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (settings.isJustified) Icons.Default.FormatAlignJustify else Icons.Default.FormatAlignLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Texto Justificado", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Alinha o texto uniformemente nas duas margens", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(
                    checked = settings.isJustified,
                    onCheckedChange = { onSettingsChanged(settings.copy(isJustified = it)) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FormatIndentIncrease,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Recuo de Primeira Linha", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Aplica recuo tradicional no início de cada parágrafo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(
                    checked = settings.hasFirstLineIndent,
                    onCheckedChange = { onSettingsChanged(settings.copy(hasFirstLineIndent = it)) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Font Family
            Text(
                text = "Tipografia",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FontOptionButton(
                    label = "Serifada",
                    fontFamily = FontFamily.Serif,
                    isSelected = settings.font == ReaderFont.SERIF,
                    onClick = { onSettingsChanged(settings.copy(font = ReaderFont.SERIF)) },
                    modifier = Modifier.weight(1f)
                )
                FontOptionButton(
                    label = "Sem Serifa",
                    fontFamily = FontFamily.SansSerif,
                    isSelected = settings.font == ReaderFont.SANS,
                    onClick = { onSettingsChanged(settings.copy(font = ReaderFont.SANS)) },
                    modifier = Modifier.weight(1f)
                )
                FontOptionButton(
                    label = "Mono",
                    fontFamily = FontFamily.Monospace,
                    isSelected = settings.font == ReaderFont.MONO,
                    onClick = { onSettingsChanged(settings.copy(font = ReaderFont.MONO)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Font Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tamanho da Fonte",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${settings.fontSizeSp.toInt()} sp",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val newSize = (settings.fontSizeSp - 1f).coerceAtLeast(12f)
                        onSettingsChanged(settings.copy(fontSizeSp = newSize))
                    }
                ) {
                    Text("A-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = settings.fontSizeSp,
                    onValueChange = { onSettingsChanged(settings.copy(fontSizeSp = it)) },
                    valueRange = 12f..32f,
                    steps = 19,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val newSize = (settings.fontSizeSp + 1f).coerceAtMost(32f)
                        onSettingsChanged(settings.copy(fontSizeSp = newSize))
                    }
                ) {
                    Text("A+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionButton(
    name: String,
    bgColor: Color,
    textColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(bgColor)
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Aa",
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            fontSize = 10.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun FontOptionButton(
    label: String,
    fontFamily: FontFamily,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Aa",
                fontFamily = fontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
