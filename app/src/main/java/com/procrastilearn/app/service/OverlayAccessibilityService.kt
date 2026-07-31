package com.procrastilearn.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.AppPreferencesRepository
import com.procrastilearn.app.domain.repository.VocabularyStudyRepository
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
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

    internal var windowManager: WindowManager? = null
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

    internal lateinit var appPreferencesRepository: AppPreferencesRepository
    internal lateinit var vocabularyRepository: VocabularyStudyRepository
    internal lateinit var getNextVocabularyItemUseCase: GetNextVocabularyItemUseCase
    internal lateinit var getSaveDifficultyRatingUseCase: SaveDifficultyRatingUseCase
    internal lateinit var dayCountersStore: DayCountersStore

    private fun initializeDependenciesIfNeeded() {
        if (!::appPreferencesRepository.isInitialized) {
            appPreferencesRepository = serviceEntryPoint.appPreferencesRepository()
        }
        if (!::vocabularyRepository.isInitialized) {
            vocabularyRepository = serviceEntryPoint.vocabularyRepository()
        }
        if (!::getNextVocabularyItemUseCase.isInitialized) {
            getNextVocabularyItemUseCase = serviceEntryPoint.getNextVocabularyItemUseCase()
        }
        if (!::getSaveDifficultyRatingUseCase.isInitialized) {
            getSaveDifficultyRatingUseCase = serviceEntryPoint.getSaveDifficultyRatingUseCase()
        }
        if (!::dayCountersStore.isInitialized) {
            dayCountersStore = serviceEntryPoint.dayCountersStore()
        }
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
        if (windowManager == null) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        }
        initializeDependenciesIfNeeded()

        serviceScope.launch {
            appPreferencesRepository.getBlockedApps().collect { apps ->
                blockedPackages = apps
            }
        }

        serviceScope.launch {
            appPreferencesRepository.isProcrastilearnEnabled().collect { enabled ->
                if (!enabled && isProcrastilearnEnabled) {
                    if (gateActive) {
                        endGateSession()
                    } else {
                        hideOverlay()
                    }
                }

                isProcrastilearnEnabled = enabled
            }
        }

        serviceScope.launch {
            dayCountersStore.readPolicy().collect { config ->
                overlayIntervalMinutes = config.overlayInterval
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

        if (!isProcrastilearnEnabled) {
            if (gateActive) {
                endGateSession()
            }
            lastTopPackage = topPackage
            return
        }

        // Check if this is a blocked app
        if (topPackage in blockedPackages) {
            if (!gateActive) {
                startGateSession(topPackage)
            } else if (lastTopPackage != topPackage) {
                // Different blocked app - restart session
                endGateSession()
                startGateSession(topPackage)
            }
            // Always update the last top package when in a blocked app
            lastTopPackage = topPackage
            gatedPackage = topPackage // Keep gatedPackage up to date
        } else if (topPackage !in ignoredPackages) {
            // User navigated to a non-blocked, non-ignored app
            if (gateActive) {
                endGateSession()
            }
            lastTopPackage = topPackage
        }
    }

    private fun createComposeOverlay(
        owner: ServiceLifecycleOwner,
        initialItem: VocabularyItem,
        onUnlock: () -> Unit,
    ): View =
        ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)

            val viewModel =
                ViewModelProvider(
                    owner,
                    ServiceViewModelFactory(
                        getNextVocabularyItemUseCase,
                        getSaveDifficultyRatingUseCase,
                    ),
                ).get(OverlayViewModel::class.java)
            // Seed the already-loaded word so the first composition renders it immediately,
            // instead of drawing an empty frame and waiting on an async update.
            viewModel.seedInitialWord(initialItem)

            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    OverlayScreen(
                        onUnlock = {
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
            return
        }
        if (gateActive && gatedPackage == pkg && overlayView != null) {
            return
        }

        serviceScope.launch {
            // Load the word *before* attaching the overlay so the first frame already shows it.
            getNextVocabularyItemUseCase()
                .onSuccess { item ->
                    gateActive = true
                    gatedPackage = pkg
                    showOverlay(item)
                    // Don't start timer here - it will be started after unlock
                }.onFailure { }
        }
    }

    internal fun startIntervalTimer() {
        if (!isProcrastilearnEnabled) {
            return
        }

        intervalTimerJob?.cancel()

        // Don't start timer if interval is 0 or overlay is currently showing
        if (overlayIntervalMinutes <= 0) {
            return
        }

        if (overlayView != null) {
            return
        }

        intervalTimerJob =
            serviceScope.launch {
                val delayMs = overlayIntervalMinutes * SECONDS_PER_MINUTE * MILLIS_PER_SECOND

                delay(delayMs)

                // If we're still in an active gate session, show the overlay
                // Trust the gateActive state rather than re-querying the current package
                if (isProcrastilearnEnabled && gateActive && gatedPackage != null) {
                    // Load the next word before re-showing so the first frame already has it.
                    getNextVocabularyItemUseCase()
                        .onSuccess { item -> showOverlay(item) }
                        .onFailure { }
                }
            }
    }

    private fun stopIntervalTimer() {
        intervalTimerJob?.cancel()
        intervalTimerJob = null
    }

    private fun endGateSession() {
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

    private fun showOverlay(initialItem: VocabularyItem) {
        if (!isProcrastilearnEnabled) {
            return
        }
        if (overlayView != null) {
            return
        }

        val owner = ServiceLifecycleOwner()
        lifecycleOwner = owner
        overlayView =
            createComposeOverlay(
                owner = owner,
                initialItem = initialItem,
                onUnlock = {
                    // Mark this app as unlocked for current session
                    gatedPackage?.let { pkg ->
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
        requestAudioFocus()
    }

    @Suppress("SwallowedException")
    private fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: IllegalArgumentException) {
                // View not attached to window or already removed
            }
        }
        releaseAudioFocus()
        overlayView = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }

    @Suppress("SwallowedException")
    private fun bringToFront(pkg: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            // App may have been uninstalled or launch intent revoked between check and launch
        } catch (e: SecurityException) {
            // App may refuse external launch (e.g. exported=false activity)
        }
    }

    @Suppress("EmptyFunctionBlock")
    override fun onInterrupt() {}

    private fun requestAudioFocus() {
        if (focusRequest != null) return

        val manager = audioManager ?: (getSystemService(AUDIO_SERVICE) as? AudioManager)
        audioManager = manager ?: return

        val focusRequest =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                        .build(),
                ).build()

        manager.requestAudioFocus(focusRequest)
        this.focusRequest = focusRequest
    }

    private fun releaseAudioFocus() {
        focusRequest?.let { request ->
            audioManager?.abandonAudioFocusRequest(request)
        }
        focusRequest = null
        audioManager = null
    }

    override fun onDestroy() {
        hideOverlay()
        stopIntervalTimer()
        serviceScope.cancel()
        super.onDestroy()
    }
}
