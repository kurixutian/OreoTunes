package com.kurixutian.oreotunes.ui.components

import android.net.Uri
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kurixutian.oreotunes.data.repository.ArtworkPalette

private val SmoothEasing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)

@Composable
fun DynamicAtmosphereBackground(
    albumArtUri: Uri?,
    palette: ArtworkPalette,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animatedBg by animateColorAsState(
        targetValue = palette.darkBackground,
        animationSpec = tween(durationMillis = 500, easing = SmoothEasing),
        label = "atmosphereBgAnim"
    )

    val animatedDominant by animateColorAsState(
        targetValue = if (palette.isMostlyNeutral) Color(0xFF22222A).copy(alpha = 0.25f) else palette.dominant.copy(alpha = 0.45f),
        animationSpec = tween(durationMillis = 500, easing = SmoothEasing),
        label = "atmosphereDominantAnim"
    )

    val animatedSecondary by animateColorAsState(
        targetValue = if (palette.isMostlyNeutral) Color(0xFF14141A).copy(alpha = 0.20f) else palette.secondary.copy(alpha = 0.35f),
        animationSpec = tween(durationMillis = 500, easing = SmoothEasing),
        label = "atmosphereSecondaryAnim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "breathingAtmosphere")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isPlaying) 1.22f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val driftOffsetX by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftOffsetX"
    )

    val driftOffsetY by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftOffsetY"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedBg)
    ) {
        // 1. Glowing Radial Atmosphere Orbs
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = driftOffsetX.dp, y = (-40 + driftOffsetY).dp)
                .size(340.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedDominant,
                            animatedDominant.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
                .blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 64.dp else 36.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-driftOffsetX).dp, y = (60 - driftOffsetY).dp)
                .size(280.dp)
                .scale(pulseScale * 0.95f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedSecondary,
                            animatedSecondary.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 72.dp else 40.dp)
        )

        // 2. High-Fidelity Blurred Artwork Layer with Smooth Crossfade
        Crossfade(
            targetState = albumArtUri,
            animationSpec = tween(durationMillis = 400, easing = SmoothEasing),
            label = "artworkBackgroundCrossfade"
        ) { targetUri ->
            if (targetUri != null) {
                AsyncImage(
                    model = targetUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(pulseScale * 1.08f)
                        .blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 80.dp else 44.dp)
                )
            }
        }

        // 3. Smooth Darkening Atmospheric Veil
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            animatedDominant.copy(alpha = 0.20f),
                            animatedBg.copy(alpha = 0.55f),
                            animatedBg.copy(alpha = 0.88f),
                            animatedBg
                        )
                    )
                )
        )
    }
}
