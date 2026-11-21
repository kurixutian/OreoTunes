package com.kurixutian.oreotunes.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur as backdropBlur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalLiquidGlassEnabled = staticCompositionLocalOf { true }

/** Configures a standard Dialog window with a light scrim and no behind-window blur. */
@Composable
fun ApplyDialogBlurBehind(
    dimAmount: Float = 0.18f,
    blurRadius: Int = 0
) {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.setDimAmount(dimAmount)
    }
}

/**
 * Renders an in-window modal when [LocalLiquidGlassBackdrop] is available so Kyant0
 * `drawBackdrop` can sample and refract the sharp background behind the dialog;
 * otherwise falls back to a standard [Dialog].
 */
@Composable
fun LiquidGlassModal(
    onDismissRequest: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalLiquidGlassBackdrop.current
    if (backdrop != null) {
        BackHandler(onBack = onDismissRequest)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(50f)
                .background(Color.Black.copy(alpha = 0.36f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
                .systemBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center,
            content = content
        )
    } else {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ApplyDialogBlurBehind()
            Box(contentAlignment = Alignment.Center, content = content)
        }
    }
}

/**
 * Shared liquid-glass backdrop effect pipeline: vibrancy boost, light frost blur,
 * and domed lens refraction with chromatic dispersion along curved edges.
 */
fun com.kyant.backdrop.BackdropEffectScope.liquidGlassEffects() {
    vibrancy()
    backdropBlur(8.dp.toPx())
    lens(
        refractionHeight = 28.dp.toPx(),
        refractionAmount = 68.dp.toPx(),
        depthEffect = true,
        chromaticAberration = true,
    )
}

/** Floating glass bar chrome with backdrop refraction when enabled or translucent fallback fill. */
@Composable
fun Modifier.liquidGlassChrome(
    backdrop: Backdrop,
    isLight: Boolean,
    shape: Shape = Capsule(),
    fallbackShape: Shape = CircleShape,
    addBorder: Boolean = true,
    glassEnabled: Boolean = LocalLiquidGlassEnabled.current
): Modifier {
    val borderColor = if (isLight) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.22f)
    val surfaceMod = if (glassEnabled) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { liquidGlassEffects() },
            onDrawSurface = {
                drawRect(
                    if (isLight) Color.White.copy(alpha = 0.32f)
                    else Color(0xFF171923).copy(alpha = 0.22f)
                )
            }
        )
    } else {
        Modifier
            .clip(fallbackShape)
            .background(
                if (isLight) Color.White.copy(alpha = 0.88f)
                else Color(0xFF171923).copy(alpha = 0.90f)
            )
    }
    return this.then(
        if (addBorder) surfaceMod.border(width = 0.8.dp, color = borderColor, shape = fallbackShape)
        else surfaceMod
    )
}

/** Shared liquid-glass surface matching the bottom navigation bar when [LocalLiquidGlassBackdrop] is present. */
@Composable
fun Modifier.liquidGlassSurface(
    isLight: Boolean,
    shape: Shape = RoundedCornerShape(28.dp),
    cornerRadius: Dp = 28.dp
): Modifier {
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    val borderColor = if (isLight) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.22f)
    val glassModifier = if (glassEnabled && backdrop != null) {
        Modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = { liquidGlassEffects() },
                onDrawSurface = {
                    drawRect(
                        if (isLight) Color.White.copy(alpha = 0.42f)
                        else Color(0xFF171923).copy(alpha = 0.38f)
                    )
                }
            )
            .border(width = 0.8.dp, color = borderColor, shape = shape)
    } else {
        val fillBrush = if (isLight) {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.78f),
                    Color(0xFFF5F7FC).copy(alpha = 0.72f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1D2030).copy(alpha = 0.72f),
                    Color(0xFF131520).copy(alpha = 0.68f)
                )
            )
        }
        Modifier
            .clip(shape)
            .background(fillBrush)
            .border(width = 0.8.dp, color = borderColor, shape = shape)
    }
    return this
        .widthIn(max = 560.dp)
        .then(glassModifier)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { }
}
