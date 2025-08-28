package com.procrastilearn.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.procrastilearn.app.domain.repository.AppPreferencesRepository
import com.procrastilearn.app.domain.repository.VocabularyRepository
import com.procrastilearn.app.overlay.OverlayScreen
import com.procrastilearn.app.overlay.OverlayViewModel
import com.procrastilearn.app.utils.ServiceLifecycleOwner
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OverlayAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Gate session state
    private var gateActive = false
    private var gatedPackage: String? = null
    private var lastHandledAt: Long = 0
    private var lastTopPackage: String? = null

    private val serviceEntryPoint: ServiceEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            ServiceEntryPoint::class.java,
        )
    }

    private val appPreferencesRepository: AppPreferencesRepository by lazy {
        serviceEntryPoint.appPreferencesRepository()
    }

    private val vocabularyRepository: VocabularyRepository by lazy {
        serviceEntryPoint.vocabularyRepository()
    }

    private val getNextVocabularyItemUseCase by lazy {
        serviceEntryPoint.getNextVocabularyItemUseCase()
    }
    private val getSaveDifficultyRatingUseCase by lazy {
        serviceEntryPoint.getSaveDifficultyRatingUseCase()
    }
    private val checkVocabularyAvailabilityUseCase by lazy {
        serviceEntryPoint.checkVocabularyAvailabilityUseCase()
    }

    private var blockedPackages: Set<String> = emptySet()

    private val ignoredPackages =
        setOf(
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.baidu.input",
            "com.android.systemui",
            "com.google.android.systemui",
        )

    override fun onServiceConnected() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        serviceScope.launch {
            appPreferencesRepository.getBlockedApps().collect { apps ->
                blockedPackages = apps
            }
        }
    }

    @Suppress("ReturnCount", "MagicNumber", "CyclomaticComplexMethod")
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val now = SystemClock.uptimeMillis()
        if (now - lastHandledAt < 80) return
        lastHandledAt = now

        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: ""

        if (pkg == packageName) return
        if (isFromInputMethod(pkg, cls) || isIgnorableSystem(pkg)) return

        val topPackage = currentTopPackage() ?: pkg

        Log.i("fsrs", "$topPackage, ")

        // Check if this is a blocked app
        if (topPackage in blockedPackages) {
            // Only start gate if we're coming from a different app (not just unlocking)
            if (!gateActive && lastTopPackage != topPackage) {
                startGateSession(topPackage)
            }
            lastTopPackage = topPackage
        } else if (topPackage !in ignoredPackages) {
            // User navigated to a non-blocked, non-ignored app
            if (gateActive) {
                endGateSession()
            }
            lastTopPackage = topPackage
        }
    }

    private fun createComposeOverlay(onUnlock: () -> Unit): View =
        ComposeView(this).apply {
            lifecycleOwner?.let { owner ->
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
            }

            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    // Provide the custom factory for ViewModels
                    val viewModel: OverlayViewModel =
                        viewModel(
                            factory =
                                ServiceViewModelFactory(
                                    getNextVocabularyItemUseCase,
                                    getSaveDifficultyRatingUseCase,
                                ),
                        )

                    OverlayScreen(
                        onUnlock = {
                            onUnlock()
                            // Don't reset lastTopPackage here, keep it as the blocked app
                            // so it won't trigger again until user leaves the app
                        },
                        viewModel = viewModel, // Pass it explicitly
                    )
                }
            }

            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

    private fun startGateSession(pkg: String) {
        if (gateActive) return

        serviceScope.launch {
            val hasItems = checkVocabularyAvailabilityUseCase()

            if (hasItems) {
                gateActive = true
                gatedPackage = pkg
                showOverlay()
            } else {
                Log.i("fsrs", "No vocabulary items available (limits reached), not showing overlay")
            }
        }
    }

    private fun endGateSession() {
        hideOverlay()
        gateActive = false
        gatedPackage = null
    }

    private fun isFromInputMethod(
        pkg: String,
        cls: String,
    ): Boolean =
        pkg.contains("inputmethod", ignoreCase = true) ||
            cls.contains("InputMethod", ignoreCase = true) ||
            pkg in ignoredPackages

    private fun isIgnorableSystem(pkg: String): Boolean = pkg in ignoredPackages

    private fun currentTopPackage(): String? = rootInActiveWindow?.packageName?.toString()

    private fun showOverlay() {
        if (overlayView != null) return

        lifecycleOwner = ServiceLifecycleOwner()
        overlayView =
            createComposeOverlay(
                onUnlock = {
                    // Mark this app as unlocked for current session
                    gatedPackage?.let { pkg ->
                        endGateSession()
                        bringToFront(pkg)
                    }
                },
            )

        val params =
            WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                type =
                    if (Settings.canDrawOverlays(this@OverlayAccessibilityService)) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    }
                flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                format = PixelFormat.TRANSLUCENT
            }

        windowManager?.addView(overlayView, params)
    }

    private fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Throwable) {
            }
        }
        overlayView = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }

    private fun bringToFront(pkg: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    @Suppress("EmptyFunctionBlock")
    override fun onInterrupt() {}

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }
}
