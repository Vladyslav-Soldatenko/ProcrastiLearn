package com.procrastilearn.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.repository.AppPreferencesRepository
import com.procrastilearn.app.domain.repository.VocabularyRepository
import com.procrastilearn.app.overlay.OverlayScreen
import com.procrastilearn.app.overlay.OverlayViewModel
import com.procrastilearn.app.utils.ServiceLifecycleOwner
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class OverlayAccessibilityService : AccessibilityService() {
    private companion object {
        const val SECONDS_PER_MINUTE = 60
        const val MILLIS_PER_SECOND = 1000L
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Gate session state
    private var gateActive = false
    private var gatedPackage: String? = null
    private var lastHandledAt: Long = 0
    private var lastTopPackage: String? = null

    private var intervalTimerJob: Job? = null
    private var overlayIntervalMinutes: Int = 0
    private var isProcrastilearnEnabled: Boolean = true
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

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

    private val dayCountersStore: DayCountersStore by lazy {
        serviceEntryPoint.dayCountersStore()
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
                Log.d("OverlayService", "Blocked packages updated: $blockedPackages")
            }
        }

        serviceScope.launch {
            appPreferencesRepository.isProcrastilearnEnabled().collect { enabled ->
                if (!enabled && isProcrastilearnEnabled) {
                    Log.d("OverlayService", "ProcrastiLearn disabled – ending active gate session if any")
                    if (gateActive) {
                        endGateSession()
                    } else {
                        hideOverlay()
                    }
                }

                isProcrastilearnEnabled = enabled
                Log.d("OverlayService", "ProcrastiLearn enabled updated: $isProcrastilearnEnabled")
            }
        }

        serviceScope.launch {
            dayCountersStore.readPolicy().collect { config ->
                overlayIntervalMinutes = config.overlayInterval
                Log.d("OverlayService", "Overlay interval updated: $overlayIntervalMinutes minutes")
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

        // Use the package from the event directly instead of querying
        val topPackage = pkg // CHANGED: Use event package directly

        Log.d("OverlayService", "Event from package: $topPackage")

        if (!isProcrastilearnEnabled) {
            if (gateActive) {
                Log.d("OverlayService", "ProcrastiLearn disabled. Ending gate session for $topPackage")
                endGateSession()
            }
            lastTopPackage = topPackage
            return
        }

        // Check if this is a blocked app
        if (topPackage in blockedPackages) {
            Log.d(
                "OverlayService",
                "Blocked app detected: $topPackage, gateActive=$gateActive, lastTopPackage=$lastTopPackage",
            )

            if (!gateActive) {
                Log.d("OverlayService", "Starting new gate session for $topPackage")
                startGateSession(topPackage)
            } else if (lastTopPackage != topPackage) {
                // Different blocked app - restart session
                Log.d("OverlayService", "Switching to different blocked app: $lastTopPackage -> $topPackage")
                endGateSession()
                startGateSession(topPackage)
            }
            // Always update the last top package when in a blocked app
            lastTopPackage = topPackage
            gatedPackage = topPackage // Keep gatedPackage up to date
        } else if (topPackage !in ignoredPackages) {
            // User navigated to a non-blocked, non-ignored app
            if (gateActive) {
                Log.d("OverlayService", "Leaving blocked app, ending gate session")
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
                            Log.d("OverlayService", "User unlocked overlay")
                            onUnlock()
                            // Start timer after unlock and after hideOverlay() has been called
                            startIntervalTimer()
                        },
                        viewModel = viewModel,
                    )
                }
            }

            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

    private fun startGateSession(pkg: String) {
        if (!isProcrastilearnEnabled) {
            Log.d("OverlayService", "ProcrastiLearn disabled, not starting gate session for $pkg")
            return
        }
        if (gateActive && gatedPackage == pkg && overlayView != null) {
            Log.d("OverlayService", "Gate session already active for $pkg with overlay showing")
            return
        }

        serviceScope.launch {
            val hasItems = checkVocabularyAvailabilityUseCase()
            Log.d("OverlayService", "Checking vocabulary availability: hasItems=$hasItems")

            if (hasItems) {
                gateActive = true
                gatedPackage = pkg
                showOverlay()
                // Don't start timer here - it will be started after unlock
                Log.d("OverlayService", "Gate session started for $pkg, overlay shown")
            } else {
                Log.i("OverlayService", "No vocabulary items available (limits reached), not showing overlay")
            }
        }
    }

    private fun startIntervalTimer() {
        if (!isProcrastilearnEnabled) {
            Log.d("OverlayService", "ProcrastiLearn disabled, skipping interval timer start")
            return
        }

        intervalTimerJob?.cancel()
        Log.d(
            "OverlayService",
            "Starting interval timer: intervalMinutes=$overlayIntervalMinutes," +
                " overlayView=${overlayView != null}, gateActive=$gateActive",
        )

        // Don't start timer if interval is 0 or overlay is currently showing
        if (overlayIntervalMinutes <= 0) {
            Log.d("OverlayService", "Timer not started: interval is 0 or negative")
            return
        }

        if (overlayView != null) {
            Log.d("OverlayService", "Timer not started: overlay is currently showing")
            return
        }

        intervalTimerJob =
            serviceScope.launch {
                val delayMs = overlayIntervalMinutes * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
                Log.d("OverlayService", "Timer started, will fire in $overlayIntervalMinutes minutes ($delayMs ms)")

                delay(delayMs)

                Log.d("OverlayService", "Timer fired! gateActive=$gateActive, gatedPackage=$gatedPackage")

                // If we're still in an active gate session, show the overlay
                // Trust the gateActive state rather than re-querying the current package
                if (isProcrastilearnEnabled && gateActive && gatedPackage != null) {
                    Log.d("OverlayService", "Still in gate session for $gatedPackage, showing overlay again")
                    // Check if vocabulary is still available
                    val hasItems = checkVocabularyAvailabilityUseCase()
                    if (hasItems) {
                        showOverlay()
                    } else {
                        Log.d("OverlayService", "No vocabulary items available, not showing overlay")
                    }
                } else {
                    Log.d("OverlayService", "Gate not active or no gated package, not showing overlay")
                }
            }
    }

    private fun stopIntervalTimer() {
        Log.d("OverlayService", "Stopping interval timer")
        intervalTimerJob?.cancel()
        intervalTimerJob = null
    }

    private fun endGateSession() {
        Log.d("OverlayService", "Ending gate session")
        hideOverlay()
        stopIntervalTimer()
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

    private fun showOverlay() {
        if (!isProcrastilearnEnabled) {
            Log.d("OverlayService", "ProcrastiLearn disabled, not showing overlay")
            return
        }
        if (overlayView != null) {
            Log.d("OverlayService", "Overlay already showing, not creating new one")
            return
        }

        Log.d("OverlayService", "Creating and showing overlay")
        lifecycleOwner = ServiceLifecycleOwner()
        overlayView =
            createComposeOverlay(
                onUnlock = {
                    // Mark this app as unlocked for current session
                    gatedPackage?.let { pkg ->
                        Log.d("OverlayService", "Hiding overlay and bringing $pkg to front")
                        hideOverlay()
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
        Log.d("OverlayService", "Overlay added to window manager")
        requestAudioFocus()
    }

    private fun hideOverlay() {
        Log.d("OverlayService", "Hiding overlay")
        overlayView?.let {
            try {
                windowManager?.removeView(it)
                Log.d("OverlayService", "Overlay removed from window manager")
            } catch (e: IllegalArgumentException) {
                // View not attached to window or already removed
                Log.w("OverlayService", "Overlay remove called on a non-attached view", e)
            }
        }
        releaseAudioFocus()
        overlayView = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }

    private fun bringToFront(pkg: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.d("OverlayService", "Brought $pkg to front")
        } catch (e: android.content.ActivityNotFoundException) {
            Log.e("OverlayService", "Launch activity for $pkg not found", e)
        } catch (e: SecurityException) {
            Log.e("OverlayService", "Security exception launching $pkg", e)
        }
    }

    @Suppress("EmptyFunctionBlock")
    override fun onInterrupt() {}

    private fun requestAudioFocus() {
        if (focusRequest != null) return

        val manager = audioManager ?: (getSystemService(AUDIO_SERVICE) as? AudioManager)
        audioManager = manager ?: return

        val focusRequest =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                        .build()
                )
                .build()

        val result = manager.requestAudioFocus(focusRequest)
        Log.i("OverlayService", "Audio focus request result: $result")
        this.focusRequest = focusRequest
    }

    private fun releaseAudioFocus() {
        focusRequest?.let { request ->
            audioManager?.abandonAudioFocusRequest(request)
            Log.i("OverlayService", "Audio focus abandoned")
        }
        focusRequest = null
        audioManager = null
    }

    override fun onDestroy() {
        Log.d("OverlayService", "Service destroying")
        hideOverlay()
        stopIntervalTimer()
        serviceScope.cancel()
        super.onDestroy()
    }
}
