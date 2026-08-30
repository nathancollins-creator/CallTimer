package com.calltimer.app.notification

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.calltimer.app.settings.AppSettings
import com.calltimer.app.util.EventLog
import java.util.Locale

/**
 * Handles everything about the time-limit alert that ISN'T the visual
 * notification: sound, spoken announcement, and vibration. This is a
 * separate class from CallTimerNotification on purpose - a spoken
 * announcement or a ToneGenerator-produced siren cannot be expressed as a
 * NotificationChannel's "sound" (channels only accept a Uri to a media file),
 * so those two modes have to be played imperatively, right here, at the
 * moment the alert fires - not attached to the notification at all.
 */
object AlertPlayer {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val limitVibratePattern = longArrayOf(0, 500, 200, 500, 200, 500)

    /** Call once, early (Application.onCreate), so the TTS engine has time to
     * initialize before the first real alert - init is asynchronous and can
     * take a second or two. */
    fun preload(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
            if (!ttsReady) EventLog.add("Text-to-speech engine failed to initialize (spoken alerts won't be available)")
        }
    }

    fun playLimitAlert(context: Context, settings: AppSettings) {
        if (settings.vibrateEnabled) vibrate(context)

        when (settings.alertSoundMode) {
            AlertSoundMode.DEFAULT -> playRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            AlertSoundMode.CUSTOM -> {
                val uriString = settings.customRingtoneUri
                if (uriString != null) {
                    playRingtone(context, Uri.parse(uriString))
                } else {
                    EventLog.add("No custom ringtone chosen yet — falling back to default alarm sound")
                    playRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                }
            }
            AlertSoundMode.SIREN -> playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 3000)
            AlertSoundMode.WARBLE -> playTone(ToneGenerator.TONE_SUP_RINGTONE, 3000)
            AlertSoundMode.SPOKEN -> speak(context, "Your call time limit has been reached.")
        }
    }

    private fun playRingtone(context: Context, uri: Uri?) {
        if (uri == null) {
            EventLog.add("No ringtone available to play for the alert")
            return
        }
        try {
            val ringtone = RingtoneManager.getRingtone(context, uri)
            if (ringtone == null) {
                EventLog.add("Could not load the selected ringtone — it may have been removed")
                return
            }
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
        } catch (t: Throwable) {
            EventLog.add("Failed to play alert ringtone: ${t.message}")
        }
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        try {
            val generator = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            generator.startTone(toneType, durationMs)
            Handler(Looper.getMainLooper()).postDelayed({
                try { generator.release() } catch (_: Throwable) {}
            }, durationMs + 300L)
        } catch (t: Throwable) {
            EventLog.add("Failed to play alert tone: ${t.message}")
        }
    }

    private fun speak(context: Context, text: String) {
        if (tts == null) preload(context)
        if (!ttsReady) {
            EventLog.add("Spoken alert skipped — text-to-speech engine wasn't ready in time")
            return
        }
        tts?.language = Locale.getDefault()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "call_timer_limit_alert")
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(limitVibratePattern, -1))
    }
}
