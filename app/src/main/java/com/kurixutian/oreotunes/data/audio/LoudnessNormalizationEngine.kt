package com.kurixutian.oreotunes.data.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import com.kurixutian.oreotunes.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

data class LoudnessProfile(
    val integratedLoudnessLufs: Float,
    val truePeakDbTp: Float,
    val normalizedGainDb: Float,
    val linearGainMultiplier: Float
)

class LoudnessNormalizationEngine(private val context: Context) {

    private val profileCache = ConcurrentHashMap<Long, LoudnessProfile>()

    companion object {
        const val TARGET_LOUDNESS_LUFS = -14.0f
        const val MAX_TRUE_PEAK_DBTP = -1.0f
        private const val DEFAULT_ASSUMED_LUFS = -10.5f // Baseline estimated mastering level for modern commercial music
        private const val DEFAULT_ASSUMED_PEAK_DBTP = -0.2f
    }

    suspend fun getOrComputeLoudnessProfile(song: Song): LoudnessProfile = withContext(Dispatchers.IO) {
        profileCache[song.id]?.let { return@withContext it }

        var measuredLufs = DEFAULT_ASSUMED_LUFS
        var measuredPeak = DEFAULT_ASSUMED_PEAK_DBTP

        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, song.contentUri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    // Check if file metadata stores ReplayGain or Loudness tags
                    if (format.containsKey("replaygain_track_gain")) {
                        val gain = format.getFloat("replaygain_track_gain")
                        measuredLufs = TARGET_LOUDNESS_LUFS - gain
                    }
                    if (format.containsKey("replaygain_track_peak")) {
                        measuredPeak = format.getFloat("replaygain_track_peak")
                    }
                    break
                }
            }
            extractor.release()
        } catch (_: Exception) {}

        // Phase 2: Gain Calculation with True Peak Safety Guardrail
        val rawTargetGainDb = TARGET_LOUDNESS_LUFS - measuredLufs
        val predictedPeakDbTp = measuredPeak + rawTargetGainDb

        val safeTargetGainDb = if (predictedPeakDbTp > MAX_TRUE_PEAK_DBTP) {
            MAX_TRUE_PEAK_DBTP - measuredPeak
        } else {
            rawTargetGainDb
        }

        // Phase 3: Linear Multiplier (Linear_Gain = 10 ^ (Target_Gain_dB / 20))
        val linearGain = 10.0f.pow(safeTargetGainDb / 20.0f).coerceIn(0.15f, 1.85f)

        val profile = LoudnessProfile(
            integratedLoudnessLufs = measuredLufs,
            truePeakDbTp = measuredPeak,
            normalizedGainDb = safeTargetGainDb,
            linearGainMultiplier = linearGain
        )

        profileCache[song.id] = profile
        profile
    }

    fun calculateSafeMultiplier(rawVolumeFactor: Float, isNormalizationEnabled: Boolean, song: Song?): Float {
        if (!isNormalizationEnabled || song == null) return rawVolumeFactor
        val cachedProfile = profileCache[song.id] ?: return rawVolumeFactor
        return (rawVolumeFactor * cachedProfile.linearGainMultiplier).coerceIn(0f, 1.85f)
    }
}
