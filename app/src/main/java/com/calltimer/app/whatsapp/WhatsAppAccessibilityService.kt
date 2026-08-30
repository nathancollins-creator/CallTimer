package com.calltimer.app.whatsapp

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.calltimer.app.call.CallDirection
import com.calltimer.app.call.CallSource
import com.calltimer.app.call.CallTimerEngine
import com.calltimer.app.settings.AppSettings
import com.calltimer.app.util.EventLog

/**
 * Scope, by design:
 *  - accessibility_service_config.xml restricts android:packageNames to just
 *    com.whatsapp and com.whatsapp.w4b - this service gets ZERO events from
 *    any other app on the device.
 *  - It only listens for window-level events (state/content changed), not
 *    text-changed/keyboard events, so it isn't positioned to capture what the
 *    user types.
 *  - It NEVER calls performAction() on anything. Unlike the earlier
 *    termination-capable design, this version only reads what's on screen -
 *    there is no code path here that can tap, click, or otherwise control
 *    WhatsApp in any way.
 *  - "WhatsApp is open" is never sufficient by itself to start the timer;
 *    only WhatsAppCallDetector reporting callActive=true does that.
 */
class WhatsAppAccessibilityService : AccessibilityService() {

    private var inCallToken: Any? = null
    private var lastObservedDirection: CallDirection = CallDirection.UNKNOWN

    override fun onServiceConnected() {
        super.onServiceConnected()
        EventLog.add("WhatsApp accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg != WhatsAppCallDetector.PACKAGE_CONSUMER && pkg != WhatsAppCallDetector.PACKAGE_BUSINESS) return

        val settings = AppSettings(applicationContext)
        if (!settings.timerEnabled || !settings.whatsappEnabled) return

        val root = rootInActiveWindow ?: return
        val result = WhatsAppCallDetector.analyze(root)

        if (result.callActive) {
            if (inCallToken == null) {
                EventLog.add("WhatsApp call detected (${result.signal})")
                val token = Any()
                inCallToken = token
                CallTimerEngine.start(
                    source = CallSource.WHATSAPP,
                    direction = lastObservedDirection,
                    token = token
                )
            }
        } else {
            if (result.likelyDirection != CallDirection.UNKNOWN) {
                lastObservedDirection = result.likelyDirection
            }
            val active = inCallToken
            if (active != null) {
                EventLog.add("WhatsApp call screen no longer detected — treating as ended")
                CallTimerEngine.ended(active)
                inCallToken = null
                lastObservedDirection = CallDirection.UNKNOWN
            }
        }
    }

    override fun onInterrupt() {
        EventLog.add("WhatsApp accessibility service interrupted by the system")
    }
}
