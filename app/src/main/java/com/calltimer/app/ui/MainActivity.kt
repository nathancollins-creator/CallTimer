package com.calltimer.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.calltimer.app.call.CallState
import com.calltimer.app.call.CallTimerEngine
import com.calltimer.app.call.CallTimerListener
import com.calltimer.app.call.CallTimerSnapshot
import com.calltimer.app.databinding.ActivityMainBinding
import com.calltimer.app.notification.AlertSoundMode
import com.calltimer.app.notification.CallTimerService
import com.calltimer.app.settings.AppSettings

class MainActivity : AppCompatActivity(), CallTimerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: AppSettings

    private val ringtonePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                settings.customRingtoneUri = uri.toString()
                updateCustomRingtoneLabel()
                Toast.makeText(this, "Ringtone saved", Toast.LENGTH_SHORT).show()
            }
        }

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
            if (minutes != null) {
                settings.durationSeconds = minutes * 60
                updateCallLimitDisplay()
            }
        }
        binding.customDurationMinutes.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val minutes = binding.customDurationMinutes.text.toString().toIntOrNull()
                if (minutes != null && minutes > 0) {
                    settings.durationSeconds = minutes * 60
                    updateCallLimitDisplay()
                }
            }
        }

        binding.warning5MinCheck.setOnCheckedChangeListener { _, checked ->
            settings.setWarningPointEnabled(AppSettings.FIVE_MIN_WARNING_SECONDS, checked)
        }
        binding.warning1MinCheck.setOnCheckedChangeListener { _, checked ->
            settings.setWarningPointEnabled(AppSettings.ONE_MIN_WARNING_SECONDS, checked)
        }
        binding.vibrateCheck.setOnCheckedChangeListener { _, checked -> settings.vibrateEnabled = checked }

        binding.alertSoundGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                binding.alertSiren.id -> AlertSoundMode.SIREN
                binding.alertWarble.id -> AlertSoundMode.WARBLE
                binding.alertSpoken.id -> AlertSoundMode.SPOKEN
                binding.alertCustom.id -> AlertSoundMode.CUSTOM
                else -> AlertSoundMode.DEFAULT
            }
            settings.alertSoundMode = mode
            val isCustom = (mode == AlertSoundMode.CUSTOM)
            binding.chooseRingtoneButton.visibility = if (isCustom) android.view.View.VISIBLE else android.view.View.GONE
            binding.customRingtoneLabel.visibility = if (isCustom) android.view.View.VISIBLE else android.view.View.GONE
            if (isCustom) updateCustomRingtoneLabel()
        }
        binding.chooseRingtoneButton.setOnClickListener { launchRingtonePicker() }

        binding.enableButton.setOnClickListener { onEnable() }
        binding.disableButton.setOnClickListener { onDisable() }

        binding.setupButton.setOnClickListener { startActivity(Intent(this, SetupActivity::class.java)) }
        binding.testButton.setOnClickListener { startActivity(Intent(this, TestActivity::class.java)) }
    }

    private fun launchRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            val current = settings.customRingtoneUri
            if (current != null) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(current))
        }
        ringtonePickerLauncher.launch(intent)
    }

    private fun updateCustomRingtoneLabel() {
        val uriString = settings.customRingtoneUri
        binding.customRingtoneLabel.text = if (uriString == null) {
            "No ringtone chosen yet"
        } else {
            try {
                val ringtone = RingtoneManager.getRingtone(this, Uri.parse(uriString))
                "Selected: ${ringtone?.getTitle(this) ?: "Unknown"}"
            } catch (_: Exception) {
                "Selected ringtone (name unavailable)"
            }
        }
    }

    private fun updateCallLimitDisplay() {
        binding.callLimitDisplay.text = CallTimerEngine.format(settings.durationSeconds)
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
        Toast.makeText(this, "CallGuard enabled", Toast.LENGTH_SHORT).show()
    }

    private fun onDisable() {
        settings.timerEnabled = false
        CallTimerService.stop(this)
        Toast.makeText(this, "CallGuard disabled", Toast.LENGTH_SHORT).show()
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
        updateCallLimitDisplay()

        val warningPoints = settings.warningPointsSeconds
        binding.warning5MinCheck.isChecked = AppSettings.FIVE_MIN_WARNING_SECONDS in warningPoints
        binding.warning1MinCheck.isChecked = AppSettings.ONE_MIN_WARNING_SECONDS in warningPoints
        binding.vibrateCheck.isChecked = settings.vibrateEnabled

        when (settings.alertSoundMode) {
            AlertSoundMode.SIREN -> binding.alertSiren.isChecked = true
            AlertSoundMode.WARBLE -> binding.alertWarble.isChecked = true
            AlertSoundMode.SPOKEN -> binding.alertSpoken.isChecked = true
            AlertSoundMode.CUSTOM -> {
                binding.alertCustom.isChecked = true
                binding.chooseRingtoneButton.visibility = android.view.View.VISIBLE
                binding.customRingtoneLabel.visibility = android.view.View.VISIBLE
                updateCustomRingtoneLabel()
            }
            AlertSoundMode.DEFAULT -> binding.alertDefault.isChecked = true
        }

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
            CallState.CONNECTED -> if (snapshot.limitFired) "CALL LIMIT REACHED" else "${CallTimerEngine.format(snapshot.remainingSeconds)} remaining"
            CallState.ENDING, CallState.ENDED -> "Call ended"
        }
    }
}
