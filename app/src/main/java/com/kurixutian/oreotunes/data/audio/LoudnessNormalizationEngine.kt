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
                        val gain = try {
                            format.getFloat("replaygain_track_gain")
                        } catch (_: Exception) {
                            try {
                                format.getString("replaygain_track_gain")?.replace("dB", "")?.trim()?.toFloatOrNull() ?: 0f
                            } catch (_: Exception) { 0f }
                        }
                        if (!gain.isNaN() && !gain.isInfinite()) {
                            measuredLufs = TARGET_LOUDNESS_LUFS - gain
                        }
                    }
                    if (format.containsKey("replaygain_track_peak")) {
                        val peak = try {
                            format.getFloat("replaygain_track_peak")
                        } catch (_: Exception) {
                            try {
                                format.getString("replaygain_track_peak")?.toFloatOrNull() ?: DEFAULT_ASSUMED_PEAK_DBTP
                            } catch (_: Exception) { DEFAULT_ASSUMED_PEAK_DBTP }
                        }
                        if (!peak.isNaN() && !peak.isInfinite()) {
                            measuredPeak = peak
                        }
                    }
                    break
                }
            }
            extractor.release()
        } catch (_: Exception) {}

        if (measuredLufs.isNaN() || measuredLufs.isInfinite()) {
            measuredLufs = DEFAULT_ASSUMED_LUFS
        }
        if (measuredPeak.isNaN() || measuredPeak.isInfinite()) {
            measuredPeak = DEFAULT_ASSUMED_PEAK_DBTP
        }

        // Phase 2: Gain Calculation with True Peak Safety Guardrail
        val rawTargetGainDb = TARGET_LOUDNESS_LUFS - measuredLufs
        val predictedPeakDbTp = measuredPeak + rawTargetGainDb

        val safeTargetGainDb = if (predictedPeakDbTp > MAX_TRUE_PEAK_DBTP) {
            MAX_TRUE_PEAK_DBTP - measuredPeak
        } else {
            rawTargetGainDb
        }

        // Phase 3: Linear Multiplier (Linear_Gain = 10 ^ (Target_Gain_dB / 20))
        val rawLinearGain = 10.0f.pow(safeTargetGainDb / 20.0f)
        val linearGain = if (rawLinearGain.isNaN() || rawLinearGain.isInfinite()) 1.0f else rawLinearGain.coerceIn(0.15f, 1.85f)

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
        val mult = cachedProfile.linearGainMultiplier
        val safeMult = if (mult.isNaN() || mult.isInfinite()) 1.0f else mult
        val res = rawVolumeFactor * safeMult
        return if (res.isNaN() || res.isInfinite()) rawVolumeFactor else res.coerceIn(0f, 1.85f)
    }
}
