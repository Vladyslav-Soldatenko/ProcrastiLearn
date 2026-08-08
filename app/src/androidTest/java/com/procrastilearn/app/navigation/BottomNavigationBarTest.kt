package com.procrastilearn.app.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class BottomNavigationBarTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clickingAddWordNavItemFromWordListNavigatesBackToAddWord() {
        lateinit var navController: androidx.navigation.NavHostController

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
                        composable(Screen.WordList.route) { Text(WORD_LIST_TEXT) }
                        composable(Screen.Dojo.route) { Text(DOJO_TEXT) }
                        composable(Screen.Settings.route) { Text(SETTINGS_TEXT) }
                    }
                }
            }
        }

        composeTestRule.runOnIdle {
            navController.navigate(Screen.WordList.route)
        }
        composeTestRule.onNodeWithText(WORD_LIST_TEXT).assertExists()

        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(R.string.nav_add_word),
            ).performClick()

        composeTestRule.onNodeWithText(ADD_WORD_TEXT).assertExists()
    }

    @Test
    fun clickingOtherNavItemsStillNavigatesNormally() {
        lateinit var navController: androidx.navigation.NavHostController

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
                        composable(Screen.WordList.route) { Text(WORD_LIST_TEXT) }
                        composable(Screen.Dojo.route) { Text(DOJO_TEXT) }
                        composable(Screen.Settings.route) { Text(SETTINGS_TEXT) }
                    }
                }
            }
        }

        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(R.string.nav_dojo),
            ).performClick()

        composeTestRule.onNodeWithText(DOJO_TEXT).assertExists()
    }

    private companion object {
        const val APPS_TEXT = "apps_screen"
        const val ADD_WORD_TEXT = "add_word_screen"
        const val WORD_LIST_TEXT = "word_list_screen"
        const val DOJO_TEXT = "dojo_screen"
        const val SETTINGS_TEXT = "settings_screen"
    }
}
