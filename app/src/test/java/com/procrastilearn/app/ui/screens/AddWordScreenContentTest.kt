package com.procrastilearn.app.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
import com.procrastilearn.app.ui.AddWordLoadingAction
import com.procrastilearn.app.ui.AddWordPreviewContent
import com.procrastilearn.app.ui.PendingWordUi
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

@RunWith(RobolectricTestRunner::class)
class AddWordScreenContentTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var context: Context

    private lateinit var onNavigateToList: () -> Unit
    private lateinit var onDeletePendingWord: (Long) -> Unit
    private lateinit var onWordChange: (String) -> Unit
    private lateinit var onTranslationChange: (String) -> Unit
    private lateinit var onUseAiToggle: (Boolean) -> Unit
    private lateinit var onTranslationDirectionToggle: () -> Unit
    private lateinit var onPreviewClick: () -> Unit
    private lateinit var onPreviewCancel: () -> Unit
    private lateinit var onPreviewConfirmAdd: () -> Unit
    private lateinit var onAddClick: () -> Unit
    private lateinit var onExistingWordDialogCancel: () -> Unit
    private lateinit var onExistingWordDialogProceed: () -> Unit

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        onNavigateToList = mockk(relaxed = true)
        onDeletePendingWord = mockk(relaxed = true)
        onWordChange = mockk(relaxed = true)
        onTranslationChange = mockk(relaxed = true)
        onUseAiToggle = mockk(relaxed = true)
        onTranslationDirectionToggle = mockk(relaxed = true)
        onPreviewClick = mockk(relaxed = true)
        onPreviewCancel = mockk(relaxed = true)
        onPreviewConfirmAdd = mockk(relaxed = true)
        onAddClick = mockk(relaxed = true)
        onExistingWordDialogCancel = mockk(relaxed = true)
        onExistingWordDialogProceed = mockk(relaxed = true)
    }

    private fun string(resId: Int) = context.getString(resId)

    @Test
    fun `shows title and subtitle`() {
        setContent()

        composeTestRule.onNodeWithText(string(R.string.add_word_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.add_word_subtitle)).assertIsDisplayed()
    }

    @Test
    fun `clicking view list icon invokes onNavigateToList`() {
        setContent()

        composeTestRule
            .onNodeWithContentDescription(string(R.string.add_word_view_list))
            .performClick()

        verify(exactly = 1) { onNavigateToList.invoke() }
    }

    @Test
    fun `shows word error supporting text when present`() {
        setContent(wordError = "Please enter a word.")

        composeTestRule.onNodeWithText("Please enter a word.").assertIsDisplayed()
    }

    @Test
    fun `shows translation error supporting text when present`() {
        setContent(translationError = "Please enter a translation.")

        composeTestRule.onNodeWithText("Please enter a translation.").assertIsDisplayed()
    }

    @Test
    fun `ai toggle hidden when openAiAvailable is false`() {
        setContent(openAiAvailable = false)

        composeTestRule.onNodeWithText(string(R.string.add_word_use_ai_toggle)).assertDoesNotExist()
    }

    @Test
    fun `translation field visible when ai unavailable`() {
        setContent(openAiAvailable = false)

        composeTestRule.onNodeWithText(string(R.string.add_word_label_translation)).assertIsDisplayed()
    }

    @Test
    fun `translation field hidden when ai available and enabled`() {
        setContent(openAiAvailable = true, useAiForTranslation = true)

        composeTestRule.onNodeWithText(string(R.string.add_word_label_translation)).assertDoesNotExist()
    }

    @Test
    fun `translation field visible when ai available but toggle off`() {
        setContent(openAiAvailable = true, useAiForTranslation = false)

        composeTestRule.onNodeWithText(string(R.string.add_word_label_translation)).assertIsDisplayed()
    }

    @Test
    fun `toggling ai checkbox invokes onUseAiToggle`() {
        setContent(openAiAvailable = true, useAiForTranslation = false)

        composeTestRule.onNode(isToggleable()).performClick()

        verify(exactly = 1) { onUseAiToggle(true) }
    }

    @Test
    fun `word input is disabled while loading with ai translation active`() {
        setContent(
            word = "Haus",
            openAiAvailable = true,
            useAiForTranslation = true,
            isLoading = true,
        )

        composeTestRule.onNodeWithText("Haus").assertIsNotEnabled()
    }

    @Test
    fun `word input stays enabled while loading when ai is not used`() {
        setContent(
            word = "Haus",
            openAiAvailable = false,
            isLoading = true,
        )

        composeTestRule.onNodeWithText("Haus").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun `translation direction row hidden when ai translation is not used`() {
        setContent(openAiAvailable = true, useAiForTranslation = false)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.add_word_toggle_direction))
            .assertDoesNotExist()
    }

    @Test
    fun `clicking translation direction toggle invokes callback`() {
        setContent(openAiAvailable = true, useAiForTranslation = true)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.add_word_toggle_direction))
            .performClick()

        verify(exactly = 1) { onTranslationDirectionToggle.invoke() }
    }

    @Test
    fun `preview button hidden when ai translation is not used`() {
        setContent(openAiAvailable = true, useAiForTranslation = false)

        composeTestRule.onNodeWithText(string(R.string.add_word_button_preview)).assertDoesNotExist()
    }

    @Test
    fun `preview button visible when ai translation is used`() {
        setContent(openAiAvailable = true, useAiForTranslation = true)

        composeTestRule.onNodeWithText(string(R.string.add_word_button_preview)).assertIsDisplayed()
    }

    @Test
    fun `clicking preview button invokes onPreviewClick`() {
        setContent(openAiAvailable = true, useAiForTranslation = true, isOnline = true)

        composeTestRule.onNodeWithText(string(R.string.add_word_button_preview)).performClick()

        verify(exactly = 1) { onPreviewClick.invoke() }
    }

    @Test
    fun `preview button disabled while offline`() {
        setContent(openAiAvailable = true, useAiForTranslation = true, isOnline = false)

        composeTestRule.onNodeWithText(string(R.string.add_word_button_preview)).assertIsNotEnabled()
    }

    @Test
    fun `clicking add button invokes onAddClick`() {
        setContent()

        composeTestRule.onNodeWithText(string(R.string.add_word_button_add)).performScrollTo().performClick()

        verify(exactly = 1) { onAddClick.invoke() }
    }

    @Test
    fun `add button disabled while loading`() {
        setContent(isLoading = true, loadingAction = AddWordLoadingAction.ADD)

        composeTestRule.onNodeWithText(string(R.string.add_word_button_add)).assertDoesNotExist()
    }

    @Test
    fun `add button shows add later label in add later mode`() {
        setContent(isAddLaterMode = true)

        composeTestRule.onNodeWithText(string(R.string.add_word_button_add_later)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.add_word_button_add)).assertDoesNotExist()
    }

    @Test
    fun `pending words section hidden when list is empty`() {
        setContent(pendingWords = emptyList())

        composeTestRule.onNodeWithText(string(R.string.add_word_pending_title)).assertDoesNotExist()
    }

    @Test
    fun `pending words section shows queued words`() {
        setContent(
            pendingWords =
                listOf(
                    PendingWordUi(id = 1, word = "Haus"),
                    PendingWordUi(id = 2, word = "Auto"),
                ),
        )

        composeTestRule.onNodeWithText(string(R.string.add_word_pending_title)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Haus").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Auto").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `deleting a pending word invokes onDeletePendingWord with its id`() {
        setContent(
            pendingWords =
                listOf(
                    PendingWordUi(id = 1, word = "Haus"),
                    PendingWordUi(id = 2, word = "Auto"),
                ),
        )

        composeTestRule
            .onAllNodesWithContentDescription(string(R.string.add_word_pending_delete))[1]
            .performScrollTo()
            .performClick()

        verify(exactly = 1) { onDeletePendingWord(2L) }
    }

    @Test
    fun `shows error message when present`() {
        setContent(errorMessage = "Something went wrong")

        composeTestRule.onNodeWithText("Something went wrong").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `hides error message when null`() {
        setContent(errorMessage = null)

        composeTestRule.onNodeWithText(string(R.string.add_word_title)).assertIsDisplayed()
    }

    @Test
    fun `shows custom success message when successful`() {
        setContent(isSuccess = true, successMessage = "Word added successfully!")

        composeTestRule.onNodeWithText("Word added successfully!").assertIsDisplayed()
    }

    @Test
    fun `falls back to default success message when none provided`() {
        setContent(isSuccess = true, successMessage = null)

        composeTestRule.onNodeWithText(string(R.string.add_word_success_default)).assertIsDisplayed()
    }

    @Test
    fun `success overlay hidden when not successful`() {
        setContent(isSuccess = false)

        composeTestRule.onNodeWithText(string(R.string.add_word_success_default)).assertDoesNotExist()
    }

    @Test
    fun `preview dialog shown when visible and content present`() {
        setContent(
            isPreviewVisible = true,
            previewContent = AddWordPreviewContent(word = "Haus", translation = "House"),
        )

        composeTestRule.onNodeWithText("Haus").assertIsDisplayed()
        composeTestRule.onNodeWithText("House").assertIsDisplayed()
    }

    @Test
    fun `preview dialog hidden when content missing even if visible flag is true`() {
        setContent(
            isPreviewVisible = true,
            previewContent = null,
        )

        composeTestRule.onNodeWithText(string(R.string.add_word_preview_title)).assertDoesNotExist()
    }

    @Test
    fun `confirming preview dialog invokes onPreviewConfirmAdd`() {
        setContent(
            isPreviewVisible = true,
            previewContent = AddWordPreviewContent(word = "Haus", translation = "House"),
        )

        // The preview dialog's confirm label shares text ("Add") with the screen's Add
        // button, which stays composed underneath the dialog; target the last match.
        val confirmButtons = composeTestRule.onAllNodesWithText(string(R.string.add_word_preview_confirm))
        confirmButtons[confirmButtons.fetchSemanticsNodes().size - 1].performClick()

        verify(exactly = 1) { onPreviewConfirmAdd.invoke() }
        verify { onAddClick wasNot called }
    }

    @Test
    fun `cancelling preview dialog invokes onPreviewCancel`() {
        setContent(
            isPreviewVisible = true,
            previewContent = AddWordPreviewContent(word = "Haus", translation = "House"),
        )

        composeTestRule.onNodeWithText(string(R.string.add_word_preview_cancel)).performClick()

        verify(exactly = 1) { onPreviewCancel.invoke() }
    }

    @Test
    fun `existing word dialog shown when visible flag set`() {
        setContent(
            isExistingWordDialogVisible = true,
            existingWordDialogWord = "Haus",
        )

        composeTestRule.onNodeWithText(string(R.string.add_word_existing_title)).assertIsDisplayed()
    }

    @Test
    fun `existing word dialog hidden by default`() {
        setContent(isExistingWordDialogVisible = false)

        composeTestRule.onNodeWithText(string(R.string.add_word_existing_title)).assertDoesNotExist()
    }

    @Test
    fun `proceeding on existing word dialog invokes onExistingWordDialogProceed`() {
        setContent(
            isExistingWordDialogVisible = true,
            existingWordDialogWord = "Haus",
        )

        composeTestRule.onNodeWithText(string(R.string.add_word_existing_proceed)).performClick()

        verify(exactly = 1) { onExistingWordDialogProceed.invoke() }
        verify { onExistingWordDialogCancel wasNot called }
    }

    @Test
    fun `cancelling existing word dialog invokes onExistingWordDialogCancel`() {
        setContent(
            isExistingWordDialogVisible = true,
            existingWordDialogWord = "Haus",
        )

        composeTestRule.onNodeWithText(string(R.string.add_word_existing_cancel)).performClick()

        verify(exactly = 1) { onExistingWordDialogCancel.invoke() }
        verify { onExistingWordDialogProceed wasNot called }
    }

    @Test
    fun `translation direction row places native before target for native-to-target direction`() {
        composeTestRule.setContent {
            TranslationDirectionRow(
                direction = AiTranslationDirection.NATIVE_TO_TARGET,
                nativeLanguageCode = "EN",
                targetLanguageCode = "RU",
                onToggle = {},
            )
        }

        val nativeLeft = composeTestRule.onNodeWithText("EN").fetchSemanticsNode().boundsInRoot.left
        val targetLeft = composeTestRule.onNodeWithText("RU").fetchSemanticsNode().boundsInRoot.left

        assertThat(nativeLeft).isLessThan(targetLeft)
    }

    @Test
    fun `translation direction row places target before native for target-to-native direction`() {
        composeTestRule.setContent {
            TranslationDirectionRow(
                direction = AiTranslationDirection.TARGET_TO_NATIVE,
                nativeLanguageCode = "EN",
                targetLanguageCode = "RU",
                onToggle = {},
            )
        }

        val nativeLeft = composeTestRule.onNodeWithText("EN").fetchSemanticsNode().boundsInRoot.left
        val targetLeft = composeTestRule.onNodeWithText("RU").fetchSemanticsNode().boundsInRoot.left

        assertThat(targetLeft).isLessThan(nativeLeft)
    }

    @Test
    fun `pending words section renders every word in order`() {
        val words =
            listOf(
                PendingWordUi(id = 1, word = "Haus"),
                PendingWordUi(id = 2, word = "Auto"),
                PendingWordUi(id = 3, word = "Fenster"),
            )
        composeTestRule.setContent {
            PendingWordsSection(pendingWords = words, onDeletePendingWord = {})
        }

        words.forEach { composeTestRule.onNodeWithText(it.word).assertIsDisplayed() }
    }

    @Suppress("LongParameterList")
    private fun setContent(
        word: String = "",
        translation: String = "",
        wordError: String? = null,
        translationError: String? = null,
        isLoading: Boolean = false,
        errorMessage: String? = null,
        isSuccess: Boolean = false,
        successMessage: String? = null,
        openAiAvailable: Boolean = false,
        useAiForTranslation: Boolean = false,
        translationDirection: AiTranslationDirection = AiTranslationDirection.TARGET_TO_NATIVE,
        nativeLanguageCode: String = "EN",
        targetLanguageCode: String = "RU",
        previewContent: AddWordPreviewContent? = null,
        isPreviewVisible: Boolean = false,
        isExistingWordDialogVisible: Boolean = false,
        existingWordDialogWord: String? = null,
        isExistingWordDialogLoading: Boolean = false,
        loadingAction: AddWordLoadingAction? = null,
        isOnline: Boolean = true,
        isAddLaterMode: Boolean = false,
        pendingWords: List<PendingWordUi> = emptyList(),
    ) {
        composeTestRule.setContent {
            AddWordContent(
                onNavigateToList = onNavigateToList,
                word = word,
                translation = translation,
                wordError = wordError,
                translationError = translationError,
                isLoading = isLoading,
                errorMessage = errorMessage,
                isSuccess = isSuccess,
                successMessage = successMessage,
                openAiAvailable = openAiAvailable,
                useAiForTranslation = useAiForTranslation,
                translationDirection = translationDirection,
                nativeLanguageCode = nativeLanguageCode,
                targetLanguageCode = targetLanguageCode,
                previewContent = previewContent,
                isPreviewVisible = isPreviewVisible,
                isExistingWordDialogVisible = isExistingWordDialogVisible,
                existingWordDialogWord = existingWordDialogWord,
                isExistingWordDialogLoading = isExistingWordDialogLoading,
                loadingAction = loadingAction,
                isOnline = isOnline,
                isAddLaterMode = isAddLaterMode,
                pendingWords = pendingWords,
                onDeletePendingWord = onDeletePendingWord,
                onWordChange = onWordChange,
                onTranslationChange = onTranslationChange,
                onUseAiToggle = onUseAiToggle,
                onTranslationDirectionToggle = onTranslationDirectionToggle,
                onPreviewClick = onPreviewClick,
                onPreviewCancel = onPreviewCancel,
                onPreviewConfirmAdd = onPreviewConfirmAdd,
                onAddClick = onAddClick,
                onExistingWordDialogCancel = onExistingWordDialogCancel,
                onExistingWordDialogProceed = onExistingWordDialogProceed,
            )
        }
    }
}
