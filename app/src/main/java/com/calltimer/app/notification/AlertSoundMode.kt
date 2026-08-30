package com.calltimer.app.notification

/**
 * How the time-limit alert should sound. Deliberately separate from whether
 * a notification is shown at all (that's controlled by the alert channel
 * being posted to, always) and separate from vibration (its own toggle,
 * applies to every mode here).
 */
enum class AlertSoundMode(val label: String) {
    DEFAULT("Default alarm sound"),
    CUSTOM("Custom ringtone (choose from phone)"),
    SIREN("Siren"),
    WARBLE("Warble"),
    SPOKEN("Spoken announcement")
}
