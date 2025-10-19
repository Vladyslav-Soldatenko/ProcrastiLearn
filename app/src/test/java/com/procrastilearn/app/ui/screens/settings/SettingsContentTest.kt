package com.procrastilearn.app.ui.screens.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
import com.procrastilearn.app.ui.theme.MyApplicationTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    manifest = Config.NONE,
    qualifiers = "xlarge",
)
class SettingsContentTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `renders key settings rows`() {
        setContent()

        composeTestRule.onNodeWithText(string(R.string.settings_study_mode_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.settings_new_cards_per_day_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.settings_reviews_per_day_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.settings_overlay_headline)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.settings_about_us_row)).assertIsDisplayed()
    }

    @Test
    fun `selecting mix mode option updates callback and closes dialog`() {
        var selectedMode: MixMode? = null
        setContent(onMixModeChange = { selectedMode = it })

        composeTestRule.onNodeWithText(string(R.string.settings_study_mode_title)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_study_mode_reviews_first)).performClick()

        assertThat(selectedMode).isEqualTo(MixMode.REVIEWS_FIRST)
        // dialog dismissed after selection
        composeTestRule.onNodeWithText(string(R.string.settings_new_cards_per_day_title)).assertIsDisplayed()
    }

    @Test
    fun `confirming new per day value invokes callback`() {
        var newPerDay: Int? = null
        setContent(onNewPerDayChange = { newPerDay = it })

        composeTestRule.onNodeWithText(string(R.string.settings_new_cards_per_day_title)).performClick()

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput("42")

        composeTestRule.onNodeWithText(string(R.string.action_ok)).performClick()

        assertThat(newPerDay).isEqualTo(42)
        // dialog dismissed after submission; ensure primary row still visible
        composeTestRule.onNodeWithText(string(R.string.settings_new_cards_per_day_title)).assertIsDisplayed()
    }

    @Test
    fun `overlay permission click delegates to callback`() {
        var overlayClicks = 0
        setContent(onOverlayClick = { overlayClicks++ })

        composeTestRule.onNodeWithText(string(R.string.settings_overlay_headline)).performClick()

        assertThat(overlayClicks).isEqualTo(1)
    }

    @Test
    fun `about us item shows dialog`() {
        setContent()

        composeTestRule.onNodeWithText(string(R.string.settings_about_us_row)).performClick()

        composeTestRule.onNodeWithText(string(R.string.settings_about_us_privacy)).assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.action_ok)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_about_us_row)).assertIsDisplayed()
    }

    private fun setContent(
        mixMode: MixMode = MixMode.MIX,
        newPerDay: Int = 10,
        reviewPerDay: Int = 50,
        overlayInterval: Int = 5,
        openAiApiKey: String? = null,
        openAiPrompt: String = "Prompt",
        overlayGranted: Boolean = false,
        a11yEnabled: Boolean = false,
        onOverlayClick: () -> Unit = {},
        onA11yClick: () -> Unit = {},
        onMixModeChange: (MixMode) -> Unit = {},
        onNewPerDayChange: (Int) -> Unit = {},
        onReviewPerDayChange: (Int) -> Unit = {},
        onOverlayIntervalChange: (Int) -> Unit = {},
        onOpenAiApiKeyChange: (String) -> Unit = {},
        onOpenAiPromptChange: (String) -> Unit = {},
        onExportClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsContent(
                    overlayGranted = overlayGranted,
                    a11yEnabled = a11yEnabled,
                    mixMode = mixMode,
                    newPerDay = newPerDay,
                    reviewPerDay = reviewPerDay,
                    overlayInterval = overlayInterval,
                    openAiApiKey = openAiApiKey,
                    openAiPrompt = openAiPrompt,
                    onOverlayClick = onOverlayClick,
                    onA11yClick = onA11yClick,
                    onMixModeChange = onMixModeChange,
                    onNewPerDayChange = onNewPerDayChange,
                    onReviewPerDayChange = onReviewPerDayChange,
                    onOverlayIntervalChange = onOverlayIntervalChange,
                    onOpenAiApiKeyChange = onOpenAiApiKeyChange,
                    onOpenAiPromptChange = onOpenAiPromptChange,
                    onExportClick = onExportClick,
                )
            }
        }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
