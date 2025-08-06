package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView

class OverlayService : AccessibilityService() {
    private var wm: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShown = false
    private val handler = Handler(Looper.getMainLooper())

    // Store the last valid app package (excluding our own)
    private var lastValidPackage: String? = null

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                event.packageName?.toString()
            }
            else -> return
        }

        // Ignore events from our own app
        if (pkg == packageName) {
            Log.i("LaunchableApp", "Ignoring event from own package")
            return
        }

        Log.i("LaunchableApp", "Event from package: $pkg, type: ${event.eventType}")

        // Update last valid package
        if (pkg != null) {
            lastValidPackage = pkg
        }

        // Cancel any pending operations
        handler.removeCallbacksAndMessages(null)

        when (pkg) {
            "com.android.vending" -> {
                // Small delay to ensure window is fully loaded
                handler.postDelayed({
                    if (!isOverlayShown) {
                        showOverlay()
                    }
                }, 100)
            }
            else -> {
                // Only hide if we're actually leaving Play Store
                // and not just getting an event from our overlay
                if (isOverlayShown && pkg != null && pkg != packageName) {
                    hideOverlay()
                }
            }
        }
    }

    private fun showOverlay() {
        Log.i("LaunchableApp", "Showing overlay")

        // Ensure no duplicate overlays
        if (isOverlayShown) return

        overlayView = createOverlayView()

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
        }

        try {
            wm?.addView(overlayView, params)
            isOverlayShown = true

            // Double-check after a short delay that we're still in Play Store
            handler.postDelayed({
                if (lastValidPackage != "com.android.vending" && isOverlayShown) {
                    Log.i("LaunchableApp", "Not in Play Store anymore, hiding overlay")
                    hideOverlay()
                }
            }, 500)
        } catch (e: Exception) {
            Log.e("LaunchableApp", "Error adding overlay", e)
            isOverlayShown = false
        }
    }

    private fun hideOverlay() {
        Log.i("LaunchableApp", "Hiding overlay")

        overlayView?.let {
            try {
                wm?.removeView(it)
            } catch (e: Exception) {
                Log.e("LaunchableApp", "Error removing overlay", e)
            }
            overlayView = null
        }
        isOverlayShown = false
    }

    private fun createOverlayView(): View {
        // Create a full-screen overlay
        val layout = FrameLayout(this).apply {
            setBackgroundColor(0xE6FF0000.toInt()) // Semi-transparent red background
            // Important: make it non-importantForAccessibility to reduce events
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        val textView = TextView(this).apply {
            text = "⚠️ WARNING ⚠️\n\nYou are in Play Store\n\nThis is a restricted app"
            textSize = 32f
            setTextColor(0xFFFFFFFF.toInt()) // White text
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        layout.addView(textView)
        return layout
    }

    override fun onInterrupt() {
        hideOverlay()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        hideOverlay()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}