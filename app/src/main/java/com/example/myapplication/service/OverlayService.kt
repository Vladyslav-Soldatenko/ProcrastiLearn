package com.example.myapplication.service

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
import com.example.myapplication.domain.model.GateSession
import com.example.myapplication.domain.repository.AppPreferencesRepository
import com.example.myapplication.domain.repository.VocabularyRepository
import com.example.myapplication.overlay.OverlayScreen
import com.example.myapplication.presentation.overlay.OverlayViewModel
import com.example.myapplication.utils.ServiceLifecycleOwner
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

    // Session tracking - like React's session state
    // Maps package name to unlock session
    private val unlockedSessions = mutableMapOf<String, GateSession>()
    private val serviceEntryPoint: ServiceEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            ServiceEntryPoint::class.java
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

    private var blockedPackages: Set<String> = emptySet()


    private val ignoredPackages = setOf(
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.baidu.input",
        "com.android.systemui",
        "com.google.android.systemui"
    )

    // Home/launcher packages that should clear sessions
    private val launcherPackages = setOf(
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.google.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.samsung.android.launcher",
        "com.mi.android.globallauncher",
        "com.miui.home"
    )

    override fun onServiceConnected() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // Load blocked apps from preferences
        serviceScope.launch {
            appPreferencesRepository.getBlockedApps().collect { apps ->
                Log.i(
                    "LaunchableApp", "foo" + if(apps.isNotEmpty()){apps.first()} else{"empty"}
                )

                blockedPackages = apps
            }
        }
    }

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

        // Clear sessions when user goes to home screen
        if (topPackage in launcherPackages) {
            clearAllSessions()
            if (gateActive) {
                endGateSession()
            }
            return
        }

        // Check if user navigated away from blocked app to a different app
        if (topPackage !in blockedPackages && topPackage !in ignoredPackages) {
            // User switched to a different (non-blocked) app
            gatedPackage?.let { previousGatedApp ->
                if (topPackage != previousGatedApp) {
                    // Clear the session for the previous app since user left it
                    clearSession(previousGatedApp)
                }
            }
            if (gateActive) {
                endGateSession()
            }
            return
        }

        if (!gateActive) {
            // Check if this app is blocked AND not already unlocked in this session
            if (topPackage in blockedPackages && !isUnlockedInSession(topPackage)) {
                startGateSession(topPackage)
            }
        } else {
            // While gate is active, check if user switched apps
            if (topPackage != gatedPackage && topPackage !in ignoredPackages) {
                endGateSession()
            }
        }
    }

    private fun isUnlockedInSession(packageName: String): Boolean {
        val session = unlockedSessions[packageName]
        return session != null && session.isActive
    }

    private fun markAsUnlocked(packageName: String) {
        unlockedSessions[packageName] = GateSession(
            packageName = packageName,
            unlockedAt = System.currentTimeMillis(),
            isActive = true
        )
    }

    private fun clearSession(packageName: String) {
        unlockedSessions.remove(packageName)
    }

    private fun clearAllSessions() {
        unlockedSessions.clear()
    }

    private fun startGateSession(pkg: String) {
        if (gateActive) return
        gateActive = true
        gatedPackage = pkg
        showOverlay()
    }

    private fun endGateSession() {
        hideOverlay()
        gateActive = false
        gatedPackage = null
    }

    private fun isFromInputMethod(pkg: String, cls: String): Boolean {
        return pkg.contains("inputmethod", ignoreCase = true) ||
                cls.contains("InputMethod", ignoreCase = true) ||
                pkg in ignoredPackages
    }

    private fun isIgnorableSystem(pkg: String): Boolean = pkg in ignoredPackages

    private fun currentTopPackage(): String? = rootInActiveWindow?.packageName?.toString()

    private fun showOverlay() {
        if (overlayView != null) return

        lifecycleOwner = ServiceLifecycleOwner()
        overlayView = createComposeOverlay(
            onUnlock = {
                // Mark this app as unlocked for current session
                gatedPackage?.let { pkg ->
                    markAsUnlocked(pkg)
                    endGateSession()
                    bringToFront(pkg)
                }
            }
        )

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = if (Settings.canDrawOverlays(this@OverlayAccessibilityService))
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
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
            } catch (_: Throwable) {}
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
        } catch (_: Throwable) {}
    }

    private fun createComposeOverlay(onUnlock: () -> Unit): View {
        return ComposeView(this).apply {
            lifecycleOwner?.let { owner ->
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
            }

            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    // Provide the custom factory for ViewModels
                    val viewModel: OverlayViewModel = viewModel(
                        factory = ServiceViewModelFactory(getNextVocabularyItemUseCase )
                    )

                    OverlayScreen(
                        onUnlock = onUnlock,
                        viewModel = viewModel  // Pass it explicitly
                    )
                }
            }

            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }
    override fun onInterrupt() {}

    override fun onDestroy() {
        hideOverlay()
        clearAllSessions()
        super.onDestroy()
    }
}