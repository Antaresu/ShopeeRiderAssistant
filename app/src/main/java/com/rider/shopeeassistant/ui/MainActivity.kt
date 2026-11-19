package com.rider.shopeeassistant.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.rider.shopeeassistant.R
import com.rider.shopeeassistant.service.FloatingOverlayService
import com.rider.shopeeassistant.service.ShopeeAccessibilityService
import com.rider.shopeeassistant.util.HuaweiSettingsHelper

class MainActivity : AppCompatActivity() {

    private lateinit var switchMaster: SwitchMaterial
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnHuawei: Button
    private lateinit var btnOpenMap: Button
    private lateinit var btnOpenEarnings: Button
    private lateinit var tvFood: TextView
    private lateinit var tvSpx: TextView
    private lateinit var tvTotal: TextView

    // Live Availability Sync Views
    private lateinit var dotAvailabilitySync: View
    private lateinit var tvAvailabilitySyncText: TextView
    private lateinit var tvAvailabilitySubNotice: TextView
    private lateinit var btnTestToggleAvailability: Button
    private var isSimulatedAvailabilityOn = true

    // Live Gesture / Click Tester Views
    private lateinit var btnTestHome: Button
    private lateinit var btnTestNotifications: Button
    private lateinit var btnTestBack: Button
    private lateinit var btnTestTapCenter: Button
    private lateinit var btnTestSwipeAccept: Button
    private lateinit var tvTesterStatus: TextView

    private val availabilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val isOn = intent.getBooleanExtra(ShopeeAccessibilityService.EXTRA_IS_AVAILABILITY_ON, true)
            updateAvailabilityUI(isOn)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Handle Huawei Nova 5T Notch / Display Cutout Insets
        val contentContainer = findViewById<View>(R.id.contentContainer)
        ViewCompat.setOnApplyWindowInsetsListener(contentContainer) { v, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val displayCutoutInset = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val topPadding = maxOf(statusBarInset.top, displayCutoutInset.top)
            v.setPadding(v.paddingLeft, topPadding, v.paddingRight, v.paddingBottom)
            insets
        }

        switchMaster = findViewById(R.id.switchMasterActive)
        btnAccessibility = findViewById(R.id.btnEnableAccessibility)
        btnOverlay = findViewById(R.id.btnEnableOverlay)
        btnHuawei = findViewById(R.id.btnHuaweiFix)
        btnOpenMap = findViewById(R.id.btnOpenGoogleMapsArea)
        btnOpenEarnings = findViewById(R.id.btnOpenEarnings)
        tvFood = findViewById(R.id.tvMainCountFood)
        tvSpx = findViewById(R.id.tvMainCountSpx)
        tvTotal = findViewById(R.id.tvMainCountTotal)

        // Bind Availability Views
        dotAvailabilitySync = findViewById(R.id.dotAvailabilitySync)
        tvAvailabilitySyncText = findViewById(R.id.tvAvailabilitySyncText)
        tvAvailabilitySubNotice = findViewById(R.id.tvAvailabilitySubNotice)
        btnTestToggleAvailability = findViewById(R.id.btnTestToggleAvailability)

        // Bind Live Tester Buttons
        btnTestHome = findViewById(R.id.btnTestHome)
        btnTestNotifications = findViewById(R.id.btnTestNotifications)
        btnTestBack = findViewById(R.id.btnTestBack)
        btnTestTapCenter = findViewById(R.id.btnTestTapCenter)
        btnTestSwipeAccept = findViewById(R.id.btnTestSwipeAccept)
        tvTesterStatus = findViewById(R.id.tvTesterStatus)

        setupListeners()

        val filter = IntentFilter(ShopeeAccessibilityService.ACTION_AVAILABILITY_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(availabilityReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(availabilityReceiver, filter)
        }
    }

