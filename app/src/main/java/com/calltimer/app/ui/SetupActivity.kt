package com.calltimer.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.calltimer.app.databinding.ActivitySetupBinding
import com.calltimer.app.settings.AppSettings

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var settings: AppSettings

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = AppSettings(this)
        binding.whatsappCheck.isChecked = settings.whatsappEnabled

        binding.grantPhoneStateButton.setOnClickListener {
            permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
        binding.grantNotificationButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.batteryButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        binding.enableAccessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.whatsappCheck.setOnCheckedChangeListener { _, checked ->
            settings.whatsappEnabled = checked
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val phoneStateGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        binding.phoneStateStatus.text = "Status: ${if (phoneStateGranted) "GRANTED" else "NOT GRANTED"}"

        val notificationsGranted = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        binding.notificationStatus.text = "Status: ${if (notificationsGranted) "GRANTED" else "NOT GRANTED"}"
        binding.grantNotificationButton.isEnabled = Build.VERSION.SDK_INT >= 33

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val batteryExempt = powerManager.isIgnoringBatteryOptimizations(packageName)
        binding.batteryStatus.text = "Status: ${if (batteryExempt) "EXEMPTED" else "NOT EXEMPTED"}"

        val expectedSuffix = "WhatsAppAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val accessibilityOn = enabledServices.split(':').any { it.contains(expectedSuffix) }
        binding.accessibilityStatus.text = "Status: ${if (accessibilityOn) "ENABLED" else "NOT ENABLED"}"
    }
}
