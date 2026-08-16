package com.procrastilearn.app.e2e

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
        seedWord()
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
        composeTestRule.waitUntilNodeExists(hasText(string(R.string.nav_apps)), DEFAULT_TIMEOUT_MS)
        composeTestRule
            .onNodeWithContentDescription(string(R.string.nav_apps), useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()

        val rowTag = "app_row_$TARGET_PACKAGE"
        val checkboxTag = "app_checkbox_$TARGET_PACKAGE"
        composeTestRule.waitUntilNodeExists(hasScrollAction(), DEFAULT_TIMEOUT_MS)
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasTestTag(rowTag))
        composeTestRule.onNodeWithTag(checkboxTag, useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        waitUntilBlockedAppsContains(TARGET_PACKAGE)

        launchTargetAppUntilOverlayAppears()

        // The overlay window can transiently report as not-shown for a beat while the window
        // manager settles the app-switch transition animation, so require displayed to be
        // stable under a short poll rather than asserting instantaneously.
        composeTestRule.assertEventuallyDisplayed(hasText(SEEDED_WORD, substring = true), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithText(string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()

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

    private fun launchTargetAppUntilOverlayAppears() {
        repeat(LAUNCH_RETRY_COUNT) { attempt ->
            targetContext.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                targetContext.startActivity(intent)
            }

            if (composeTestRule.nodeVisibleWithin(hasText(SEEDED_WORD, substring = true), LAUNCH_POLL_TIMEOUT_MS)) {
                return
            }

            if (attempt < LAUNCH_RETRY_COUNT - 1) {
                targetContext.startActivity(
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
                composeTestRule.waitForIdle()
            }
        }
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

    private fun seedWord() {
        runBlocking(Dispatchers.IO) {
            databaseEntryPoint().appDatabase().vocabularyDao().insertVocabulary(
                VocabularyEntity(
                    word = SEEDED_WORD,
                    translation = "seeded-translation",
                    correctCount = 0,
                    incorrectCount = 0,
                    fsrsCardJson = "",
                    fsrsDueAt = 0L,
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
        const val LAUNCH_POLL_TIMEOUT_MS = 3_000L
        const val LAUNCH_RETRY_COUNT = 3
        const val SERVICE_BIND_POLL_MS = 100L
        const val TARGET_PACKAGE = "com.android.settings"
        const val SEEDED_WORD = "overlayflashword"
    }
}
