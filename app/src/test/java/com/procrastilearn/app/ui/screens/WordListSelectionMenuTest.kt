package com.procrastilearn.app.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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

@RunWith(RobolectricTestRunner::class)
class WordListSelectionMenuTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var context: Context
    private lateinit var onDismissRequest: () -> Unit
    private lateinit var onToggleSelectAll: () -> Unit
    private lateinit var onEnableBidirectional: () -> Unit
    private lateinit var onDisableBidirectional: () -> Unit
    private lateinit var onDelete: () -> Unit

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        onDismissRequest = mockk(relaxed = true)
        onToggleSelectAll = mockk(relaxed = true)
        onEnableBidirectional = mockk(relaxed = true)
        onDisableBidirectional = mockk(relaxed = true)
        onDelete = mockk(relaxed = true)
    }

    private fun string(resId: Int) = context.getString(resId)

    private fun setContent(
        expanded: Boolean = true,
        allDisplayedSelected: Boolean = false,
        canSelectAll: Boolean = true,
        canEnableBidirectional: Boolean = true,
        canDisableBidirectional: Boolean = true,
        canDelete: Boolean = true,
    ) {
        composeTestRule.setContent {
            WordListSelectionMenu(
                expanded = expanded,
                allDisplayedSelected = allDisplayedSelected,
                canSelectAll = canSelectAll,
                canEnableBidirectional = canEnableBidirectional,
                canDisableBidirectional = canDisableBidirectional,
                canDelete = canDelete,
                onDismissRequest = onDismissRequest,
                onToggleSelectAll = onToggleSelectAll,
                onEnableBidirectional = onEnableBidirectional,
                onDisableBidirectional = onDisableBidirectional,
                onDelete = onDelete,
            )
        }
    }

    @Test
    fun `all four actions are listed when expanded`() {
        setContent()

        composeTestRule.onNodeWithText(string(R.string.action_select_all)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_enable)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_disable)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsDisplayed()
    }

    @Test
    fun `test both directions is enabled when at least one selected word is forward only`() {
        setContent(canEnableBidirectional = true)

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_enable)).assertIsEnabled()
    }

    @Test
    fun `test both directions is disabled when every selected word is already bidirectional`() {
        setContent(canEnableBidirectional = false)

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_enable)).assertIsNotEnabled()
    }

    @Test
    fun `test forward only is enabled when at least one selected word is bidirectional`() {
        setContent(canDisableBidirectional = true)

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_disable)).assertIsEnabled()
    }

    @Test
    fun `test forward only is disabled when every selected word is already forward only`() {
        setContent(canDisableBidirectional = false)

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_disable)).assertIsNotEnabled()
    }

    @Test
    fun `both direction actions are disabled when the selection is empty`() {
        setContent(canEnableBidirectional = false, canDisableBidirectional = false)

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_enable)).assertIsNotEnabled()
        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_disable)).assertIsNotEnabled()
    }

    @Test
    fun `delete is disabled when the selection is empty`() {
        setContent(canDelete = false)

        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsNotEnabled()
    }

    @Test
    fun `delete is enabled when at least one word is selected`() {
        setContent(canDelete = true)

        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsEnabled()
    }

    @Test
    fun `select all is disabled when no words are displayed`() {
        setContent(canSelectAll = false)

        composeTestRule.onNodeWithText(string(R.string.action_select_all)).assertIsNotEnabled()
    }

    @Test
    fun `tapping test both directions invokes the enable callback and dismisses the menu`() {
        setContent(canEnableBidirectional = true)

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_enable)).performClick()

        verify(exactly = 1) { onEnableBidirectional() }
    }

    @Test
    fun `tapping test forward only invokes the disable callback and dismisses the menu`() {
        setContent(canDisableBidirectional = true)

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_disable)).performClick()

        verify(exactly = 1) { onDisableBidirectional() }
    }

    @Test
    fun `a disabled item does not invoke its callback when tapped`() {
        setContent(canEnableBidirectional = false, canDisableBidirectional = false, canDelete = false)

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_enable)).performClick()
        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_disable)).performClick()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()

        verify { onEnableBidirectional wasNot called }
        verify { onDisableBidirectional wasNot called }
        verify { onDelete wasNot called }
    }
}
