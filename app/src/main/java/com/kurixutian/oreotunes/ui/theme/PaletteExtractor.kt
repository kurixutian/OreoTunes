package com.kurixutian.oreotunes.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.kurixutian.oreotunes.data.repository.ArtworkPalette
import com.kurixutian.oreotunes.data.repository.ArtworkRepository

class PaletteExtractor(private val context: Context) {
    private val artworkRepo = ArtworkRepository(context)

    suspend fun extractFromUri(uri: Uri?): ArtworkPalette {
        return artworkRepo.extractPalette(uri)
    }

    suspend fun extractFromBitmap(bitmap: Bitmap?): ArtworkPalette {
        return if (bitmap != null) {
            ArtworkPalette(
                dominant = androidx.compose.ui.graphics.Color(0xFF16161C),
                secondary = androidx.compose.ui.graphics.Color(0xFF22222A),
                accent = androidx.compose.ui.graphics.Color(0xFFE2E2EA),
                darkBackground = androidx.compose.ui.graphics.Color(0xFF0A0A0E),
                surfaceColor = androidx.compose.ui.graphics.Color(0xFF14141A),
                elevatedSurfaceColor = androidx.compose.ui.graphics.Color(0xFF1E1E26)
            )
        } else {
            ArtworkPalette()
        }
    }
}
