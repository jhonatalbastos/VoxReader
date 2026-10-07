package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.BookEntity
import com.example.model.ReadSegment
import com.example.model.VoiceCatalog
import com.example.tts.TtsEngineType
import java.io.File

/**
 * Fullscreen audio player modal sheet styled like a premium music player.
 * Features a dynamic, animated book cover with smooth transitions for new books
 * and tactile scale/elevation animation when playback is started or paused.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerSheet(
    book: BookEntity,
    isPlaying: Boolean,
    isBuffering: Boolean,
    activeEngine: TtsEngineType,
    currentChapterTitle: String,
    paragraphProgressText: String,
    progressFraction: Float,
    statusMessage: String?,
    recentSegments: List<ReadSegment> = emptyList(),
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onRewind: () -> Unit = {},
    onReplaySegment: (ReadSegment) -> Unit = {},
    onOpenReader: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("audio_player_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("btn_close_player_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Fechar player",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "REPRODUZINDO AUDIOLIVRO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Modo Player de Áudio",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onOpenVoiceSettings,
                    modifier = Modifier.testTag("btn_player_voice_settings")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Configurar voz",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // HERO BOOK COVER / ALBUM ARTWORK WITH SMOOTH TRANSITION ANIMATIONS
            // 1. Transition when book changes: AnimatedContent with fade + scale.
            // 2. Transition when started / paused: animateFloatAsState scale & elevation!
            // =========================================================================
            val coverScale by animateFloatAsState(
                targetValue = if (isPlaying) 1.0f else 0.88f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "coverScaleAnimation"
            )

            val coverElevation by animateDpAsState(
                targetValue = if (isPlaying) 20.dp else 4.dp,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "coverElevationAnimation"
            )

            val coverCornerRadius by animateDpAsState(
                targetValue = if (isPlaying) 24.dp else 18.dp,
                animationSpec = tween(durationMillis = 400),
                label = "coverCornerRadiusAnimation"
            )

            val coverDimAlpha by animateFloatAsState(
                targetValue = if (isPlaying) 0.0f else 0.15f,
                animationSpec = tween(durationMillis = 350),
                label = "coverDimAlphaAnimation"
            )

            // Pulsing Aura glow when playing
            val infiniteTransition = rememberInfiniteTransition(label = "playerAura")
            val auraGlowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.55f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "auraGlow"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 340.dp)
                    .aspectRatio(1f)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Ambient colorful glow behind the album cover (active when playing)
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(1f)
                            .scale(coverScale * 1.05f)
                            .blur(36.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .alpha(auraGlowAlpha)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(book.coverGradientEnd),
                                        Color(book.coverGradientStart),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                // Smooth Book Change Transition: AnimatedContent
                AnimatedContent(
                    targetState = book,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(500, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.84f, animationSpec = tween(500))) togetherWith
                                (fadeOut(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                                        scaleOut(targetScale = 1.08f, animationSpec = tween(400)))
                    },
                    label = "BookCoverCrossfade"
                ) { currentBook ->
                    val coverFile = currentBook.coverImagePath?.let { File(it) }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .aspectRatio(1f)
                            .scale(coverScale)
                            .shadow(elevation = coverElevation, shape = RoundedCornerShape(coverCornerRadius))
                            .clip(RoundedCornerShape(coverCornerRadius))
                            .testTag("audio_player_album_cover"),
                        shape = RoundedCornerShape(coverCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(currentBook.coverGradientStart),
                                            Color(currentBook.coverGradientEnd)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (coverFile != null && coverFile.exists()) {
                                AsyncImage(
                                    model = coverFile,
                                    contentDescription = currentBook.title,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoStories,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = currentBook.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentBook.author.ifBlank { "Autor" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Subtle dimming overlay when paused
                            if (!isPlaying && coverDimAlpha > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .background(Color.Black.copy(alpha = coverDimAlpha)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Pause,
                                            contentDescription = "Pausado",
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }

                            // Playing Equalizer Indicator in corner
                            if (isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "TOCANDO",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // =========================================================================
            // BOOK & CHAPTER METADATA
            // =========================================================================
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("audio_player_title")
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = book.author.ifBlank { "Autor Desconhecido" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Chapter & Engine Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = currentChapterTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                val voiceLabel = when (book.voiceEngine) {
                    "GEMINI_TTS" -> "Gemini ${book.voiceId}"
                    "PIPER_TTS" -> {
                        val piperVoice = com.example.tts.piper.PiperVoiceCatalog.DEFAULT_BRAZILIAN_VOICES.find { it.id == book.voiceId }
                        "Piper ${piperVoice?.name ?: book.voiceId.takeLast(10)}"
                    }
                    else -> VoiceCatalog.findById(book.voiceId).displayName
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$voiceLabel • ${String.format("%.1fx", book.voiceSpeed)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // =========================================================================
            // PROGRESS BAR & STATUS
            // =========================================================================
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = paragraphProgressText.ifBlank { "Início da leitura" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(progressFraction * 100).toInt()}% concluído",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // =========================================================================
            // TRANSPORT PLAYBACK CONTROLS
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Skip Previous Paragraph
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_player_skip_prev")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Parágrafo anterior",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Rewind / Re-listen to confusing sentence
                IconButton(
                    onClick = onRewind,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .testTag("btn_player_rewind")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Voltar ou reouvir frase",
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Play / Pause Giant Button
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(76.dp)
                        .testTag("btn_player_play_pause"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar leitura" else "Iniciar leitura",
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }

                // Skip Next Paragraph
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_player_skip_next")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Próximo parágrafo",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Rewind quick action label
            Text(
                text = "Toque em ↺ para voltar ou reouvir a frase atual",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Log of the last 10 read segments
            if (recentSegments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                var showHistoryExpanded by remember { mutableStateOf(false) }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("recent_segments_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showHistoryExpanded = !showHistoryExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Últimos ${recentSegments.size} Trechos Lidos",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (showHistoryExpanded) "Ocultar" else "Ver Todos",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (showHistoryExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                recentSegments.forEachIndexed { _, segment ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 2.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onReplaySegment(segment)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Replay,
                                                    contentDescription = "Reouvir este trecho",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = segment.shortSnippet,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Parágrafo ${segment.paragraphIndex + 1} • Toque para reouvir",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // =========================================================================
            // ACTION BUTTONS (Open full reader text)
            // =========================================================================
            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onOpenReader()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_player_open_reader"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Acompanhar Texto no Leitor",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
