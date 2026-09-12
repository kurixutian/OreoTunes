package com.kurixutian.oreotunes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kurixutian.oreotunes.data.preferences.AppThemeMode
import com.kurixutian.oreotunes.data.preferences.DarkThemeStyle
import com.kurixutian.oreotunes.data.preferences.LightThemeStyle
import com.kurixutian.oreotunes.data.repository.ArtworkPalette

@Composable
fun LiquidMusicTheme(
    palette: ArtworkPalette,
    themeMode: AppThemeMode = AppThemeMode.DEFAULT,
    darkStyle: DarkThemeStyle = DarkThemeStyle.AMOLED_DYNAMIC,
    lightStyle: LightThemeStyle = LightThemeStyle.PURE_WHITE_DYNAMIC,
    customAccent: Color = Color(0xFF64D2FF),
    content: @Composable () -> Unit
) {
    val primaryAccent = when (themeMode) {
        AppThemeMode.DEFAULT -> palette.accent
        AppThemeMode.DARK -> when (darkStyle) {
            DarkThemeStyle.AMOLED_DYNAMIC -> palette.accent
            DarkThemeStyle.AMOLED_CUSTOM_ACCENT -> customAccent
        }
        AppThemeMode.LIGHT -> when (lightStyle) {
            LightThemeStyle.PURE_WHITE_DYNAMIC -> palette.lightAccent
            LightThemeStyle.PURE_WHITE_CUSTOM_ACCENT -> customAccent
        }
    }

    val secondaryAccent = when (themeMode) {
        AppThemeMode.DEFAULT -> palette.secondary
        AppThemeMode.DARK -> if (darkStyle == DarkThemeStyle.AMOLED_CUSTOM_ACCENT) customAccent else palette.secondary
        AppThemeMode.LIGHT -> if (lightStyle == LightThemeStyle.PURE_WHITE_CUSTOM_ACCENT) customAccent else palette.lightAccent
    }

    val colorScheme = if (themeMode == AppThemeMode.LIGHT) {
        lightColorScheme(
            primary = primaryAccent,
            secondary = secondaryAccent,
            background = Color(0xFFF7F8FC),
            surface = Color.White,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF121520),
            onSurface = Color(0xFF121520)
        )
    } else {
        val bg = if (themeMode == AppThemeMode.DARK) Color.Black else palette.darkBackground
        val surfaceColor = if (themeMode == AppThemeMode.DARK) Color(0xFF101014) else palette.surfaceColor
        darkColorScheme(
            primary = primaryAccent,
            secondary = secondaryAccent,
            background = bg,
            surface = surfaceColor,
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onBackground = Color(0xFFF4F4F8),
            onSurface = Color(0xFFF4F4F8)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
