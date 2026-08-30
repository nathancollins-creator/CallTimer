package com.calltimer.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.calltimer.app.call.CallState
import com.calltimer.app.call.CallTimerEngine
import com.calltimer.app.call.CallTimerListener
import com.calltimer.app.call.CallTimerSnapshot
import com.calltimer.app.databinding.ActivityMainBinding
import com.calltimer.app.notification.CallTimerService
import com.calltimer.app.settings.AppSettings

class MainActivity : AppCompatActivity(), CallTimerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = AppSettings(this)

        loadIntoUi()

        binding.durationPresets.setOnCheckedChangeListener { _, checkedId ->
            binding.customDurationMinutes.isEnabled = (checkedId == binding.durationCustom.id)
            val minutes = when (checkedId) {
                binding.duration5.id -> 5
                binding.duration10.id -> 10
                binding.duration15.id -> 15
                binding.duration20.id -> 20
                binding.duration30.id -> 30
                else -> null
            }
            if (minutes != null) settings.durationSeconds = minutes * 60
        }
        binding.customDurationMinutes.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val minutes = binding.customDurationMinutes.text.toString().toIntOrNull()
                if (minutes != null && minutes > 0) settings.durationSeconds = minutes * 60
            }
        }

        binding.warningCheck.setOnCheckedChangeListener { _, checked -> settings.warningEnabled = checked }
        binding.soundCheck.setOnCheckedChangeListener { _, checked -> settings.soundEnabled = checked }
        binding.vibrateCheck.setOnCheckedChangeListener { _, checked -> settings.vibrateEnabled = checked }

        binding.enableButton.setOnClickListener { onEnable() }
        binding.disableButton.setOnClickListener { onDisable() }

        binding.setupButton.setOnClickListener { startActivity(Intent(this, SetupActivity::class.java)) }
        binding.testButton.setOnClickListener { startActivity(Intent(this, TestActivity::class.java)) }
    }

    private fun onEnable() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), 2001)
            Toast.makeText(this, "Grant the Phone permission, then tap Enable again", Toast.LENGTH_LONG).show()
            return
        }
        requestNotificationPermissionIfNeeded()

        settings.timerEnabled = true
        CallTimerService.start(this)
        Toast.makeText(this, "Call Timer enabled", Toast.LENGTH_SHORT).show()
    }

    private fun onDisable() {
        settings.timerEnabled = false
        CallTimerService.stop(this)
        Toast.makeText(this, "Call Timer disabled", Toast.LENGTH_SHORT).show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2002)
            }
        }
    }

    private fun loadIntoUi() {
        val minutes = settings.durationSeconds / 60
        when (minutes) {
            5 -> binding.duration5.isChecked = true
            10 -> binding.duration10.isChecked = true
            15 -> binding.duration15.isChecked = true
            20 -> binding.duration20.isChecked = true
            30 -> binding.duration30.isChecked = true
            else -> {
                binding.durationCustom.isChecked = true
                binding.customDurationMinutes.isEnabled = true
                binding.customDurationMinutes.setText(minutes.toString())
            }
        }
        binding.warningCheck.isChecked = settings.warningEnabled
        binding.soundCheck.isChecked = settings.soundEnabled
        binding.vibrateCheck.isChecked = settings.vibrateEnabled
        updateStatusHeader()
    }

    private fun updateStatusHeader() {
        if (settings.timerEnabled) {
            binding.statusHeader.text = "🟢 ACTIVE"
        } else {
            binding.statusHeader.text = "🔴 DISABLED"
            binding.statusDetail.text = "Not watching for calls"
        }
    }

    override fun onStart() {
        super.onStart()
        CallTimerEngine.addListener(this)
        updateStatusHeader()
    }

    override fun onStop() {
        super.onStop()
        CallTimerEngine.removeListener(this)
    }

    override fun onSnapshot(snapshot: CallTimerSnapshot) {
        if (!settings.timerEnabled) return
        binding.statusHeader.text = "🟢 ACTIVE"
        binding.statusDetail.text = when (snapshot.state) {
            CallState.IDLE -> "Watching for calls"
            CallState.RINGING -> "Incoming call ringing…"
            CallState.DIALING -> "Call dialing…"
            CallState.CONNECTED -> if (snapshot.limitFired) "TIME LIMIT REACHED" else "${CallTimerEngine.format(snapshot.remainingSeconds)} remaining"
            CallState.ENDING, CallState.ENDED -> "Call ended"
        }
    }
}
