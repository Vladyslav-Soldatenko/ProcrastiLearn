package com.procrastilearn.app.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.MainThread
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.procrastilearn.app.BuildConfig
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.repository.NoAvailableItemsException
import com.procrastilearn.app.domain.model.GateTimingSettings
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.model.toGateTimingSettings
import com.procrastilearn.app.domain.repository.AppPreferencesRepository
import com.procrastilearn.app.domain.repository.VocabularyStudyRepository
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import com.procrastilearn.app.overlay.GateCompletion
import com.procrastilearn.app.overlay.OverlayScreen
import com.procrastilearn.app.overlay.OverlayViewModel
import com.procrastilearn.app.utils.ServiceLifecycleOwner
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

private const val TAG = "OverlayAccessibilityService"

@SuppressLint("SyntheticAccessor")
@Suppress("TooManyFunctions")
class OverlayAccessibilityService : AccessibilityService() {
    internal var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private var gateCompletionCallback: ((GateCompletion) -> Unit)? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller = GateSessionController()
    private var firstCardLoadJob: Job? = null
    private var intervalTimerJob: Job? = null
    private var blockedPackages: Set<String> = emptySet()
    private var isProcrastilearnEnabled = false
    private var timing = GateTimingSettings()
    private var cardsPerGate = 1
    private var appsReady = false
    private var enabledReady = false
    private var policyReady = false
    private var pauseBackgroundAudio = true
    private var foregroundPackage: String? = null
    private var foregroundEpoch = 0L
    private var ownActivityResumed = false
    private var connected = false
    private var screenReceiverRegistered = false
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var elapsedClock: () -> Long = SystemClock::elapsedRealtime

