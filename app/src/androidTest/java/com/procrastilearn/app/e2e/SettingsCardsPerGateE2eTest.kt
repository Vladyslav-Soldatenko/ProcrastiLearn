package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsCardsPerGateE2eTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var targetContext: Context

    @Before
    fun beforeEach() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        setCardsPerGate(1)
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        setCardsPerGate(1)
    }

    @Test
    fun minimumAndMaximumValuesPersistAfterLeavingAndReopeningSettings() {
        setCardsPerGate(2)
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)
        assertSummaryShows(2)
        openCardsPerGateDialog()
        enterValue(1)
        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_ok)).performClick()
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) { storedCardsPerGate() == 1 }

        openCardsPerGateDialog()
        enterValue(100)
        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_ok)).performClick()
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) { storedCardsPerGate() == 100 }
        assertSummaryShows(100)

        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)
        assertSummaryShows(100)
    }

    @Test
    fun outOfRangeValuesCannotBeSaved() {
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)
        openCardsPerGateDialog()

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput("0")
        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_ok)).assertIsNotEnabled()
        field.performTextClearance()
        field.performTextInput("101")
        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_ok)).assertIsNotEnabled()

        assertEquals(1, storedCardsPerGate())
    }

    private fun openCardsPerGateDialog() {
        composeTestRule
            .onNodeWithText(targetContext.getString(R.string.settings_cards_per_gate_headline))
            .performScrollTo()
            .performClick()
    }

    private fun enterValue(value: Int) {
        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(value.toString())
    }

    private fun assertSummaryShows(value: Int) {
        val summary = targetContext.getString(R.string.settings_cards_per_gate_summary, value)
        composeTestRule.waitUntilNodeExists(hasText(summary), E2E_TIMEOUT_MS)
        composeTestRule
            .onNodeWithText(summary)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun setCardsPerGate(value: Int) {
        runBlocking { targetContext.preferencesEntryPoint().dayCountersStore().setCardsPerGate(value) }
    }

    private fun storedCardsPerGate(): Int =
        runBlocking {
            targetContext
                .preferencesEntryPoint()
                .dayCountersStore()
                .readPolicy()
                .first()
                .cardsPerGate
        }
}
