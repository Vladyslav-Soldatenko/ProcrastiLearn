package com.procrastilearn.app.ui.screens.settings.components

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
import com.procrastilearn.app.ui.theme.MyApplicationTheme
import io.mockk.called
import io.mockk.mockk
import io.mockk.verify
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
class MixModeSettingsItemTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var onClick: () -> Unit
    private lateinit var context: Context

    @Before
    fun setup() {
        onClick = mockk(relaxed = true)
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `displays mixed mode label`() {
        assertModeDisplayed(
            mixMode = MixMode.MIX,
            expectedTextRes = R.string.settings_study_mode_mixed,
        )
    }

    @Test
    fun `displays reviews first mode label`() {
        assertModeDisplayed(
            mixMode = MixMode.REVIEWS_FIRST,
            expectedTextRes = R.string.settings_study_mode_reviews_first,
        )
    }

    @Test
    fun `displays new first mode label`() {
        assertModeDisplayed(
            mixMode = MixMode.NEW_FIRST,
            expectedTextRes = R.string.settings_study_mode_new_first,
        )
    }

    @Test
    fun `invokes callback when row clicked`() {
        setContent(MixMode.MIX)

        composeTestRule
            .onNodeWithText(string(R.string.settings_study_mode_title))
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    @Test
    fun `supporting text delegates click to callback`() {
        setContent(MixMode.REVIEWS_FIRST)

        composeTestRule
            .onNode(
                hasText(string(R.string.settings_study_mode_reviews_first)),
                useUnmergedTree = true,
            ).performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    private fun setContent(mixMode: MixMode) {
        composeTestRule.setContent {
            MyApplicationTheme {
                MixModeSettingsItem(
                    mixMode = mixMode,
                    onClick = onClick,
                )
            }
        }
    }

    private fun assertModeDisplayed(
        mixMode: MixMode,
        expectedTextRes: Int,
    ) {
        setContent(mixMode)

        composeTestRule
            .onNodeWithText(string(R.string.settings_study_mode_title))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(string(expectedTextRes))
            .assertIsDisplayed()

        verify { onClick wasNot called }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
