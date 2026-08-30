package com.calltimer.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.calltimer.app.settings.AppSettings

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == CallTimerNotification.ACTION_DISABLE) {
            AppSettings(context).timerEnabled = false
            CallTimerService.stop(context)
        }
    }
}
