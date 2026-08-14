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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.procrastilearn.app.data.export.VocabularyImportFailureReason
import com.procrastilearn.app.data.export.VocabularyImportResult
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.NewCardOrder
import com.procrastilearn.app.domain.model.StudyDirectionMode
import com.procrastilearn.app.domain.parser.VocabularyImportOption
import com.procrastilearn.app.ui.SettingsViewModel
import com.procrastilearn.app.ui.components.LanguageSelectionDialog
import com.procrastilearn.app.ui.screens.settings.components.AboutUsDialog
import com.procrastilearn.app.ui.screens.settings.components.AboutUsSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.AccessibilityPermissionItem
import com.procrastilearn.app.ui.screens.settings.components.AddCardsForTodaySettingsItem
import com.procrastilearn.app.ui.screens.settings.components.ExportSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.ImportSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.LanguagePairSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.MixModeDialog
import com.procrastilearn.app.ui.screens.settings.components.MixModeSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.NewCardOrderDialog
import com.procrastilearn.app.ui.screens.settings.components.NewCardOrderSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.NewPerDaySettingsItem
import com.procrastilearn.app.ui.screens.settings.components.NumberInputDialog
import com.procrastilearn.app.ui.screens.settings.components.OpenAiApiKeySettingsItem
import com.procrastilearn.app.ui.screens.settings.components.OpenAiPromptSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.OpenAiReversePromptSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.OverlayPermissionItem
import com.procrastilearn.app.ui.screens.settings.components.RatingDelaySettingsItem
import com.procrastilearn.app.ui.screens.settings.components.ReviewPerDaySettingsItem
import com.procrastilearn.app.ui.screens.settings.components.SettingsSectionHeader
import com.procrastilearn.app.ui.screens.settings.components.ShowOverlayIntervalSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.StringInputDialog
import com.procrastilearn.app.ui.screens.settings.components.StudyDirectionDialog
import com.procrastilearn.app.ui.screens.settings.components.StudyDirectionSettingsItem
import com.procrastilearn.app.ui.screens.settings.components.openAccessibilitySettings
import com.procrastilearn.app.ui.screens.settings.components.openOverlaySettings
import com.procrastilearn.app.ui.theme.MyApplicationTheme
import com.procrastilearn.app.utils.isPermissionsGranted
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate

private const val MAX_RATING_DELAY_SECONDS = 60

sealed interface DialogState {
    object None : DialogState

    object MixMode : DialogState

    object StudyDirection : DialogState

    object AddCardsForToday : DialogState

    object NewPerDay : DialogState

    object NewCardOrder : DialogState

    object ReviewPerDay : DialogState

    object OverlayInterval : DialogState

    object RatingDelay : DialogState

    object AboutUs : DialogState

    object OpenAiApiKey : DialogState

    object OpenAiPrompt : DialogState

    object OpenAiReversePrompt : DialogState

    object LanguageSelection : DialogState
}

