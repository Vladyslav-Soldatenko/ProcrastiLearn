package com.procrastilearn.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppsListScreenPreviewsTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    @Test
    fun `loading preview renders the loading indicator`() {
        composeTestRule.setContent { AppsListScreenLoadingPreview() }

        composeTestRule.onNodeWithTag("apps_list_loading_indicator").assertIsDisplayed()
    }

    @Test
    fun `error preview renders the error text`() {
        composeTestRule.setContent { AppsListScreenErrorPreview() }

        composeTestRule.onNodeWithTag("apps_list_error_text").assertIsDisplayed()
    }

    @Test
    fun `content preview renders app rows`() {
        composeTestRule.setContent { AppsListScreenContentPreview() }

        composeTestRule.onNodeWithTag("app_row_com.example.focus").assertIsDisplayed()
    }

    @Test
    fun `content disabled preview renders app rows`() {
        composeTestRule.setContent { AppsListScreenContentDisabledPreview() }

        composeTestRule.onNodeWithTag("app_row_com.example.focus").assertIsDisplayed()
    }
}
