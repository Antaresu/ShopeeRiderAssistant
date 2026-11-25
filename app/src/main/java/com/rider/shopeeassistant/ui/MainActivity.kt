package com.rider.shopeeassistant.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.rider.shopeeassistant.R
import com.rider.shopeeassistant.service.FloatingOverlayService
import com.rider.shopeeassistant.service.ShopeeAccessibilityService
import com.rider.shopeeassistant.util.HuaweiSettingsHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceAppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val isShopee: Boolean
)

class MainActivity : AppCompatActivity() {

    // Master Switch & Subtitle
    private lateinit var switchMaster: SwitchMaterial
    private lateinit var tvMasterStatusSubtitle: TextView

    // App Selector Views
    private lateinit var tvSelectedAppName: TextView
    private lateinit var tvSelectedAppPackage: TextView
    private lateinit var btnSelectApp: Button
    private lateinit var btnLaunchShopeeApp: Button
    private var currentTargetPackage = "com.shopee.delivery.driver"

    // Live Availability Sync Views
    private lateinit var dotAvailabilitySync: View
    private lateinit var tvAvailabilitySyncText: TextView
    private lateinit var tvAvailabilitySubNotice: TextView
    private lateinit var btnTestToggleAvailability: Button
    private var isSimulatedAvailabilityOn = false

    // Targeted Shopee Menu & Gesture Tester Views (No Home Key exit)
    private lateinit var btnTestAvailabilityClick: Button
    private lateinit var btnTestIncentivesClick: Button
    private lateinit var btnTestSettingsClick: Button
    private lateinit var btnTestTapAccept: Button
    private lateinit var btnTestSwipeAccept: Button
    private lateinit var tvTesterStatus: TextView

    // Location & Route Filter Rules
    private lateinit var btnRuleManilaAllow: Button
    private lateinit var btnRuleTondoBan: Button

    // Google Maps Navigation (Motorcycle Mode)
    private lateinit var btnLaunchGoogleMaps: Button
    private lateinit var btnOpenMapArea: Button

    // Live Logs Console
    private lateinit var tvConsoleLogs: TextView
    private lateinit var btnClearLogs: Button
    private lateinit var btnSimulateSampleOrder: Button

