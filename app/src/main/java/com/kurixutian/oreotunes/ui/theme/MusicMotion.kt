package com.kurixutian.oreotunes.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object MusicMotion {
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardDecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    const val DurationShort = 180
    const val DurationMedium = 320
    const val DurationLong = 450

    val SnappySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val SmoothSpring = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = 380f
    )

    fun <T> standardTween(durationMillis: Int = DurationMedium) = tween<T>(
        durationMillis = durationMillis,
        easing = EmphasizedEasing
    )
}
