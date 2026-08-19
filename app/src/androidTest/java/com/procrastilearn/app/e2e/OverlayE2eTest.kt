package com.procrastilearn.app.e2e

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.di.DatabaseEntryPoint
import com.procrastilearn.app.domain.repository.AppPreferencesRepository
import com.procrastilearn.app.service.OverlayAccessibilityService
import com.procrastilearn.app.service.ServiceEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class OverlayE2eTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var targetContext: Context
    private lateinit var uiAutomation: UiAutomation
    private var previousEnabledServices: String? = null

    @Before
    fun beforeEach() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        // UiAutomation suppresses all other accessibility services while connected, so without
        // this flag the shell commands below would write enabled_accessibility_services
        // correctly but AccessibilityManagerService would never actually bind our service.
        uiAutomation =
            InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

        val serviceComponent = "${targetContext.packageName}/${OverlayAccessibilityService::class.java.name}"
        previousEnabledServices = uiAutomation.shell("settings get secure enabled_accessibility_services").trim()
        uiAutomation.shell("settings put secure enabled_accessibility_services $serviceComponent")
        uiAutomation.shell("settings put secure accessibility_enabled 1")
        uiAutomation.shell("appops set ${targetContext.packageName} SYSTEM_ALERT_WINDOW allow")
        waitUntilAccessibilityServiceBound()

        resetAppState()
        seedWord(SEEDED_WORD, "seeded-translation", position = 0L)
        seedWord(SECOND_WORD, "seeded-translation-2", position = 1L)
        runBlocking(Dispatchers.IO) {
            appPreferencesRepository().setBlockedApps(emptySet())
            appPreferencesRepository().setProcrastilearnEnabled(true)
        }

        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        runBlocking(Dispatchers.IO) {
            appPreferencesRepository().setProcrastilearnEnabled(false)
            appPreferencesRepository().setBlockedApps(emptySet())
            appPreferencesRepository().setProcrastilearnEnabled(true)
            dayCountersStore().setRatingDelaySeconds(0)
        }
        resetAppState()

        val restored = previousEnabledServices
        if (restored.isNullOrBlank() || restored == "null") {
            uiAutomation.shell("settings put secure enabled_accessibility_services \"\"")
            uiAutomation.shell("settings put secure accessibility_enabled 0")
        } else {
            uiAutomation.shell("settings put secure enabled_accessibility_services $restored")
        }
        uiAutomation.shell("appops set ${targetContext.packageName} SYSTEM_ALERT_WINDOW default")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun selectingAndOpeningBlockedAppShowsOverlayThenHidesAfterRating() {
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()

        composeTestRule.assertEventuallyDisplayed(hasText(SEEDED_WORD, substring = true), DEFAULT_TIMEOUT_MS)
        revealTranslation()

        listOf(
            R.string.rating_again,
            R.string.rating_hard,
            R.string.rating_good,
            R.string.rating_easy,
        ).forEach { resId -> composeTestRule.assertEventuallyDisplayed(hasText(string(resId)), DEFAULT_TIMEOUT_MS) }

        composeTestRule.onNodeWithText(string(R.string.rating_good)).performClick()

        composeTestRule.waitUntilNodeGone(hasText(SEEDED_WORD, substring = true), DEFAULT_TIMEOUT_MS)
        composeTestRule.waitUntilNodeGone(hasText(string(R.string.rating_good)), DEFAULT_TIMEOUT_MS)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun revealingTranslationWithRatingDelayShowsCountdownAndDisablesRatingButtons() {
        setRatingDelayViaSettings(RATING_DELAY_SECONDS)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        revealTranslation()

        composeTestRule.assertEventuallyDisplayed(hasTestTag("rating_lock_countdown"), DEFAULT_TIMEOUT_MS)
        listOf(
            R.string.rating_again,
            R.string.rating_hard,
            R.string.rating_good,
            R.string.rating_easy,
        ).forEach { resId ->
            composeTestRule.onNodeWithText(string(resId)).assertIsNotEnabled()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun attemptingToRateWhileLockedIsANoOp() {
        setRatingDelayViaSettings(RATING_DELAY_SECONDS)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        revealTranslation()

        composeTestRule.assertEventuallyDisplayed(hasTestTag("rating_lock_countdown"), DEFAULT_TIMEOUT_MS)
        listOf(
            R.string.rating_again,
            R.string.rating_hard,
            R.string.rating_good,
            R.string.rating_easy,
        ).forEach { resId ->
            composeTestRule.onNodeWithText(string(resId)).assertIsNotEnabled()
        }

        composeTestRule.onNodeWithText(string(R.string.rating_good)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.assertEventuallyDisplayed(hasText(string(R.string.rating_good)), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithText(string(R.string.rating_good)).assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun ratingButtonsUnlockAndRatingSucceedsAfterCountdownExpires() {
        setRatingDelayViaSettings(RATING_DELAY_SECONDS)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        revealTranslation()

        composeTestRule.assertEventuallyDisplayed(hasTestTag("rating_lock_countdown"), DEFAULT_TIMEOUT_MS)
        waitForRatingUnlock()

        composeTestRule.onNodeWithText(string(R.string.rating_good)).assertIsEnabled()
        composeTestRule.onNodeWithText(string(R.string.rating_good)).performClick()

        composeTestRule.waitUntilNodeGone(hasText(string(R.string.rating_good)), DEFAULT_TIMEOUT_MS)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun newOverlaySessionRequiresFullDelayAgain() {
        setRatingDelayViaSettings(RATING_DELAY_SECONDS)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        revealTranslation()
        composeTestRule.assertEventuallyDisplayed(hasTestTag("rating_lock_countdown"), DEFAULT_TIMEOUT_MS)
        waitForRatingUnlock()
        composeTestRule.onNodeWithText(string(R.string.rating_good)).performClick()
        composeTestRule.waitUntilNodeGone(hasText(string(R.string.rating_good)), DEFAULT_TIMEOUT_MS)

        launchTargetAppUntilOverlayAppears()
        revealTranslation()

        composeTestRule.assertEventuallyDisplayed(hasTestTag("rating_lock_countdown"), DEFAULT_TIMEOUT_MS)
        composeTestRule
            .onNodeWithTag("rating_lock_countdown", useUnmergedTree = true)
            .assertTextEquals(RATING_DELAY_SECONDS.toString())
        listOf(
            R.string.rating_again,
            R.string.rating_hard,
            R.string.rating_good,
            R.string.rating_easy,
        ).forEach { resId ->
            composeTestRule.onNodeWithText(string(resId)).assertIsNotEnabled()
        }
    }

    private fun selectTargetAppAsBlocked() {
        composeTestRule.waitUntilNodeExists(hasText(string(R.string.nav_apps)), DEFAULT_TIMEOUT_MS)
        composeTestRule
            .onNodeWithContentDescription(string(R.string.nav_apps), useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()

        val rowTag = "app_row_$TARGET_PACKAGE"
        val checkboxTag = "app_checkbox_$TARGET_PACKAGE"

        composeTestRule.waitUntilNodeExists(hasScrollAction(), DEFAULT_TIMEOUT_MS)
        composeTestRule.waitUntilNodeGone(hasTestTag("apps_list_loading_indicator"), APPS_LIST_TIMEOUT_MS)
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasTestTag(rowTag))
        composeTestRule.onNodeWithTag(checkboxTag, useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        waitUntilBlockedAppsContains(TARGET_PACKAGE)
    }

    private fun revealTranslation() {
        composeTestRule.onNodeWithText(string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun setRatingDelayViaSettings(seconds: Int) {
        composeTestRule.waitUntilNodeExists(hasText(string(R.string.nav_settings)), DEFAULT_TIMEOUT_MS)
        composeTestRule
            .onNodeWithContentDescription(string(R.string.nav_settings), useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()

        openRatingDelayDialog()

        val ratingDelayField = composeTestRule.onNode(hasSetTextAction())
        ratingDelayField.performClick()
        composeTestRule.waitForIdle()
        ratingDelayField.performTextReplacement(seconds.toString())
        composeTestRule.onNodeWithText(string(R.string.action_ok)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(string(R.string.nav_apps), useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun openRatingDelayDialog() {
        repeat(DIALOG_OPEN_RETRY_COUNT) {
            composeTestRule.onNodeWithText(string(R.string.settings_rating_delay_headline)).performClick()
            composeTestRule.waitForIdle()
            if (composeTestRule.nodeVisibleWithin(
                    hasText(string(R.string.settings_rating_delay_title)),
                    DIALOG_OPEN_POLL_TIMEOUT_MS,
                )
            ) {
                return
            }
        }
        error("Rating delay dialog did not appear after $DIALOG_OPEN_RETRY_COUNT attempts")
    }

    private fun waitForRatingUnlock() {
        composeTestRule.waitUntilNodeGone(hasTestTag("rating_lock_countdown"), RATING_LOCK_TIMEOUT_MS)
    }

    private fun launchTargetAppUntilOverlayAppears() {
        repeat(LAUNCH_RETRY_COUNT) {
            goHome()

            targetContext.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                targetContext.startActivity(intent)
            }

            if (composeTestRule.nodeVisibleWithin(
                    hasText(string(R.string.learning_show_translation)),
                    LAUNCH_POLL_TIMEOUT_MS,
                )
            ) {
                return
            }
        }
    }

    private fun goHome() {
        targetContext.startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        composeTestRule.waitForIdle()
        Thread.sleep(GO_HOME_SETTLE_MS)
    }

    private fun waitUntilAccessibilityServiceBound() {
        val manager = targetContext.getSystemService(AccessibilityManager::class.java)
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val bound =
                manager
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .any { it.resolveInfo.serviceInfo.packageName == targetContext.packageName }
            if (bound) return
            Thread.sleep(SERVICE_BIND_POLL_MS)
        }
        error("OverlayAccessibilityService never appeared in the enabled accessibility service list")
    }

    private fun waitUntilBlockedAppsContains(packageName: String) {
        runBlocking(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (appPreferencesRepository().getBlockedApps().first().contains(packageName)) return@runBlocking
                delay(SERVICE_BIND_POLL_MS)
            }
        }
    }

    private fun string(resId: Int) = targetContext.getString(resId)

    private fun seedWord(
        word: String,
        translation: String,
        position: Long = 0L,
    ) {
        runBlocking(Dispatchers.IO) {
            databaseEntryPoint().appDatabase().vocabularyDao().insertVocabulary(
                VocabularyEntity(
                    word = word,
                    translation = translation,
                    correctCount = 0,
                    incorrectCount = 0,
                    fsrsCardJson = "",
                    fsrsDueAt = 0L,
                    position = position,
                ),
            )
        }
    }

    private fun resetAppState() {
        runBlocking(Dispatchers.IO) {
            val db = databaseEntryPoint().appDatabase()
            db.vocabularyDao().deleteAllVocabulary()
            db.undoSnapshotDao().deleteAll()
            // DayCountersStore persists across app runs (unlike the DB tables above), so a
            // daily new-word cap exhausted by an earlier run would otherwise make
            // getNextVocabularyItemUseCase() throw NoAvailableItemsException here.
            dayCountersStore().resetFor(todayStamp())
        }
    }

    private fun todayStamp(): Int =
        LocalDate
            .now()
            .format(DateTimeFormatter.BASIC_ISO_DATE)
            .toInt()

    private fun dayCountersStore(): DayCountersStore =
        EntryPointAccessors
            .fromApplication(
                targetContext.applicationContext,
                ServiceEntryPoint::class.java,
            ).dayCountersStore()

    private fun databaseEntryPoint(): DatabaseEntryPoint =
        EntryPointAccessors.fromApplication(
            targetContext.applicationContext,
            DatabaseEntryPoint::class.java,
        )

    private fun appPreferencesRepository(): AppPreferencesRepository =
        EntryPointAccessors
            .fromApplication(
                targetContext.applicationContext,
                ServiceEntryPoint::class.java,
            ).appPreferencesRepository()

    private fun UiAutomation.shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(executeShellCommand(command)).bufferedReader().use { it.readText() }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L

        // The installed-apps list is loaded via PackageManager.queryIntentActivities() plus a
        // per-app icon-load loop (AppRepositoryImpl.loadLaunchableApps()). On a cold emulator this
        // can be slow the first time it runs (odex/vdex verification of large system packages like
        // Google Play services), well past DEFAULT_TIMEOUT_MS, so give it its own longer budget.
        const val APPS_LIST_TIMEOUT_MS = 30_000L
        const val LAUNCH_POLL_TIMEOUT_MS = 3_000L
        const val LAUNCH_RETRY_COUNT = 3
        const val DIALOG_OPEN_POLL_TIMEOUT_MS = 5_000L
        const val DIALOG_OPEN_RETRY_COUNT = 3
        const val SERVICE_BIND_POLL_MS = 100L
        const val TARGET_PACKAGE = "com.android.settings"
        const val SEEDED_WORD = "overlayflashword"
        const val SECOND_WORD = "overlayflashwordtwo"
        const val RATING_DELAY_SECONDS = 3
        const val RATING_LOCK_TIMEOUT_MS = 10_000L
        const val GO_HOME_SETTLE_MS = 500L
    }
}