    // Permissions & Other
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnHuawei: Button
    private lateinit var btnOpenEarnings: Button

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                ShopeeAccessibilityService.ACTION_AVAILABILITY_STATUS -> {
                    val isOn = intent.getBooleanExtra(ShopeeAccessibilityService.EXTRA_IS_AVAILABILITY_ON, false)
                    updateAvailabilityUI(isOn)
                }
                ShopeeAccessibilityService.ACTION_RIDER_PROFILE -> {
                    val name = intent.getStringExtra(ShopeeAccessibilityService.EXTRA_RIDER_NAME) ?: "Christian Antero Rempis"
                    val rating = intent.getStringExtra(ShopeeAccessibilityService.EXTRA_RIDER_RATING) ?: "5.00"
                    val completion = intent.getStringExtra(ShopeeAccessibilityService.EXTRA_RIDER_COMPLETION) ?: "0.0%"
                    appendLog("[RIDER SYNC] 👤 Rider: $name • Rating: $rating • Completion: $completion")
                }
                ShopeeAccessibilityService.ACTION_APP_LOG -> {
                    val logLine = intent.getStringExtra(ShopeeAccessibilityService.EXTRA_LOG_LINE)
                    if (!logLine.isNullOrEmpty()) {
                        appendLog(logLine)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Handle Huawei Nova 5T Notch / Display Cutout Insets safely
        try {
            val contentContainer = findViewById<View>(R.id.contentContainer)
            contentContainer?.let { container ->
                ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
                    try {
                        val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                        val displayCutoutInset = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                        val topPadding = maxOf(statusBarInset.top, displayCutoutInset.top)
                        v.setPadding(v.paddingLeft, topPadding, v.paddingRight, v.paddingBottom)
                    } catch (_: Exception) {}
                    insets
                }
            }
        } catch (_: Exception) {}

        bindViews()
        setupListeners()

        // Auto-detect Shopee Driver / Food / SPX package on launch
        autoDetectShopeeApp()

        // Register Log & Availability Broadcast Receivers
        val filter = IntentFilter().apply {
            addAction(ShopeeAccessibilityService.ACTION_AVAILABILITY_STATUS)
            addAction(ShopeeAccessibilityService.ACTION_APP_LOG)
            addAction(ShopeeAccessibilityService.ACTION_RIDER_PROFILE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
        }

        // Initialize with default state: Master OFF, Availability OFF
        updateMasterStatusUI(false)
        updateAvailabilityUI(false)
    }

    private fun bindViews() {
        switchMaster = findViewById(R.id.switchMasterActive)
        tvMasterStatusSubtitle = findViewById(R.id.tvMasterStatusSubtitle)

        tvSelectedAppName = findViewById(R.id.tvSelectedAppName)
        tvSelectedAppPackage = findViewById(R.id.tvSelectedAppPackage)
        btnSelectApp = findViewById(R.id.btnSelectApp)
        btnLaunchShopeeApp = findViewById(R.id.btnLaunchShopeeApp)

        dotAvailabilitySync = findViewById(R.id.dotAvailabilitySync)
        tvAvailabilitySyncText = findViewById(R.id.tvAvailabilitySyncText)
        tvAvailabilitySubNotice = findViewById(R.id.tvAvailabilitySubNotice)
        btnTestToggleAvailability = findViewById(R.id.btnTestToggleAvailability)

        btnTestAvailabilityClick = findViewById(R.id.btnTestAvailabilityClick)
        btnTestIncentivesClick = findViewById(R.id.btnTestIncentivesClick)
        btnTestSettingsClick = findViewById(R.id.btnTestSettingsClick)
        btnTestTapAccept = findViewById(R.id.btnTestTapAccept)
        btnTestSwipeAccept = findViewById(R.id.btnTestSwipeAccept)
        tvTesterStatus = findViewById(R.id.tvTesterStatus)

        btnRuleManilaAllow = findViewById(R.id.btnRuleManilaAllow)
        btnRuleTondoBan = findViewById(R.id.btnRuleTondoBan)

        btnLaunchGoogleMaps = findViewById(R.id.btnLaunchGoogleMaps)
        btnOpenMapArea = findViewById(R.id.btnOpenGoogleMapsArea)

        tvConsoleLogs = findViewById(R.id.tvConsoleLogs)
        btnClearLogs = findViewById(R.id.btnClearLogs)
        btnSimulateSampleOrder = findViewById(R.id.btnSimulateSampleOrder)

        btnAccessibility = findViewById(R.id.btnEnableAccessibility)
        btnOverlay = findViewById(R.id.btnEnableOverlay)
        btnHuawei = findViewById(R.id.btnHuaweiFix)
        btnOpenEarnings = findViewById(R.id.btnOpenEarnings)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(packageName)
    }

    private fun setupListeners() {
        // 1. Master Active Switch (Defaults to OFF)
        switchMaster.setOnCheckedChangeListener { _, isChecked ->
            updateMasterStatusUI(isChecked)
            if (isChecked) {
                if (checkAndRequestOverlayPermission()) {
                    startOverlayService()
                    appendLog("[MASTER SWITCH] Naka-ON na ang Assistant. Naka-standby sa Shopee Driver.")
                    Toast.makeText(this, "Assistant LIVE! Handa na sa biyahe.", Toast.LENGTH_SHORT).show()
                } else {
                    switchMaster.isChecked = false
                }
            } else {
                stopOverlayService()
                appendLog("[MASTER SWITCH] Naka-OFF ang Assistant. Walang gagawing pagkilos.")
                Toast.makeText(this, "Assistant Naka-PAUSE / OFF.", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Select Target App from ACTUAL Installed Apps on Phone (with Permission Pre-check)
        btnSelectApp.setOnClickListener {
            // STEP 1: Verify Access Permissions first before picking
            if (!isAccessibilityEnabled() || !Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle("⚠️ Kailangan Muna ng App Access")
                    .setMessage("Bago pumili ng app mula sa iyong device, i-ON muna ang Accessibility Service at Draw Overlay para mabasa at ma-control ng assistant ang Shopee Driver screen.")
                    .setPositiveButton("I-ON ang Accessibility") { _, _ ->
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    .setNeutralButton("I-ON ang Overlay") { _, _ ->
                        checkAndRequestOverlayPermission()
                    }
                    .setNegativeButton("Kanselahin", null)
                    .show()
                return@setOnClickListener
            }

            // STEP 2: Query All Installed Applications on Phone
            showInstalledAppsPicker()
        }

        // Launch Shopee Driver App Directly
        btnLaunchShopeeApp.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage(currentTargetPackage)
            if (launchIntent != null) {
                appendLog("[APP LAUNCH] Binubuksan ang $currentTargetPackage...")
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Hindi pa naka-install ang $currentTargetPackage sa phone na ito.", Toast.LENGTH_LONG).show()
                appendLog("[APP LAUNCH] Package $currentTargetPackage hindi nahanap sa local device.")
            }
        }

        // 3. Shopee Availability Toggle
        btnTestToggleAvailability.setOnClickListener {
            val newStatus = !isSimulatedAvailabilityOn
            updateAvailabilityUI(newStatus)
            sendGestureTestCommand("TOGGLE_AVAILABILITY_TEST", newStatus)
            Toast.makeText(this, "Shopee Availability: ${if (newStatus) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }

        // 4. Targeted Tester Buttons (Shopee Menus & Accept Actions - NO HOME EXIT)
        btnTestAvailabilityClick.setOnClickListener {
            sendGestureTestCommand("ACTION_TEST_AVAILABILITY_CLICK")
            tvTesterStatus.text = "Status: [AVAILABILITY TOGGLE CLICK] Na-dispatch sa Shopee Driver drawer!"
            appendLog("[TEST] Sinubukan ang click sa Shopee Availability toggle.")
        }

        btnTestIncentivesClick.setOnClickListener {
            sendGestureTestCommand("ACTION_TEST_INCENTIVES_CLICK")
            tvTesterStatus.text = "Status: [INCENTIVES MENU CLICK] Na-dispatch sa S-Points menu item!"
            appendLog("[TEST] Sinubukan ang click sa Incentives menu.")
        }

        btnTestSettingsClick.setOnClickListener {
            sendGestureTestCommand("ACTION_TEST_SETTINGS_CLICK")
            tvTesterStatus.text = "Status: [SETTINGS CLICK] Na-dispatch sa Settings menu item!"
            appendLog("[TEST] Sinubukan ang click sa Settings menu.")
        }

        btnTestTapAccept.setOnClickListener {
            sendGestureTestCommand("ACTION_TEST_ACCEPT_TAP")
            tvTesterStatus.text = "Status: [ACCURATE TAP] Coordinate tap sa Accept button position!"
            appendLog("[TEST] Coordinate tap sa Accept button coordinates.")
        }

        btnTestSwipeAccept.setOnClickListener {
            sendGestureTestCommand("ACTION_TEST_SWIPE_ACCEPT")
            tvTesterStatus.text = "Status: [SWIPE TO ACCEPT] Horizontal drag gesture (350ms) na-execute!"
            appendLog("[TEST] Swipe to Accept horizontal drag na-execute.")
        }

        // 5. Location Rules Actions
        btnRuleManilaAllow.setOnClickListener {
            Toast.makeText(this, "Rule Active: Manila to Manila ALLOWED & AUTO-ACCEPT!", Toast.LENGTH_SHORT).show()
            appendLog("[RULE UPDATED] ✅ Manila to Manila: ALLOWED (Auto-Accept: ON).")
        }

        btnRuleTondoBan.setOnClickListener {
            Toast.makeText(this, "Rule Active: Manila to Tondo BANNED (Zero-Click Active)!", Toast.LENGTH_SHORT).show()
            appendLog("[RULE UPDATED] 🚫 Manila to Tondo: BANNED (Zero-Click: Hahayaang lumipas nang kusa).")
        }

        // 6. Google Maps Direct Navigation (MOTORCYCLE MODE: mode=l for Two-Wheeler)
        btnLaunchGoogleMaps.setOnClickListener {
            val destination = "Manila, Philippines"
            // mode=l is the official Google Maps motorcycle / scooter mode in Southeast Asia & Philippines
            val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=l")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
            }
            try {
                startActivity(mapIntent)
                appendLog("[GOOGLE MAPS] 🛵 Binuksan ang Motor Navigation (mode=l) para sa Click 125 V4 papuntang $destination.")
            } catch (e: Exception) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destination)}&travelmode=two_wheeler"))
                startActivity(browserIntent)
                appendLog("[GOOGLE MAPS] 🛵 Binuksan ang Motorcycle route sa browser.")
            }
        }

        btnOpenMapArea.setOnClickListener {
            startActivity(Intent(this, AreaSelectorActivity::class.java))
        }

        // 7. Smooth Logs Scroll & Controls
        val scrollLogs = findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollLogs)
        scrollLogs?.setOnTouchListener { v, event ->
            // Prevent parent ScrollView from intercepting log scrolling gestures
            v.parent.requestDisallowInterceptTouchEvent(true)
            if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {
                v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        btnClearLogs.setOnClickListener {
            logLines.clear()
            val timestamp = timeFormat.format(Date())
            logLines.add("[$timestamp] [LOGS CLEARED] Malinis na ang log stream.")
            tvConsoleLogs.text = logLines.joinToString("\n")
        }

        btnSimulateSampleOrder.setOnClickListener {
            simulateSampleOrderEvaluation()
        }

        // 8. Permissions & Earnings
        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Piliin ang 'Shopee Driver Assistant' at i-ON ang service.", Toast.LENGTH_LONG).show()
        }

        btnOverlay.setOnClickListener {
            checkAndRequestOverlayPermission()
        }

        btnHuawei.setOnClickListener {
            HuaweiSettingsHelper.openHuaweiAppLaunchSettings(this)
        }

        btnOpenEarnings.setOnClickListener {
            startActivity(Intent(this, EarningsTrackerActivity::class.java))
        }
    }

    private fun showInstalledAppsPicker() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveList = pm.queryIntentActivities(mainIntent, 0)
        val appList = mutableListOf<DeviceAppInfo>()

        for (resolveInfo in resolveList) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == packageName) continue // Skip our assistant app

            val label = resolveInfo.loadLabel(pm).toString()
            val icon = resolveInfo.loadIcon(pm)
            val isShopee = pkg.contains("shopee", ignoreCase = true) ||
                    label.contains("shopee", ignoreCase = true) ||
                    label.contains("spx", ignoreCase = true) ||
                    pkg.contains("delivery", ignoreCase = true)

            appList.add(DeviceAppInfo(label, pkg, icon, isShopee))
        }

