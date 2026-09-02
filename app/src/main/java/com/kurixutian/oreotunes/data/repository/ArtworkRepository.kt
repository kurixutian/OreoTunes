package com.kurixutian.oreotunes.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArtworkPalette(
    val dominant: Color = Color(0xFF16161C),
    val secondary: Color = Color(0xFF22222A),
    val accent: Color = Color(0xFFE2E2EA),
    val lightAccent: Color = Color(0xFF181A24),
    val darkBackground: Color = Color(0xFF0A0A0E),
    val surfaceColor: Color = Color(0xFF14141A),
    val elevatedSurfaceColor: Color = Color(0xFF1E1E26),
    val textPrimary: Color = Color(0xFFFFFFFF),
    val textSecondary: Color = Color(0xFFB0B0B8),
    val isMostlyNeutral: Boolean = true,
    val isMostlyDark: Boolean = true,
    val averageLuminance: Float = 0.1f,
    val averageSaturation: Float = 0.0f
) {
    // Backward compatibility getters
    val primary: Color get() = accent
    val background: Color get() = darkBackground
    val backgroundDim: Color get() = surfaceColor
}

class ArtworkRepository(private val context: Context) {

    companion object {
        private const val TAG = "OreoTunesPalette"
        private val paletteCache = LruCache<String, ArtworkPalette>(60)
    }

    suspend fun extractPalette(artworkUri: Uri?): ArtworkPalette = withContext(Dispatchers.IO) {
        if (artworkUri == null) {
            return@withContext defaultNeutralPalette()
        }

        val cacheKey = artworkUri.toString()
        paletteCache.get(cacheKey)?.let { return@withContext it }

        val bitmap = loadResizedBitmap(artworkUri, maxDimension = 128)
            ?: return@withContext defaultNeutralPalette().also { paletteCache.put(cacheKey, it) }

        val extracted = analyzeBitmapPalette(bitmap)
        paletteCache.put(cacheKey, extracted)
        extracted
    }

    private fun loadResizedBitmap(uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            val fullBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val sample = maxOf(info.size.width / maxDimension, info.size.height / maxDimension, 1)
                    decoder.setTargetSampleSize(sample)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }

