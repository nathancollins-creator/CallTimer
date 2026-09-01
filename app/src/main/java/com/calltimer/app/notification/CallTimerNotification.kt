package com.calltimer.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.calltimer.app.R
import com.calltimer.app.call.CallSource
import com.calltimer.app.call.CallState
import com.calltimer.app.call.CallTimerEngine
import com.calltimer.app.call.CallTimerSnapshot

/**
 * These channels are all visual-only now (no sound, no vibration attached to
 * any of them) - see AlertPlayer.kt for how the time-limit alert's sound,
 * speech, and vibration actually get played. The one exception is
 * CHANNEL_WARNING, which keeps a plain default notification sound since the
 * 1-minute warning was never asked to have selectable styles - it's just an
 * on/off notification per the original brief.
 */
object CallTimerNotification {

    const val CHANNEL_STATUS = "call_timer_status"
    const val CHANNEL_WARNING = "call_timer_warning"
    const val CHANNEL_LIMIT_VISUAL = "call_timer_limit_visual"

    const val STATUS_NOTIFICATION_ID = 4201
    const val ALERT_NOTIFICATION_ID = 4202
    const val ACTION_DISABLE = "com.calltimer.app.action.DISABLE_TIMER"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)

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
        createIfMissing(CHANNEL_WARNING, "CallGuard — warnings", "Warnings before your call limit is reached", NotificationManager.IMPORTANCE_DEFAULT) {
            // Deliberately left with its system default sound/vibration - the
            // warning has always just been on/off, never a chosen style.
        }
        createIfMissing(CHANNEL_LIMIT_VISUAL, "CallGuard — call limit reached", "The call-limit alert (visual only - see in-app Alert style setting for sound)", NotificationManager.IMPORTANCE_HIGH) {
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

    fun buildWarning(context: Context, snapshot: CallTimerSnapshot, warningPointSeconds: Int): Notification {
        ensureChannels(context)
        val label = CallTimerEngine.formatMinutesLabel(warningPointSeconds)
        return NotificationCompat.Builder(context, CHANNEL_WARNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("CallGuard: $label remaining")
            .setContentText("Your ${sourceLabel(snapshot.source)} call will hit its limit in $label.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    /** Visual only - AlertPlayer.playLimitAlert() handles sound/speech/vibration separately. */
    fun buildLimitReached(context: Context, snapshot: CallTimerSnapshot): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_LIMIT_VISUAL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("CALLGUARD: CALL LIMIT REACHED")
            .setContentText("Your ${sourceLabel(snapshot.source)} call has reached its limit. CallGuard will not end the call — hang up when you're ready.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    private fun sourceLabel(source: CallSource): String = when (source) {
        CallSource.CELLULAR -> "phone"
        CallSource.WHATSAPP -> "WhatsApp"
        CallSource.TEST -> "simulated"
    }

    private fun statusContentFor(s: CallTimerSnapshot): Pair<String, String> {
        return when (s.state) {
            CallState.IDLE -> "CallGuard" to "Watching for calls"
            CallState.RINGING -> "CallGuard" to "Incoming call ringing…"
            CallState.DIALING -> "CallGuard" to "Call dialing…"
            CallState.CONNECTED -> {
                val label = if (s.limitFired) "CALL LIMIT REACHED" else "${CallTimerEngine.format(s.remainingSeconds)} remaining"
                (if (s.isSimulated) "CallGuard (TEST MODE)" else "CallGuard Active") to label
            }
            CallState.ENDING, CallState.ENDED -> "CallGuard" to "Call ended"
        }
    }
}
