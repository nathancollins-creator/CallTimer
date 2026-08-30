package com.calltimer.app.settings

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("call_timer_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DURATION_SECONDS = "duration_seconds"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_WARNING_ENABLED = "warning_enabled"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATE_ENABLED = "vibrate_enabled"

        const val DEFAULT_DURATION_SECONDS = 10 * 60 // 10:00, per spec
        const val WARNING_LEAD_SECONDS = 60           // "warn 1 minute before"
    }

    var durationSeconds: Int
        get() = prefs.getInt(KEY_DURATION_SECONDS, DEFAULT_DURATION_SECONDS)
        set(value) = prefs.edit().putInt(KEY_DURATION_SECONDS, value).apply()

    var timerEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var warningEnabled: Boolean
        get() = prefs.getBoolean(KEY_WARNING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WARNING_ENABLED, value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var vibrateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE_ENABLED, value).apply()
}
