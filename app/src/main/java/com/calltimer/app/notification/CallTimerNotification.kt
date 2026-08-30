package com.calltimer.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.calltimer.app.R
import com.calltimer.app.call.CallState
import com.calltimer.app.call.CallTimerEngine
import com.calltimer.app.call.CallTimerSnapshot

/**
 * IMPORTANT ANDROID DETAIL: on API 26+ a notification's sound and vibration
 * are fixed by whichever NotificationChannel it's posted to - they CANNOT be
 * overridden per-notification via NotificationCompat.Builder.setSound()/
 * setVibrate() (those calls are silently ignored once a channel exists; they
 * only ever worked pre-Oreo). So a "Sound on/off, Vibrate on/off" toggle can
 * only be implemented correctly by having one channel PER combination and
 * choosing which channel to post to at alert time. That's what the four
 * CHANNEL_LIMIT_* channels below are for.
 */
object CallTimerNotification {

    const val CHANNEL_STATUS = "call_timer_status"
    const val CHANNEL_WARNING = "call_timer_warning"
    private const val CHANNEL_LIMIT_BOTH = "call_timer_limit_both"
    private const val CHANNEL_LIMIT_SOUND_ONLY = "call_timer_limit_sound"
    private const val CHANNEL_LIMIT_VIBRATE_ONLY = "call_timer_limit_vibrate"
    private const val CHANNEL_LIMIT_SILENT = "call_timer_limit_silent"

    const val STATUS_NOTIFICATION_ID = 4201
    const val ALERT_NOTIFICATION_ID = 4202
    const val ACTION_DISABLE = "com.calltimer.app.action.DISABLE_TIMER"

    private val limitVibratePattern = longArrayOf(0, 500, 200, 500, 200, 500)

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        fun createIfMissing(id: String, name: String, desc: String, importance: Int, configure: NotificationChannel.() -> Unit) {
            if (manager.getNotificationChannel(id) == null) {
                val channel = NotificationChannel(id, name, importance).apply {
                    description = desc
                    setShowBadge(false)
                }
                channel.configure()
                manager.createNotificationChannel(channel)
            }
        }

        createIfMissing(CHANNEL_STATUS, context.getString(R.string.channel_name_status), context.getString(R.string.channel_desc_status), NotificationManager.IMPORTANCE_LOW) {
            setSound(null, null)
        }

        createIfMissing(CHANNEL_WARNING, "Call Timer — 1 minute warning", "The one-minute-remaining warning", NotificationManager.IMPORTANCE_DEFAULT) {
            setSound(notificationSound, alarmAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400)
        }

        createIfMissing(CHANNEL_LIMIT_BOTH, "Call Timer — time limit (sound + vibrate)", "Time-limit alert with sound and vibration", NotificationManager.IMPORTANCE_HIGH) {
            setSound(alarmSound, alarmAttrs)
            enableVibration(true)
            vibrationPattern = limitVibratePattern
        }
        createIfMissing(CHANNEL_LIMIT_SOUND_ONLY, "Call Timer — time limit (sound only)", "Time-limit alert with sound, no vibration", NotificationManager.IMPORTANCE_HIGH) {
            setSound(alarmSound, alarmAttrs)
            enableVibration(false)
        }
        createIfMissing(CHANNEL_LIMIT_VIBRATE_ONLY, "Call Timer — time limit (vibrate only)", "Time-limit alert with vibration, no sound", NotificationManager.IMPORTANCE_HIGH) {
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = limitVibratePattern
        }
        createIfMissing(CHANNEL_LIMIT_SILENT, "Call Timer — time limit (silent)", "Time-limit alert, notification only", NotificationManager.IMPORTANCE_HIGH) {
            setSound(null, null)
            enableVibration(false)
        }
    }

    fun buildStatus(context: Context, snapshot: CallTimerSnapshot): Notification {
        ensureChannels(context)
        val (title, text) = statusContentFor(snapshot)

        val disableIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_DISABLE
        }
        val disablePendingIntent = PendingIntent.getBroadcast(
            context, 0, disableIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(0, "Disable", disablePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun buildWarning(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_WARNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Call Timer: 1 minute remaining")
            .setContentText("Your call will hit its time limit in 1 minute.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    fun buildLimitReached(context: Context, soundEnabled: Boolean, vibrateEnabled: Boolean): Notification {
        ensureChannels(context)
        val channelId = when {
            soundEnabled && vibrateEnabled -> CHANNEL_LIMIT_BOTH
            soundEnabled -> CHANNEL_LIMIT_SOUND_ONLY
            vibrateEnabled -> CHANNEL_LIMIT_VIBRATE_ONLY
            else -> CHANNEL_LIMIT_SILENT
        }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("CALL TIMER: TIME LIMIT REACHED")
            .setContentText("You've reached your call time limit. Call Timer will not end the call — hang up when you're ready.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    private fun statusContentFor(s: CallTimerSnapshot): Pair<String, String> {
        return when (s.state) {
            CallState.IDLE -> "Call Timer" to "Watching for calls"
            CallState.RINGING -> "Call Timer" to "Incoming call ringing…"
            CallState.DIALING -> "Call Timer" to "Call dialing…"
            CallState.CONNECTED -> {
                val label = if (s.limitFired) "TIME LIMIT REACHED" else "${CallTimerEngine.format(s.remainingSeconds)} remaining"
                (if (s.isSimulated) "Call Timer (TEST MODE)" else "Call Timer Active") to label
            }
            CallState.ENDING, CallState.ENDED -> "Call Timer" to "Call ended"
        }
    }
}
