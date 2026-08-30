package com.calltimer.app.util

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * In-memory, on-device-only event log for the Test/Debug screen. Nothing
 * here is written to disk beyond the process lifetime, and nothing is ever
 * transmitted off the device.
 */
object EventLog {

    private const val MAX_ENTRIES = 200
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val entries = ArrayDeque<String>()
    private val listeners = mutableListOf<(List<String>) -> Unit>()

    @Synchronized
    fun add(message: String) {
        val line = "${timeFormat.format(System.currentTimeMillis())} — $message"
        entries.addLast(line)
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
        val snapshot = entries.toList()
        listeners.forEach { it(snapshot) }
    }

    @Synchronized
    fun snapshot(): List<String> = entries.toList()

    @Synchronized
    fun addListener(listener: (List<String>) -> Unit) {
        listeners.add(listener)
        listener(snapshot())
    }

    @Synchronized
    fun removeListener(listener: (List<String>) -> Unit) {
        listeners.remove(listener)
    }
}
