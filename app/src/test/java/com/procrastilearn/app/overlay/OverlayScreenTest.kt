package com.procrastilearn.app.overlay

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
import io.github.openspacedrepetition.Rating
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
class OverlayScreenTest {
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
    fun `shows vocabulary word and reveal button when answer hidden`() {
        val revealTranslationText = context.getString(R.string.learning_show_translation)

        composeTestRule.setContent {
            OverlayScreen(
                uiState =
                    OverlayUiState(
                        vocabularyItem = sampleVocabularyItem,
                        showAnswer = false,
                    ),
                onToggleShowAnswer = {},
                onDifficultySelected = {},
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(sampleVocabularyItem.word).assertIsDisplayed()
        composeTestRule.onNodeWithText(revealTranslationText).assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(sampleVocabularyItem.translation, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun `clicking reveal button invokes callback`() {
        val revealTranslationText = context.getString(R.string.learning_show_translation)
        var toggled = false

        composeTestRule.setContent {
            OverlayScreen(
                uiState =
                    OverlayUiState(
                        vocabularyItem = sampleVocabularyItem,
                        showAnswer = false,
                    ),
                onToggleShowAnswer = { toggled = true },
                onDifficultySelected = {},
            )
        }

        composeTestRule.onNodeWithText(revealTranslationText).performClick()

        composeTestRule.runOnIdle {
            assertThat(toggled).isTrue()
        }
    }

    @Test
    fun `shows translation and difficulty buttons when answer visible`() {
        val revealTranslationText = context.getString(R.string.learning_show_translation)
        val questionText = context.getString(R.string.learning_question)
        val ratingLabels: List<String> =
            listOf(
                context.getString(R.string.rating_again),
                context.getString(R.string.rating_hard),
                context.getString(R.string.rating_good),
                context.getString(R.string.rating_easy),
            )

        composeTestRule.setContent {
            OverlayScreen(
                uiState =
                    OverlayUiState(
                        vocabularyItem = sampleVocabularyItem,
                        showAnswer = true,
                    ),
                onToggleShowAnswer = {},
                onDifficultySelected = {},
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onAllNodesWithText(revealTranslationText)
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(sampleVocabularyItem.translation, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(questionText).assertIsDisplayed()
        ratingLabels.forEach { label ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun `clicking difficulty button passes rating`() {
        val goodLabel = context.getString(R.string.rating_good)
        var selectedRating: Rating? = null

        composeTestRule.setContent {
            OverlayScreen(
                uiState =
                    OverlayUiState(
                        vocabularyItem = sampleVocabularyItem,
                        showAnswer = true,
                    ),
                onToggleShowAnswer = {},
                onDifficultySelected = { selectedRating = it },
            )
        }

        composeTestRule.onNodeWithText(goodLabel).performClick()

        composeTestRule.runOnIdle {
            assertThat(selectedRating).isEqualTo(Rating.GOOD)
        }
    }

    @Test
    fun `shows new badge when vocabulary item marked as new`() {
        val revealTranslationText = context.getString(R.string.learning_show_translation)

        composeTestRule.setContent {
            OverlayScreen(
                uiState =
                    OverlayUiState(
                        vocabularyItem = sampleVocabularyItem.copy(isNew = true),
                        showAnswer = false,
                    ),
                onToggleShowAnswer = {},
                onDifficultySelected = {},
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("${sampleVocabularyItem.word} NEW").assertIsDisplayed()
        composeTestRule.onNodeWithText(revealTranslationText).assertIsDisplayed()
    }

    @Test
    fun `falls back to placeholder texts when vocabulary missing`() {
        val noWordText = context.getString(R.string.learning_no_word)
        val noTranslationText = context.getString(R.string.learning_no_translation)

        composeTestRule.setContent {
            OverlayScreen(
                uiState =
                    OverlayUiState(
                        vocabularyItem = null,
                        showAnswer = true,
                    ),
                onToggleShowAnswer = {},
                onDifficultySelected = {},
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(noWordText).assertIsDisplayed()
        composeTestRule.onNodeWithText(noTranslationText, useUnmergedTree = true).assertIsDisplayed()
    }
}
