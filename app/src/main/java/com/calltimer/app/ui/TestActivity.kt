package com.calltimer.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.calltimer.app.call.CallDirection
import com.calltimer.app.call.CallSource
import com.calltimer.app.call.CallState
import com.calltimer.app.call.CallTimerEngine
import com.calltimer.app.call.CallTimerListener
import com.calltimer.app.call.CallTimerSnapshot
import com.calltimer.app.databinding.ActivityTestBinding
import com.calltimer.app.util.EventLog

class TestActivity : AppCompatActivity(), CallTimerListener {

    private lateinit var binding: ActivityTestBinding
    private var currentTestToken: Any? = null

    private val logListener: (List<String>) -> Unit = { lines ->
        runOnUiThread { binding.eventLogText.text = lines.joinToString("\n") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startTestButton.setOnClickListener { startTest() }
        binding.endTestButton.setOnClickListener { endTest() }
    }

    private fun selectedTestSeconds(): Int = when (binding.testDurationGroup.checkedRadioButtonId) {
        binding.test10s.id -> 10
        binding.test30s.id -> 30
        binding.test1m.id -> 60
        binding.test5m.id -> 300
        binding.test10m.id -> 600
        else -> 10
    }

    private fun startTest() {
        val seconds = selectedTestSeconds()
        val token = Any()
        currentTestToken = token
        EventLog.add("[SIMULATED] Test call started ($seconds s)")
        CallTimerEngine.start(
            source = CallSource.TEST,
            direction = CallDirection.UNKNOWN,
            token = token,
            durationSecondsOverride = seconds,
            simulated = true
        )
    }

    private fun endTest() {
        currentTestToken?.let {
            EventLog.add("[SIMULATED] Test call ended by user")
            CallTimerEngine.ended(it)
        }
        currentTestToken = null
    }

    override fun onStart() {
        super.onStart()
        CallTimerEngine.addListener(this)
        EventLog.addListener(logListener)
    }

    override fun onStop() {
        super.onStop()
        CallTimerEngine.removeListener(this)
        EventLog.removeListener(logListener)
    }

    override fun onSnapshot(snapshot: CallTimerSnapshot) {
        val detection = if (snapshot.state == CallState.CONNECTED) "CONNECTED" else "NOT CONNECTED"
        val type = when {
            snapshot.state != CallState.CONNECTED -> "—"
            snapshot.source == CallSource.TEST -> "SIMULATED"
            else -> "CELLULAR (${snapshot.direction.name})"
        }
        val timerStatus = when (snapshot.state) {
            CallState.CONNECTED -> if (snapshot.limitFired) "LIMIT REACHED (still running)" else "RUNNING"
            else -> "STOPPED"
        }
        val timer = "${CallTimerEngine.format(snapshot.elapsedSeconds)} elapsed / ${CallTimerEngine.format(snapshot.totalSeconds)} limit"
        val lastAlert = when {
            snapshot.limitFired -> "TIME LIMIT REACHED"
            snapshot.warningFired -> "1-minute warning fired"
            else -> "Not yet triggered"
        }

        binding.debugFields.text = "Call detection: $detection\n" +
            "Call type: $type\n" +
            "Timer: $timer\n" +
            "Timer status: $timerStatus\n" +
            "Last alert: $lastAlert\n" +
            "Last event: ${EventLog.snapshot().lastOrNull() ?: "—"}"
    }
}
