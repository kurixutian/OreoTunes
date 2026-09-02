package com.kurixutian.oreotunes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurixutian.oreotunes.ui.theme.Manrope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun ModernGlassScrollBar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    itemsList: List<String> = emptyList(),
    headerOffsetCount: Int = 0,
    thumbWidth: Dp = 5.dp,
    touchTargetWidth: Dp = 48.dp,
    thumbColor: Color = Color.White.copy(alpha = 0.70f),
    activeThumbColor: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.12f)
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var isHoldingThumb by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var lastTargetIndex by remember { mutableIntStateOf(-1) }
    var activePreviewLetter by remember { mutableStateOf("") }
    var touchYPx by remember { mutableFloatStateOf(0f) }

    val isScrolling = listState.isScrollInProgress

    LaunchedEffect(isScrolling, isHoldingThumb) {
        if (isScrolling || isHoldingThumb) {
            isVisible = true
        } else {
            delay(2000)
            if (!isHoldingThumb && !listState.isScrollInProgress) {
                isVisible = false
            }
        }
    }

    val totalItems = listState.layoutInfo.totalItemsCount
    val firstVisibleIndex = listState.firstVisibleItemIndex
    val firstVisibleOffset = listState.firstVisibleItemScrollOffset

    val thumbHeightDp = remember(barHeightPx, totalItems) {
        if (barHeightPx <= 0f || totalItems <= 0) 42.dp
        else {
            val fraction = (8f / totalItems.toFloat()).coerceIn(0.08f, 0.35f)
            with(density) { max(barHeightPx * fraction, 36.dp.toPx()).toDp() }
        }
    }

    val thumbOffsetDp = remember(barHeightPx, firstVisibleIndex, firstVisibleOffset, totalItems, thumbHeightDp) {
        if (barHeightPx <= 0f || totalItems <= 1) 0.dp
        else {
            val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
            val scrollableTrackPx = max(1f, barHeightPx - thumbHeightPx)
            val currentProgress = ((firstVisibleIndex.toFloat() + (firstVisibleOffset.toFloat() / 300f).coerceIn(0f, 0.99f)) / max(1f, (totalItems - 1).toFloat())).coerceIn(0f, 1f)
            with(density) { (currentProgress * scrollableTrackPx).toDp() }
        }
    }

    fun applyQuickScroll(y: Float) {
        if (barHeightPx <= 0f || totalItems <= 0) return
        val clampedY = y.coerceIn(0f, barHeightPx)
        touchYPx = clampedY
        val progress = (clampedY / barHeightPx).coerceIn(0f, 1f)
        val targetIndex = (progress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)

        val songItemIndex = targetIndex - headerOffsetCount
        if (itemsList.isNotEmpty() && songItemIndex in itemsList.indices) {
            val title = itemsList[songItemIndex].trim()
            activePreviewLetter = if (title.isNotEmpty()) title.take(1).uppercase() else "#"
        } else {
            activePreviewLetter = if (targetIndex < headerOffsetCount) "★" else ""
        }

        if (targetIndex != lastTargetIndex) {
            lastTargetIndex = targetIndex
            scrollJob?.cancel()
            scrollJob = coroutineScope.launch {
                try {
                    listState.scroll(MutatePriority.PreventUserInput) {
                        // Suppress other scroll interactions while thumb drag is dominant
                    }
                    listState.scrollToItem(targetIndex)
                } catch (_: Exception) {}
            }
        }
    }

    AnimatedVisibility(
        visible = (isVisible || isHoldingThumb) && totalItems > 3,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(touchTargetWidth)
                .fillMaxHeight()
                .onGloballyPositioned { coordinates ->
                    barHeightPx = coordinates.size.height.toFloat()
                }
                .pointerInput(totalItems, headerOffsetCount) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isHoldingThumb = true
                        isVisible = true
                        applyQuickScroll(down.position.y)
                        down.consume()

                        drag(down.id) { change ->
                            if (change.positionChange() != androidx.compose.ui.geometry.Offset.Zero) {
                                change.consume()
                                applyQuickScroll(change.position.y)
                            }
                        }

                        isHoldingThumb = false
                        lastTargetIndex = -1
                        activePreviewLetter = ""
                    }
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            // Floating scrub bubble
            if (isHoldingThumb && activePreviewLetter.isNotEmpty()) {
                val bubbleOffsetY = with(density) { (touchYPx - 24.dp.toPx()).coerceIn(0f, max(0f, barHeightPx - 50.dp.toPx())).toDp() }
                Box(
                    modifier = Modifier
                        .offset(y = bubbleOffsetY)
                        .align(Alignment.TopEnd)
                        .padding(end = 28.dp)
                        .size(46.dp)
                        .shadow(12.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFF1B2238).copy(alpha = 0.96f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activePreviewLetter,
                        fontFamily = Manrope,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }
            }

            // Track background bar
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .width(thumbWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(trackColor)
            )

            // Scrollbar thumb indicator (stays visible and responsive while dragging)
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .align(Alignment.TopEnd)
                    .offset(y = thumbOffsetDp)
                    .width(if (isHoldingThumb) thumbWidth + 2.dp else thumbWidth)
                    .height(thumbHeightDp)
                    .clip(CircleShape)
                    .background(if (isHoldingThumb) activeThumbColor else thumbColor)
                    .border(0.5.dp, Color.White.copy(alpha = 0.30f), CircleShape)
            )
        }
    }
}
