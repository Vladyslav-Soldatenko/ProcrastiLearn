package com.procrastilearn.app.navigation

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
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
class BottomNavigationBarTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var navController: NavHostController
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun string(resId: Int) = context.getString(resId)

    private fun setContent(startDestination: String = Screen.Apps.route) {
        composeTestRule.setContent {
            navController = rememberNavController()
            Column {
                NavHost(navController = navController, startDestination = startDestination) {
                    composable(Screen.Apps.route) { Text("Apps screen") }
                    composable(Screen.AddWord.route) { Text("Add word screen") }
                    composable(Screen.WordList.route) { Text("Word list screen") }
                    composable(Screen.Dojo.route) { Text("Dojo screen") }
                    composable(Screen.Settings.route) { Text("Settings screen") }
                }
                BottomNavigationBar(navController)
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `apps tab is selected by default and shows the apps destination`() {
        setContent()

        composeTestRule.onNodeWithText(string(R.string.nav_apps)).assertIsSelected()
        composeTestRule.onNodeWithText("Apps screen").assertIsDisplayed()
    }

    @Test
    fun `clicking a tab navigates to its destination and selects it`() {
        setContent()

        composeTestRule.onNodeWithText(string(R.string.nav_dojo)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Dojo screen").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.nav_dojo)).assertIsSelected()
        composeTestRule.onNodeWithText(string(R.string.nav_apps)).assertIsNotSelected()
        assertThat(navController.currentDestination?.route).isEqualTo(Screen.Dojo.route)
    }

    @Test
    fun `clicking each destination navigates to the correct route`() {
        setContent()

        val destinations =
            listOf(
                string(R.string.nav_add_word) to Screen.AddWord.route,
                string(R.string.nav_dojo) to Screen.Dojo.route,
                string(R.string.nav_settings) to Screen.Settings.route,
                string(R.string.nav_apps) to Screen.Apps.route,
            )

        destinations.forEach { (label, route) ->
            composeTestRule.onNodeWithText(label).performClick()
            composeTestRule.waitForIdle()

            assertThat(navController.currentDestination?.route).isEqualTo(route)
        }
    }

    @Test
    fun `re-clicking the already selected tab keeps the same destination`() {
        setContent()

        composeTestRule.onNodeWithText(string(R.string.nav_apps)).performClick()
        composeTestRule.waitForIdle()

        assertThat(navController.currentDestination?.route).isEqualTo(Screen.Apps.route)
        composeTestRule.onNodeWithText("Apps screen").assertIsDisplayed()
    }

    @Test
    fun `navigating to word list then tapping Add Word pops back to the existing entry`() {
        setContent()

        composeTestRule.onNodeWithText(string(R.string.nav_add_word)).performClick()
        composeTestRule.waitForIdle()
        val addWordEntryId = navController.currentBackStackEntry?.id

        navController.navigate(Screen.WordList.route)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Word list screen").assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.nav_add_word)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Add word screen").assertIsDisplayed()
        assertThat(navController.currentDestination?.route).isEqualTo(Screen.AddWord.route)
        assertThat(navController.currentBackStackEntry?.id).isEqualTo(addWordEntryId)
    }

    @Test
    fun `tapping Add Word from a screen other than word list navigates normally`() {
        setContent()

        composeTestRule.onNodeWithText(string(R.string.nav_dojo)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.nav_add_word)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Add word screen").assertIsDisplayed()
        assertThat(navController.currentDestination?.route).isEqualTo(Screen.AddWord.route)
    }
}
