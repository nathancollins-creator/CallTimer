package com.calltimer.app.call

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.calltimer.app.util.EventLog

/**
 * ============================================================================
 *  STEP 1 FINDING: the mechanism this class uses, and its one honest limit
 * ============================================================================
 * TelephonyManager's call-state notifications (IDLE / RINGING / OFFHOOK) are
 * available to any app holding the single READ_PHONE_STATE permission - no
 * special role, no becoming the default dialer, no accessibility service.
 * That's the entire reason Version 2 doesn't need any of that setup.
 *
 * What these three states can and cannot tell us:
 *
 *   INCOMING calls: fully reliable. RINGING means the phone is ringing and
 *   NOT yet answered. The transition RINGING -> OFFHOOK happens at the exact
 *   moment the call is answered. This is an accurate, unambiguous signal.
 *
 *   OUTGOING calls: this is the one real limitation, and it's an Android
 *   platform restriction, not a bug we can code around at this permission
 *   level. IDLE -> OFFHOOK fires the instant YOU start dialing out - while
 *   it's still ringing on the other end - not when the other person answers.
 *   Android does not expose a separate "outgoing call answered" signal to
 *   apps that aren't the active InCallService (which requires holding
 *   RoleManager.ROLE_DIALER - the "become the default phone app" role we
 *   deliberately removed in this version to simplify setup).
 *
 * Consequence: for OUTGOING calls, this app starts the timer a few seconds
 * early (as soon as you dial), not from the moment the other person picks
 * up. For INCOMING calls, it is exact. This is documented up front rather
 * than silently shipped - see the README's "Known limitations" and the
 * final WORKING/PARTIALLY WORKING verdicts.
 * ============================================================================
 */
class CellularCallDetector(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var activeToken: Any? = null

    private var modernCallback: TelephonyCallback? = null
    private var legacyListener: PhoneStateListener? = null

    fun start() {
        if (Build.VERSION.SDK_INT >= 31) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handleState(state)
            }
            modernCallback = callback
            telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Suppress("DEPRECATION")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = handleState(state)
            }
            legacyListener = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
        EventLog.add("Cellular call detector started (watching for calls)")
    }

    fun stop() {
        modernCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        modernCallback = null
        legacyListener?.let {
            @Suppress("DEPRECATION")
            telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        legacyListener = null
        activeToken?.let { CallTimerEngine.ended(it) }
        activeToken = null
        EventLog.add("Cellular call detector stopped")
    }

    private fun handleState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                if (lastState != TelephonyManager.CALL_STATE_IDLE) {
                    EventLog.add("Call ended (state -> IDLE)")
                    activeToken?.let { CallTimerEngine.ended(it) }
                    activeToken = null
                    CallTimerEngine.setPreConnectState(CallState.IDLE)
                }
            }

            TelephonyManager.CALL_STATE_RINGING -> {
                EventLog.add("Incoming call ringing (not answered yet — timer not started)")
                CallTimerEngine.setPreConnectState(CallState.RINGING)
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                when (lastState) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        // Exact, reliable: this is the moment the incoming call was answered.
                        EventLog.add("Incoming call answered")
                        val token = Any()
                        activeToken = token
                        CallTimerEngine.start(CallSource.CELLULAR, CallDirection.INCOMING, token)
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        // Best-effort: see the class doc comment above. We log the
                        // limitation every time it applies, not just in the README.
                        EventLog.add("Outgoing call dialing — starting timer now (Android can't detect the exact answer moment for outgoing calls without dialer-app access; see README)")
                        val token = Any()
                        activeToken = token
                        CallTimerEngine.start(CallSource.CELLULAR, CallDirection.OUTGOING, token)
                    }
                    else -> { /* already OFFHOOK - ignore duplicate callback */ }
                }
            }
        }
        lastState = state
    }
}
