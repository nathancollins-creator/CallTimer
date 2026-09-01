package com.calltimer.app.settings

import android.content.Context
import android.content.SharedPreferences
import com.calltimer.app.notification.AlertSoundMode

class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("call_timer_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DURATION_SECONDS = "duration_seconds"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_WARNING_POINTS = "warning_points_seconds"
        private const val KEY_VIBRATE_ENABLED = "vibrate_enabled"
        private const val KEY_ALERT_SOUND_MODE = "alert_sound_mode"
        private const val KEY_CUSTOM_RINGTONE_URI = "custom_ringtone_uri"
        private const val KEY_WHATSAPP_ENABLED = "whatsapp_enabled"

        const val DEFAULT_DURATION_SECONDS = 20 * 60 // 20:00 - CallGuard default per spec
        const val FIVE_MIN_WARNING_SECONDS = 5 * 60
        const val ONE_MIN_WARNING_SECONDS = 60
        private val DEFAULT_WARNING_POINTS = setOf(FIVE_MIN_WARNING_SECONDS, ONE_MIN_WARNING_SECONDS)
    }

    var durationSeconds: Int
        get() = prefs.getInt(KEY_DURATION_SECONDS, DEFAULT_DURATION_SECONDS)
        set(value) = prefs.edit().putInt(KEY_DURATION_SECONDS, value).apply()

    var timerEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /**
     * Lead times (in seconds-before-limit) at which a warning fires. Stored
     * as a set so the engine stays generic - today's UI only exposes the two
     * points the spec asks for (5 min / 1 min), each independently
     * toggleable, but nothing here hardcodes that there are exactly two.
     */
    var warningPointsSeconds: Set<Int>
        get() {
            val raw = prefs.getString(KEY_WARNING_POINTS, null) ?: return DEFAULT_WARNING_POINTS
            return raw.split(',').mapNotNull { it.toIntOrNull() }.toSet()
        }
        set(value) = prefs.edit().putString(KEY_WARNING_POINTS, value.joinToString(",")).apply()

    fun setWarningPointEnabled(seconds: Int, enabled: Boolean) {
        val current = warningPointsSeconds
        warningPointsSeconds = if (enabled) current + seconds else current - seconds
    }

    /** Applies to every alert style below - a spoken/tone/ringtone alert can still be paired with vibration. */
    var vibrateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE_ENABLED, value).apply()

    var alertSoundMode: AlertSoundMode
        get() = AlertSoundMode.entries.firstOrNull { it.name == prefs.getString(KEY_ALERT_SOUND_MODE, null) }
            ?: AlertSoundMode.DEFAULT
        set(value) = prefs.edit().putString(KEY_ALERT_SOUND_MODE, value.name).apply()

    /** Only meaningful when alertSoundMode == CUSTOM. Null until the user has picked one. */
    var customRingtoneUri: String?
        get() = prefs.getString(KEY_CUSTOM_RINGTONE_URI, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_RINGTONE_URI, value).apply()

    var whatsappEnabled: Boolean
        get() = prefs.getBoolean(KEY_WHATSAPP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_WHATSAPP_ENABLED, value).apply()
}
