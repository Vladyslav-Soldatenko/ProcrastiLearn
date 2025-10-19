package com.procrastilearn.app.ui.screens.settings.components

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.procrastilearn.app.R
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
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
class AboutUsDialogTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var onDismiss: () -> Unit
    private lateinit var uriHandler: UriHandler
    private lateinit var context: Context

    private val privacyPolicyUrl = "https://procrastilearn.app/privacy"

    @Before
    fun setup() {
        onDismiss = mockk(relaxed = true)
        uriHandler = mockk(relaxed = true)
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `renders dialog without triggering callbacks`() {
        setDialogContent()

        verify { onDismiss wasNot called }
        verify { uriHandler wasNot called }
    }

    @Test
    fun `displays about us content sections`() {
        setDialogContent()

        expectedStrings().forEach { text ->
            composeTestRule
                .onNodeWithText(text, substring = true)
                .assertIsDisplayed()
        }
    }

    @Test
    fun `privacy policy text is clickable and opens provided url`() {
        setDialogContent()

        composeTestRule
            .onNodeWithText(string(R.string.settings_about_us_privacy))
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { uriHandler.openUri(privacyPolicyUrl) }
        verify { onDismiss wasNot called }
    }

    @Test
    fun `confirm button invokes onDismiss`() {
        setDialogContent()

        composeTestRule
            .onNodeWithText(string(R.string.action_ok))
            .assertIsDisplayed()
            .performClick()

        verify(exactly = 1) { onDismiss.invoke() }
        verify { uriHandler wasNot called }
    }

    private fun setDialogContent() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                AboutUsDialog(
                    onDismiss = onDismiss,
                    privacyPolicyUrl = privacyPolicyUrl,
                )
            }
        }
    }

    private fun expectedStrings(): List<String> =
        listOf(
            string(R.string.settings_about_us_title),
            string(R.string.settings_about_us_intro),
            string(R.string.settings_about_us_mission_title),
            string(R.string.settings_about_us_mission_body),
            string(R.string.settings_about_us_who_title),
            string(R.string.settings_about_us_who_body),
            string(R.string.settings_about_us_values_title),
            string(R.string.settings_about_us_values_simplicity),
            string(R.string.settings_about_us_values_growth),
            string(R.string.settings_about_us_contact_title),
            string(R.string.settings_about_us_contact_body),
        )

    private fun string(resId: Int): String = context.getString(resId).trim()
}
