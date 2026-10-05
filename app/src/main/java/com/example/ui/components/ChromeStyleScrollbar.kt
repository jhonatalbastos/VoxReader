package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChromeStyleScrollbar(
    listState: LazyListState,
    totalParagraphs: Int,
    currentParagraphIndex: Int,
    isReadingAloud: Boolean,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var isHoveredOrActive by remember { mutableStateOf(false) }

    // Keep visible while scrolling or dragging, fade out 2s after inactivity
    val isScrolling = listState.isScrollInProgress
    LaunchedEffect(isScrolling, isDragging) {
        if (isScrolling || isDragging) {
            isHoveredOrActive = true
        } else {
            delay(2200)
            isHoveredOrActive = false
        }
    }

    val alphaAnim by animateFloatAsState(
        targetValue = if (isHoveredOrActive) 1f else 0.35f,
        animationSpec = tween(350),
        label = "ScrollbarAlpha"
    )

    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount.coerceAtLeast(1)
    val visibleItems = layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val firstVisible = listState.firstVisibleItemIndex

    // Accurate chapter read percentage
    val readProgressFraction by remember(firstVisible, isReadingAloud, currentParagraphIndex, totalParagraphs) {
        derivedStateOf {
            if (isReadingAloud && currentParagraphIndex >= 0 && totalParagraphs > 0) {
                ((currentParagraphIndex + 1).toFloat() / totalParagraphs).coerceIn(0f, 1f)
            } else {
                (firstVisible.toFloat() / (totalItems - visibleItems).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
    }
    val readPercentage = (readProgressFraction * 100).toInt().coerceIn(0, 100)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(54.dp)
            .testTag("chrome_scrollbar_container")
    ) {
        val totalHeight = maxHeight
        val thumbHeight = ((visibleItems.toFloat() / totalItems) * totalHeight.value).dp
            .coerceIn(48.dp, (totalHeight.value * 0.4f).dp)
        val availableTravel = totalHeight - thumbHeight
        val thumbOffset = availableTravel * readProgressFraction

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = thumbOffset)
                .alpha(alphaAnim)
                .pointerInput(totalItems) {
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            isDragging = true
                            val deltaFraction = dragAmount / totalHeight.toPx()
                            val newFraction = (readProgressFraction + deltaFraction).coerceIn(0f, 1f)
                            val targetIndex = (newFraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                            coroutineScope.launch {
                                listState.scrollToItem(targetIndex)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                // Floating percentage badge like Chrome Android / modern reader
                AnimatedVisibility(
                    visible = isScrolling || isDragging,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(300))
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = "$readPercentage%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                // Chrome-style vertical pill thumb
                Box(
                    modifier = Modifier
                        .size(
                            width = if (isDragging) 8.dp else 5.dp,
                            height = thumbHeight
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isDragging) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                )
            }
        }
    }
}
