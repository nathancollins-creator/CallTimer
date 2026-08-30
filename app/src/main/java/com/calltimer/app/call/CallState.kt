package com.calltimer.app.call

/**
 * IDLE -> RINGING -> CONNECTED -> ENDING -> ENDED   (incoming)
 * IDLE -> DIALING -> CONNECTED -> ENDING -> ENDED   (outgoing)
 * Only CONNECTED starts the timer. ENDED immediately stops it. There is no
 * WARNING/FAILED state in this version - warnings and the limit alert are
 * just notifications fired at fixed points during CONNECTED, not states of
 * their own, since nothing about them changes what the timer does next.
 */
enum class CallState {
    IDLE,
    RINGING,
    DIALING,
    CONNECTED,
    ENDING,
    ENDED
}

enum class CallSource {
    CELLULAR,
    TEST
    // WHATSAPP intentionally left out of this version - see README "What's
    // deferred to a later version". CallTimerEngine takes a CallSource so a
    // future WhatsApp detector can plug into the exact same engine without
    // changing anything in this file or in CallTimerEngine.
}

enum class CallDirection {
    INCOMING,
    OUTGOING,
    UNKNOWN
}
