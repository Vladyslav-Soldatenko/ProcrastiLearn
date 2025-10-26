package com.procrastilearn.app.ui.screens.settings

import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.procrastilearn.app.domain.parser.VocabularyImportOption
import com.procrastilearn.app.ui.SettingsViewModel
import com.procrastilearn.app.ui.VocabularyImportFailureReason
import com.procrastilearn.app.ui.VocabularyImportResult
import com.procrastilearn.app.ui.screens.settings.components.AboutUsDialog
import com.procrastilearn.app.ui.screens.settings.components.AboutUsSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.AccessibilityPermissionItem
import com.procrastilearn.app.ui.screens.settings.components.ExportSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.ImportSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.MixModeDialog
import com.procrastilearn.app.ui.screens.settings.components.MixModeSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.NewPerDaySettingsItem
import com.procrastilearn.app.ui.screens.settings.components.NumberInputDialog
import com.procrastilearn.app.ui.screens.settings.components.OpenAiApiKeySettingsItem
import com.procrastilearn.app.ui.screens.settings.components.OpenAiPromptSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.OverlayPermissionItem
import com.procrastilearn.app.ui.screens.settings.components.ReviewPerDaySettingsItem
import com.procrastilearn.app.ui.screens.settings.components.ShowOverlayIntervalSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.StringInputDialog
import com.procrastilearn.app.ui.screens.settings.components.openAccessibilitySettings
import com.procrastilearn.app.ui.screens.settings.components.openOverlaySettings
import com.procrastilearn.app.ui.theme.MyApplicationTheme
import com.procrastilearn.app.utils.isPermissionsGranted
import java.time.LocalDate

sealed interface DialogState {
    object None : DialogState

    object MixMode : DialogState

    object NewPerDay : DialogState

    object ReviewPerDay : DialogState

    object OverlayInterval : DialogState

    object AboutUs : DialogState

    object OpenAiApiKey : DialogState

