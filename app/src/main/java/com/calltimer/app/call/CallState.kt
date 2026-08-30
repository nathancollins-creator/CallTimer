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
    WHATSAPP,
    TEST
}

enum class CallDirection {
    INCOMING,
    OUTGOING,
    UNKNOWN
}
