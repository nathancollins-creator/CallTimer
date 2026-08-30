package com.calltimer.app.call

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.calltimer.app.settings.AppSettings
import com.calltimer.app.util.EventLog

data class CallTimerSnapshot(
    val state: CallState = CallState.IDLE,
    val source: CallSource = CallSource.CELLULAR,
    val direction: CallDirection = CallDirection.UNKNOWN,
    val totalSeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val warningFired: Boolean = false,
    val limitFired: Boolean = false,
    val isSimulated: Boolean = false
) {
    val remainingSeconds: Int get() = (totalSeconds - elapsedSeconds).coerceAtLeast(0)
}

interface CallTimerListener {
    fun onSnapshot(snapshot: CallTimerSnapshot)
}

/**
 * DETECT -> COUNT -> ALERT. That's the whole job. There is no code path in
 * this object, or anywhere it talks to, that can end a call - see the
 * project README for why that was deliberately removed.
 */
object CallTimerEngine {

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<CallTimerListener>()

    private var snapshot = CallTimerSnapshot()
    private var activeToken: Any? = null
    private var appContext: Context? = null

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, 1000)
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun addListener(listener: CallTimerListener) {
        listeners.add(listener)
        listener.onSnapshot(snapshot)
    }

    fun removeListener(listener: CallTimerListener) {
        listeners.remove(listener)
    }

    fun currentSnapshot() = snapshot

    /**
     * Purely informational: lets the debug screen show RINGING/DIALING while
     * a call hasn't connected yet. Never starts the timer and is ignored
     * while a real timer is already running, so it can't clobber live state.
     */
    fun setPreConnectState(state: CallState) {
        if (activeToken != null) return
        if (snapshot.state == state) return
        snapshot = snapshot.copy(state = state)
        publish()
    }

    /** Called the moment a call is CONFIRMED connected - never on ringing/dialing. */
    fun start(
        source: CallSource,
        direction: CallDirection,
        token: Any,
        durationSecondsOverride: Int? = null,
        simulated: Boolean = false
    ) {
        if (activeToken == token && snapshot.state == CallState.CONNECTED) return

        val ctx = appContext ?: return
        val settings = AppSettings(ctx)
        val duration = durationSecondsOverride ?: settings.durationSeconds

        activeToken = token
        snapshot = CallTimerSnapshot(
            state = CallState.CONNECTED,
            source = source,
            direction = direction,
            totalSeconds = duration,
            elapsedSeconds = 0,
            warningFired = false,
            limitFired = false,
            isSimulated = simulated
        )
        EventLog.add("Call connected (${direction.name.lowercase()}) — timer started at ${format(duration)}")
        publish()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000)
    }

    /** Call ended - by either party, at any point. Always stops the timer immediately. */
    fun ended(token: Any) {
        if (activeToken != token) return
        handler.removeCallbacks(tick)
        EventLog.add("Call ended — timer stopped")
        snapshot = snapshot.copy(state = CallState.ENDED)
        publish()
        resetSoon()
    }

    /** User turned Call Timer off entirely. */
    fun stopAll() {
        handler.removeCallbacks(tick)
        activeToken = null
        snapshot = CallTimerSnapshot()
        publish()
    }

    private fun onTick() {
        val ctx = appContext ?: return
        val settings = AppSettings(ctx)

        val elapsed = snapshot.elapsedSeconds + 1
        val remaining = (snapshot.totalSeconds - elapsed).coerceAtLeast(0)
        var warningFired = snapshot.warningFired
        var limitFired = snapshot.limitFired

        if (!warningFired && settings.warningEnabled && remaining <= AppSettings.WARNING_LEAD_SECONDS) {
            warningFired = true
            EventLog.add("Warning issued — 1 minute remaining")
        }
        if (!limitFired && remaining <= 0) {
            limitFired = true
            EventLog.add("TIME LIMIT REACHED")
        }

        snapshot = snapshot.copy(elapsedSeconds = elapsed, warningFired = warningFired, limitFired = limitFired)
        publish()

        if (limitFired) {
            // Stop counting once the limit alert has fired - the call is still
            // going (we never end it), so there's nothing further to measure.
            // We keep listening for the natural end via ended(), separately.
            handler.removeCallbacks(tick)
        }
    }

    private fun resetSoon() {
        val tokenAtSchedule = activeToken
        handler.postDelayed({
            if (activeToken === tokenAtSchedule) {
                activeToken = null
                snapshot = CallTimerSnapshot()
                publish()
            }
        }, 3000)
    }

    private fun publish() {
        listeners.toList().forEach { it.onSnapshot(snapshot) }
    }

    fun format(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%d:%02d".format(m, s)
    }
}
