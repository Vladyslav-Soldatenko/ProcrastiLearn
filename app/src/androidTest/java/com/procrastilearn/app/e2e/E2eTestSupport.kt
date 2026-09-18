package com.procrastilearn.app.e2e

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.R
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.di.DatabaseEntryPoint
import com.procrastilearn.app.di.PreferencesEntryPoint
import com.procrastilearn.app.domain.model.SearchScope
import com.procrastilearn.app.domain.model.StudyDirectionMode
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val E2E_TIMEOUT_MS = 15_000L
const val E2E_SHORT_TIMEOUT_MS = 5_000L
const val E2E_DEFAULT_NEW_CARDS_PER_DAY = 15
const val E2E_ANKI_IMPORT_TIMEOUT_MS = 50_000L
const val E2E_ANKI_IMPORT_ROW_TIMEOUT_MS = 10_000L

private const val ONBOARDING_STEP_TIMEOUT_MS = 1_500L
private const val NODE_POLL_INTERVAL_MS = 100L
private const val WORD_LIST_SEARCH_FIELD_TAG = "word_list_search_field"
private const val WORD_LIST_ITEM_TAG_PREFIX = "word_list_item_"

private val wordListItemMatcher =
    SemanticsMatcher("has a word-list item tag") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(WORD_LIST_ITEM_TAG_PREFIX) == true
    }

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitUntilNodeExists(
    matcher: SemanticsMatcher,
    timeoutMillis: Long,
) {
    waitUntil(timeoutMillis) {
        try {
            onNode(matcher, useUnmergedTree = true).fetchSemanticsNode()
            true
        } catch (_: AssertionError) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitUntilNodeGone(
    matcher: SemanticsMatcher,
    timeoutMillis: Long,
) {
    waitUntil(timeoutMillis) {
        try {
            onNode(matcher, useUnmergedTree = true).fetchSemanticsNode()
            false
        } catch (_: AssertionError) {
            true
        } catch (_: IllegalStateException) {
            true
        }
    }
}

fun ComposeTestRule.nodeVisibleWithin(
    matcher: SemanticsMatcher,
    timeoutMillis: Long,
): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        waitForIdle()
        val displayed =
            try {
                onNode(matcher, useUnmergedTree = true).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: IllegalStateException) {
                false
            }
        if (displayed) return true
        Thread.sleep(NODE_POLL_INTERVAL_MS)
    }
    return false
}

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.assertEventuallyDisplayed(
    matcher: SemanticsMatcher,
    timeoutMillis: Long,
) {
    check(nodeVisibleWithin(matcher, timeoutMillis)) {
        "Node matching $matcher was not displayed within ${timeoutMillis}ms"
    }
}

private object OnboardingState {
    @Volatile
    var dismissed = false
}

fun ComposeTestRule.dismissOnboardingIfPresent(context: Context) {
    if (OnboardingState.dismissed) return

    val notNow = context.getString(R.string.action_not_now)
    repeat(2) {
        if (nodeVisibleWithin(hasText(notNow), ONBOARDING_STEP_TIMEOUT_MS)) {
            val notNowNode = onNodeWithText(notNow, useUnmergedTree = true)
            // ProminentA11yDisclosureScreen is a full-screen scrollable Column (not a compact
            // AlertDialog like OverlayPermissionDialog, the other screen this loop can hit), so
            // on small emulator viewports its "Not now" button can be below the fold: present in
            // the semantics tree (nodeVisibleWithin finds it) but at screen coordinates
            // performClick() can't actually hit without scrolling to it first. performScrollTo()
            // throws when the node has no scrollable ancestor at all, which is exactly the case
            // for OverlayPermissionDialog's button - it's already fully visible, so skip the
            // scroll there instead of failing the test over it.
            try {
                notNowNode.performScrollTo()
            } catch (_: AssertionError) {
                // No scrollable ancestor - already visible, nothing to scroll to.
            }
            notNowNode.performClick()
            waitForIdle()
        }
    }

    val languageTitle = context.getString(R.string.language_selection_dialog_title)
    if (!nodeVisibleWithin(hasText(languageTitle), ONBOARDING_STEP_TIMEOUT_MS)) {
        OnboardingState.dismissed = true
        return
    }

    onNodeWithTag("language_selection_native_field", useUnmergedTree = true).performClick()
    waitForIdle()
    onNodeWithText(context.getString(R.string.language_name_english), useUnmergedTree = true)
        .performScrollTo()
        .performClick()
    waitForIdle()

    onNodeWithTag("language_selection_target_field", useUnmergedTree = true).performClick()
    waitForIdle()
    onNodeWithText(context.getString(R.string.language_name_russian), useUnmergedTree = true)
        .performScrollTo()
        .performClick()
    waitForIdle()

    onNodeWithText(context.getString(R.string.action_continue), useUnmergedTree = true).performClick()
    waitForIdle()

    OnboardingState.dismissed = true
}

