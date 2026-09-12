package com.kurixutian.oreotunes.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kurixutian.oreotunes.domain.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingArtwork(
    activeQueue: List<Song>,
    pagerState: PagerState,
    artworkScalePercent: Int,
    artworkCornerRadiusDp: Int,
    onOptionsClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var isArtworkLongPressed by remember {
        mutableStateOf(false)
    }

    val artworkPressScale =
        androidx.compose.animation.core.animateFloatAsState(
            targetValue =
                if (isArtworkLongPressed) {
                    0.92f
                } else {
                    1.0f
                },
            animationSpec =
                androidx.compose.animation.core.spring(
                    dampingRatio = 0.65f,
                    stiffness =
                        androidx.compose.animation.core.Spring
                            .StiffnessMediumLow
                ),
            label = "artworkLongPressScale"
        )

    val scaleFraction =
        (artworkScalePercent / 100f)
            .coerceIn(0.65f, 1f)

    val artworkShape =
        RoundedCornerShape(artworkCornerRadiusDp.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        key(activeQueue.map { it.id }) {
            HorizontalPager(
                state = pagerState,
                pageSpacing = 16.dp,
                key = { page ->
                    activeQueue.getOrNull(page)?.id ?: page
                },
                modifier = Modifier.fillMaxSize()
            ) { page ->

                val pageSong =
                    activeQueue.getOrNull(page)

                val isCurrentPage =
                    page == pagerState.currentPage

                val pageOffset =
                    abs(
                        (pagerState.currentPage - page) +
                            pagerState.currentPageOffsetFraction
                    )

                val targetScale =
                    lerp(
                        start = 0.88f,
                        stop = 1.0f,
                        fraction =
                            (1f -
                                pageOffset.coerceIn(
                                    0f,
                                    1f
                                ))
                    ) *
                        if (isCurrentPage) {
                            artworkPressScale.value
                        } else {
                            1.0f
                        }

                val targetAlpha =
                    lerp(
                        start = 0.40f,
                        stop = 1.0f,
                        fraction =
                            (1f -
                                pageOffset.coerceIn(
                                    0f,
                                    1f
                                ))
                    )

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(
                                fraction = scaleFraction
                            )
                            .aspectRatio(1f)
                            .graphicsLayer {
                                scaleX = targetScale
                                scaleY = targetScale
                                alpha = targetAlpha
                            }
                            .clip(artworkShape)
                            .background(
                                Color(0xFF1B1622)
                            )
                            .pointerInput(pageSong?.id) {
                                detectTapGestures(
                                    onPress = {
                                        isArtworkLongPressed =
                                            false
                                    },
                                    onLongPress = {
                                        if (pageSong != null) {
                                            coroutineScope.launch {
                                                isArtworkLongPressed =
                                                    true

                                                delay(180)

                                                isArtworkLongPressed =
                                                    false

                                                onOptionsClick(
                                                    pageSong
                                                )
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        ArtworkThumbnail(
                            model = pageSong?.albumArtUri,
                            contentDescription =
                                pageSong?.title,
                            shape = artworkShape,
                            targetSizeDp = 360.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
