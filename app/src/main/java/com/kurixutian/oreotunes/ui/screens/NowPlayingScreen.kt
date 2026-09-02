package com.kurixutian.oreotunes.ui.screens

import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kurixutian.oreotunes.data.repository.ArtworkPalette
import com.kurixutian.oreotunes.data.repository.LyricLine
import com.kurixutian.oreotunes.data.repository.LyricsExtractor
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.ui.components.ArtworkThumbnail
import com.kurixutian.oreotunes.ui.components.DynamicAtmosphereBackground
import com.kurixutian.oreotunes.ui.theme.Manrope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val SmoothEasing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    song: Song?,
    songsQueue: List<Song>,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isShuffle: Boolean,
    repeatMode: Int,
    palette: ArtworkPalette,
    isActionSheetOpen: Boolean = false,
    artworkScalePercent: Int = 100,
    artworkCornerRadiusDp: Int = 28,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: (Song) -> Unit,
    onOptionsClick: (Song) -> Unit = {},
    onViewArtist: (String) -> Unit = {},
    onSelectQueueItem: (Song) -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onRemoveFromQueue: (Song) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (song == null) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var currentVolume by remember {
        mutableFloatStateOf(
            audioManager?.let {
                val current = it.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                if (max > 0f) current / max else 0.5f
            } ?: 0.5f
        )
    }

    var showQueueSheet by remember { mutableStateOf(false) }
    var parsedLyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }

    var showControlsInLyrics by remember { mutableStateOf(true) }
    var hideControlsJob by remember { mutableStateOf<Job?>(null) }

    val lyricsListState = rememberLazyListState()

    // 1. Cascading Dismissal Physics
    val verticalDismissOffset = remember { Animatable(0f) }

    // 2. Lyrics Sheet Progress
    val lyricsSheetProgress = remember { Animatable(0f) }
    val isLyricsOpen by remember { derivedStateOf { lyricsSheetProgress.value > 0.05f } }

    // 3. Artwork Long-Press Feedback
    var isArtworkLongPressed by remember { mutableStateOf(false) }
    val artworkPressScale by animateFloatAsState(
        targetValue = if (isArtworkLongPressed) 0.92f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "artworkLongPressScale"
    )

    val activeQueue = remember(songsQueue, song) {
        if (songsQueue.isNotEmpty()) songsQueue else listOf(song)
    }
    val currentSongIndex = remember(song.id, activeQueue) {
        val idx = activeQueue.indexOfFirst { it.id == song.id }
        if (idx == -1) 0 else idx
    }

    val pagerState = rememberPagerState(
        initialPage = currentSongIndex.coerceIn(0, (activeQueue.size - 1).coerceAtLeast(0)),
        pageCount = { activeQueue.size }
    )

    val visibleSong by remember(pagerState.currentPage, activeQueue, song) {
        derivedStateOf {
            activeQueue.getOrNull(pagerState.currentPage) ?: song
        }
    }

    LaunchedEffect(song.id) {
        val targetIdx = activeQueue.indexOfFirst { it.id == song.id }
        if (targetIdx != -1 && pagerState.currentPage != targetIdx && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(
                page = targetIdx,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 380f)
            )
        }
    }

    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.settledPage in activeQueue.indices) {
            val targetSong = activeQueue[pagerState.settledPage]
            if (targetSong.id != song.id) {
                onSelectQueueItem(targetSong)
            }
        }
    }

    fun triggerUserWakeUp() {
        showControlsInLyrics = true
        hideControlsJob?.cancel()
        hideControlsJob = coroutineScope.launch {
            delay(4000)
            showControlsInLyrics = false
        }
    }

    BackHandler(enabled = isLyricsOpen) {
        coroutineScope.launch {
            lyricsSheetProgress.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    LaunchedEffect(visibleSong.id, visibleSong.contentUri) {
        parsedLyrics = LyricsExtractor.extractEmbeddedLyrics(context, visibleSong.contentUri)
    }

    LaunchedEffect(isLyricsOpen) {
        if (isLyricsOpen) {
            triggerUserWakeUp()
        } else {
            hideControlsJob?.cancel()
            showControlsInLyrics = true
        }
    }

    val currentLyricIndex by remember(currentPosition, parsedLyrics) {
        derivedStateOf {
            if (parsedLyrics.isEmpty()) -1
            else {
                val index = parsedLyrics.indexOfLast { it.timestampMs in 0..currentPosition }
                if (index != -1) index else 0
            }
        }
    }

    LaunchedEffect(currentLyricIndex, isLyricsOpen) {
        if (isLyricsOpen && currentLyricIndex >= 0 && currentLyricIndex < parsedLyrics.size) {
            coroutineScope.launch {
                try {
                    lyricsListState.animateScrollToItem(
                        index = (currentLyricIndex - 2).coerceAtLeast(0)
                    )
                } catch (_: Exception) {}
            }
        }
    }

    val isAnyBackdropBlurActive = showQueueSheet || isActionSheetOpen

    val screenBackdropBlur by animateDpAsState(
        targetValue = if (isAnyBackdropBlurActive) 28.dp else 0.dp,
        animationSpec = tween(durationMillis = 200, easing = SmoothEasing),
        label = "nowPlayingScreenBlur"
    )

    val queueModalState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun dismissQueueGracefully(action: (() -> Unit)? = null) {
        coroutineScope.launch {
            try {
                queueModalState.hide()
            } finally {
                showQueueSheet = false
                action?.invoke()
            }
        }
    }

    val dismissProgress = (verticalDismissOffset.value / (screenHeightPx * 0.7f)).coerceIn(0f, 1f)
    val screenCascadeScale = (1f - (dismissProgress * 0.12f)).coerceIn(0.88f, 1f)
    val screenCornerRadius = (dismissProgress * 36.dp.value).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = verticalDismissOffset.value
                scaleX = screenCascadeScale
                scaleY = screenCascadeScale
            }
            .clip(RoundedCornerShape(screenCornerRadius))
            .background(Color(0xFF0D0B12))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }
    ) {
        DynamicAtmosphereBackground(
            albumArtUri = visibleSong.albumArtUri,
            palette = palette,
            isPlaying = isPlaying
        )

        // -------------------------------------------------------------
        // LAYER 1: Apple Music Proportional Layout
        // -------------------------------------------------------------
        val mainContentScale = (1f - (lyricsSheetProgress.value * 0.08f)).coerceIn(0.92f, 1f)
        val mainContentAlpha = (1f - (lyricsSheetProgress.value * 0.75f)).coerceIn(0f, 1f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = mainContentScale
                    scaleY = mainContentScale
                    alpha = mainContentAlpha
                }
                .blur(screenBackdropBlur)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {},
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                if (verticalDismissOffset.value > 0f) {
                                    val newDismiss = (verticalDismissOffset.value + dragAmount).coerceAtLeast(0f)
                                    verticalDismissOffset.snapTo(newDismiss)
                                } else if (dragAmount < 0f || lyricsSheetProgress.value > 0f) {
                                    val deltaFraction = -dragAmount / screenHeightPx
                                    lyricsSheetProgress.snapTo((lyricsSheetProgress.value + deltaFraction).coerceIn(0f, 1f))
                                } else if (dragAmount > 0f) {
                                    verticalDismissOffset.snapTo(verticalDismissOffset.value + dragAmount)
                                }
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (verticalDismissOffset.value > 0f) {
                                    val threshold = screenHeightPx * 0.24f
                                    if (verticalDismissOffset.value > threshold) {
                                        verticalDismissOffset.animateTo(screenHeightPx, spring(dampingRatio = 0.82f, stiffness = 380f))
                                        onCollapse()
                                        delay(50)
                                        verticalDismissOffset.snapTo(0f)
                                    } else {
                                        verticalDismissOffset.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
                                    }
                                }

                                if (lyricsSheetProgress.value > 0f) {
                                    if (lyricsSheetProgress.value > 0.18f) {
                                        lyricsSheetProgress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                                    } else {
                                        lyricsSheetProgress.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                verticalDismissOffset.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
                                if (lyricsSheetProgress.value < 0.5f) {
                                    lyricsSheetProgress.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
                                } else {
                                    lyricsSheetProgress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Drag Handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp)
                    .clickable { onCollapse() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color.White.copy(alpha = 0.32f))
                )
            }

            // 2. Large 1:1 Album Artwork Carousel
            val scaleFraction = (artworkScalePercent / 100f).coerceIn(0.65f, 1f)
            val artworkShape = RoundedCornerShape(artworkCornerRadiusDp.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 16.dp,
                    key = { page -> if (page in activeQueue.indices) "${activeQueue[page].id}_$page" else page },
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pageSong = activeQueue.getOrNull(page)
                    val isCurrentPage = page == pagerState.currentPage

                    val pageOffset = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    val targetScale = lerp(
                        start = 0.88f,
                        stop = 1.0f,
                        fraction = (1f - pageOffset.coerceIn(0f, 1f))
                    ) * if (isCurrentPage) artworkPressScale else 1.0f

                    val targetAlpha = lerp(
                        start = 0.40f,
                        stop = 1.0f,
                        fraction = (1f - pageOffset.coerceIn(0f, 1f))
                    )

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(fraction = scaleFraction)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    scaleX = targetScale
                                    scaleY = targetScale
                                    alpha = targetAlpha
                                }
                                .clip(artworkShape)
                                .background(Color(0xFF1B1622))
                                .pointerInput(pageSong?.id) {
                                    detectTapGestures(
                                        onPress = {
                                            isArtworkLongPressed = false
                                        },
                                        onLongPress = {
                                            if (pageSong != null) {
                                                coroutineScope.launch {
                                                    isArtworkLongPressed = true
                                                    delay(180)
                                                    isArtworkLongPressed = false
                                                    onOptionsClick(pageSong)
                                                }
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            ArtworkThumbnail(
                                model = pageSong?.albumArtUri,
                                contentDescription = pageSong?.title,
                                shape = artworkShape,
                                targetSizeDp = 360.dp,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // 3. Track Title, Artist, Heart & Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (visibleSong.artist.isNotBlank()) {
                                onViewArtist(visibleSong.artist)
                            }
                        }
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = visibleSong.title,
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 23.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = visibleSong.artist,
                        fontFamily = Manrope,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable { onFavorite(visibleSong) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (visibleSong.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (visibleSong.isFavorite) Color(0xFFFF375F) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable { onOptionsClick(visibleSong) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // 4. Seekbar, Timestamps & Fixed Centered Lossless Badge
            Column(modifier = Modifier.fillMaxWidth()) {
                var barWidthPx by remember { mutableFloatStateOf(0f) }
                val activeDuration = if (visibleSong.id == song.id && duration > 0) duration else visibleSong.duration
                val activePosition = if (visibleSong.id == song.id) currentPosition else 0L
                val progressFraction = if (activeDuration > 0) (activePosition.toFloat() / activeDuration.toFloat()).coerceIn(0f, 1f) else 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .onGloballyPositioned { barWidthPx = it.size.width.toFloat() }
                        .pointerInput(activeDuration) {
                            detectTapGestures { offset ->
                                if (barWidthPx > 0 && activeDuration > 0) {
                                    val newFraction = (offset.x / barWidthPx).coerceIn(0f, 1f)
                                    onSeek((newFraction * activeDuration).toLong())
                                }
                            }
                        }
                        .pointerInput(activeDuration) {
                            detectHorizontalDragGestures { change, _ ->
                                if (barWidthPx > 0 && activeDuration > 0) {
                                    val newFraction = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                                    onSeek((newFraction * activeDuration).toLong())
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progressFraction.coerceAtLeast(0.005f))
                            .height(4.5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.90f))
                    )
                }

                // Dedicated 3-element baseline container: Lossless label is permanently locked to center
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = formatTime(activePosition),
                        fontFamily = Manrope,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.60f),
                        modifier = Modifier.align(Alignment.CenterStart)
                    )

                    Text(
                        text = "Lossless",
                        fontFamily = Manrope,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.75f),
                        letterSpacing = 0.2.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    val remainingMs = (activeDuration - activePosition).coerceAtLeast(0L)
                    Text(
                        text = "-${formatTime(remainingMs)}",
                        fontFamily = Manrope,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.60f),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }

            // 5. Playback Controls Row: Outer buttons (dimmed 0.45f) + Tight Center Cluster (36.dp spacing)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onShuffle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) Color.White else Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Center group: Previous, Play/Pause, Next
                Row(
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onSkipPrev() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FastRewind,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(60.dp)
                    ) {
                        AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(140)) },
                            label = "nowPlayingSolidPlayPause"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FastForward,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onRepeat,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = when (repeatMode) {
                            1 -> Icons.Rounded.RepeatOne
                            2 -> Icons.Rounded.Repeat
                            else -> Icons.Rounded.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode > 0) Color.White else Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 6. Volume Slider Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeMute,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.50f),
                    modifier = Modifier.size(16.dp)
                )

                var volBarWidthPx by remember { mutableFloatStateOf(0f) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .onGloballyPositioned { volBarWidthPx = it.size.width.toFloat() }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                if (volBarWidthPx > 0) {
                                    val vol = (offset.x / volBarWidthPx).coerceIn(0f, 1f)
                                    currentVolume = vol
                                    audioManager?.let {
                                        val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        it.setStreamVolume(AudioManager.STREAM_MUSIC, (vol * max).toInt(), 0)
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                if (volBarWidthPx > 0) {
                                    val vol = (change.position.x / volBarWidthPx).coerceIn(0f, 1f)
                                    currentVolume = vol
                                    audioManager?.let {
                                        val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        it.setStreamVolume(AudioManager.STREAM_MUSIC, (vol * max).toInt(), 0)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = currentVolume.coerceIn(0f, 1f))
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.85f))
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.50f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // 7. Bottom Toolbar: Lyrics, FLAC Specs, Queue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            lyricsSheetProgress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = "Lyrics",
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = "FLAC • 44.1 kHz • 957 kbps • 34.0 MB",
                    fontFamily = Manrope,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.50f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                )

                IconButton(
                    onClick = { showQueueSheet = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.List,
                        contentDescription = "Queue",
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // LAYER 2: Hardware-Accelerated Real-Time Sliding Lyrics Sheet
        // -------------------------------------------------------------
        if (lyricsSheetProgress.value > 0.001f) {
            val lyricsTranslateY = (1f - lyricsSheetProgress.value) * screenHeightPx
            val lyricsScale = (0.92f + (lyricsSheetProgress.value * 0.08f)).coerceIn(0.92f, 1f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = lyricsTranslateY
                        scaleX = lyricsScale
                        scaleY = lyricsScale
                    }
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.60f)
                            )
                        )
                    )
                    .blur(screenBackdropBlur)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {},
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    coroutineScope.launch {
                                        val deltaFraction = -dragAmount / screenHeightPx
                                        lyricsSheetProgress.snapTo((lyricsSheetProgress.value + deltaFraction).coerceIn(0f, 1f))
                                    }
                                },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        if (lyricsSheetProgress.value < 0.82f) {
                                            lyricsSheetProgress.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                                        } else {
                                            lyricsSheetProgress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                                        }
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        lyricsSheetProgress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                                    }
                                }
                            )
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                coroutineScope.launch {
                                    lyricsSheetProgress.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                    ) {
                        ArtworkThumbnail(
                            model = visibleSong.albumArtUri,
                            contentDescription = visibleSong.title,
                            shape = RoundedCornerShape(8.dp),
                            targetSizeDp = 44.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = visibleSong.title,
                                fontFamily = Manrope,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = visibleSong.artist,
                                fontFamily = Manrope,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onFavorite(visibleSong) }) {
                            Icon(
                                imageVector = if (visibleSong.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (visibleSong.isFavorite) Color(0xFFFF375F) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { onOptionsClick(visibleSong) }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Options",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                if (parsedLyrics.isNotEmpty()) {
                    LazyColumn(
                        state = lyricsListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    triggerUserWakeUp()
                                }
                            },
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                        contentPadding = PaddingValues(vertical = 100.dp)
                    ) {
                        itemsIndexed(parsedLyrics) { index, line ->
                            val isCurrent = index == currentLyricIndex
                            val alpha by animateFloatAsState(
                                targetValue = if (isCurrent) 1.0f else 0.35f,
                                animationSpec = tween(300),
                                label = "lyricAlpha"
                            )

                            Text(
                                text = line.text.ifBlank { "• • •" },
                                fontFamily = Manrope,
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                                fontSize = if (isCurrent) 24.sp else 18.sp,
                                color = Color.White.copy(alpha = alpha),
                                lineHeight = 34.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        triggerUserWakeUp()
                                        if (line.timestampMs >= 0) {
                                            onSeek(line.timestampMs)
                                        }
                                    }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No synchronized lyrics found in file metadata.",
                            fontFamily = Manrope,
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.50f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    var barWidthPx by remember { mutableFloatStateOf(0f) }
                    val activeDuration = if (visibleSong.id == song.id && duration > 0) duration else visibleSong.duration
                    val activePosition = if (visibleSong.id == song.id) currentPosition else 0L
                    val progressFraction = if (activeDuration > 0) (activePosition.toFloat() / activeDuration.toFloat()).coerceIn(0f, 1f) else 0f

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .onGloballyPositioned { barWidthPx = it.size.width.toFloat() }
                            .pointerInput(activeDuration) {
                                detectTapGestures { offset ->
                                    triggerUserWakeUp()
                                    if (barWidthPx > 0 && activeDuration > 0) {
                                        val newFraction = (offset.x / barWidthPx).coerceIn(0f, 1f)
                                        onSeek((newFraction * activeDuration).toLong())
                                    }
                                }
                            }
                            .pointerInput(activeDuration) {
                                detectHorizontalDragGestures { change, _ ->
                                    triggerUserWakeUp()
                                    if (barWidthPx > 0 && activeDuration > 0) {
                                        val newFraction = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                                        onSeek((newFraction * activeDuration).toLong())
                                    }
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.20f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = progressFraction.coerceAtLeast(0.005f))
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.90f))
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 1.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatTime(activePosition), fontFamily = Manrope, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                        Text(formatTime(activeDuration), fontFamily = Manrope, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                    }

                    AnimatedVisibility(
                        visible = showControlsInLyrics,
                        enter = fadeIn(tween(240)) + expandVertically(tween(240)),
                        exit = fadeOut(tween(240)) + shrinkVertically(tween(240))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    triggerUserWakeUp()
                                    onShuffle()
                                }) {
                                    Icon(
                                        Icons.Rounded.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = if (isShuffle) Color.White else Color.White.copy(alpha = 0.45f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    triggerUserWakeUp()
                                    onSkipPrev()
                                }, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.Rounded.FastRewind, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                                IconButton(
                                    onClick = {
                                        triggerUserWakeUp()
                                        onPlayPause()
                                    },
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    triggerUserWakeUp()
                                    onSkipNext()
                                }, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.Rounded.FastForward, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                                IconButton(onClick = {
                                    triggerUserWakeUp()
                                    onRepeat()
                                }) {
                                    Icon(
                                        if (repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                        contentDescription = "Repeat",
                                        tint = if (repeatMode > 0) Color.White else Color.White.copy(alpha = 0.45f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                lyricsSheetProgress.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                            }
                        }) {
                            Icon(Icons.Rounded.ExpandMore, contentDescription = "Minimize Lyrics", tint = Color.White.copy(alpha = 0.70f))
                        }
                        IconButton(onClick = { showQueueSheet = true }) {
                            Icon(Icons.AutoMirrored.Rounded.List, contentDescription = "Queue", tint = Color.White.copy(alpha = 0.80f), modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }

    // Interactive Translucent Frosted Glass Queue Sheet
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { dismissQueueGracefully() },
            sheetState = queueModalState,
            containerColor = Color.Transparent,
            scrimColor = Color.Black.copy(alpha = 0.45f),
            dragHandle = null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color(0xFF141724).copy(alpha = 0.65f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp, bottom = 12.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .align(Alignment.CenterHorizontally)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Up Next (${songsQueue.size})",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        if (songsQueue.size > 1) {
                            TextButton(
                                onClick = { dismissQueueGracefully { onClearQueue() } }
                            ) {
                                Text("Clear", color = Color(0xFFFF6584), fontFamily = Manrope)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(songsQueue, key = { index, item -> "${item.id}_$index" }) { index, qSong ->
                            val isCurrent = qSong.id == song.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isCurrent) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                                    .clickable {
                                        dismissQueueGracefully { onSelectQueueItem(qSong) }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArtworkThumbnail(
                                    model = qSong.albumArtUri,
                                    contentDescription = qSong.title,
                                    shape = RoundedCornerShape(8.dp),
                                    targetSizeDp = 42.dp,
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = qSong.title,
                                        fontFamily = Manrope,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = qSong.artist,
                                        fontFamily = Manrope,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.50f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!isCurrent) {
                                    IconButton(onClick = { onRemoveFromQueue(qSong) }) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Remove",
                                            tint = Color.White.copy(alpha = 0.40f),
                                            modifier = Modifier.size(18.dp)
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
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(minutes, seconds)
}