fun Context.string(
    @StringRes resId: Int,
): String = getString(resId)

fun Context.databaseEntryPoint(): DatabaseEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, DatabaseEntryPoint::class.java)

fun Context.preferencesEntryPoint(): PreferencesEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, PreferencesEntryPoint::class.java)

fun Context.resetE2eDatabase() {
    runBlocking {
        withContext(Dispatchers.IO) {
            val database = databaseEntryPoint().appDatabase()
            database.vocabularyDao().deleteAllVocabulary()
            database.undoSnapshotDao().deleteAll()
        }
    }
}

fun Context.insertVocabulary(
    vocabulary: VocabularyEntity,
    assignNextPosition: Boolean = true,
): Long =
    runBlocking {
        withContext(Dispatchers.IO) {
            val dao = databaseEntryPoint().appDatabase().vocabularyDao()
            dao.insertVocabulary(
                if (assignNextPosition) vocabulary.copy(position = dao.getMaxPosition() + 1) else vocabulary,
            )
        }
    }

fun Context.vocabularyById(id: Long): VocabularyEntity? =
    runBlocking {
        withContext(Dispatchers.IO) {
            databaseEntryPoint().appDatabase().vocabularyDao().getVocabularyById(id)
        }
    }

fun Context.vocabularyByWord(word: String): VocabularyEntity? =
    runBlocking {
        withContext(Dispatchers.IO) {
            databaseEntryPoint()
                .appDatabase()
                .vocabularyDao()
                .getVocabularyByWord(VocabularyEntity.normalizeWord(word))
        }
    }

fun Context.allVocabularyEntities(): List<VocabularyEntity> =
    runBlocking {
        withContext(Dispatchers.IO) {
            databaseEntryPoint()
                .appDatabase()
                .vocabularyDao()
                .getAllVocabulary()
                .first()
        }
    }

fun Context.resetDailyCounters() {
    runBlocking {
        withContext(Dispatchers.IO) {
            preferencesEntryPoint().dayCountersStore().resetFor(todayStamp())
        }
    }
}

fun Context.setNewCardsPerDay(count: Int) {
    runBlocking {
        withContext(Dispatchers.IO) {
            val counters = preferencesEntryPoint().dayCountersStore()
            counters.resetFor(todayStamp())
            counters.setNewPerDay(count)
        }
    }
}

fun Context.setStudyDirectionMode(mode: StudyDirectionMode) {
    runBlocking {
        withContext(Dispatchers.IO) {
            preferencesEntryPoint().dayCountersStore().setStudyDirectionMode(mode)
        }
    }
}

fun Context.resetWordListSearchScope() {
    runBlocking {
        withContext(Dispatchers.IO) {
            preferencesEntryPoint().wordListSearchPreferencesStore().setScope(SearchScope())
        }
    }
}

fun Context.seedWord(
    word: String,
    translation: String,
    position: Long? = null,
    bidirectional: Boolean = false,
    correctCount: Int = 0,
    fsrsDueAt: Long = 0L,
    backwardFsrsDueAt: Long = 0L,
    backwardPromptOverride: String? = null,
    backwardAnswerOverride: String? = null,
): Long =
    insertVocabulary(
        VocabularyEntity(
            word = word,
            translation = translation,
            position = position ?: 0L,
            bidirectional = bidirectional,
            correctCount = correctCount,
            fsrsCardJson = "",
            fsrsDueAt = fsrsDueAt,
            backwardFsrsCardJson = "",
            backwardFsrsDueAt = backwardFsrsDueAt,
            backwardPromptOverride = backwardPromptOverride,
            backwardAnswerOverride = backwardAnswerOverride,
        ),
        assignNextPosition = position == null,
    )

fun todayStamp(): Int = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE).toInt()

fun ComposeTestRule.navigateTo(
    context: Context,
    @StringRes destinationResId: Int,
    timeoutMillis: Long = E2E_TIMEOUT_MS,
) {
    val label = context.string(destinationResId)
    waitUntilNodeExists(hasText(label), timeoutMillis)
    onNodeWithContentDescription(label, useUnmergedTree = true).performClick()
    waitForIdle()
}

fun ComposeTestRule.navigateToWordList(
    context: Context,
    timeoutMillis: Long = E2E_TIMEOUT_MS,
) {
    navigateTo(context, R.string.nav_add_word, timeoutMillis)
    val viewListLabel = context.string(R.string.action_view_list)
    waitUntilNodeExists(hasContentDescription(viewListLabel), timeoutMillis)
    onNodeWithContentDescription(viewListLabel).performClick()
    waitForIdle()
}

fun wordListItemTag(id: Long): String = "$WORD_LIST_ITEM_TAG_PREFIX$id"

fun ComposeTestRule.displayedWordListItemIds(): List<Long> =
    onAllNodes(wordListItemMatcher, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.TestTag) }
        .map { tag -> tag.removePrefix(WORD_LIST_ITEM_TAG_PREFIX).toLong() }

