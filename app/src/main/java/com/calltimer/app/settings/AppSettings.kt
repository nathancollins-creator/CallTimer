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
        private const val KEY_WARNING_ENABLED = "warning_enabled"
        private const val KEY_VIBRATE_ENABLED = "vibrate_enabled"
        private const val KEY_ALERT_SOUND_MODE = "alert_sound_mode"
        private const val KEY_CUSTOM_RINGTONE_URI = "custom_ringtone_uri"
        private const val KEY_WHATSAPP_ENABLED = "whatsapp_enabled"

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
