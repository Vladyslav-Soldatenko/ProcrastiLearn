package com.procrastilearn.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.procrastilearn.app.domain.model.AppInfo
import com.procrastilearn.app.ui.AppsViewModel
import com.procrastilearn.app.ui.theme.MyApplicationTheme
import com.procrastilearn.app.ui.views.AppsList

@Composable
fun AppsListScreen() {
    val vm: AppsViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    AppsListScreenContent(
        state = state,
        onEnabledChange = vm::setEnabled,
        onToggle = vm::toggle,
    )
}

@Composable
private fun AppsListScreenContent(
    state: AppsViewModel.UiState,
    onToggle: (AppInfo) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppsList(
        apps = state.apps,
        selectedKeys = state.selected,
        isEnabled = state.isEnabled,
        isLoading = state.isLoading,
        errorMessage = state.error,
        onToggleEnabled = onEnabledChange,
        onToggle = onToggle,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun AppsListScreenLoadingPreview() {
    MyApplicationTheme {
        AppsListScreenContent(
            state = AppsViewModel.UiState(isLoading = true),
            onToggle = {},
            onEnabledChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppsListScreenErrorPreview() {
    MyApplicationTheme {
        AppsListScreenContent(
            state =
                AppsViewModel.UiState(
                    error = "Unable to load apps",
                    isEnabled = true,
                ),
            onToggle = {},
            onEnabledChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppsListScreenContentPreview() {
    MyApplicationTheme {
        AppsListScreenContent(
            state =
                AppsViewModel.UiState(
                    apps =
                        listOf(
                            AppInfo(label = "Focus Timer", packageName = "com.example.focus"),
                            AppInfo(label = "Study Buddy", packageName = "com.example.study"),
                            AppInfo(label = "Game Hub", packageName = "com.example.games"),
                        ),
                    selected = setOf("com.example.focus", "com.example.games"),
                    isLoading = false,
                    isEnabled = true,
                ),
            onToggle = {},
            onEnabledChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppsListScreenContentDisabledPreview() {
    MyApplicationTheme {
        AppsListScreenContent(
            state =
                AppsViewModel.UiState(
                    apps =
                        listOf(
                            AppInfo(label = "Focus Timer", packageName = "com.example.focus"),
                            AppInfo(label = "Study Buddy", packageName = "com.example.study"),
                            AppInfo(label = "Game Hub", packageName = "com.example.games"),
                        ),
                    selected = setOf("com.example.focus", "com.example.games"),
                    isLoading = false,
                    isEnabled = false,
                ),
            onToggle = {},
            onEnabledChange = {},
        )
    }
}