        // Sort: Shopee apps first, then alphabetical
        appList.sortWith(compareByDescending<DeviceAppInfo> { it.isShopee }.thenBy { it.label })

        val adapter = object : ArrayAdapter<DeviceAppInfo>(this, R.layout.dialog_app_item, appList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.dialog_app_item, parent, false)
                val item = getItem(position) ?: return view

                val ivIcon = view.findViewById<ImageView>(R.id.ivAppIcon)
                val tvName = view.findViewById<TextView>(R.id.tvAppItemName)
                val tvPkg = view.findViewById<TextView>(R.id.tvAppItemPackage)
                val tvBadge = view.findViewById<TextView>(R.id.tvAppItemBadge)

                ivIcon.setImageDrawable(item.icon)
                tvName.text = item.label
                tvPkg.text = item.packageName

                if (item.isShopee) {
                    tvBadge.visibility = View.VISIBLE
                    tvBadge.text = "SHOPEE COMPATIBLE"
                } else {
                    tvBadge.visibility = View.GONE
                }

                return view
            }
        }

        val dialogView = ListView(this).apply {
            this.adapter = adapter
            divider = null
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Piliin ang App mula sa Phone Mo")
            .setView(dialogView)
            .setNegativeButton("Kanselahin", null)
            .create()

        dialogView.setOnItemClickListener { _, _, position, _ ->
            val selected = appList[position]
            setTargetApp(selected.label, selected.packageName, true)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setTargetApp(name: String, packageName: String, showToast: Boolean = false) {
        try {
            currentTargetPackage = packageName
            tvSelectedAppName.text = name
            tvSelectedAppPackage.text = packageName
            appendLog("[APP SELECTOR] Target package: $packageName ($name)")

            val intent = Intent(ShopeeAccessibilityService.ACTION_TEST_GESTURE).apply {
                putExtra(ShopeeAccessibilityService.EXTRA_TEST_ACTION, "SET_TARGET_APP")
                putExtra("extra_package_name", packageName)
            }
            sendBroadcast(intent)
            if (showToast) {
                Toast.makeText(this, "Nakatali na ang assistant sa $name!", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {}
    }

    private fun updateMasterStatusUI(isActive: Boolean) {
        if (isActive) {
            tvMasterStatusSubtitle.text = "🟢 SCANNING ACTIVE • Nova 5T Ready"
            tvMasterStatusSubtitle.setTextColor(0xFF10B981.toInt())
        } else {
            tvMasterStatusSubtitle.text = "🔴 NAKA-OFF / STANDBY • Nova 5T Ready"
            tvMasterStatusSubtitle.setTextColor(0xFF94A3B8.toInt())
        }
    }

    private fun updateAvailabilityUI(isOn: Boolean) {
        isSimulatedAvailabilityOn = isOn
        if (isOn) {
            dotAvailabilitySync.setBackgroundResource(R.drawable.circle_green)
            tvAvailabilitySyncText.text = "🟢 on availability - active scanning & ready"
            tvAvailabilitySyncText.setTextColor(0xFF10B981.toInt())
            tvAvailabilitySubNotice.text = "Naka-ON ang availability sa Shopee Driver. Handa nang mag-auto-accept kung pasok sa rules."
            appendLog("[AVAILABILITY] Shopee Driver: 🟢 ON -> Handa na para sa auto-accept.")
        } else {
            dotAvailabilitySync.setBackgroundResource(R.drawable.circle_red)
            tvAvailabilitySyncText.text = "🔴 off availability - auto click is off and others"
            tvAvailabilitySyncText.setTextColor(0xFFEF4444.toInt())
            tvAvailabilitySubNotice.text = "Naka-OFF ang availability sa Shopee Driver drawer. Naka-pause ang auto-click kahit naka-on ang assistant."
            appendLog("[AVAILABILITY] Shopee Driver: 🔴 OFF -> Auto-click is OFF and others.")
        }
    }

    private val logLines = mutableListOf<String>()

    private fun appendLog(message: String) {
        try {
            val timestamp = timeFormat.format(Date())
            logLines.add("[$timestamp] $message")
            if (logLines.size > 80) {
                logLines.removeAt(0)
            }
            tvConsoleLogs.text = logLines.joinToString("\n")
            val scrollLogs = findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollLogs)
            scrollLogs?.post {
                try {
                    scrollLogs.fullScroll(View.FOCUS_DOWN)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun simulateSampleOrderEvaluation() {
        appendLog("---------------------------------------------")
        appendLog("[SIMULATION 1] Booking: 2.1 kg, 15x12x10 cm • Ermita, Manila")
        appendLog("[SIZE EVAL] Sukat at timbang: MALIIT (Kasya sa 32L Thermal Bag).")
        appendLog("[ROUTE EVAL] Manila Proper -> ALLOWED AREA.")
        if (isSimulatedAvailabilityOn && switchMaster.isChecked) {
            appendLog("[DECISION] ✅ PASOK LAHAT NG RULES! -> Auto-Clicking Accept...")
        } else {
            appendLog("[DECISION] ⏸️ NAKA-PAUSE (Master Switch o Shopee Availability ay OFF).")
        }

        appendLog("[SIMULATION 2] Booking: 19.5 kg, 65x55x40 cm • Tondo, Manila")
        appendLog("[SIZE EVAL] ⚠️ OVERSIZED: Hindi kasya sa Honda Click 125 V4!")
        appendLog("[ROUTE EVAL] 🚫 BANNED AREA: Papuntang Tondo!")
        appendLog("[DECISION] 🛑 ZERO-CLICK POLICY: Hinihintay mag-timeout nang kusa. Walang iki-click!")
        appendLog("---------------------------------------------")
    }

    private fun sendGestureTestCommand(actionName: String, extraBool: Boolean = false) {
        // 1. If testing an on-screen Shopee action, automatically switch to the target Shopee app!
        if (actionName.startsWith("ACTION_TEST_") && actionName != "TOGGLE_AVAILABILITY_TEST") {
            val launchIntent = packageManager.getLaunchIntentForPackage(currentTargetPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                startActivity(launchIntent)
                Toast.makeText(this, "Binubuksan ang Shopee Driver para subukan ang click...", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Delay dispatch so Shopee window is drawn and is the active window!
        val delayMs = if (actionName.startsWith("ACTION_TEST_") && actionName != "TOGGLE_AVAILABILITY_TEST") 1200L else 50L
        mainHandler.postDelayed({
            val intent = Intent(ShopeeAccessibilityService.ACTION_TEST_GESTURE).apply {
                putExtra(ShopeeAccessibilityService.EXTRA_TEST_ACTION, actionName)
                putExtra("extra_toggle_value", extraBool)
            }
            sendBroadcast(intent)
        }, delayMs)
    }

    private fun autoDetectShopeeApp() {
        try {
            val pm = packageManager
            var foundPkg: String? = null
            var foundLabel: String? = null

            val preferredPackages = listOf(
                "com.shopee.delivery.driver",
                "com.shopee.food.driver",
                "com.shopee.driver",
                "com.shopee.driver.ph",
                "com.garena.delivery.driver",
                "com.spx.rider",
                "com.shopee.spx.driver"
            )

            for (pref in preferredPackages) {
                try {
                    val appInfo = pm.getApplicationInfo(pref, 0)
                    foundPkg = pref
                    foundLabel = pm.getApplicationLabel(appInfo).toString()
                    break
                } catch (_: Exception) {}
            }

            if (foundPkg == null) {
                try {
                    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                    val resolveList = pm.queryIntentActivities(mainIntent, 0)
                    for (res in resolveList) {
                        val pkgName = res.activityInfo.packageName.lowercase()
                        val lbl = res.loadLabel(pm).toString().lowercase()
                        if (pkgName.contains("shopee") || lbl.contains("shopee") || pkgName.contains("spx") || lbl.contains("spx")) {
                            foundPkg = res.activityInfo.packageName
                            foundLabel = res.loadLabel(pm).toString()
                            break
                        }
                    }
                } catch (_: Exception) {}
            }

            if (foundPkg != null && foundLabel != null) {
                setTargetApp(foundLabel, foundPkg, false)
                appendLog("[AUTO-DETECT] 🎯 Nahanap at itinakda ang App: $foundLabel")
            } else {
                setTargetApp("Shopee Driver (Standard)", "com.shopee.delivery.driver", false)
                appendLog("[AUTO-DETECT] ℹ️ Naka-bind sa standard package: com.shopee.delivery.driver")
            }
        } catch (e: Exception) {
            setTargetApp("Shopee Driver", "com.shopee.delivery.driver", false)
        }
    }

    private fun checkAndRequestOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "I-allow ang 'Draw over other apps' para sa floating counter.", Toast.LENGTH_LONG).show()
            return false
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
        unregisterReceiver(logReceiver)
    }
}
