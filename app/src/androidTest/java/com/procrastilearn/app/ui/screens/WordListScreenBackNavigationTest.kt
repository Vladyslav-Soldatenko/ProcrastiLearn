package com.procrastilearn.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.procrastilearn.app.R
import com.procrastilearn.app.ui.WordListViewModel
import com.procrastilearn.app.ui.theme.MyApplicationTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordListScreenBackNavigationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backButtonIsDisplayedNextToTitle() {
        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                WordListContent(
                    words = persistentListOf(),
                    searchQuery = "",
                    onSearchQueryChange = {},
                    onDelete = {},
                    onEdit = {},
                    onReset = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.getString(R.string.word_list_navigate_back),
            ).assertExists()
        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(R.string.word_list_title),
            ).assertExists()
    }

    @Test
    fun clickingBackButtonInvokesCallback() {
        var backClicked = 0

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                WordListContent(
                    words = persistentListOf(),
                    searchQuery = "",
                    onSearchQueryChange = {},
                    onDelete = {},
                    onEdit = {},
                    onReset = {},
                    onNavigateBack = { backClicked++ },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.getString(R.string.word_list_navigate_back),
            ).performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, backClicked)
        }
    }

    @Test
    fun systemBackPressInSelectionModeExitsSelectionModeInsteadOfNavigatingBack() {
        var backClicked = 0
        var exitSelectionClicked = 0

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                WordListContent(
                    words = persistentListOf(),
                    searchQuery = "",
                    onSearchQueryChange = {},
                    onDelete = {},
                    onEdit = {},
                    onReset = {},
                    onNavigateBack = { backClicked++ },
                    selectionState = WordListViewModel.SelectionState(isActive = true),
                    onExitSelectionMode = { exitSelectionClicked++ },
                )
            }
        }

        Espresso.pressBack()

        composeTestRule.runOnIdle {
            assertEquals(1, exitSelectionClicked)
            assertEquals(0, backClicked)
        }
    }
}
