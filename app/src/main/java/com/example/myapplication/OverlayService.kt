package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class OverlayService : AccessibilityService() {
    private var wm: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShown = false
    private var lifecycleOwner: ServiceLifecycleOwner? = null

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

        if (pkg == packageName) return

        when (pkg) {
            "com.android.vending" -> {
                if (!isOverlayShown) showOverlay()
            }
            else -> {
                if (isOverlayShown && pkg != null) hideOverlay()
            }
        }
    }

    private fun showOverlay() {
        if (isOverlayShown) return

        lifecycleOwner = ServiceLifecycleOwner()
        overlayView = createComposeOverlay()

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
        }

        wm?.addView(overlayView, params)
        isOverlayShown = true
    }

    private fun hideOverlay() {
        overlayView?.let {
            wm?.removeView(it)
            overlayView = null
        }
        isOverlayShown = false
        lifecycleOwner = null
    }

    private fun createComposeOverlay(): View {
        return ComposeView(this).apply {
            setContent {
                WarningOverlay()
            }

            // Setup lifecycle for Compose
            lifecycleOwner?.let { owner ->
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
            }

            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    override fun onInterrupt() {
        hideOverlay()
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }
}

@Composable
fun WarningOverlay() {
    // State management
    var isAuthenticated by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isAuthenticated)
                    Color.Green.copy(alpha = 0.9f)
                else
                    Color.Red.copy(alpha = 0.9f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isAuthenticated) {
                Text(
                    text = "⚠️ WARNING ⚠️",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Play Store is restricted",
                    fontSize = 20.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                // Placeholder for password input
                Text(
                    text = "[Password Input Here]",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                if (showError) {
                    Text(
                        text = "Incorrect password",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }

                // Placeholder for buttons
                Text(
                    text = "[Go Back] [Unlock]",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                Text(
                    text = "✓ Access Granted",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "[Continue Button]",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

// Minimal lifecycle owner implementation
class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore: ViewModelStore = store
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }
}