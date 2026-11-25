package com.rider.shopeeassistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.rider.shopeeassistant.data.DecisionVerdict
import com.rider.shopeeassistant.data.OrderExtractionResult
import com.rider.shopeeassistant.data.OrderRuleModel
import com.rider.shopeeassistant.util.OrderParser
import java.util.Locale

class ShopeeAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {

    private val TAG = "ShopeeAssistantService"
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentRules = OrderRuleModel()
    private var lastClickedOrderSignature = ""
    private var lastClickTimestamp = 0L
    private var lastDetectedRiderName: String? = null

    // Live Shopee Driver Availability State (ON vs OFF) - Defaults to OFF as shown in driver screenshot
    var isShopeeAvailabilityOn: Boolean = false

    // Target App Package (Can be selected/changed from UI)
    var targetPackageName: String = "com.shopee.delivery.driver"

    // Action Mode: TAP vs SWIPE
    var isSwipeModeEnabled = true
    var analyzeDelayMs = 450L

    private fun ensureDrawerOpen(onReady: () -> Unit) {
        val root = rootInActiveWindow
        if (OrderParser.isDrawerOpen(root)) {
            onReady()
        } else {
            // Swipe from left edge to open drawer
            val displayMetrics = resources.displayMetrics
            val startX = 10f
            val endX = displayMetrics.widthPixels * 0.70f
            val y = displayMetrics.heightPixels * 0.45f
            dispatchSwipeGesture(startX, endX, y, 280)
            broadcastLog("[ACTION] Binubuksan ang Shopee Driver side drawer...")
            mainHandler.postDelayed({
                onReady()
            }, 450)
        }
    }

    private val testCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val actionType = intent.getStringExtra(EXTRA_TEST_ACTION) ?: return

