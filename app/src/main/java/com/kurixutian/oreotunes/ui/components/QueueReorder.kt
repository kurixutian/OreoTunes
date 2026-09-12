package com.kurixutian.oreotunes.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurixutian.oreotunes.domain.model.Song
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueReorder(
    songsQueue: List<Song>,
    currentSongId: Long,
    onSelectSong: (Song) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveFromQueue: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    /*
     * Local visual queue.
     *
     * The currently playing song is intentionally excluded because
     * it remains pinned above the upcoming queue.
     */
    val reorderQueue = remember(currentSongId) {
        mutableStateListOf<Song>().apply {
            addAll(
                songsQueue.filter { it.id != currentSongId }
            )
        }
    }

    var draggedSongId by remember {
        mutableStateOf<Long?>(null)
    }

    var dragOffsetY by remember {
        mutableFloatStateOf(0f)
    }

    var draggedItemHeightPx by remember {
        mutableFloatStateOf(0f)
    }

    var dragStartIndex by remember {
        mutableIntStateOf(-1)
    }

    /*
     * Keep the visual queue synchronized with the real queue.
     *
     * IMPORTANT:
     * Never replace the local list while a drag is active.
     * This prevents the queue from snapping/resetting during reordering.
     */
    LaunchedEffect(songsQueue, currentSongId, draggedSongId) {
        if (draggedSongId == null) {
            val latestUpcoming =
                songsQueue.filter { it.id != currentSongId }

            if (
                reorderQueue.map { it.id } !=
                latestUpcoming.map { it.id }
            ) {
                reorderQueue.clear()
                reorderQueue.addAll(latestUpcoming)
            }
        }
    }

    val queueListState = rememberLazyListState()

    LazyColumn(
        state = queueListState,
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = draggedSongId == null,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = reorderQueue,
            key = { _, item -> item.id }
        ) { _, qSong ->

            val isDragging = draggedSongId == qSong.id

            val dragScale by animateFloatAsState(
                targetValue = if (isDragging) 1.035f else 1f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 450f
                ),
                label = "queueDragScale"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItemPlacement()
                    .graphicsLayer {
                        translationY =
                            if (isDragging) dragOffsetY else 0f

                        scaleX = dragScale
                        scaleY = dragScale
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isDragging) {
                            Color.White.copy(alpha = 0.16f)
                        } else {
                            Color.White.copy(alpha = 0.045f)
                        }
                    )
                    .onGloballyPositioned { coordinates ->
                        if (isDragging) {
                            draggedItemHeightPx =
                                coordinates.size.height.toFloat()
                        }
                    }
                    .pointerInput(qSong.id) {
                        detectDragGesturesAfterLongPress(

                            onDragStart = {
                                val startIndex =
                                    reorderQueue.indexOfFirst {
                                        it.id == qSong.id
                                    }

                                if (startIndex >= 0) {
                                    dragStartIndex = startIndex
                                    draggedSongId = qSong.id
                                    dragOffsetY = 0f
                                }
                            },

                            onDragCancel = {
                                dragOffsetY = 0f
                                draggedSongId = null
                                draggedItemHeightPx = 0f
                                dragStartIndex = -1
                            },

                            onDragEnd = {
                                if (draggedSongId == qSong.id) {

                                    val finalIndex =
                                        reorderQueue.indexOfFirst {
                                            it.id == qSong.id
                                        }

                                    val startIndex = dragStartIndex

                                    /*
                                     * Reset gesture state BEFORE committing.
                                     *
                                     * This allows another drag to begin immediately.
                                     */
                                    dragOffsetY = 0f
                                    draggedSongId = null
                                    draggedItemHeightPx = 0f
                                    dragStartIndex = -1

                                    /*
                                     * The callback uses UPCOMING queue indices.
                                     * The currently playing song is not included.
                                     */
                                    if (
                                        startIndex >= 0 &&
                                        finalIndex >= 0 &&
                                        startIndex != finalIndex
                                    ) {
                                        onMoveQueueItem(
                                            startIndex,
                                            finalIndex
                                        )
                                    }
                                } else {
                                    dragOffsetY = 0f
                                    draggedSongId = null
                                    draggedItemHeightPx = 0f
                                    dragStartIndex = -1
                                }
                            },

                            onDrag = { change, dragAmount ->
                                change.consume()

                                if (draggedSongId != qSong.id) {
                                    return@detectDragGesturesAfterLongPress
                                }

                                dragOffsetY += dragAmount.y

                                val rowPitch =
                                    if (draggedItemHeightPx > 0f) {
                                        draggedItemHeightPx +
                                            with(density) {
                                                8.dp.toPx()
                                            }
                                    } else {
                                        with(density) {
                                            68.dp.toPx()
                                        }
                                    }

                                /*
                                 * MOVE UP
                                 */
                                while (
                                    dragOffsetY <= -rowPitch / 2f
                                ) {
                                    val currentIndex =
                                        reorderQueue.indexOfFirst {
                                            it.id == qSong.id
                                        }

                                    if (currentIndex <= 0) {
                                        dragOffsetY =
                                            max(
                                                -rowPitch / 2f,
                                                dragOffsetY
                                            )
                                        break
                                    }

                                    val targetIndex =
                                        currentIndex - 1

                                    val movedSong =
                                        reorderQueue.removeAt(
                                            currentIndex
                                        )

                                    reorderQueue.add(
                                        targetIndex,
                                        movedSong
                                    )

                                    dragOffsetY += rowPitch
                                }

                                /*
                                 * MOVE DOWN
                                 */
                                while (
                                    dragOffsetY >= rowPitch / 2f
                                ) {
                                    val currentIndex =
                                        reorderQueue.indexOfFirst {
                                            it.id == qSong.id
                                        }

                                    if (
                                        currentIndex < 0 ||
                                        currentIndex >=
                                        reorderQueue.lastIndex
                                    ) {
                                        dragOffsetY =
                                            minOf(
                                                rowPitch / 2f,
                                                dragOffsetY
                                            )
                                        break
                                    }

                                    val targetIndex =
                                        currentIndex + 1

                                    val movedSong =
                                        reorderQueue.removeAt(
                                            currentIndex
                                        )

                                    reorderQueue.add(
                                        targetIndex,
                                        movedSong
                                    )

                                    dragOffsetY -= rowPitch
                                }
                            }
                        )
                    }
                    .clickable {
                        if (!isDragging) {
                            onSelectSong(qSong)
                        }
                    }
                    .padding(
                        start = 8.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                        end = 4.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkThumbnail(
                    model = qSong.albumArtUri,
                    contentDescription = qSong.title,
                    shape = RoundedCornerShape(9.dp),
                    targetSizeDp = 44.dp,
                    modifier = Modifier.size(44.dp)
                )

                Spacer(
                    modifier = Modifier.width(12.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = qSong.title,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 1
                    )

                    Text(
                        text = qSong.artist,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = {
                        if (!isDragging) {
                            onRemoveFromQueue(qSong)
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove from queue",
                        tint = Color.White.copy(alpha = 0.60f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}