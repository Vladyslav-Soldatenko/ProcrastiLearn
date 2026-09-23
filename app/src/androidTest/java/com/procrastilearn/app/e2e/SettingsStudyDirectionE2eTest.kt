package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.StudyDirectionMode
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
        targetContext.setStudyDirectionMode(StudyDirectionMode.BIDIRECTIONAL)
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        targetContext.setStudyDirectionMode(StudyDirectionMode.BIDIRECTIONAL)
    }

    @Test
    fun selectingEachDirectionModeUpdatesSettingsRowLabelImmediately() {
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)

        composeTestRule.selectStudyDirectionMode(targetContext, StudyDirectionMode.FORWARD)
        assertRowShows(StudyDirectionMode.FORWARD)

        composeTestRule.selectStudyDirectionMode(targetContext, StudyDirectionMode.BACKWARD)
        assertRowShows(StudyDirectionMode.BACKWARD)

        composeTestRule.selectStudyDirectionMode(targetContext, StudyDirectionMode.BIDIRECTIONAL)
        assertRowShows(StudyDirectionMode.BIDIRECTIONAL)
    }

    @Test
    fun selectedModePersistsAcrossNavigatingAwayAndBackToSettings() {
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)
        composeTestRule.selectStudyDirectionMode(targetContext, StudyDirectionMode.BACKWARD)
        assertRowShows(StudyDirectionMode.BACKWARD)

        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)

        assertRowShows(StudyDirectionMode.BACKWARD)
    }

    @Test
    fun initialModeReflectsExplicitlySetBaselineRatherThanAssumingDefault() {
        targetContext.setStudyDirectionMode(StudyDirectionMode.FORWARD)

        composeTestRule.navigateTo(targetContext, R.string.nav_settings)

        assertRowShows(StudyDirectionMode.FORWARD)
    }

    private fun assertRowShows(mode: StudyDirectionMode) {
        composeTestRule.onNodeWithText(targetContext.studyDirectionModeLabel(mode)).assertIsDisplayed()
    }
}
