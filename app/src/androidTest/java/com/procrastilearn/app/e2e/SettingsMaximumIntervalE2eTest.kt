package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsMaximumIntervalE2eTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var targetContext: Context

    @Before
    fun beforeEach() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        setMaximumInterval(365)
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        setMaximumInterval(365)
    }

    @Test
    fun changingIntervalPersistsAndIsShownWhenReturningToSettings() {
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)
        composeTestRule.onNodeWithText(targetContext.getString(R.string.settings_maximum_interval_days, 365))
            .performScrollTo().assertIsDisplayed()
        openIntervalDialog()
        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("730")
        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_ok)).performClick()

        composeTestRule.waitUntil(E2E_TIMEOUT_MS) { storedMaximumInterval() == 730 }
        composeTestRule.onNodeWithText(targetContext.getString(R.string.settings_maximum_interval_days, 730))
            .performScrollTo().assertIsDisplayed()
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)
        composeTestRule.onNodeWithText(targetContext.getString(R.string.settings_maximum_interval_days, 730))
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun invalidIntervalCannotBeSavedAndMinimumCanBeSaved() {
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)
        openIntervalDialog()
        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("0")
        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_ok)).assertIsNotEnabled()
        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("36501")
        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_ok)).assertIsNotEnabled()
        assertThat(storedMaximumInterval()).isEqualTo(365)
        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("1")
        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_ok)).performClick()
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) { storedMaximumInterval() == 1 }
        composeTestRule.onNodeWithText(targetContext.getString(R.string.settings_maximum_interval_days, 1))
            .performScrollTo().assertIsDisplayed()
    }

    private fun openIntervalDialog() {
        composeTestRule.onNodeWithText(targetContext.getString(R.string.settings_maximum_interval_title))
            .performScrollTo().performClick()
    }

    private fun setMaximumInterval(value: Int) {
        runBlocking { targetContext.preferencesEntryPoint().dayCountersStore().setMaximumIntervalDays(value) }
    }

    private fun storedMaximumInterval(): Int =
        runBlocking { targetContext.preferencesEntryPoint().dayCountersStore().readPolicy().first().maximumIntervalDays }
}
