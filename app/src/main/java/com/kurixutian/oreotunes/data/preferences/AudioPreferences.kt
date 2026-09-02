package com.kurixutian.oreotunes.data.preferences

import android.content.Context
import android.content.SharedPreferences

class AudioPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("oreo_audio_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"
    }

    var isCrossfadeEnabled: Boolean
        get() = prefs.getBoolean(KEY_CROSSFADE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CROSSFADE_ENABLED, value).apply()

    var crossfadeDurationSec: Int
        get() = prefs.getInt(KEY_CROSSFADE_DURATION, 4)
        set(value) = prefs.edit().putInt(KEY_CROSSFADE_DURATION, value).apply()
}