    object OpenAiPrompt : DialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val permissionStates = rememberPermissionStates(ctx)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importOptions = viewModel.importOptions
    var pendingImportOptionId by rememberSaveable { mutableStateOf<String?>(null) }

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                viewModel.exportVocabularyToUri(ctx, uri) { ok ->
                    Toast
                        .makeText(
                            ctx,
                            if (ok) {
                                ctx.getString(
                                    R.string.settings_export_success,
                                )
                            } else {
                                ctx.getString(R.string.settings_export_failure)
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            val optionId = pendingImportOptionId
            if (uri != null && optionId != null) {
                viewModel.importVocabularyFromUri(ctx, optionId, uri) { result ->
                    val message =
                        when (result) {
                            is VocabularyImportResult.Success ->
                                ctx.getString(R.string.settings_import_success, result.importedCount)
                            is VocabularyImportResult.Failure ->
                                when (result.reason) {
                                    VocabularyImportFailureReason.UNSUPPORTED_FORMAT ->
                                        ctx.getString(R.string.settings_import_failure_format)
                                    VocabularyImportFailureReason.FILE_ERROR,
                                    VocabularyImportFailureReason.PARSE_ERROR,
                                    -> ctx.getString(R.string.settings_import_failure_generic)
                                }
                        }
                    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                }
            }
            pendingImportOptionId = null
        }
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
            openAiApiKey = state.openAiApiKey,
            openAiPrompt = state.openAiPrompt,
            onOverlayClick = { openOverlaySettings(ctx) },
            onA11yClick = { openAccessibilitySettings(ctx) },
            onMixModeChange = viewModel::onMixModeChange,
            onNewPerDayChange = viewModel::onNewPerDayChange,
            onReviewPerDayChange = viewModel::onReviewPerDayChange,
            onOverlayIntervalChange = viewModel::onOverlayIntervalChange,
            onOpenAiApiKeyChange = viewModel::onOpenAiApiKeyChange,
            onOpenAiPromptChange = viewModel::onOpenAiPromptChange,
            onExportClick = {
                val name = "vocabulary-export-${LocalDate.now()}.json"
                exportLauncher.launch(name)
            },
            importOptions = importOptions,
            onImportOptionSelected = { option ->
                pendingImportOptionId = option.id
                val mimeTypes =
                    option.mimeTypes.takeIf { it.isNotEmpty() }
                        ?: listOf("*/*")
                importLauncher.launch(mimeTypes.toTypedArray())
            },
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

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun SettingsContent(
    modifier: Modifier = Modifier,
    overlayGranted: Boolean,
    a11yEnabled: Boolean,
    mixMode: MixMode,
    newPerDay: Int,
    reviewPerDay: Int,
    overlayInterval: Int,
    openAiApiKey: String?,
    openAiPrompt: String,
    onOverlayClick: () -> Unit,
    onA11yClick: () -> Unit,
    onMixModeChange: (MixMode) -> Unit,
    onNewPerDayChange: (Int) -> Unit,
    onReviewPerDayChange: (Int) -> Unit,
    onOverlayIntervalChange: (Int) -> Unit,
    onOpenAiApiKeyChange: (String) -> Unit,
    onOpenAiPromptChange: (String) -> Unit,
    onExportClick: () -> Unit,
    importOptions: List<VocabularyImportOption> = emptyList(),
    onImportOptionSelected: (VocabularyImportOption) -> Unit = {},
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
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
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

            Spacer(Modifier.height(4.dp))

            OpenAiApiKeySettingsItem(
                apiKey = openAiApiKey,
                onClick = { dialogState = DialogState.OpenAiApiKey },
            )
            OpenAiPromptSettingsItem(
                prompt = openAiPrompt,
                onClick = { dialogState = DialogState.OpenAiPrompt },
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

            Spacer(Modifier.height(4.dp))

            AboutUsSettingsItem(
                onClick = { dialogState = DialogState.AboutUs },
            )

            Spacer(Modifier.height(4.dp))

            if (importOptions.isNotEmpty()) {
                ImportSettingsItem(
                    options = importOptions,
                    onOptionSelected = onImportOptionSelected,
                )

                Spacer(Modifier.height(4.dp))
            }

            ExportSettingsItem(onClick = onExportClick)
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
                minValue = 0,
                onValueConfirm = {
                    onOverlayIntervalChange(it)
                    dialogState = DialogState.None
                },
                onDismiss = { dialogState = DialogState.None },
            )
        }
        DialogState.AboutUs -> {
            val url = "https://gist.github.com/Vladyslav-Soldatenko/adb5953ce000b9e8515d3dcd87773aef"
            AboutUsDialog(
                onDismiss = { dialogState = DialogState.None },
                privacyPolicyUrl = url,
            )
        }

        DialogState.OpenAiApiKey -> {
            StringInputDialog(
                title = stringResource(R.string.settings_openai_api_key_dialog_title),
                currentValue = openAiApiKey.orEmpty(),
                onValueConfirm = {
                    onOpenAiApiKeyChange(it)
                    dialogState = DialogState.None
                },
                onDismiss = { dialogState = DialogState.None },
                isPassword = true,
            )
        }
        DialogState.OpenAiPrompt -> {
            StringInputDialog(
                title = stringResource(R.string.settings_openai_prompt_dialog_title),
                currentValue = openAiPrompt,
                onValueConfirm = {
                    onOpenAiPromptChange(it)
                    dialogState = DialogState.None
                },
                onDismiss = { dialogState = DialogState.None },
                isPassword = false,
                singleLine = false,
                maxLines = 12,
            )
        }

        DialogState.None -> { /* No dialog shown */ }
    }
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
            openAiApiKey = null,
            openAiPrompt = "Prompt",
            onOverlayClick = {},
            onA11yClick = {},
            onMixModeChange = {},
            onNewPerDayChange = {},
            onReviewPerDayChange = {},
            onOverlayIntervalChange = {},
            onOpenAiApiKeyChange = {},
            onOpenAiPromptChange = {},
            onExportClick = {},
            importOptions =
                listOf(
                    VocabularyImportOption(
                        id = "apkg",
                        titleResId = R.string.settings_import_option_anki_apkg,
                        descriptionResId = R.string.settings_import_option_anki_apkg_desc,
                        mimeTypes = listOf("application/apkg"),
                        extensions = setOf("apkg"),
                    ),
                ),
            onImportOptionSelected = {},
        )
    }
}
