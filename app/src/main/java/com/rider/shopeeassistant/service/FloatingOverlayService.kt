package com.rider.shopeeassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.rider.shopeeassistant.R

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    private var tvReadout: TextView? = null
    private var tvCoords: TextView? = null
    private var tvVerdict: TextView? = null
    private var dotStatus: View? = null

    private val liveUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            // Handle Availability Status Live Broadcast
            if (intent.action == ShopeeAccessibilityService.ACTION_AVAILABILITY_STATUS ||
                intent.hasExtra(ShopeeAccessibilityService.EXTRA_IS_AVAILABILITY_ON)) {
                val isAvailabilityOn = intent.getBooleanExtra(ShopeeAccessibilityService.EXTRA_IS_AVAILABILITY_ON, true)
                updateAvailabilityDisplay(isAvailabilityOn)
                if (!isAvailabilityOn) return
            }

            val fare = intent.getFloatExtra(ShopeeAccessibilityService.EXTRA_FARE, 0f)
            val weight = intent.getFloatExtra(ShopeeAccessibilityService.EXTRA_WEIGHT, 0f)
            val dropoff = intent.getStringExtra(ShopeeAccessibilityService.EXTRA_DROPOFF) ?: ""
            val clickX = intent.getIntExtra(ShopeeAccessibilityService.EXTRA_CLICK_X, -1)
            val clickY = intent.getIntExtra(ShopeeAccessibilityService.EXTRA_CLICK_Y, -1)
            val verdict = intent.getStringExtra(ShopeeAccessibilityService.EXTRA_VERDICT) ?: ""
            val reason = intent.getStringExtra(ShopeeAccessibilityService.EXTRA_REASON) ?: ""

            if (fare > 0 || weight > 0 || dropoff.isNotEmpty()) {
                tvReadout?.text = "₱${fare.toInt()} • ${weight}kg • $dropoff"
            }
            if (clickX > 0 && clickY > 0) {
                tvCoords?.text = "🎯 Tap Target: [X: $clickX, Y: $clickY]"
            }
            if (verdict.isNotEmpty()) {
                tvVerdict?.text = reason
                if (verdict.contains("ZERO_CLICK")) {
                    tvVerdict?.setTextColor(0xFFEF4444.toInt()) // Red
                    dotStatus?.setBackgroundResource(R.drawable.circle_red)
                } else if (verdict.contains("AUTO_ACCEPT")) {
                    tvVerdict?.setTextColor(0xFF10B981.toInt()) // Green
                    dotStatus?.setBackgroundResource(R.drawable.circle_green)
                }
            }
        }
    }

    private fun updateAvailabilityDisplay(isOn: Boolean) {
        if (isOn) {
            dotStatus?.setBackgroundResource(R.drawable.circle_green)
            tvVerdict?.text = "🟢 ON AVAILABILITY - Ready to Auto-Accept"
            tvVerdict?.setTextColor(0xFF10B981.toInt())
            tvCoords?.text = "Auto-Click: ACTIVE"
            tvCoords?.setTextColor(0xFF38BDF8.toInt())
        } else {
            dotStatus?.setBackgroundResource(R.drawable.circle_red)
            tvReadout?.text = "Shopee Driver is Offline / Break"
            tvVerdict?.text = "🔴 off availability - auto click is off and others"
            tvVerdict?.setTextColor(0xFFEF4444.toInt())
            tvCoords?.text = "Auto-Click: PAUSED (Offline)"
            tvCoords?.setTextColor(0xFF94A3B8.toInt())
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        setupOverlayView()

        val filter = IntentFilter().apply {
            addAction(ShopeeAccessibilityService.ACTION_LIVE_EXTRACTION)
            addAction(ShopeeAccessibilityService.ACTION_AVAILABILITY_STATUS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(liveUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(liveUpdateReceiver, filter)
        }
    }

    private fun setupOverlayView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_hud, null)

        tvReadout = overlayView?.findViewById(R.id.overlayReadoutText)
        tvCoords = overlayView?.findViewById(R.id.overlayCoordText)
        tvVerdict = overlayView?.findViewById(R.id.overlayVerdictBanner)
        dotStatus = overlayView?.findViewById(R.id.overlayStatusDot)

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 120
        }

        overlayView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(overlayView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(overlayView, params)
    }

    private fun startForegroundServiceNotification() {
        val channelId = "shopee_assistant_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Shopee Assistant Scanner",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Shopee Assistant Active")
            .setContentText("Binabasa ang screen at naka-standby sa Shopee Driver")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(liveUpdateReceiver)
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
