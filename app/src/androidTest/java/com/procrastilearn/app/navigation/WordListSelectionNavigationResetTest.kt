package com.procrastilearn.app.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.procrastilearn.app.R
import com.procrastilearn.app.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordListSelectionNavigationResetTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun navigatingAwayViaBottomNavAndBackResetsSelectionModeStandIn() {
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            navController = rememberNavController()
            MyApplicationTheme(dynamicColor = false) {
                Scaffold(
                    bottomBar = { BottomNavigationBar(navController = navController) },
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.AddWord.route,
                        modifier = Modifier.fillMaxSize().padding(padding),
                    ) {
                        composable(Screen.Apps.route) { Text(APPS_TEXT) }
                        composable(Screen.AddWord.route) { Text(ADD_WORD_TEXT) }
                        composable(Screen.WordList.route) {
                            var isActive by remember { mutableStateOf(false) }
                            Text(if (isActive) SELECTION_ACTIVE_TEXT else SELECTION_INACTIVE_TEXT)
                            Button(onClick = { isActive = true }) { Text(ENTER_SELECTION_BUTTON) }
                        }
                        composable(Screen.Dojo.route) { Text(DOJO_TEXT) }
                        composable(Screen.Settings.route) { Text(SETTINGS_TEXT) }
                    }
                }
            }
        }

        composeTestRule.runOnIdle { navController.navigate(Screen.WordList.route) }
        composeTestRule.onNodeWithText(SELECTION_INACTIVE_TEXT).assertExists()

        composeTestRule.onNodeWithText(ENTER_SELECTION_BUTTON).performClick()
        composeTestRule.onNodeWithText(SELECTION_ACTIVE_TEXT).assertExists()

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.nav_dojo))
            .performClick()
        composeTestRule.onNodeWithText(DOJO_TEXT).assertExists()

        composeTestRule.runOnIdle { navController.navigate(Screen.WordList.route) }

        composeTestRule.onNodeWithText(SELECTION_INACTIVE_TEXT).assertExists()
    }

    private companion object {
        const val APPS_TEXT = "apps_screen"
        const val ADD_WORD_TEXT = "add_word_screen"
        const val DOJO_TEXT = "dojo_screen"
        const val SETTINGS_TEXT = "settings_screen"
        const val SELECTION_ACTIVE_TEXT = "selection_active"
        const val SELECTION_INACTIVE_TEXT = "selection_inactive"
        const val ENTER_SELECTION_BUTTON = "enter_selection"
    }
}
