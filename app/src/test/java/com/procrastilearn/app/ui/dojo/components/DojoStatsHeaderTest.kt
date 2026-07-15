package com.procrastilearn.app.ui.dojo.components

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.procrastilearn.app.R
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
class DojoStatsHeaderTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var onUndo: () -> Unit
    private lateinit var context: Context

    @Before
    fun setup() {
        onUndo = mockk(relaxed = true)
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `displays new and due counts`() {
        setContent(newQuotaRemaining = 17, pendingReviewCount = 10, canUndo = false)

        composeTestRule.onNodeWithText("17", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("10", substring = true).assertIsDisplayed()
    }

    @Test
    fun `undo button is disabled and inert when canUndo is false`() {
        setContent(newQuotaRemaining = 5, pendingReviewCount = 5, canUndo = false)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.dojo_undo_content_description))
            .assertIsNotEnabled()
            .performClick()

        verify { onUndo wasNot called }
    }

    @Test
    fun `undo button triggers callback when canUndo is true`() {
        setContent(newQuotaRemaining = 5, pendingReviewCount = 5, canUndo = true)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.dojo_undo_content_description))
            .performClick()

        verify(exactly = 1) { onUndo.invoke() }
    }

    private fun setContent(
        newQuotaRemaining: Int,
        pendingReviewCount: Int,
        canUndo: Boolean,
    ) {
        composeTestRule.setContent {
            MyApplicationTheme {
                DojoStatsHeader(
                    newQuotaRemaining = newQuotaRemaining,
                    pendingReviewCount = pendingReviewCount,
                    canUndo = canUndo,
                    onUndo = onUndo,
                )
            }
        }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
