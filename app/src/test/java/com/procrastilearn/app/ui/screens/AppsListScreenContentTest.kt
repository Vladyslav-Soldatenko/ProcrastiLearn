package com.procrastilearn.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.AppInfo
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
import com.procrastilearn.app.ui.AppsViewModel
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
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
class AppsListScreenContentTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var onToggle: (AppInfo) -> Unit
    private lateinit var onEnabledChange: (Boolean) -> Unit

    private val testApps =
        listOf(
            AppInfo(label = "Focus Timer", packageName = "com.example.focus"),
            AppInfo(label = "Study Buddy", packageName = "com.example.study"),
        )

    @Before
    fun setup() {
        onToggle = mockk(relaxed = true)
        onEnabledChange = mockk(relaxed = true)
    }

    @Test
    fun `shows loading indicator when state is loading`() {
        setContent(AppsViewModel.UiState(isLoading = true))

        composeTestRule.onNodeWithTag("apps_list_loading_indicator").assertIsDisplayed()
    }

    @Test
    fun `shows error text when state has an error`() {
        setContent(AppsViewModel.UiState(error = "Unable to load apps", isLoading = false))

        composeTestRule
            .onNodeWithTag("apps_list_error_text")
            .assertIsDisplayed()
            .assertTextContains("Unable to load apps", substring = true)
    }

    @Test
    fun `renders app rows from state and marks selected apps checked`() {
        setContent(
            AppsViewModel.UiState(
                apps = testApps,
                selected = setOf("com.example.focus"),
                isLoading = false,
            ),
        )

        composeTestRule.onNodeWithTag("app_row_com.example.focus").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_row_com.example.study").assertIsDisplayed()
    }

    @Test
    fun `clicking an app row invokes onToggle with that app`() {
        setContent(
            AppsViewModel.UiState(
                apps = testApps,
                isLoading = false,
            ),
        )

        composeTestRule.onNodeWithTag("app_row_com.example.study").performClick()

        verify(exactly = 1) { onToggle(testApps[1]) }
    }

    @Test
    fun `clicking the enable toggle invokes onEnabledChange with the inverse value`() {
        setContent(AppsViewModel.UiState(isEnabled = true, isLoading = false))

        composeTestRule.onNodeWithTag("apps_list_enable_toggle").performClick()

        verify(exactly = 1) { onEnabledChange(false) }
    }

    @Test
    fun `disabled apps cannot be toggled`() {
        var toggledApp: AppInfo? = null
        setContent(
            AppsViewModel.UiState(
                apps = testApps,
                isEnabled = false,
                isLoading = false,
            ),
            onToggleOverride = { toggledApp = it },
        )

        composeTestRule.onNodeWithTag("app_row_com.example.focus").performClick()

        assertThat(toggledApp).isNull()
    }

    private fun setContent(
        state: AppsViewModel.UiState,
        onToggleOverride: ((AppInfo) -> Unit)? = null,
    ) {
        composeTestRule.setContent {
            AppsListScreenContent(
                state = state,
                onToggle = onToggleOverride ?: onToggle,
                onEnabledChange = onEnabledChange,
            )
        }
    }
}