fun ComposeTestRule.lastVisibleWordListItemBottomYPx(): Float =
    onAllNodes(wordListItemMatcher, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .maxOf { node -> node.boundsInRoot.bottom }

fun ComposeTestRule.longPressWordListItem(
    id: Long,
    timeoutMillis: Long = E2E_TIMEOUT_MS,
) {
    val tag = wordListItemTag(id)
    waitUntilNodeExists(hasTestTag(tag), timeoutMillis)
    onNodeWithTag(tag).performTouchInput { longClick() }
    waitForIdle()
}

fun ComposeTestRule.clickWordListItem(id: Long) {
    onNodeWithTag(wordListItemTag(id)).performClick()
    waitForIdle()
}

fun ComposeTestRule.openWordListSelectionMenuAndTap(
    context: Context,
    @StringRes actionResId: Int,
) {
    onNodeWithContentDescription(context.string(R.string.word_list_more_actions_selection)).performClick()
    waitForIdle()
    onNodeWithText(context.string(actionResId)).performClick()
    waitForIdle()
}

fun ComposeTestRule.confirmWordListBulkDelete(
    context: Context,
    timeoutMillis: Long = E2E_TIMEOUT_MS,
) {
    waitUntilNodeExists(hasText(context.string(R.string.word_list_bulk_delete_confirm_title)), timeoutMillis)
    onNodeWithText(context.string(R.string.action_delete)).performClick()
    waitForIdle()
}

fun ComposeTestRule.typeInWordListSearch(
    query: String,
    timeoutMillis: Long = E2E_TIMEOUT_MS,
) {
    waitUntilNodeExists(hasTestTag(WORD_LIST_SEARCH_FIELD_TAG), timeoutMillis)
    onNodeWithTag(WORD_LIST_SEARCH_FIELD_TAG).performTextInput(query)
    waitForIdle()
}

fun ComposeTestRule.clearWordListSearch() {
    onNodeWithTag(WORD_LIST_SEARCH_FIELD_TAG).performTextClearance()
    waitForIdle()
}

fun ComposeTestRule.openWordListSearchScope(
    context: Context,
    timeoutMillis: Long = E2E_TIMEOUT_MS,
) {
    val contentDescription = context.string(R.string.word_list_search_scope_content_description)
    waitUntilNodeExists(hasContentDescription(contentDescription), timeoutMillis)
    onNodeWithContentDescription(contentDescription).performClick()
    waitForIdle()
}

fun ComposeTestRule.selectStudyDirectionMode(
    context: Context,
    mode: StudyDirectionMode,
) {
    onNodeWithText(context.string(R.string.settings_review_direction_title)).performClick()
    waitForIdle()
    onNodeWithText(context.studyDirectionModeLabel(mode)).performClick()
    waitForIdle()
}

fun Context.studyDirectionModeLabel(mode: StudyDirectionMode): String =
    when (mode) {
        StudyDirectionMode.FORWARD -> string(R.string.settings_review_direction_forward)
        StudyDirectionMode.BACKWARD -> string(R.string.settings_review_direction_backward)
        StudyDirectionMode.BIDIRECTIONAL -> string(R.string.settings_review_direction_bidirectional)
    }

fun ComposeTestRule.recreateActivity() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        activity.recreate()
    }
}

fun UiAutomation.shell(command: String): String =
    ParcelFileDescriptor.AutoCloseInputStream(executeShellCommand(command)).bufferedReader().use { it.readText() }

fun Context.stagedAnkiDeckUri(
    authority: String,
    deckFileName: String,
): Uri =
    Uri
        .Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority(authority)
        .appendPath("import")
        .appendPath("anki")
        .appendPath(deckFileName)
        .build()

fun prepareAnkiDocumentPickerResponse(
    instrumentationContext: Context,
    targetContext: Context,
    uri: Uri,
) {
    instrumentationContext.grantUriPermission(
        targetContext.packageName,
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
    )
    val resultIntent =
        Intent().apply {
            setDataAndType(uri, "application/apkg")
            clipData = ClipData.newRawUri("anki-deck", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, resultIntent))
}

fun ComposeTestRule.openAnkiImport(
    context: Context,
    rowTimeoutMillis: Long = E2E_SHORT_TIMEOUT_MS,
) {
    val importRow = context.string(R.string.settings_import_row)
    waitUntilNodeExists(hasText(importRow), rowTimeoutMillis)
    onNodeWithText(importRow, useUnmergedTree = true).performScrollTo()
    onNodeWithText(importRow, useUnmergedTree = true).performClick()
    waitForIdle()

    val ankiOption = context.string(R.string.settings_import_option_anki_apkg)
    waitUntilNodeExists(hasText(ankiOption), rowTimeoutMillis)
    onNodeWithText(ankiOption, useUnmergedTree = true).performClick()
}