    private fun updateAvailabilityUI(isOn: Boolean) {
        isSimulatedAvailabilityOn = isOn
        if (isOn) {
            dotAvailabilitySync.setBackgroundResource(R.drawable.circle_green)
            tvAvailabilitySyncText.text = "🟢 on availability - active scanning & ready"
            tvAvailabilitySyncText.setTextColor(0xFF10B981.toInt())
            tvAvailabilitySubNotice.text = "Naka-ON ang availability sa Shopee. Handa nang mag-auto-accept kung pasok sa rules."
        } else {
            dotAvailabilitySync.setBackgroundResource(R.drawable.circle_red)
            tvAvailabilitySyncText.text = "🔴 off availability - auto click is off and others"
            tvAvailabilitySyncText.setTextColor(0xFFEF4444.toInt())
            tvAvailabilitySubNotice.text = "Naka-OFF ang availability sa Shopee Driver drawer. Naka-pause ang auto-click kahit naka-on ang assistant."
        }
    }

    private fun setupListeners() {
        // Master Active Toggle
        switchMaster.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (checkAndRequestOverlayPermission()) {
                    startOverlayService()
                    Toast.makeText(this, "Assistant LIVE! Naka-standby na para sa Shopee Driver.", Toast.LENGTH_SHORT).show()
                } else {
                    switchMaster.isChecked = false
                }
            } else {
                stopOverlayService()
                Toast.makeText(this, "Assistant Naka-PAUSE.", Toast.LENGTH_SHORT).show()
            }
        }

        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Piliin ang 'Shopee Driver Assistant' at i-ON ang service.", Toast.LENGTH_LONG).show()
        }

        btnOverlay.setOnClickListener {
            checkAndRequestOverlayPermission()
        }

        btnHuawei.setOnClickListener {
            HuaweiSettingsHelper.openHuaweiAppLaunchSettings(this)
        }

        btnOpenMap.setOnClickListener {
            startActivity(Intent(this, AreaSelectorActivity::class.java))
        }

        btnOpenEarnings.setOnClickListener {
            startActivity(Intent(this, EarningsTrackerActivity::class.java))
        }

        // Toggle Availability Test Button
        btnTestToggleAvailability.setOnClickListener {
            val newStatus = !isSimulatedAvailabilityOn
            updateAvailabilityUI(newStatus)

            val intent = Intent(ShopeeAccessibilityService.ACTION_TEST_GESTURE).apply {
                putExtra(ShopeeAccessibilityService.EXTRA_TEST_ACTION, "TOGGLE_AVAILABILITY_TEST")
                putExtra("extra_toggle_value", newStatus)
            }
            sendBroadcast(intent)
            Toast.makeText(this, "Availability changed: ${if (newStatus) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }

        // Live Gesture Testers
        btnTestHome.setOnClickListener {
            sendGestureTestCommand("ACTION_HOME")
            tvTesterStatus.text = "Status: [GLOBAL_ACTION_HOME] Ipinadala sa Accessibility Service!"
        }

        btnTestNotifications.setOnClickListener {
            sendGestureTestCommand("ACTION_NOTIFICATIONS")
            tvTesterStatus.text = "Status: [GLOBAL_ACTION_NOTIFICATIONS] Ibinaba ang Notification Shade."
        }

        btnTestBack.setOnClickListener {
            sendGestureTestCommand("ACTION_BACK")
            tvTesterStatus.text = "Status: [GLOBAL_ACTION_BACK] Ipinadala ang Back key press."
        }

        btnTestTapCenter.setOnClickListener {
            sendGestureTestCommand("ACTION_TAP_CENTER")
            tvTesterStatus.text = "Status: [SINGLE TAP] Coordinate tap sa gitna ng screen."
        }

        btnTestSwipeAccept.setOnClickListener {
            sendGestureTestCommand("ACTION_SWIPE_TEST")
            tvTesterStatus.text = "Status: [SWIPE TO ACCEPT] Horizontal drag gesture (kaliwa pakanan)."
        }
    }

    private fun sendGestureTestCommand(actionName: String) {
        val intent = Intent(ShopeeAccessibilityService.ACTION_TEST_GESTURE).apply {
            putExtra(ShopeeAccessibilityService.EXTRA_TEST_ACTION, actionName)
        }
        sendBroadcast(intent)
        Toast.makeText(this, "Gesture Test Command ($actionName) isinagawa!", Toast.LENGTH_SHORT).show()
    }

    private fun checkAndRequestOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(this, "I-allow ang 'Permit drawing over other apps'.", Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    private fun startOverlayService() {
        val intent = Intent(this, FloatingOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopOverlayService() {
        val intent = Intent(this, FloatingOverlayService::class.java)
        stopService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(availabilityReceiver)
    }
}
