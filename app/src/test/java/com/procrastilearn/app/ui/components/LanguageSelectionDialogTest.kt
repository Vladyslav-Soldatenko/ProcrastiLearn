package com.procrastilearn.app.ui.components

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
import com.procrastilearn.app.ui.theme.MyApplicationTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LanguageSelectionDialogTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private var confirmedPair: Pair<Language, Language>? = null
    private var dismissCount = 0
    private lateinit var context: Context

    @Before
    fun setup() {
        confirmedPair = null
        dismissCount = 0
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `shows title, subtitle and both field labels`() {
        setContent()

        composeTestRule.onNodeWithText(string(R.string.language_selection_dialog_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.language_selection_subtitle)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.language_selection_native_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.language_selection_target_label)).assertIsDisplayed()
    }

    @Test
    fun `confirm is disabled when neither language is selected`() {
        setContent()

        composeTestRule.onNodeWithText(string(R.string.action_continue)).assertIsNotEnabled()
    }

    @Test
    fun `confirm stays disabled after only the native language is chosen`() {
        setContent()

        selectLanguage(fieldTag = "language_selection_native_field", displayName = "English")

        composeTestRule.onNodeWithText(string(R.string.action_continue)).assertIsNotEnabled()
        assertThat(confirmedPair).isNull()
    }

    @Test
    fun `confirm stays disabled after only the target language is chosen`() {
        setContent()

        selectLanguage(fieldTag = "language_selection_target_field", displayName = "Spanish")

        composeTestRule.onNodeWithText(string(R.string.action_continue)).assertIsNotEnabled()
        assertThat(confirmedPair).isNull()
    }

    @Test
    fun `selecting both distinct languages enables confirm and invokes callback`() {
        setContent()

        selectLanguage(fieldTag = "language_selection_native_field", displayName = "English")
        selectLanguage(fieldTag = "language_selection_target_field", displayName = "Spanish")

        val confirmButton = composeTestRule.onNodeWithText(string(R.string.action_continue))
        confirmButton.assertIsEnabled()
        confirmButton.performClick()

        assertThat(confirmedPair).isEqualTo(Language.ENGLISH to Language.SPANISH)
    }

    @Test
    fun `native dropdown excludes the currently selected target language`() {
        setContent()

        selectLanguage(fieldTag = "language_selection_target_field", displayName = "Spanish")

        composeTestRule.onNodeWithTag("language_selection_native_field").performClick()
        // "Spanish" should only appear once (as the target field's own value), not a second time as a
        // selectable native option.
        composeTestRule.onAllNodesWithText("Spanish").assertCountEquals(1)
    }

    @Test
    fun `target dropdown excludes the currently selected native language`() {
        setContent()

        selectLanguage(fieldTag = "language_selection_native_field", displayName = "English")

        composeTestRule.onNodeWithTag("language_selection_target_field").performClick()
        composeTestRule.onAllNodesWithText("English").assertCountEquals(1)
    }

    @Test
    fun `changing native language away from target keeps a stale target selection intact`() {
        setContent()

        selectLanguage(fieldTag = "language_selection_native_field", displayName = "English")
        selectLanguage(fieldTag = "language_selection_target_field", displayName = "Spanish")
        selectLanguage(fieldTag = "language_selection_native_field", displayName = "German")

        val confirmButton = composeTestRule.onNodeWithText(string(R.string.action_continue))
        confirmButton.assertIsEnabled()
        confirmButton.performClick()

        assertThat(confirmedPair).isEqualTo(Language.GERMAN to Language.SPANISH)
    }

    @Test
    fun `initial values pre-fill both fields and enable confirm immediately`() {
        setContent(
            initialNativeLanguage = Language.ENGLISH,
            initialTargetLanguage = Language.RUSSIAN,
        )

        composeTestRule.onNodeWithText("English").assertIsDisplayed()
        composeTestRule.onNodeWithText("Russian").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_continue)).assertIsEnabled()
    }

    @Test
    fun `confirming pre-filled values invokes callback with those values unchanged`() {
        setContent(
            initialNativeLanguage = Language.ENGLISH,
            initialTargetLanguage = Language.RUSSIAN,
        )

        composeTestRule.onNodeWithText(string(R.string.action_continue)).performClick()

        assertThat(confirmedPair).isEqualTo(Language.ENGLISH to Language.RUSSIAN)
    }

    @Test
    fun `mandatory mode without onDismiss does not show a cancel button`() {
        setContent(isDismissable = false)

        composeTestRule.onNodeWithText(string(R.string.action_cancel)).assertDoesNotExist()
    }

    @Test
    fun `dismissable mode shows cancel button and invokes onDismiss`() {
        setContent(isDismissable = true)

        composeTestRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        assertThat(dismissCount).isEqualTo(1)
        assertThat(confirmedPair).isNull()
    }

    private fun selectLanguage(
        fieldTag: String,
        displayName: String,
    ) {
        composeTestRule.onNodeWithTag(fieldTag).performClick()
        composeTestRule.onNodeWithText(displayName).performClick()
    }

    private fun setContent(
        initialNativeLanguage: Language? = null,
        initialTargetLanguage: Language? = null,
        isDismissable: Boolean = false,
    ) {
        composeTestRule.setContent {
            MyApplicationTheme {
                LanguageSelectionDialog(
                    initialNativeLanguage = initialNativeLanguage,
                    initialTargetLanguage = initialTargetLanguage,
                    onConfirm = { native, target -> confirmedPair = native to target },
                    onDismiss = if (isDismissable) ({ dismissCount++ }) else null,
                )
            }
        }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
