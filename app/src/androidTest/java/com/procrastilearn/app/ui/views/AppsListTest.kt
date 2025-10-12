package com.procrastilearn.app.ui.views

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.AppInfo
import com.procrastilearn.app.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppsListTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingStateShowsProgressIndicator() {
        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                AppsList(
                    apps =
                        listOf(
                            AppInfo("Alpha App", "com.example.alpha"),
                        ),
                    selectedKeys = emptySet(),
                    isEnabled = true,
                    isLoading = true,
                    errorMessage = null,
                    onToggleEnabled = {},
                    onToggle = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LOADING_TAG).assertIsDisplayed()
        assertThrows(AssertionError::class.java) {
            composeTestRule.onNodeWithTag(ERROR_TEXT_TAG).assertIsDisplayed()
        }
        assertThrows(AssertionError::class.java) {
            composeTestRule.onNodeWithTag(appRowTag("com.example.alpha")).assertIsDisplayed()
        }
    }

    @Test
    fun togglingEnableCallbackGetsInvoked() {
        val toggledValues = mutableListOf<Boolean>()

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                AppsList(
                    apps = emptyList(),
                    selectedKeys = emptySet(),
                    isEnabled = false,
                    isLoading = false,
                    errorMessage = null,
                    onToggleEnabled = { toggledValues += it },
                    onToggle = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(ENABLE_ROW_TAG).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(true), toggledValues)
        }
    }

    @Test
    fun errorStateDisplaysMessage() {
        val errorMessage = "Network down"

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                AppsList(
                    apps = emptyList(),
                    selectedKeys = emptySet(),
                    isEnabled = true,
                    isLoading = false,
                    errorMessage = errorMessage,
                    onToggleEnabled = {},
                    onToggle = {},
                )
            }
        }

        val expectedText =
            composeTestRule.activity.getString(
                R.string.apps_list_error_message,
                errorMessage,
            )

        composeTestRule.onNodeWithTag(ERROR_TEXT_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedText).assertIsDisplayed()
        assertThrows(AssertionError::class.java) {
            composeTestRule.onNodeWithTag(LOADING_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun showsAppNamesWhenDataAvailable() {
        val apps =
            listOf(
                AppInfo(label = "Alpha App", packageName = "com.example.alpha"),
                AppInfo(label = "Beta App", packageName = "com.example.beta"),
            )

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                AppsList(
                    apps = apps,
                    selectedKeys = setOf("com.example.alpha"),
                    isEnabled = true,
                    isLoading = false,
                    errorMessage = null,
                    onToggleEnabled = {},
                    onToggle = {},
                )
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.apps_list_enable_procrastilearn_title),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Alpha App").assertIsDisplayed()
        composeTestRule.onNodeWithText("Beta App").assertIsDisplayed()
        composeTestRule.onNodeWithTag(appCheckboxTag("com.example.alpha")).assertIsOn()
        composeTestRule.onNodeWithTag(appCheckboxTag("com.example.beta")).assertIsOff()
    }

    @Test
    fun itemToggleInvokesCallbackWhenListEnabled() {
        val toggledApps = mutableListOf<AppInfo>()
        val alpha =
            AppInfo(
                label = "Alpha App",
                packageName = "com.example.alpha",
            )

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                AppsList(
                    apps = listOf(alpha),
                    selectedKeys = emptySet(),
                    isEnabled = true,
                    isLoading = false,
                    errorMessage = null,
                    onToggleEnabled = {},
                    onToggle = { toggledApps += it },
                )
            }
        }

        composeTestRule.onNodeWithTag(appRowTag(alpha.packageName)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(alpha), toggledApps)
        }
    }

    @Test
    fun disabledListPreventsUserInteraction() {
        val toggledApps = mutableListOf<AppInfo>()
        val alpha =
            AppInfo(
                label = "Alpha App",
                packageName = "com.example.alpha",
            )

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                AppsList(
                    apps = listOf(alpha),
                    selectedKeys = setOf(alpha.packageName),
                    isEnabled = false,
                    isLoading = false,
                    errorMessage = null,
                    onToggleEnabled = {},
                    onToggle = { toggledApps += it },
                )
            }
        }

        composeTestRule.onNodeWithTag(appCheckboxTag(alpha.packageName)).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(appCheckboxTag(alpha.packageName)).assertIsOn()

        composeTestRule.onNodeWithTag(appRowTag(alpha.packageName)).performClick()
        composeTestRule.onNodeWithTag(appCheckboxTag(alpha.packageName)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(emptyList<AppInfo>(), toggledApps)
        }
        composeTestRule.onNodeWithTag(appCheckboxTag(alpha.packageName)).assertIsOn()
    }

    private companion object {
        const val ENABLE_ROW_TAG = "apps_list_enable_toggle"
        const val LOADING_TAG = "apps_list_loading_indicator"
        const val ERROR_TEXT_TAG = "apps_list_error_text"

        fun appRowTag(packageName: String) = "app_row_$packageName"
        fun appCheckboxTag(packageName: String) = "app_checkbox_$packageName"
    }
}
