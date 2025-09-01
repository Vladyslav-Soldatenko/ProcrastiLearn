package com.procrastilearn.app.ui.screens.settings

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.ui.SettingsViewModel
import com.procrastilearn.app.ui.screens.settings.components.AccessibilityPermissionItem
import com.procrastilearn.app.ui.screens.settings.components.MixModeSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.NewPerDaySettingsItem
import com.procrastilearn.app.ui.screens.settings.components.NumberInputDialog
import com.procrastilearn.app.ui.screens.settings.components.OverlayPermissionItem
import com.procrastilearn.app.ui.screens.settings.components.ReviewPerDaySettingsItem
import com.procrastilearn.app.ui.screens.settings.components.ShowOverlayIntervalSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.openAccessibilitySettings
import com.procrastilearn.app.ui.screens.settings.components.openOverlaySettings
import com.procrastilearn.app.ui.theme.MyApplicationTheme
import com.procrastilearn.app.utils.isPermissionsGranted

sealed interface DialogState {
    object None : DialogState

    object MixMode : DialogState

    object NewPerDay : DialogState

    object ReviewPerDay : DialogState

    object OverlayInterval : DialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val permissionStates = rememberPermissionStates(ctx)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { SettingsTopBar() },
    ) { innerPadding ->
        SettingsContent(
            modifier = Modifier.padding(innerPadding),
            overlayGranted = permissionStates.overlayGranted,
            a11yEnabled = permissionStates.a11yEnabled,
            mixMode = state.mixMode,
            newPerDay = state.newPerDay,
            reviewPerDay = state.reviewPerDay,
            overlayInterval = state.overlayInterval,
            onOverlayClick = { openOverlaySettings(ctx) },
            onA11yClick = { openAccessibilitySettings(ctx) },
            onMixModeChange = viewModel::onMixModeChange,
            onNewPerDayChange = viewModel::onNewPerDayChange,
            onReviewPerDayChange = viewModel::onReviewPerDayChange,
            onOverlayIntervalChange = viewModel::onOverlayIntervalChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar() {
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
    )
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier,
    overlayGranted: Boolean,
    a11yEnabled: Boolean,
    mixMode: MixMode,
    newPerDay: Int,
    reviewPerDay: Int,
    overlayInterval: Int,
    onOverlayClick: () -> Unit,
    onA11yClick: () -> Unit,
    onMixModeChange: (MixMode) -> Unit,
    onNewPerDayChange: (Int) -> Unit,
    onReviewPerDayChange: (Int) -> Unit,
    onOverlayIntervalChange: (Int) -> Unit,
) {
    var dialogState by remember { mutableStateOf<DialogState>(DialogState.None) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
        ) {
            MixModeSettingsItem(
                mixMode = mixMode,
                onClick = { dialogState = DialogState.MixMode },
            )

            Spacer(Modifier.height(4.dp))

            NewPerDaySettingsItem(
                value = newPerDay,
                onClick = { dialogState = DialogState.NewPerDay },
            )

            Spacer(Modifier.height(4.dp))

            ReviewPerDaySettingsItem(
                value = reviewPerDay,
                onClick = { dialogState = DialogState.ReviewPerDay },
            )
            ShowOverlayIntervalSettingsItem(
                value = overlayInterval,
                onClick = { dialogState = DialogState.OverlayInterval },
            )
            Divider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )

            // Permission settings
            OverlayPermissionItem(
                isGranted = overlayGranted,
                onClick = onOverlayClick,
            )

            Spacer(Modifier.height(4.dp))

            AccessibilityPermissionItem(
                isEnabled = a11yEnabled,
                onClick = onA11yClick,
            )
        }
    }

    // Dialogs
    when (dialogState) {
        DialogState.MixMode -> {
            MixModeDialog(
                currentMode = mixMode,
                onModeSelected = {
                    onMixModeChange(it)
                    dialogState = DialogState.None
                },
                onDismiss = { dialogState = DialogState.None },
            )
        }
        DialogState.NewPerDay -> {
            NumberInputDialog(
                title = stringResource(R.string.settings_new_cards_per_day_title),
                currentValue = newPerDay,
                onValueConfirm = {
                    onNewPerDayChange(it)
                    dialogState = DialogState.None
                },
                onDismiss = { dialogState = DialogState.None },
            )
        }
        DialogState.ReviewPerDay -> {
            NumberInputDialog(
                title = stringResource(R.string.settings_reviews_per_day_title),
                currentValue = reviewPerDay,
                onValueConfirm = {
                    onReviewPerDayChange(it)
                    dialogState = DialogState.None
                },
                onDismiss = { dialogState = DialogState.None },
            )
        }
        DialogState.OverlayInterval -> {
            NumberInputDialog(
                title = stringResource(R.string.settings_overlay_interval_title),
                currentValue = overlayInterval,
                onValueConfirm = {
                    onOverlayIntervalChange(it)
                    dialogState = DialogState.None
                },
                onDismiss = { dialogState = DialogState.None },
            )
        }
        DialogState.None -> { /* No dialog shown */ }
    }
}

@Composable
private fun MixModeDialog(
    currentMode: MixMode,
    onModeSelected: (MixMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_study_mode_title)) },
        text = {
            Column {
                MixMode.entries.forEach { mode ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onModeSelected(mode) }
                                .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text =
                                    when (mode) {
                                        MixMode.MIX -> stringResource(R.string.settings_study_mode_mixed)
                                        MixMode.REVIEWS_FIRST ->
                                            stringResource(
                                                R.string.settings_study_mode_reviews_first,
                                            )
                                        MixMode.NEW_FIRST -> stringResource(R.string.settings_study_mode_new_first)
                                    },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text =
                                    when (mode) {
                                        MixMode.MIX -> stringResource(R.string.settings_study_mode_mixed_desc)
                                        MixMode.REVIEWS_FIRST ->
                                            stringResource(
                                                R.string.settings_study_mode_reviews_first_desc,
                                            )
                                        MixMode.NEW_FIRST -> stringResource(R.string.settings_study_mode_new_first_desc)
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun rememberPermissionStates(context: Context): PermissionStates {
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var a11yEnabled by remember { mutableStateOf(isPermissionsGranted(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    overlayGranted = Settings.canDrawOverlays(context)
                    a11yEnabled = isPermissionsGranted(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return PermissionStates(overlayGranted, a11yEnabled)
}

data class PermissionStates(
    val overlayGranted: Boolean,
    val a11yEnabled: Boolean,
)

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview_AllGranted() {
    MyApplicationTheme {
        SettingsContent(
            overlayGranted = true,
            a11yEnabled = true,
            mixMode = MixMode.MIX,
            newPerDay = 20,
            reviewPerDay = 200,
            overlayInterval = 6,
            onOverlayClick = {},
            onA11yClick = {},
            onMixModeChange = {},
            onNewPerDayChange = {},
            onReviewPerDayChange = {},
            onOverlayIntervalChange = {},
        )
    }
}
