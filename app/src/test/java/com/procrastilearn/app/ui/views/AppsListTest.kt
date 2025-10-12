
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.procrastilearn.app.domain.model.AppInfo
import com.procrastilearn.app.ui.views.AppsList
import io.mockk.called
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    manifest = Config.NONE,
    qualifiers = "xlarge"  // Add this
)
class AppsListTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockOnToggleEnabled: (Boolean) -> Unit
    private lateinit var mockOnToggle: (AppInfo) -> Unit

    private val testApps = listOf(
        AppInfo(
            packageName = "com.app1",
            label = "App 1",
            icon = ColorDrawable(android.graphics.Color.RED)
        ),
        AppInfo(
            packageName = "com.app2",
            label = "App 2",
            icon = ColorDrawable(android.graphics.Color.BLUE)
        ),
        AppInfo(
            packageName = "com.app3",
            label = "App 3 with very long name that should be truncated",
            icon = null
        )
    )

    @Before
    fun setup() {
        mockOnToggleEnabled = mockk(relaxed = true)
        mockOnToggle = mockk(relaxed = true)
    }

    @Test
    fun `displays loading indicator when isLoading is true`() {
        composeTestRule.setContent {
            AppsList(
                apps = emptyList(),
                selectedKeys = emptySet(),
                isEnabled = true,
                isLoading = true,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        composeTestRule
            .onNodeWithTag("apps_list_loading_indicator")
            .assertIsDisplayed()
    }

    @Test
    fun `displays error message when errorMessage is not null`() {
        val errorMessage = "Network error occurred"

        composeTestRule.setContent {
            AppsList(
                apps = emptyList(),
                selectedKeys = emptySet(),
                isEnabled = true,
                isLoading = false,
                errorMessage = errorMessage,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        composeTestRule
            .onNodeWithTag("apps_list_error_text")
            .assertIsDisplayed()
            .assertTextContains(errorMessage, substring = true)
    }

    @Test
    fun `displays list of apps when not loading and no error`() {
        composeTestRule.setContent {
            AppsList(
                apps = testApps,
                selectedKeys = emptySet(),
                isEnabled = true,
                isLoading = false,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        testApps.forEach { app ->
            composeTestRule
                .onNodeWithTag("app_row_${app.packageName}")
                .assertIsDisplayed()

            composeTestRule
                .onNodeWithText(app.label)
                .assertIsDisplayed()
        }
    }

    @Test
    fun `enable toggle is displayed and clickable`() {
        composeTestRule.setContent {
            AppsList(
                apps = testApps,
                selectedKeys = emptySet(),
                isEnabled = false,
                isLoading = false,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        composeTestRule
            .onNodeWithTag("apps_list_enable_toggle")
            .assertIsDisplayed()
            .performClick()

        verify { mockOnToggleEnabled.invoke(true) }
    }

    @Test
    fun `enable checkbox reflects enabled state`() {
        composeTestRule.setContent {
            AppsList(
                apps = testApps,
                selectedKeys = emptySet(),
                isEnabled = true,
                isLoading = false,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        composeTestRule
            .onNodeWithTag("apps_list_enable_checkbox")
            .assertIsOn()
    }

    @Test
    fun `enable checkbox reflects disabled state`() {
        composeTestRule.setContent {
            AppsList(
                apps = testApps,
                selectedKeys = emptySet(),
                isEnabled = false,
                isLoading = false,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        composeTestRule
            .onNodeWithTag("apps_list_enable_checkbox")
            .assertIsOff()
    }

    @Test
    fun `clicking enable checkbox toggles state`() {
        composeTestRule.setContent {
            AppsList(
                apps = testApps,
                selectedKeys = emptySet(),
                isEnabled = false,
                isLoading = false,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        composeTestRule
            .onNodeWithTag("apps_list_enable_checkbox")
            .performClick()

        verify { mockOnToggleEnabled.invoke(true) }
    }

    @Test
    fun `app checkboxes reflect selected state`() {
        val selectedKeys = setOf("com.app1", "com.app3")

        composeTestRule.setContent {
            AppsList(
                apps = testApps,
                selectedKeys = selectedKeys,
                isEnabled = true,
                isLoading = false,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        composeTestRule
            .onNodeWithTag("app_checkbox_com.app1")
            .assertIsOn()

        composeTestRule
            .onNodeWithTag("app_checkbox_com.app2")
            .assertIsOff()

        composeTestRule
            .onNodeWithTag("app_checkbox_com.app3")
            .assertIsOn()
    }

    @Test
    fun `clicking app row toggles selection when enabled`() {
        composeTestRule.setContent {
            AppsList(
                apps = testApps,
                selectedKeys = emptySet(),
                isEnabled = true,
                isLoading = false,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        composeTestRule
            .onNodeWithTag("app_row_com.app1")
            .performClick()

        verify { mockOnToggle.invoke(testApps[0]) }
    }

    @Test
    fun `clicking app checkbox toggles selection when enabled`() {
        composeTestRule.setContent {
            AppsList(
                apps = testApps,
                selectedKeys = setOf("com.app1"),
                isEnabled = true,
                isLoading = false,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        composeTestRule
            .onNodeWithTag("app_checkbox_com.app1")
            .performClick()

        verify { mockOnToggle.invoke(testApps[0]) }
    }

    @Test
    fun `app rows are not clickable when disabled`() {
        composeTestRule.setContent {
            AppsList(
                apps = testApps,
                selectedKeys = emptySet(),
                isEnabled = false,
                isLoading = false,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        composeTestRule
            .onNodeWithTag("app_row_com.app1")
            .performClick()

        verify { mockOnToggle wasNot called }
    }

    @Test
    fun `app checkboxes are disabled when isEnabled is false`() {
        composeTestRule.setContent {
            AppsList(
                apps = testApps,
                selectedKeys = emptySet(),
                isEnabled = false,
                isLoading = false,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        testApps.forEach { app ->
            composeTestRule
                .onNodeWithTag("app_checkbox_${app.packageName}")
                .assertIsNotEnabled()
        }
    }

    @Test
    fun `app checkboxes are enabled when isEnabled is true`() {
        composeTestRule.setContent {
            AppsList(
                apps = testApps,
                selectedKeys = emptySet(),
                isEnabled = true,
                isLoading = false,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        testApps.forEach { app ->
            composeTestRule
                .onNodeWithTag("app_checkbox_${app.packageName}")
                .assertIsEnabled()
        }
    }

    @Test
    fun `empty apps list displays only enable toggle`() {
        composeTestRule.setContent {
            AppsList(
                apps = emptyList(),
                selectedKeys = emptySet(),
                isEnabled = true,
                isLoading = false,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        composeTestRule
            .onNodeWithTag("apps_list_enable_toggle")
            .assertIsDisplayed()

        composeTestRule
            .onAllNodesWithTag("app_row_com.app1", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun `app with no icon still displays correctly`() {
        val appWithoutIcon = testApps[2] // App 3 has null icon

        composeTestRule.setContent {
            AppsList(
                apps = listOf(appWithoutIcon),
                selectedKeys = emptySet(),
                isEnabled = true,
                isLoading = false,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        composeTestRule
            .onNodeWithTag("app_row_${appWithoutIcon.packageName}")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(appWithoutIcon.label)
            .assertIsDisplayed()
    }

    @Test
    fun `scrolling works with many apps`() {
        val manyApps = List(50) { index ->
            AppInfo(
                packageName = "com.app$index",
                label = "App $index",
                icon = null
            )
        }

        composeTestRule.setContent {
            AppsList(
                apps = manyApps,
                selectedKeys = emptySet(),
                isEnabled = true,
                isLoading = false,
                errorMessage = null,
                onToggleEnabled = mockOnToggleEnabled,
                onToggle = mockOnToggle
            )
        }

        // Check first app is displayed
        composeTestRule
            .onNodeWithText("App 0")
            .assertIsDisplayed()

        // Scroll to bottom
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("app_row_com.app49"))

        // Verify last app is displayed
        composeTestRule
            .onNodeWithText("App 49", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