    private val serviceEntryPoint: ServiceEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, ServiceEntryPoint::class.java)
    }

    @Suppress("LateinitUsage")
    internal lateinit var appPreferencesRepository: AppPreferencesRepository

    @Suppress("LateinitUsage")
    internal lateinit var vocabularyRepository: VocabularyStudyRepository

    @Suppress("LateinitUsage")
    internal lateinit var getNextVocabularyItemUseCase: GetNextVocabularyItemUseCase

    @Suppress("LateinitUsage")
    internal lateinit var getSaveDifficultyRatingUseCase: SaveDifficultyRatingUseCase

    @Suppress("LateinitUsage")
    internal lateinit var dayCountersStore: DayCountersStore

    @Suppress("LateinitUsage")
    internal lateinit var ownActivityForegroundStore: OwnActivityForegroundStore

    private val ignoredPackages =
        setOf(
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.baidu.input",
            "com.android.systemui",
            "com.google.android.systemui",
        )

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == Intent.ACTION_SCREEN_OFF || intent.action == Intent.ACTION_SCREEN_ON) {
                    invalidateForeground()
                }
                updateContext()
            }
        }

    @MainThread
    internal fun setStudyUseCasesForE2eTests(
        getNextVocabularyItemUseCase: GetNextVocabularyItemUseCase,
        getSaveDifficultyRatingUseCase: SaveDifficultyRatingUseCase,
    ) {
        checkDebugMainThread()
        this.getNextVocabularyItemUseCase = getNextVocabularyItemUseCase
        this.getSaveDifficultyRatingUseCase = getSaveDifficultyRatingUseCase
    }

    @MainThread
    internal fun resetGateStateForE2eTests() {
        checkDebugMainThread()
        handle(GateEvent.Stop)
        controller = GateSessionController()
        invalidateForeground()
        elapsedClock = SystemClock::elapsedRealtime
        updateContext()
    }

    @MainThread
    internal fun setGateClockForE2eTests(clock: () -> Long) {
        checkDebugMainThread()
        elapsedClock = clock
    }

    @MainThread
    internal fun reevaluateGateTimingForE2eTests() {
        checkDebugMainThread()
        updateContext()
        val state = controller.state
        if (state.nextRepeatDeadlineElapsedMs?.let { it <= elapsedClock() } == true) {
            handle(GateEvent.RepeatWake(state.timerRevision))
        }
    }

    @MainThread
    internal fun gateSnapshotForE2eTests(): GateRuntimeSnapshot {
        checkDebugMainThread()
        val state = controller.state
        val attempt = (state.phase as? GatePhase.Loading)?.attempt ?: (state.phase as? GatePhase.Studying)?.attempt
        return GateRuntimeSnapshot(
            foregroundPackage = state.context?.foregroundPackage,
            activeAttemptId = attempt?.id,
            activeAttemptPackage = attempt?.packageName,
            lastReviewedReleaseElapsedMs = state.lastReviewedReleaseElapsedMs,
            cooldownDeadlineElapsedMs = state.cooldownDeadlineElapsedMs,
            nextRepeatDeadlineElapsedMs = state.nextRepeatDeadlineElapsedMs,
            overlayAttached = overlayView != null,
            audioFocusAttached = focusRequest != null,
        )
    }

    private fun initializeDependenciesIfNeeded() {
        if (!::appPreferencesRepository.isInitialized) {
            appPreferencesRepository =
                serviceEntryPoint.appPreferencesRepository()
        }
        if (!::vocabularyRepository.isInitialized) vocabularyRepository = serviceEntryPoint.vocabularyRepository()
        if (!::getNextVocabularyItemUseCase.isInitialized) {
            getNextVocabularyItemUseCase =
                serviceEntryPoint.getNextVocabularyItemUseCase()
        }
        if (!::getSaveDifficultyRatingUseCase.isInitialized) {
            getSaveDifficultyRatingUseCase =
                serviceEntryPoint.getSaveDifficultyRatingUseCase()
        }
        if (!::dayCountersStore.isInitialized) dayCountersStore = serviceEntryPoint.dayCountersStore()
        if (!::ownActivityForegroundStore.isInitialized) {
            ownActivityForegroundStore =
                serviceEntryPoint.ownActivityForegroundStore()
        }
    }

    override fun onServiceConnected() {
        if (connected) return
        connected = true
        if (windowManager == null) windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        initializeDependenciesIfNeeded()
        ownActivityResumed = ownActivityForegroundStore.isResumed.value
        if (BuildConfig.DEBUG) debugInstanceReference = WeakReference(this)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiverRegistered = true
        serviceScope.launch {
            appPreferencesRepository.getBlockedApps().distinctUntilChanged().collect {
                blockedPackages = it
                appsReady = true
                updateContext()
            }
        }
        serviceScope.launch {
            appPreferencesRepository.isProcrastilearnEnabled().distinctUntilChanged().collect {
                isProcrastilearnEnabled = it
                enabledReady = true
                updateContext()
            }
        }
        serviceScope.launch {
            appPreferencesRepository.pauseBackgroundAudio().distinctUntilChanged().collect {
                pauseBackgroundAudio = it
            }
        }
        serviceScope.launch {
            dayCountersStore
                .readPolicy()
                .map { it.toGateTimingSettings() to it.cardsPerGate }
                .distinctUntilChanged()
                .collect {
                    timing = it.first
                    cardsPerGate = it.second
                    policyReady = true
                    updateContext()
                }
        }
        serviceScope.launch {
            ownActivityForegroundStore.isResumed.collect {
                ownActivityResumed = it
                if (it) invalidateForeground()
                updateContext()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        if (pkg == packageName) {
            if (ownActivityForegroundStore.isResumed.value || cls.endsWith("Activity")) {
                invalidateForeground()
                updateContext()
            }
        } else if (pkg !in ignoredPackages &&
            !pkg.contains("inputmethod", true) &&
            !cls.contains("InputMethod", true)
        ) {
            if (foregroundPackage != pkg) {
                foregroundPackage = pkg
                foregroundEpoch++
            }
            updateContext()
        }
    }

    private fun invalidateForeground() {
        foregroundPackage = null
        foregroundEpoch++
    }

    private fun updateContext() {
        val deviceEligible = isDeviceEligible()
        ownActivityResumed = ownActivityForegroundStore.isResumed.value
        if ((!deviceEligible || ownActivityResumed) && foregroundPackage != null) invalidateForeground()
        val previous = controller.state.context
        val snapshot =
            GateContextSnapshot(
                preferencesReady = appsReady && enabledReady && policyReady,
                enabled = isProcrastilearnEnabled,
                blockedPackages = blockedPackages,
                timing = timing,
                cardsPerGate = cardsPerGate,
                foregroundPackage = foregroundPackage,
                foregroundEpoch = foregroundEpoch,
                deviceEligible = deviceEligible,
                ownActivityResumed = ownActivityResumed,
            )
        if (
            previous != null &&
            previous.isEligible != snapshot.isEligible &&
            previous.foregroundEpoch == foregroundEpoch
        ) {
            foregroundEpoch++
        }
        handle(GateEvent.ContextChanged(snapshot.copy(foregroundEpoch = foregroundEpoch)))
    }

    private fun handle(event: GateEvent) {
        controller.handle(event, elapsedClock()).forEach { effect ->
            when (effect) {
                is GateEffect.LoadFirstCard -> {
                    loadFirstCard(effect.attempt)
                }
                GateEffect.CancelFirstCardLoad -> {
                    firstCardLoadJob?.cancel()
                    firstCardLoadJob = null
                }
                is GateEffect.ShowOverlay -> {
                    showOverlay(effect.attempt, effect.item)
                }
                GateEffect.HideOverlay -> {
                    hideOverlay()
                }
                is GateEffect.ScheduleRepeat -> {
                    scheduleRepeat(effect)
                }
                GateEffect.CancelRepeat -> {
                    intervalTimerJob?.cancel()
                    intervalTimerJob = null
                }
                is GateEffect.ReturnToApp -> {
                    val context = controller.state.context
                    if (
                        context?.isEligible == true &&
                        context.foregroundPackage == effect.packageName &&
                        isDeviceEligible()
                    ) {
                        bringToFront(effect.packageName)
                    }
                }
            }
        }
    }

    private fun loadFirstCard(attempt: GateAttempt) {
        firstCardLoadJob?.cancel()
        firstCardLoadJob =
            serviceScope.launch {
                val result = getNextVocabularyItemUseCase()
                currentCoroutineContext().ensureActive()
                updateContext()
                currentCoroutineContext().ensureActive()
                result.fold(
                    onSuccess = { handle(GateEvent.FirstCardLoaded(attempt.id, it)) },
                    onFailure = {
                        if (it is CancellationException) throw it
                        if (it !is NoAvailableItemsException) Log.w(TAG, "Failed to load first gate card", it)
                        handle(GateEvent.FirstCardUnavailable(attempt.id))
                    },
                )
            }
    }

    private fun scheduleRepeat(effect: GateEffect.ScheduleRepeat) {
        intervalTimerJob?.cancel()
        intervalTimerJob =
            serviceScope.launch {
                delay((effect.deadlineElapsedMs - elapsedClock()).coerceAtLeast(0))
                updateContext()
                currentCoroutineContext().ensureActive()
                handle(GateEvent.RepeatWake(effect.timerRevision))
            }
    }

    private fun completeGate(
        attempt: GateAttempt,
        completion: GateCompletion,
    ) {
        updateContext()
        val studying = controller.state.phase as? GatePhase.Studying ?: return
        if (studying.attempt.id != attempt.id || overlayView == null) return
        hideOverlay()
        handle(GateEvent.OverlayReleased(attempt.id, completion))
    }

    @Suppress("DEPRECATION", "TooGenericExceptionCaught")
    private fun showOverlay(
        attempt: GateAttempt,
        initialItem: VocabularyItem,
    ) {
        updateContext()
        val studying = controller.state.phase as? GatePhase.Studying ?: return
        if (studying.attempt.id != attempt.id || overlayView != null) return
        val owner = ServiceLifecycleOwner()
        lifecycleOwner = owner
        val callback: (GateCompletion) -> Unit = { completeGate(attempt, it) }
        gateCompletionCallback = callback
        val view = createComposeOverlay(owner, initialItem, attempt, callback)
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
                flags =
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                format = PixelFormat.TRANSLUCENT
            }
        try {
            checkNotNull(windowManager).addView(view, params)
            overlayView = view
            if (pauseBackgroundAudio) requestAudioFocus()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Failed to attach gate overlay", error)
            if (overlayView != null) {
                hideOverlay()
            } else {
                owner.onDestroy()
                lifecycleOwner = null
                gateCompletionCallback = null
                releaseAudioFocus()
            }
            handle(GateEvent.OverlayAttachFailed(attempt.id))
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "Failed to remove overlay view", error)
            }
        }
        releaseAudioFocus()
        overlayView = null
        gateCompletionCallback = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }

    @Suppress("EmptyFunctionBlock")
    override fun onInterrupt() {}

    private fun requestAudioFocus() {
        if (focusRequest != null) return
        val manager = audioManager ?: getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = manager
        val request =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                        .build(),
                ).build()
        try {
            if (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                focusRequest = request
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Failed to request gate audio focus", error)
        }
    }

    private fun releaseAudioFocus() {
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
        audioManager = null
    }

    override fun onDestroy() {
        handle(GateEvent.Stop)
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver)
            screenReceiverRegistered = false
        }
        serviceScope.cancel()
        if (BuildConfig.DEBUG && debugInstanceForE2eTests === this) debugInstanceReference = null
        super.onDestroy()
    }

    internal companion object {
        @Volatile
        private var debugInstanceReference: WeakReference<OverlayAccessibilityService>? = null

        internal val debugInstanceForE2eTests: OverlayAccessibilityService?
            get() = debugInstanceReference?.get()
    }
}