            when (actionType) {
                "ACTION_TEST_AVAILABILITY_CLICK" -> {
                    ensureDrawerOpen {
                        val root = rootInActiveWindow
                        var clicked = OrderParser.clickAvailabilitySwitch(root)
                        if (!clicked) {
                            val bounds = OrderParser.findAvailabilitySwitchBounds(root)
                            if (bounds != null) {
                                dispatchTapGesture(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                                clicked = true
                            }
                        }
                        mainHandler.postDelayed({
                            val updatedRoot = rootInActiveWindow
                            val detected = if (updatedRoot != null) OrderParser.detectAvailabilitySwitch(updatedRoot) else null
                            if (detected != null) {
                                updateAvailabilityStatus(detected, false)
                            } else {
                                updateAvailabilityStatus(!isShopeeAvailabilityOn, true)
                            }
                        }, 400)
                        broadcastLog("[TEST CLICK] Shopee Availability switch toggle: ${if (clicked) "Click na-dispatch sa screen" else "Simulated toggle"}")
                        speak("Sinubukan ang Availability toggle.")
                    }
                }
                "ACTION_TEST_INCENTIVES_CLICK" -> {
                    ensureDrawerOpen {
                        val root = rootInActiveWindow
                        var clicked = OrderParser.clickNodeWithText(root, listOf("incentives", "instant s-points", "s-points"))
                        if (!clicked) {
                            val bounds = OrderParser.findNodeBoundsWithText(root, listOf("incentives", "instant s-points", "s-points"))
                            if (bounds != null) {
                                dispatchTapGesture(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                                clicked = true
                            }
                        }
                        broadcastLog("[TEST CLICK] Shopee Incentives menu click: ${if (clicked) "Na-click ang Incentives sa Shopee drawer" else "Walang Incentives text sa screen"}")
                        speak(if (clicked) "Na-click ang Incentives sa Shopee." else "Sinubukan ang Incentives menu.")
                    }
                }
                "ACTION_TEST_SETTINGS_CLICK" -> {
                    ensureDrawerOpen {
                        val root = rootInActiveWindow
                        var clicked = OrderParser.clickNodeWithText(root, listOf("settings", "setting"))
                        if (!clicked) {
                            val bounds = OrderParser.findNodeBoundsWithText(root, listOf("settings", "setting"))
                            if (bounds != null) {
                                dispatchTapGesture(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                                clicked = true
                            }
                        }
                        broadcastLog("[TEST CLICK] Shopee Settings menu click: ${if (clicked) "Na-click ang Settings sa Shopee drawer" else "Walang Settings text sa screen"}")
                        speak(if (clicked) "Na-click ang Settings sa Shopee." else "Sinubukan ang Settings menu.")
                    }
                }
                "ACTION_TEST_BALANCE_CLICK" -> {
                    ensureDrawerOpen {
                        val root = rootInActiveWindow
                        var clicked = OrderParser.clickNodeWithText(root, listOf("my balance", "balance"))
                        if (!clicked) {
                            val bounds = OrderParser.findNodeBoundsWithText(root, listOf("my balance", "balance"))
                            if (bounds != null) {
                                dispatchTapGesture(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                                clicked = true
                            }
                        }
                        broadcastLog("[TEST CLICK] Shopee My Balance click: ${if (clicked) "Na-click ang My Balance" else "Walang My Balance text sa screen"}")
                    }
                }
                "ACTION_TEST_NOTIFICATION_CLICK" -> {
                    ensureDrawerOpen {
                        val root = rootInActiveWindow
                        var clicked = OrderParser.clickNodeWithText(root, listOf("notification", "notifications"))
                        if (!clicked) {
                            val bounds = OrderParser.findNodeBoundsWithText(root, listOf("notification", "notifications"))
                            if (bounds != null) {
                                dispatchTapGesture(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                                clicked = true
                            }
                        }
                        broadcastLog("[TEST CLICK] Shopee Notification click: ${if (clicked) "Na-click ang Notification" else "Walang Notification text sa screen"}")
                    }
                }
                "ACTION_TEST_ACCEPT_TAP" -> {
                    val displayMetrics = resources.displayMetrics
                    val centerX = displayMetrics.widthPixels / 2f
                    val centerY = displayMetrics.heightPixels * 0.88f
                    dispatchTapGesture(centerX, centerY)
                    broadcastLog("[TEST TAP] Coordinate tap sa Accept button position [X=${centerX.toInt()}, Y=${centerY.toInt()}]")
                    speak("Sinubukan ang tap sa Accept button.")
                }
                "ACTION_TEST_SWIPE_ACCEPT" -> {
                    val displayMetrics = resources.displayMetrics
                    val startX = displayMetrics.widthPixels * 0.15f
                    val endX = displayMetrics.widthPixels * 0.85f
                    val y = displayMetrics.heightPixels * 0.88f
                    dispatchSwipeGesture(startX, endX, y, 350)
                    broadcastLog("[TEST GESTURE] Horizontal swipe drag to accept [X1=${startX.toInt()} -> X2=${endX.toInt()}, Y=${y.toInt()}]")
                    speak("Sinubukan ang Swipe to Accept gesture.")
                }
                "TOGGLE_AVAILABILITY_TEST" -> {
                    val newStatus = intent.getBooleanExtra("extra_toggle_value", !isShopeeAvailabilityOn)
                    updateAvailabilityStatus(newStatus, true)
                }
                "SET_TARGET_APP" -> {
                    val newPkg = intent.getStringExtra("extra_package_name") ?: "com.shopee.delivery.driver"
                    targetPackageName = newPkg
                    broadcastLog("[APP BIND] Target App pinalitan sa: $targetPackageName")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Shopee Accessibility Service Connected and Active!")
        tts = TextToSpeech(this, this)

        // Dynamic package monitoring: listen to any target package without restrictive static XML filters
        val info = serviceInfo
        info.packageNames = null
        serviceInfo = info

        val filter = IntentFilter(ACTION_TEST_GESTURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(testCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(testCommandReceiver, filter)
        }

        sendBroadcast(Intent(ACTION_SERVICE_STATUS).putExtra(EXTRA_IS_ACTIVE, true))
        broadcastAvailabilityStatus()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("fil", "PH"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.ENGLISH
            }
            isTtsReady = true
            speak("Shopee Driver Assistant ready na.")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // 1. CRITICAL GUARD: NEVER process accessibility events from our own assistant app!
        // This prevents self-targeting loops, clicking our own buttons, and spam clicks.
        if (packageName == this.packageName || packageName == "com.rider.shopeeassistant") {
            return
        }

        // 2. Ignore system UI / keyboard / launchers
        if (packageName.contains("launcher", ignoreCase = true) ||
            packageName.contains("systemui", ignoreCase = true) ||
            packageName.contains("inputmethod", ignoreCase = true)) {
            return
        }

        val isTargetApp = (targetPackageName.isNotEmpty() && packageName.equals(targetPackageName, ignoreCase = true)) ||
                packageName.contains("shopee", ignoreCase = true) ||
                packageName.contains("delivery", ignoreCase = true) ||
                packageName.contains("driver", ignoreCase = true) ||
                packageName.contains("food", ignoreCase = true) ||
                packageName.contains("spx", ignoreCase = true)

        if (!isTargetApp) {
            return
        }

        val rootNode = rootInActiveWindow ?: return

        try {
            // Read Rider Profile (Christian Antero Rempis, Rating, Completion)
            val profile = OrderParser.detectRiderProfile(rootNode)
            if (profile.name != null && profile.name != lastDetectedRiderName) {
                lastDetectedRiderName = profile.name
                val profileText = "👤 ${profile.name} • ⭐ ${profile.rating ?: "5.00"} • 📊 ${profile.completionRate ?: "0.0%"}"
                broadcastLog("[RIDER PROFILE] $profileText")
                val riderIntent = Intent(ACTION_RIDER_PROFILE).apply {
                    putExtra(EXTRA_RIDER_NAME, profile.name)
                    putExtra(EXTRA_RIDER_RATING, profile.rating ?: "5.00")
                    putExtra(EXTRA_RIDER_COMPLETION, profile.completionRate ?: "0.0%")
                }
                sendBroadcast(riderIntent)
            }

            // 1. LIVE CHECK: Basahin kung naka-ON o naka-OFF ang Availability toggle sa Shopee Driver drawer o main screen
            val detectedAvailability = OrderParser.detectAvailabilitySwitch(rootNode)
            if (detectedAvailability != null && detectedAvailability != isShopeeAvailabilityOn) {
                updateAvailabilityStatus(detectedAvailability, false)
                broadcastLog("[LIVE AVAILABILITY] Shopee state na-detect: ${if (detectedAvailability) "🟢 ON (Online)" else "🔴 OFF (Offline)"}")
            }

            // 2. KUNG NAKA-OFF ANG AVAILABILITY SA SHOPEE: Auto-click is OFF & others
            if (!isShopeeAvailabilityOn) {
                val offResult = OrderExtractionResult(
                    reasonText = "🔴 off availability - auto click is off and others",
                    verdict = DecisionVerdict.IGNORED_NOT_AN_ORDER,
                    riderName = profile.name ?: lastDetectedRiderName,
                    riderRating = profile.rating,
                    riderCompletion = profile.completionRate
                )
                broadcastLiveReadout(offResult)
                return
            }

            // 3. STEP 1: ANALYZE FIRST (Basahin ang buong text, timbang, sukat, address)
            val extractionResult = OrderParser.scanScreenTree(rootNode, currentRules)

            // I-broadcast ang live status sa Floating Overlay
            broadcastLiveReadout(extractionResult)

            // 4. STEP 2: VERIFY BAGO PINDUTIN
            if (extractionResult.acceptButtonNode != null && extractionResult.buttonScreenBounds != null) {
                handleOrderDecision(extractionResult)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error scanning screen: ${e.message}", e)
        }
    }

    private fun updateAvailabilityStatus(isOn: Boolean, isFromManualTest: Boolean) {
        isShopeeAvailabilityOn = isOn
        broadcastAvailabilityStatus()

        val statusText = if (isOn) "🟢 ON AVAILABILITY - Ready to Auto-Accept" else "🔴 OFF AVAILABILITY - Auto-click is OFF and others"
        broadcastLog("[AVAILABILITY UPDATE] Shopee Driver: $statusText")

        if (isOn) {
            speak("Shopee availability is ON. Auto click and scanning active.")
        } else {
            speak("Shopee availability is OFF. Auto click is paused.")
        }
        Log.i(TAG, "Shopee Availability updated to: ${if (isOn) "ON" else "OFF"}")
    }

    private fun broadcastAvailabilityStatus() {
        val intent = Intent(ACTION_AVAILABILITY_STATUS).apply {
            putExtra(EXTRA_IS_AVAILABILITY_ON, isShopeeAvailabilityOn)
        }
        sendBroadcast(intent)
    }

    private fun handleOrderDecision(result: OrderExtractionResult) {
        val now = System.currentTimeMillis()
        val signature = "${result.dropoffAddress}_${result.farePhp}_${result.weightKg}"

        // Anti-spam debounce
        if (signature == lastClickedOrderSignature && (now - lastClickTimestamp) < 10000) {
            return
        }

        // DOUBLE-CHECK: Kung naka-off ang availability, wag mag-click
        if (!isShopeeAvailabilityOn) {
            Log.w(TAG, "Booking detected but Shopee Availability is OFF. Auto-click blocked.")
            return
        }

        when (result.verdict) {
            DecisionVerdict.AUTO_ACCEPT -> {
                lastClickedOrderSignature = signature
                lastClickTimestamp = now

                speak(result.reasonText)
                broadcastLog("[ACTION] ✅ PASOK SA RULES: Manila & Kasya sa 32L bag! Auto-accept na-dispatch.")

                mainHandler.postDelayed({
                    if (!isShopeeAvailabilityOn) return@postDelayed

                    val bounds = result.buttonScreenBounds ?: return@postDelayed
                    val buttonText = result.acceptButtonNode?.text?.toString()?.lowercase() ?: ""
                    val requiresSwipe = isSwipeModeEnabled || buttonText.contains("swipe") || buttonText.contains("slide")

                    if (requiresSwipe) {
                        val startX = bounds.left + (bounds.width() * 0.15f)
                        val endX = bounds.right - (bounds.width() * 0.15f)
                        val centerY = bounds.centerY().toFloat()
                        dispatchSwipeGesture(startX, endX, centerY, 350)
                        broadcastLog("[GESTURE] Swipe to Accept na-execute sa Shopee screen [${startX.toInt()} -> ${endX.toInt()}, Y=${centerY.toInt()}].")
                    } else {
                        executeAccurateClick(result.acceptButtonNode, bounds.centerX(), bounds.centerY())
                        broadcastLog("[CLICK] Coordinate click na-execute sa Accept button [X=${bounds.centerX()}, Y=${bounds.centerY()}].")
                    }
                }, analyzeDelayMs)
            }

            DecisionVerdict.ZERO_CLICK_BANNED_AREA -> {
                lastClickedOrderSignature = signature
                lastClickTimestamp = now
                speak(result.reasonText)
                broadcastLog("[ACTION] 🚫 ZERO-CLICK POLICY: Papuntang Tondo! Walang pipindutin at walang iki-click.")
                Log.i(TAG, "ZERO-CLICK POLICY ACTIVATED: Banned Area Tondo.")
            }

            DecisionVerdict.ZERO_CLICK_OVERSIZED -> {
                lastClickedOrderSignature = signature
                lastClickTimestamp = now
                speak(result.reasonText)
                broadcastLog("[ACTION] ⚠️ ZERO-CLICK POLICY: Masyadong malaki/mabigat (>15kg o >32L bag)! Walang pipindutin.")
                Log.i(TAG, "ZERO-CLICK POLICY ACTIVATED: Oversized order.")
            }

            DecisionVerdict.IGNORED_NOT_AN_ORDER -> {}
        }
    }

    fun broadcastLog(message: String) {
        val intent = Intent(ACTION_APP_LOG).apply {
            putExtra(EXTRA_LOG_LINE, message)
        }
        sendBroadcast(intent)
        Log.d(TAG, message)
    }

    private fun executeAccurateClick(node: AccessibilityNodeInfo?, x: Int, y: Int) {
        var clicked = false
        if (node != null && node.isClickable) {
            clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        if (!clicked) {
            dispatchTapGesture(x.toFloat(), y.toFloat())
        }
    }

    private fun dispatchTapGesture(x: Float, y: Float) {
        val clickPath = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(clickPath, 0, 50))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun dispatchSwipeGesture(startX: Float, endX: Float, y: Float, durationMs: Long) {
        val swipePath = Path().apply {
            moveTo(startX, y)
            lineTo(endX, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(swipePath, 0, durationMs))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun broadcastLiveReadout(result: OrderExtractionResult) {
        val intent = Intent(ACTION_LIVE_EXTRACTION).apply {
            putExtra(EXTRA_FARE, result.farePhp ?: 0f)
            putExtra(EXTRA_WEIGHT, result.weightKg ?: 0f)
            putExtra(EXTRA_DROPOFF, result.dropoffAddress)
            putExtra(EXTRA_CLICK_X, result.clickX ?: -1)
            putExtra(EXTRA_CLICK_Y, result.clickY ?: -1)
            putExtra(EXTRA_VERDICT, result.verdict.name)
            putExtra(EXTRA_REASON, result.reasonText)
            putExtra(EXTRA_IS_AVAILABILITY_ON, isShopeeAvailabilityOn)
            putExtra(EXTRA_RIDER_NAME, result.riderName ?: lastDetectedRiderName ?: "")
            putExtra(EXTRA_RIDER_RATING, result.riderRating ?: "")
            putExtra(EXTRA_RIDER_COMPLETION, result.riderCompletion ?: "")
        }
        sendBroadcast(intent)
    }

    private fun speak(message: String) {
        if (isTtsReady && currentRules.voiceAlertsEnabled) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "shopee_voice_id")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(testCommandReceiver)
        tts?.stop()
        tts?.shutdown()
        sendBroadcast(Intent(ACTION_SERVICE_STATUS).putExtra(EXTRA_IS_ACTIVE, false))
    }

    companion object {
        const val ACTION_SERVICE_STATUS = "com.rider.shopeeassistant.ACTION_SERVICE_STATUS"
        const val ACTION_LIVE_EXTRACTION = "com.rider.shopeeassistant.ACTION_LIVE_EXTRACTION"
        const val ACTION_AVAILABILITY_STATUS = "com.rider.shopeeassistant.ACTION_AVAILABILITY_STATUS"
        const val ACTION_RIDER_PROFILE = "com.rider.shopeeassistant.ACTION_RIDER_PROFILE"
        const val ACTION_TEST_GESTURE = "com.rider.shopeeassistant.ACTION_TEST_GESTURE"
        const val ACTION_APP_LOG = "com.rider.shopeeassistant.ACTION_APP_LOG"
        const val EXTRA_LOG_LINE = "extra_log_line"
        const val EXTRA_TEST_ACTION = "extra_test_action"

        const val EXTRA_IS_ACTIVE = "extra_is_active"
        const val EXTRA_IS_AVAILABILITY_ON = "extra_is_availability_on"
        const val EXTRA_FARE = "extra_fare"
        const val EXTRA_WEIGHT = "extra_weight"
        const val EXTRA_DROPOFF = "extra_dropoff"
        const val EXTRA_CLICK_X = "extra_click_x"
        const val EXTRA_CLICK_Y = "extra_click_y"
        const val EXTRA_VERDICT = "extra_verdict"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_RIDER_NAME = "extra_rider_name"
        const val EXTRA_RIDER_RATING = "extra_rider_rating"
        const val EXTRA_RIDER_COMPLETION = "extra_rider_completion"
    }
}
