package com.kurixutian.oreotunes

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import kotlinx.coroutines.Dispatchers

class MusicApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_artwork_cache"))
                    .maxSizeBytes(80L * 1024 * 1024)
                    .build()
            }
            // Safely use ARGB_8888 without hardware bitmap pixel-access crashes
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .allowHardware(false) // Prevents crashes with Compose canvas & palette analysis
            .allowRgb565(true)
            .dispatcher(Dispatchers.IO)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(120)
            .build()
    }
}
