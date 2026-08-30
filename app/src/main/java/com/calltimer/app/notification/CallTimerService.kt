package com.calltimer.app.notification

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.calltimer.app.call.CallTimerEngine
import com.calltimer.app.call.CallTimerListener
import com.calltimer.app.call.CallTimerSnapshot
import com.calltimer.app.call.CellularCallDetector
import com.calltimer.app.settings.AppSettings
import com.calltimer.app.util.EventLog

/**
 * Runs for as long as the user has Call Timer switched ON (not just during a
 * call) - it has to, since that's the only way to keep listening for the
 * next call while the app is backgrounded. A visible, low-priority
 * notification is the trade Android requires for that: without one, Android
 * would stop this service within seconds of the app losing focus.
 */
class CallTimerService : Service(), CallTimerListener {

    private var detector: CellularCallDetector? = null
    private var previousSnapshot: CallTimerSnapshot = CallTimerSnapshot()

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, CallTimerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallTimerService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallTimerNotification.ensureChannels(this)
        val initial = CallTimerNotification.buildStatus(this, CallTimerEngine.currentSnapshot())
        ServiceCompat.startForeground(
            this,
            CallTimerNotification.STATUS_NOTIFICATION_ID,
            initial,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        previousSnapshot = CallTimerEngine.currentSnapshot()
        CallTimerEngine.addListener(this)

        // MainActivity checks this permission before the FIRST call to
        // start(), but Android can revive this service later (START_STICKY,
        // after the process was killed) by calling onCreate() directly,
        // bypassing that check entirely - and the permission could in theory
        // have been revoked in system Settings in the meantime. Registering
        // the telephony listener without it would throw SecurityException
        // and crash the service, so we guard it explicitly here too.
        val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPhoneStatePermission) {
            detector = CellularCallDetector(this).also { it.start() }
        } else {
            EventLog.add("Missing READ_PHONE_STATE permission — stopping (grant it from Permissions / Setup, then Enable again)")
            AppSettings(this).timerEnabled = false
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onSnapshot(snapshot: CallTimerSnapshot) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(CallTimerNotification.STATUS_NOTIFICATION_ID, CallTimerNotification.buildStatus(this, snapshot))

        val settings = AppSettings(this)
        val justWarned = snapshot.warningFired && !previousSnapshot.warningFired
        val justReachedLimit = snapshot.limitFired && !previousSnapshot.limitFired

        if (justWarned && settings.warningEnabled) {
            manager.notify(CallTimerNotification.ALERT_NOTIFICATION_ID, CallTimerNotification.buildWarning(this))
        }
        if (justReachedLimit) {
            manager.notify(
                CallTimerNotification.ALERT_NOTIFICATION_ID,
                CallTimerNotification.buildLimitReached(this, settings.soundEnabled, settings.vibrateEnabled)
            )
        }

        previousSnapshot = snapshot
    }

    override fun onDestroy() {
        detector?.stop()
        detector = null
        CallTimerEngine.removeListener(this)
        CallTimerEngine.stopAll()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
