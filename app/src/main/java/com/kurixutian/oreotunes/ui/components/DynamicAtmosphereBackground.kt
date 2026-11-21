package com.kurixutian.oreotunes.ui.components

import android.net.Uri
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.kurixutian.oreotunes.data.repository.ArtworkPalette
import com.kurixutian.oreotunes.ui.utils.LocalReducedMotion

private val SmoothEasing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)

@Composable
fun DynamicAtmosphereBackground(
    albumArtUri: Uri?,
    palette: ArtworkPalette,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val animDurationMs = if (LocalReducedMotion.current) 0 else 400

    val animatedBg by animateColorAsState(
        targetValue = palette.darkBackground,
        animationSpec = tween(durationMillis = animDurationMs, easing = SmoothEasing),
        label = "atmosphereBgAnim"
    )

    val animatedDominant by animateColorAsState(
        targetValue = if (palette.isMostlyNeutral) Color(0xFF2E3244).copy(alpha = 0.50f) else palette.dominant.copy(alpha = 0.65f),
        animationSpec = tween(durationMillis = animDurationMs, easing = SmoothEasing),
        label = "atmosphereDominantAnim"
    )

    val animatedSecondary by animateColorAsState(
        targetValue = if (palette.isMostlyNeutral) Color(0xFF1E2232).copy(alpha = 0.40f) else palette.secondary.copy(alpha = 0.55f),
        animationSpec = tween(durationMillis = animDurationMs, easing = SmoothEasing),
        label = "atmosphereSecondaryAnim"
    )

    val animatedAccent by animateColorAsState(
        targetValue = if (palette.isMostlyNeutral) Color(0xFF404860).copy(alpha = 0.35f) else palette.accent.copy(alpha = 0.45f),
        animationSpec = tween(durationMillis = animDurationMs, easing = SmoothEasing),
        label = "atmosphereAccentAnim"
    )

    val imageRequest = remember(albumArtUri) {
        if (albumArtUri != null) {
            ImageRequest.Builder(context)
                .data(albumArtUri)
                .size(180) // Downscaled for ultra-fast decode & low GPU memory
                .precision(Precision.INEXACT)
                .crossfade(true)
                .build()
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedBg)
    ) {
        // 1. Vibrant Top-Center Ambient Glow Orb (radial gradient already soft; no extra GPU blur pass)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-60).dp)
                .size(380.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedDominant.copy(alpha = animatedDominant.alpha * 0.72f),
                            animatedDominant.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 2. Secondary Floating Atmosphere Orb (Center-Right)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 60.dp, y = 80.dp)
                .size(340.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedSecondary.copy(alpha = animatedSecondary.alpha * 0.72f),
                            animatedAccent.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 3. Lower Accent Glow Orb (Bottom-Left)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = 40.dp)
                .size(280.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedAccent.copy(alpha = animatedAccent.alpha * 0.72f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 4. High-Fidelity Blurred Artwork Layer with Smooth Crossfade
        Crossfade(
            targetState = imageRequest,
            animationSpec = tween(durationMillis = if (animDurationMs == 0) 0 else 350, easing = SmoothEasing),
            label = "artworkBackgroundCrossfade"
        ) { req ->
            if (req != null) {
                AsyncImage(
                    model = req,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.20f
                            scaleY = 1.20f
                            alpha = 0.82f
                        }
                        .blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 28.dp else 18.dp)
                )
            }
        }

        // 5. Refined Frosted Glass Vignette & Atmospheric Veil
        // Provides deep contrast for controls & text while letting vivid artwork colors shine through
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.10f),
                            Color.Black.copy(alpha = 0.30f),
                            animatedBg.copy(alpha = 0.65f),
                            animatedBg.copy(alpha = 0.85f),
                            animatedBg
                        )
                    )
                )
        )
    }
}