data class GateRuntimeSnapshot(
    val foregroundPackage: String?,
    val activeAttemptId: Long?,
    val activeAttemptPackage: String?,
    val lastReviewedReleaseElapsedMs: Long?,
    val cooldownDeadlineElapsedMs: Long?,
    val nextRepeatDeadlineElapsedMs: Long?,
    val overlayAttached: Boolean,
    val audioFocusAttached: Boolean,
)

private fun Context.bringToFront(pkg: String) {
    try {
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (error: android.content.ActivityNotFoundException) {
        Log.w(TAG, "Failed to bring $pkg to front", error)
    } catch (error: SecurityException) {
        Log.w(TAG, "Failed to bring $pkg to front", error)
    }
}

private fun checkDebugMainThread() {
    check(BuildConfig.DEBUG) { "E2E hooks are only available in debug builds" }
    check(Looper.myLooper() == Looper.getMainLooper()) { "E2E hooks must run on the main thread" }
}

private fun Context.isDeviceEligible(): Boolean =
    (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive &&
        !(getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

private fun OverlayAccessibilityService.createComposeOverlay(
    owner: ServiceLifecycleOwner,
    initialItem: VocabularyItem,
    attempt: GateAttempt,
    callback: (GateCompletion) -> Unit,
): View =
    ComposeView(this).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        val viewModel =
            ViewModelProvider(
                owner,
                ServiceViewModelFactory(getNextVocabularyItemUseCase, getSaveDifficultyRatingUseCase, dayCountersStore),
            ).get(OverlayViewModel::class.java)
        viewModel.seedInitialWord(initialItem, attempt.requiredCards)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                OverlayScreen(onGateCompleted = callback, viewModel = viewModel)
            }
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
