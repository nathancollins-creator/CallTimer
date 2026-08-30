package com.calltimer.app.whatsapp

import android.view.accessibility.AccessibilityNodeInfo
import com.calltimer.app.call.CallDirection

/**
 * ============================================================================
 *  WHY THIS IS THE ONLY WAY, AND WHY IT'S FRAGILE
 * ============================================================================
 * WhatsApp exposes no API, broadcast, or ConnectionService integration for
 * its calls - the only signal available to a third-party app is what's
 * visible on screen via AccessibilityNodeInfo. That means:
 *
 *   1) This WILL break on some future WhatsApp update. There is no version
 *      of this file that's permanently correct.
 *   2) We favor a STRUCTURAL signal over a TEXT signal wherever possible:
 *      WhatsApp's active-call screen shows a running call-duration readout
 *      implemented as a standard android.widget.Chronometer. Matching on
 *      *class name* is far more stable across WhatsApp versions and
 *      languages than matching English strings would be.
 *   3) Resource-id checks below are EXAMPLES based on common WhatsApp builds
 *      at the time this was written - NOT guaranteed for your installed
 *      version. Verify with `adb shell uiautomator dump` (or Android
 *      Studio's Layout Inspector) against a live WhatsApp call on your
 *      phone, and update the lists below - this is the one file you should
 *      need to touch to fix detection if it stops working.
 * ============================================================================
 */
object WhatsAppCallDetector {

    const val PACKAGE_CONSUMER = "com.whatsapp"
    const val PACKAGE_BUSINESS = "com.whatsapp.w4b"

    private val CALL_SCREEN_ID_HINTS = listOf(
        "voip_call_duration",
        "call_duration",
        "voip_incall_duration_view"
    )

    private val RINGING_TEXT_HINTS = listOf("incoming voice call", "incoming call", "voice call")
    private val DIALING_TEXT_HINTS = listOf("calling…", "calling...", "ringing…", "ringing...")

    data class DetectionResult(
        val callActive: Boolean,
        val likelyDirection: CallDirection,
        val signal: String
    )

    fun analyze(root: AccessibilityNodeInfo?): DetectionResult {
        if (root == null) return DetectionResult(false, CallDirection.UNKNOWN, "no root node")

        val chronometer = findFirstByClassName(root, "android.widget.Chronometer")
        if (chronometer != null) {
            return DetectionResult(true, CallDirection.UNKNOWN, "Chronometer node present (call timer running)")
        }

        val idHit = CALL_SCREEN_ID_HINTS.any { hint -> findByViewIdContains(root, hint) != null }
        if (idHit) {
            return DetectionResult(true, CallDirection.UNKNOWN, "resource-id hint matched")
        }

        val text = collectText(root, maxNodes = 400).lowercase()
        val direction = when {
            RINGING_TEXT_HINTS.any { text.contains(it) } -> CallDirection.INCOMING
            DIALING_TEXT_HINTS.any { text.contains(it) } -> CallDirection.OUTGOING
            else -> CallDirection.UNKNOWN
        }
        return DetectionResult(false, direction, if (direction != CallDirection.UNKNOWN) "pre-connect screen text matched" else "no signal")
    }

    private fun findFirstByClassName(node: AccessibilityNodeInfo, className: String, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 40) return null
        if (node.className?.toString() == className) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstByClassName(child, className, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun findByViewIdContains(node: AccessibilityNodeInfo, fragment: String, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 40) return null
        val id = node.viewIdResourceName
        if (id != null && id.contains(fragment, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findByViewIdContains(child, fragment, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo, maxNodes: Int): String {
        val sb = StringBuilder()
        var count = 0
        fun walk(n: AccessibilityNodeInfo) {
            if (count >= maxNodes) return
            count++
            n.text?.let { sb.append(it).append(' ') }
            n.contentDescription?.let { sb.append(it).append(' ') }
            for (i in 0 until n.childCount) {
                if (count >= maxNodes) return
                n.getChild(i)?.let { walk(it) }
            }
        }
        walk(node)
        return sb.toString()
    }
}
