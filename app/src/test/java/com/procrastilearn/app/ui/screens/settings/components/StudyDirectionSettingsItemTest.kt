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
import com.procrastilearn.app.domain.model.StudyDirectionMode
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
class StudyDirectionSettingsItemTest {
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
    fun `displays forward mode label`() {
        assertModeDisplayed(
            mode = StudyDirectionMode.FORWARD,
            expectedTextRes = R.string.settings_review_direction_forward,
        )
    }

    @Test
    fun `displays backward mode label`() {
        assertModeDisplayed(
            mode = StudyDirectionMode.BACKWARD,
            expectedTextRes = R.string.settings_review_direction_backward,
        )
    }

    @Test
    fun `displays bidirectional mode label`() {
        assertModeDisplayed(
            mode = StudyDirectionMode.BIDIRECTIONAL,
            expectedTextRes = R.string.settings_review_direction_bidirectional,
        )
    }

    @Test
    fun `invokes callback when row clicked`() {
        setContent(StudyDirectionMode.FORWARD)

        composeTestRule
            .onNodeWithText(string(R.string.settings_review_direction_title))
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    @Test
    fun `supporting text delegates click to callback`() {
        setContent(StudyDirectionMode.BACKWARD)

        composeTestRule
            .onNode(
                hasText(string(R.string.settings_review_direction_backward)),
                useUnmergedTree = true,
            ).performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    private fun setContent(mode: StudyDirectionMode) {
        composeTestRule.setContent {
            MyApplicationTheme {
                StudyDirectionSettingsItem(
                    mode = mode,
                    onClick = onClick,
                )
            }
        }
    }

    private fun assertModeDisplayed(
        mode: StudyDirectionMode,
        expectedTextRes: Int,
    ) {
        setContent(mode)

        composeTestRule
            .onNodeWithText(string(R.string.settings_review_direction_title))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(string(expectedTextRes))
            .assertIsDisplayed()

        verify { onClick wasNot called }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
