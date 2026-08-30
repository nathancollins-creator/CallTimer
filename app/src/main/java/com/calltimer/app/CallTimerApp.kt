package com.calltimer.app

import android.app.Application
import com.calltimer.app.call.CallTimerEngine
import com.calltimer.app.notification.CallTimerNotification

class CallTimerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CallTimerEngine.init(this)
        CallTimerNotification.ensureChannels(this)
    }
}
