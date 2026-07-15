package com.procrastilearn.app.ui.dojo

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
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
)
class DojoScreenTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val sampleVocabularyItem =
        VocabularyItem(
            id = 1L,
            word = "Haus",
            translation = "House",
            isNew = false,
        )

    @Test
    fun `speaker icon hidden when pronunciation is disabled`() {
        composeTestRule.setContent {
            DojoScreen(
                uiState =
                    DojoUiState(
                        vocabularyItem = sampleVocabularyItem,
                        showAnswer = false,
                        isLoading = false,
                        pronunciationEnabled = false,
                    ),
                onToggleShowAnswer = {},
                onDifficultySelected = {},
            )
        }
        composeTestRule.waitForIdle()

        val speakContentDescription = context.getString(R.string.learning_speak_word)
        composeTestRule
            .onAllNodesWithContentDescription(speakContentDescription)
            .assertCountEquals(0)
    }

    @Test
    fun `speaker icon shown and wired when pronunciation is enabled`() {
        var spoken = false

        composeTestRule.setContent {
            DojoScreen(
                uiState =
                    DojoUiState(
                        vocabularyItem = sampleVocabularyItem,
                        showAnswer = false,
                        isLoading = false,
                        pronunciationEnabled = true,
                    ),
                onToggleShowAnswer = {},
                onDifficultySelected = {},
                onSpeakWord = { spoken = true },
            )
        }
        composeTestRule.waitForIdle()

        val speakContentDescription = context.getString(R.string.learning_speak_word)
        composeTestRule
            .onNodeWithContentDescription(speakContentDescription)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertThat(spoken).isTrue()
        }
    }
}
