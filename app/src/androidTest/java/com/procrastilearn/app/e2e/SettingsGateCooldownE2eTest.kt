package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.GateTimingChangeResult
import com.procrastilearn.app.domain.model.GateTimingSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsGateCooldownE2eTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var targetContext: Context
    private lateinit var originalTiming: GateTimingSettings

    @Before
    fun beforeEach() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        originalTiming = storedTiming()
        setTiming(GateTimingSettings())
        composeTestRule.dismissOnboardingIfPresent(targetContext)
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)
    }

    @After
    fun afterEach() {
        setTiming(originalTiming)
    }

    @Test
    fun cooldownPersistsAfterLeavingAndReopeningSettings() {
        saveCooldown(2)
        assertCooldownSummary(2)

        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)

        assertCooldownSummary(2)
        openCooldownDialog()
        composeTestRule.onNode(hasSetTextAction()).assertTextEquals("2")
        assertEquals(GateTimingSettings(cooldownMinutes = 2), storedTiming())
    }

    @Test
    fun cooldownAboveEnabledRepeatCannotBeSaved() {
        setTiming(GateTimingSettings(cooldownMinutes = 2, repeatIntervalMinutes = 5))
        openCooldownDialog()
        enterValue(6)

        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_ok)).assertIsNotEnabled()
        composeTestRule
            .onNodeWithText(targetContext.getString(R.string.settings_gate_cooldown_exceeds_repeat, 5))
            .assertIsDisplayed()
        assertEquals(GateTimingSettings(cooldownMinutes = 2, repeatIntervalMinutes = 5), storedTiming())
    }

    @Test
    fun repeatBelowCooldownCannotBeSavedButZeroDisablesRepeats() {
        setTiming(GateTimingSettings(cooldownMinutes = 2, repeatIntervalMinutes = 5))
        openRepeatDialog()
        enterValue(1)
        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_ok)).assertIsNotEnabled()
        composeTestRule
            .onNodeWithText(targetContext.getString(R.string.settings_gate_repeat_below_cooldown, 2))
            .assertIsDisplayed()
        assertEquals(GateTimingSettings(cooldownMinutes = 2, repeatIntervalMinutes = 5), storedTiming())

        enterValue(0)
        confirmTiming(GateTimingSettings(cooldownMinutes = 2))
        composeTestRule
            .onNodeWithTag("gate_repeat_interval_setting")
            .performScrollTo()
            .assertTextContains(targetContext.getString(R.string.settings_rating_delay_off))
    }

    @Test
    fun equalDurationsAndZeroCooldownCanBeSaved() {
        setTiming(GateTimingSettings(cooldownMinutes = 2, repeatIntervalMinutes = 5))
        openRepeatDialog()
        enterValue(2)
        confirmTiming(GateTimingSettings(cooldownMinutes = 2, repeatIntervalMinutes = 2))

        saveCooldown(0, repeatMinutes = 2)
        assertEquals(GateTimingSettings(repeatIntervalMinutes = 2), storedTiming())
        composeTestRule
            .onNodeWithTag("gate_cooldown_setting")
            .performScrollTo()
            .assertTextContains(targetContext.getString(R.string.settings_rating_delay_off))
    }

    @Test
    fun cooldownMaximumPersistsAndOutOfRangeValueCannotBeSaved() {
        saveCooldown(2000)
        assertCooldownSummary(2000)
        openCooldownDialog()
        enterValue(2001)

        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_ok)).assertIsNotEnabled()
        assertEquals(GateTimingSettings(cooldownMinutes = 2000), storedTiming())
    }

    private fun saveCooldown(
        minutes: Int,
        repeatMinutes: Int = 0,
    ) {
        openCooldownDialog()
        enterValue(minutes)
        confirmTiming(GateTimingSettings(minutes, repeatMinutes))
    }

    private fun openCooldownDialog() {
        composeTestRule
            .onNodeWithText(targetContext.getString(R.string.settings_gate_cooldown_headline))
            .performScrollTo()
            .performClick()
    }

    private fun openRepeatDialog() {
        composeTestRule
            .onNodeWithText(targetContext.getString(R.string.settings_overlay_interval_headline))
            .performScrollTo()
            .performClick()
    }

    private fun enterValue(value: Int) {
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement(value.toString())
    }

    private fun confirmTiming(expected: GateTimingSettings) {
        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_ok)).performClick()
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) { storedTiming() == expected }
        composeTestRule.waitUntilNodeGone(hasSetTextAction(), E2E_TIMEOUT_MS)
    }

    private fun assertCooldownSummary(value: Int) {
        val summary = targetContext.resources.getQuantityString(R.plurals.gate_cooldown_minutes, value, value)
        composeTestRule.waitUntilNodeExists(hasText(summary), E2E_TIMEOUT_MS)
        composeTestRule.onNodeWithText(summary).performScrollTo().assertIsDisplayed()
    }

    private fun storedTiming(): GateTimingSettings =
        runBlocking {
            targetContext
                .preferencesEntryPoint()
                .dayCountersStore()
                .readGateTiming()
                .first()
        }

    private fun setTiming(timing: GateTimingSettings) {
        runBlocking {
            val store = targetContext.preferencesEntryPoint().dayCountersStore()
            assertEquals(GateTimingChangeResult.Applied, store.setOverlayInterval(0))
            assertEquals(GateTimingChangeResult.Applied, store.setGateCooldownMinutes(timing.cooldownMinutes))
            assertEquals(GateTimingChangeResult.Applied, store.setOverlayInterval(timing.repeatIntervalMinutes))
        }
    }
}