@Suppress("DEPRECATION") // replacement androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel is not yet published
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val permissionStates = rememberPermissionStates(ctx)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val availableNewCount by viewModel.availableNewCount.collectAsStateWithLifecycle()
    val availableToAddToday by viewModel.availableToAddToday.collectAsStateWithLifecycle()
    val importOptions = viewModel.importOptions
    var pendingImportOptionId by rememberSaveable { mutableStateOf<String?>(null) }

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                viewModel.exportVocabularyToUri(ctx, uri) { result ->
                    val message =
                        result.fold(
                            onSuccess = { ctx.getString(R.string.settings_export_success) },
                            onFailure = { error ->
                                error.message ?: ctx.getString(R.string.settings_export_failure)
                            },
                        )
                    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
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
                                ctx.resources.getQuantityString(
                                    R.plurals.settings_import_success,
                                    result.importedCount,
                                    result.importedCount,
                                )
                            is VocabularyImportResult.Failure ->
                                when (result.reason) {
                                    VocabularyImportFailureReason.UNSUPPORTED_FORMAT ->
                                        ctx.getString(R.string.settings_import_failure_format)
                                    VocabularyImportFailureReason.UNSUPPORTED_SCHEMA_VERSION ->
                                        ctx.getString(R.string.settings_import_failure_schema_version)
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
        modifier = modifier,
        topBar = { SettingsTopBar() },
    ) { innerPadding ->
        SettingsContent(
            modifier = Modifier.padding(innerPadding),
            overlayGranted = permissionStates.overlayGranted,
            a11yEnabled = permissionStates.a11yEnabled,
            studySettings =
                StudySettings(
                    mixMode = state.mixMode,
                    studyDirectionMode = state.studyDirectionMode,
                    newPerDay = state.newPerDay,
                    availableNewCount = availableNewCount,
                    availableToAddToday = availableToAddToday,
                    reviewPerDay = state.reviewPerDay,
                    overlayInterval = state.overlayInterval,
                    ratingDelaySeconds = state.ratingDelaySeconds,
                    newCardOrder = state.newCardOrder,
                ),
            studyCallbacks =
                StudySettingsCallbacks(
                    onMixModeChange = viewModel::onMixModeChange,
                    onStudyDirectionModeChange = viewModel::onStudyDirectionModeChange,
                    onNewPerDayDialogOpen = viewModel::loadAvailableNewCount,
                    onNewPerDayChange = viewModel::onNewPerDayChange,
                    onAddCardsForToday = viewModel::onAddCardsForToday,
                    onReviewPerDayChange = viewModel::onReviewPerDayChange,
                    onOverlayIntervalChange = viewModel::onOverlayIntervalChange,
                    onRatingDelayChange = viewModel::onRatingDelayChange,
                    onNewCardOrderChange = viewModel::onNewCardOrderChange,
                ),
            aiSettings =
                AiSettings(
                    openAiApiKey = state.openAiApiKey,
                    openAiPrompt = state.openAiPrompt,
                    openAiReversePrompt = state.openAiReversePrompt,
                    nativeLanguage = state.nativeLanguage,
                    targetLanguage = state.targetLanguage,
                ),
            aiCallbacks =
                AiSettingsCallbacks(
                    onOpenAiApiKeyChange = viewModel::onOpenAiApiKeyChange,
                    onOpenAiPromptChange = viewModel::onOpenAiPromptChange,
                    onOpenAiReversePromptChange = viewModel::onOpenAiReversePromptChange,
                    onLanguagePairChange = viewModel::onLanguagePairChange,
                ),
            onOverlayClick = { openOverlaySettings(ctx) },
            onA11yClick = { openAccessibilitySettings(ctx) },
            onExportClick = {
                val name = "vocabulary-export-${LocalDate.now()}.json"
                exportLauncher.launch(name)
            },
            importOptions = importOptions.toImmutableList(),
            onImportOptionSelect = { option ->
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
        expandedHeight = 60.dp,
    )
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable
internal fun SettingsContent(
    overlayGranted: Boolean,
    a11yEnabled: Boolean,
    studySettings: StudySettings,
    studyCallbacks: StudySettingsCallbacks,
    aiSettings: AiSettings,
    aiCallbacks: AiSettingsCallbacks,
    onOverlayClick: () -> Unit,
    onA11yClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
    importOptions: ImmutableList<VocabularyImportOption> = persistentListOf(),
    onImportOptionSelect: (VocabularyImportOption) -> Unit = {},
) {
    val mixMode = studySettings.mixMode
    val studyDirectionMode = studySettings.studyDirectionMode
    val newPerDay = studySettings.newPerDay
    val reviewPerDay = studySettings.reviewPerDay
    val overlayInterval = studySettings.overlayInterval
    val ratingDelaySeconds = studySettings.ratingDelaySeconds
    val newCardOrder = studySettings.newCardOrder
    val nativeLanguage = aiSettings.nativeLanguage
    val targetLanguage = aiSettings.targetLanguage
    val openAiApiKey = aiSettings.openAiApiKey
    val openAiPrompt = aiSettings.openAiPrompt
    val openAiReversePrompt = aiSettings.openAiReversePrompt
    val onNewPerDayDialogOpen = studyCallbacks.onNewPerDayDialogOpen
    val onAddCardsForToday = studyCallbacks.onAddCardsForToday
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
            SettingsSectionHeader(
                title = stringResource(R.string.settings_section_study_reviews),
                showDivider = false,
            )

            MixModeSettingsItem(
                mixMode = mixMode,
                onClick = { dialogState = DialogState.MixMode },
            )

            Spacer(Modifier.height(4.dp))

            StudyDirectionSettingsItem(
                mode = studyDirectionMode,
                onClick = { dialogState = DialogState.StudyDirection },
            )

            Spacer(Modifier.height(4.dp))

            AddCardsForTodaySettingsItem(
                onClick = {
                    onNewPerDayDialogOpen()
                    dialogState = DialogState.AddCardsForToday
                },
            )

            Spacer(Modifier.height(4.dp))

            NewPerDaySettingsItem(
                value = newPerDay,
                onClick = {
                    onNewPerDayDialogOpen()
                    dialogState = DialogState.NewPerDay
                },
            )

            Spacer(Modifier.height(4.dp))

            NewCardOrderSettingsItem(
                newCardOrder = newCardOrder,
                onClick = { dialogState = DialogState.NewCardOrder },
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

            RatingDelaySettingsItem(
                value = ratingDelaySeconds,
                onClick = { dialogState = DialogState.RatingDelay },
            )

            Spacer(Modifier.height(4.dp))

            LanguagePairSettingsItem(
                nativeLanguage = nativeLanguage,
                targetLanguage = targetLanguage,
                onClick = { dialogState = DialogState.LanguageSelection },
            )

            SettingsSectionHeader(title = stringResource(R.string.settings_section_ai_features))

            OpenAiApiKeySettingsItem(
                apiKey = openAiApiKey,
                onClick = { dialogState = DialogState.OpenAiApiKey },
            )
            OpenAiPromptSettingsItem(
                prompt = openAiPrompt,
                nativeLanguageCode = nativeLanguage.code.uppercase(),
                targetLanguageCode = targetLanguage.code.uppercase(),
                onClick = { dialogState = DialogState.OpenAiPrompt },
            )
            OpenAiReversePromptSettingsItem(
                prompt = openAiReversePrompt,
                nativeLanguageCode = nativeLanguage.code.uppercase(),
                targetLanguageCode = targetLanguage.code.uppercase(),
                onClick = { dialogState = DialogState.OpenAiReversePrompt },
            )

            SettingsSectionHeader(title = stringResource(R.string.settings_section_permissions))

            OverlayPermissionItem(
                isGranted = overlayGranted,
                onClick = onOverlayClick,
            )

            Spacer(Modifier.height(4.dp))

            AccessibilityPermissionItem(
                isEnabled = a11yEnabled,
                onClick = onA11yClick,
            )

            SettingsSectionHeader(title = stringResource(R.string.settings_section_data_about))

            if (importOptions.isNotEmpty()) {
                ImportSettingsItem(
                    options = importOptions,
                    onOptionSelect = onImportOptionSelect,
                )

                Spacer(Modifier.height(4.dp))
            }

            ExportSettingsItem(onClick = onExportClick)

            Spacer(Modifier.height(4.dp))

            AboutUsSettingsItem(
                onClick = { dialogState = DialogState.AboutUs },
            )
        }
    }

    SettingsDialogs(
        dialogState = dialogState,
        onDialogStateChange = { dialogState = it },
        studySettings = studySettings,
        studyCallbacks = studyCallbacks,
        aiSettings = aiSettings,
        aiCallbacks = aiCallbacks,
    )
}

@Composable
private fun SettingsDialogs(
    dialogState: DialogState,
    onDialogStateChange: (DialogState) -> Unit,
    studySettings: StudySettings,
    studyCallbacks: StudySettingsCallbacks,
    aiSettings: AiSettings,
    aiCallbacks: AiSettingsCallbacks,
) {
    val dismiss = { onDialogStateChange(DialogState.None) }
    when (dialogState) {
        DialogState.MixMode -> {
            MixModeDialog(
                currentMode = studySettings.mixMode,
                onModeSelect = {
                    studyCallbacks.onMixModeChange(it)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        DialogState.StudyDirection -> {
            StudyDirectionDialog(
                currentMode = studySettings.studyDirectionMode,
                onModeSelect = {
                    studyCallbacks.onStudyDirectionModeChange(it)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        DialogState.AddCardsForToday -> {
            NumberInputDialog(
                title =
                    stringResource(
                        R.string.settings_add_cards_for_today_dialog_title,
                        studySettings.availableToAddToday,
                    ),
                currentValue = 0,
                minValue = 1,
                maxValue = studySettings.availableToAddToday,
                onValueConfirm = {
                    studyCallbacks.onAddCardsForToday(it)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        DialogState.NewPerDay -> {
            NumberInputDialog(
                title =
                    stringResource(
                        R.string.settings_new_cards_per_day_dialog_title,
                        studySettings.availableNewCount,
                    ),
                currentValue = studySettings.newPerDay,
                minValue = 0,
                onValueConfirm = {
                    studyCallbacks.onNewPerDayChange(it)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        DialogState.NewCardOrder -> {
            NewCardOrderDialog(
                currentOrder = studySettings.newCardOrder,
                onOrderSelect = {
                    studyCallbacks.onNewCardOrderChange(it)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        DialogState.ReviewPerDay -> {
            NumberInputDialog(
                title = stringResource(R.string.settings_reviews_per_day_title),
                currentValue = studySettings.reviewPerDay,
                minValue = 0,
                onValueConfirm = {
                    studyCallbacks.onReviewPerDayChange(it)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        DialogState.OverlayInterval -> {
            NumberInputDialog(
                title = stringResource(R.string.settings_overlay_interval_title),
                currentValue = studySettings.overlayInterval,
                minValue = 0,
                onValueConfirm = {
                    studyCallbacks.onOverlayIntervalChange(it)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        DialogState.RatingDelay -> {
            NumberInputDialog(
                title = stringResource(R.string.settings_rating_delay_title),
                currentValue = studySettings.ratingDelaySeconds,
                minValue = 0,
                maxValue = MAX_RATING_DELAY_SECONDS,
                onValueConfirm = {
                    studyCallbacks.onRatingDelayChange(it)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        DialogState.AboutUs -> {
            val url = "https://gist.github.com/Vladyslav-Soldatenko/adb5953ce000b9e8515d3dcd87773aef"
            AboutUsDialog(
                onDismiss = dismiss,
                privacyPolicyUrl = url,
            )
        }

        DialogState.None -> { /* No dialog shown */ }

        else -> {
            AiSettingsDialogs(dialogState, dismiss, aiSettings, aiCallbacks)
        }
    }
}

@Composable
private fun AiSettingsDialogs(
    dialogState: DialogState,
    dismiss: () -> Unit,
    aiSettings: AiSettings,
    aiCallbacks: AiSettingsCallbacks,
) {
    when (dialogState) {
        DialogState.OpenAiApiKey -> {
            StringInputDialog(
                title = stringResource(R.string.settings_openai_api_key_dialog_title),
                currentValue = aiSettings.openAiApiKey.orEmpty(),
                onValueConfirm = {
                    aiCallbacks.onOpenAiApiKeyChange(it)
                    dismiss()
                },
                onDismiss = dismiss,
                isPassword = true,
            )
        }
        DialogState.OpenAiPrompt -> {
            StringInputDialog(
                title =
                    stringResource(
                        R.string.settings_openai_prompt_dialog_title,
                        aiSettings.targetLanguage.code.uppercase(),
                        aiSettings.nativeLanguage.code.uppercase(),
                    ),
                currentValue = aiSettings.openAiPrompt,
                onValueConfirm = {
                    aiCallbacks.onOpenAiPromptChange(it)
                    dismiss()
                },
                onDismiss = dismiss,
                isPassword = false,
                singleLine = false,
                maxLines = 12,
                helperText = stringResource(R.string.settings_openai_prompt_placeholder_hint),
            )
        }
        DialogState.OpenAiReversePrompt -> {
            StringInputDialog(
                title =
                    stringResource(
                        R.string.settings_openai_reverse_prompt_dialog_title,
                        aiSettings.nativeLanguage.code.uppercase(),
                        aiSettings.targetLanguage.code.uppercase(),
                    ),
                currentValue = aiSettings.openAiReversePrompt,
                onValueConfirm = {
                    aiCallbacks.onOpenAiReversePromptChange(it)
                    dismiss()
                },
                onDismiss = dismiss,
                isPassword = false,
                singleLine = false,
                maxLines = 12,
                helperText = stringResource(R.string.settings_openai_prompt_placeholder_hint),
            )
        }

        DialogState.LanguageSelection -> {
            LanguageSelectionDialog(
                initialNativeLanguage = aiSettings.nativeLanguage,
                initialTargetLanguage = aiSettings.targetLanguage,
                onConfirm = { native, target ->
                    aiCallbacks.onLanguagePairChange(native, target)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }

        else -> {
            Unit
        }
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
private fun SettingsScreenAllGrantedPreview() {
    MyApplicationTheme {
        SettingsContent(
            overlayGranted = true,
            a11yEnabled = true,
            studySettings =
                StudySettings(
                    mixMode = MixMode.MIX,
                    studyDirectionMode = StudyDirectionMode.FORWARD,
                    newPerDay = 20,
                    availableNewCount = 0,
                    availableToAddToday = 0,
                    reviewPerDay = 200,
                    overlayInterval = 6,
                    ratingDelaySeconds = 0,
                    newCardOrder = NewCardOrder.SEQUENTIAL,
                ),
            studyCallbacks =
                StudySettingsCallbacks(
                    onMixModeChange = {},
                    onStudyDirectionModeChange = {},
                    onNewPerDayDialogOpen = {},
                    onNewPerDayChange = {},
                    onAddCardsForToday = {},
                    onReviewPerDayChange = {},
                    onOverlayIntervalChange = {},
                    onRatingDelayChange = {},
                    onNewCardOrderChange = {},
                ),
            aiSettings =
                AiSettings(
                    openAiApiKey = null,
                    openAiPrompt = "Prompt",
                    openAiReversePrompt = "Reverse prompt",
                    nativeLanguage = Language.ENGLISH,
                    targetLanguage = Language.RUSSIAN,
                ),
            aiCallbacks =
                AiSettingsCallbacks(
                    onOpenAiApiKeyChange = {},
                    onOpenAiPromptChange = {},
                    onOpenAiReversePromptChange = {},
                    onLanguagePairChange = { _, _ -> },
                ),
            onOverlayClick = {},
            onA11yClick = {},
            onExportClick = {},
            importOptions =
                persistentListOf(
                    VocabularyImportOption(
                        id = "apkg",
                        titleResId = R.string.settings_import_option_anki_apkg,
                        descriptionResId = R.string.settings_import_option_anki_apkg_desc,
                        mimeTypes = listOf("application/apkg"),
                        extensions = setOf("apkg"),
                    ),
                ),
            onImportOptionSelect = {},
        )
    }
}
