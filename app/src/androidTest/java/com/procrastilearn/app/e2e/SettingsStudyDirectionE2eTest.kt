package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.di.PreferencesEntryPoint
import com.procrastilearn.app.domain.model.StudyDirectionMode
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStudyDirectionE2eTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var targetContext: Context

    @Before
    fun beforeEach() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        resetAppState()
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        resetAppState()
    }

    @Test
    fun selectingEachDirectionModeUpdatesSettingsRowLabelImmediately() {
        navigateToSettings()

        selectStudyDirectionMode(StudyDirectionMode.FORWARD)
        assertRowShows(StudyDirectionMode.FORWARD)

        selectStudyDirectionMode(StudyDirectionMode.BACKWARD)
        assertRowShows(StudyDirectionMode.BACKWARD)

        selectStudyDirectionMode(StudyDirectionMode.BIDIRECTIONAL)
        assertRowShows(StudyDirectionMode.BIDIRECTIONAL)
    }

    @Test
    fun selectedModePersistsAcrossNavigatingAwayAndBackToSettings() {
        navigateToSettings()
        selectStudyDirectionMode(StudyDirectionMode.BACKWARD)
        assertRowShows(StudyDirectionMode.BACKWARD)

        navigateToDojo()
        navigateToSettings()

        assertRowShows(StudyDirectionMode.BACKWARD)
    }

    @Test
    fun initialModeReflectsExplicitlySetBaselineRatherThanAssumingDefault() {
        setMode(StudyDirectionMode.FORWARD)

        navigateToSettings()

        assertRowShows(StudyDirectionMode.FORWARD)
    }

    private fun string(resId: Int) = targetContext.getString(resId)

    private fun navigateToDojo() = navigateTo(R.string.nav_dojo)

    private fun navigateToSettings() = navigateTo(R.string.nav_settings)

    private fun navigateTo(labelResId: Int) {
        val label = targetContext.getString(labelResId)
        composeTestRule.waitUntilNodeExists(hasText(label), DEFAULT_TIMEOUT_MS)
        composeTestRule
            .onNodeWithContentDescription(label, useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun selectStudyDirectionMode(mode: StudyDirectionMode) {
        composeTestRule.onNodeWithText(string(R.string.settings_review_direction_title)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(modeLabel(mode)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun assertRowShows(mode: StudyDirectionMode) {
        composeTestRule.onNodeWithText(modeLabel(mode)).assertIsDisplayed()
    }

    private fun modeLabel(mode: StudyDirectionMode): String =
        when (mode) {
            StudyDirectionMode.FORWARD -> string(R.string.settings_review_direction_forward)
            StudyDirectionMode.BACKWARD -> string(R.string.settings_review_direction_backward)
            StudyDirectionMode.BIDIRECTIONAL -> string(R.string.settings_review_direction_bidirectional)
        }

    private fun setMode(mode: StudyDirectionMode) {
        runBlocking {
            withContext(Dispatchers.IO) {
                preferencesEntryPoint().dayCountersStore().setStudyDirectionMode(mode)
            }
        }
    }

    private fun resetAppState() = setMode(StudyDirectionMode.BIDIRECTIONAL)

    private fun preferencesEntryPoint(): PreferencesEntryPoint =
        EntryPointAccessors.fromApplication(
            targetContext.applicationContext,
            PreferencesEntryPoint::class.java,
        )

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}
