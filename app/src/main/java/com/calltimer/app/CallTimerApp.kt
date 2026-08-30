package com.calltimer.app

import android.app.Application
import com.calltimer.app.call.CallTimerEngine
import com.calltimer.app.notification.AlertPlayer
import com.calltimer.app.notification.CallTimerNotification

class CallTimerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CallTimerEngine.init(this)
        CallTimerNotification.ensureChannels(this)
        // Started here (not lazily at first alert) so the TTS engine has a
        // real chance to finish its async init before a call ever ends -
        // even a short test call gives it plenty of time.
        AlertPlayer.preload(this)
    }
}