            if (fullBitmap.width > maxDimension || fullBitmap.height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(fullBitmap.width, fullBitmap.height)
                val targetW = (fullBitmap.width * scale).toInt().coerceAtLeast(1)
                val targetH = (fullBitmap.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(fullBitmap, targetW, targetH, true)
            } else {
                fullBitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun analyzeBitmapPalette(bitmap: Bitmap): ArtworkPalette {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalSaturation = 0.0
        var totalLuminance = 0.0
        var validPixelCount = 0
        val hsl = FloatArray(3)

        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 64) continue

            ColorUtils.colorToHSL(pixel, hsl)
            totalSaturation += hsl[1]
            totalLuminance += hsl[2]
            validPixelCount++
        }

        val avgSat = if (validPixelCount > 0) (totalSaturation / validPixelCount).toFloat() else 0f
        val avgLum = if (validPixelCount > 0) (totalLuminance / validPixelCount).toFloat() else 0.1f

        val isMostlyNeutral = avgSat < 0.13f
        val isMostlyDark = avgLum < 0.32f

        val palette = Palette.from(bitmap).maximumColorCount(24).generate()
        val swatches = palette.swatches.sortedByDescending { it.population }
        val dominantSwatch = swatches.firstOrNull()

        val generatedPalette = if (isMostlyNeutral || swatches.isEmpty() || dominantSwatch == null) {
            val lumClamp = avgLum.coerceIn(0.04f, 0.45f)
            val baseGrayInt = (lumClamp * 255).toInt().coerceIn(16, 110)
            val dominantColor = Color(baseGrayInt, baseGrayInt, (baseGrayInt + 2).coerceAtMost(255))
            val secondaryColor = Color((baseGrayInt * 1.4f).toInt().coerceIn(28, 145), (baseGrayInt * 1.4f).toInt().coerceIn(28, 145), (baseGrayInt * 1.4f).toInt().coerceIn(30, 150))
            val accentColor = Color(0xFFEEEEF2) // Light accent for dark surfaces
            val lightAccentColor = Color(0xFF141722) // Deep dark obsidian for light surfaces
            val darkBg = Color(0xFF0A0A0D)
            val surface = Color(0xFF131317)
            val elevatedSurface = Color(0xFF1C1C22)

            ArtworkPalette(
                dominant = dominantColor,
                secondary = secondaryColor,
                accent = accentColor,
                lightAccent = lightAccentColor,
                darkBackground = darkBg,
                surfaceColor = surface,
                elevatedSurfaceColor = elevatedSurface,
                textPrimary = Color.White,
                textSecondary = Color(0xFFB0B0B8),
                isMostlyNeutral = true,
                isMostlyDark = isMostlyDark,
                averageLuminance = avgLum,
                averageSaturation = avgSat
            )
        } else {
            val dominantRgb = dominantSwatch.rgb
            val dominantHsl = FloatArray(3).apply { ColorUtils.colorToHSL(dominantRgb, this) }

            val colorfulSwatch = swatches.firstOrNull { s ->
                val sHsl = FloatArray(3).apply { ColorUtils.colorToHSL(s.rgb, this) }
                sHsl[1] >= 0.22f && sHsl[2] in 0.20f..0.85f
            } ?: dominantSwatch

            // Dark Mode Accent (Light & Crisp on dark backgrounds)
            val accentHsl = FloatArray(3).apply { ColorUtils.colorToHSL(colorfulSwatch.rgb, this) }
            accentHsl[1] = accentHsl[1].coerceIn(0.40f, 0.95f)
            accentHsl[2] = accentHsl[2].coerceIn(0.58f, 0.74f)
            val accentColorInt = ColorUtils.HSLToColor(accentHsl)

            // Light Mode Accent (Deep & Saturated on clean white backgrounds)
            val lightAccentHsl = FloatArray(3).apply { ColorUtils.colorToHSL(colorfulSwatch.rgb, this) }
            lightAccentHsl[1] = lightAccentHsl[1].coerceIn(0.60f, 0.98f)
            lightAccentHsl[2] = lightAccentHsl[2].coerceIn(0.32f, 0.44f)
            val lightAccentColorInt = ColorUtils.HSLToColor(lightAccentHsl)

            val bgHsl = floatArrayOf(
                dominantHsl[0],
                (dominantHsl[1] * 0.40f).coerceIn(0.04f, 0.35f),
                (dominantHsl[2] * 0.16f).coerceIn(0.04f, 0.075f)
            )
            val darkBgInt = ColorUtils.HSLToColor(bgHsl)

            val surfaceHsl = floatArrayOf(
                dominantHsl[0],
                (dominantHsl[1] * 0.30f).coerceIn(0.02f, 0.25f),
                0.09f
            )
            val elevatedSurfaceHsl = floatArrayOf(
                dominantHsl[0],
                (dominantHsl[1] * 0.35f).coerceIn(0.03f, 0.30f),
                0.14f
            )

            ArtworkPalette(
                dominant = Color(dominantRgb),
                secondary = Color(colorfulSwatch.rgb),
                accent = Color(accentColorInt),
                lightAccent = Color(lightAccentColorInt),
                darkBackground = Color(darkBgInt),
                surfaceColor = Color(ColorUtils.HSLToColor(surfaceHsl)),
                elevatedSurfaceColor = Color(ColorUtils.HSLToColor(elevatedSurfaceHsl)),
                textPrimary = Color.White,
                textSecondary = Color(0xFFD8D8E0),
                isMostlyNeutral = false,
                isMostlyDark = isMostlyDark,
                averageLuminance = avgLum,
                averageSaturation = avgSat
            )
        }

        Log.d(
            TAG,
            "Dominant: #${Integer.toHexString(generatedPalette.dominant.toArgb())} | " +
                    "DarkAccent: #${Integer.toHexString(generatedPalette.accent.toArgb())} | " +
                    "LightAccent: #${Integer.toHexString(generatedPalette.lightAccent.toArgb())} | " +
                    "AvgSat: ${"%.2f".format(avgSat)} | Neutral: $isMostlyNeutral"
        )

        return generatedPalette
    }

    private fun defaultNeutralPalette() = ArtworkPalette(
        dominant = Color(0xFF141418),
        secondary = Color(0xFF222228),
        accent = Color(0xFFEEEEF2),
        lightAccent = Color(0xFF181A24),
        darkBackground = Color(0xFF09090C),
        surfaceColor = Color(0xFF121216),
        elevatedSurfaceColor = Color(0xFF1A1A20),
        textPrimary = Color.White,
        textSecondary = Color(0xFFA6A6B0),
        isMostlyNeutral = true,
        isMostlyDark = true,
        averageLuminance = 0.08f,
        averageSaturation = 0.0f
    )
}
