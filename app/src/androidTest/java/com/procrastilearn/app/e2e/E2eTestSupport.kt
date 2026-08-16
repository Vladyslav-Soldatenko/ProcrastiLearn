package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.procrastilearn.app.R

// Generous enough to cover MainActivity's cold-start (Hilt injection, DataStore reads, first
// Compose frame) on slow CI emulators — nodeVisibleWithin returns as soon as the node appears,
// so this doesn't slow down fast environments, only raises the worst-case ceiling. A tighter
// budget risks one gating screen's slow first render eating the whole repeat() iteration,
// leaving the next gating screen (e.g. the overlay permission dialog) never dismissed.
private const val ONBOARDING_STEP_TIMEOUT_MS = 10_000L
private const val NODE_POLL_INTERVAL_MS = 100L

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

fun ComposeTestRule.nodeVisibleWithin(
    matcher: SemanticsMatcher,
    timeoutMillis: Long,
): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        waitForIdle()
        val exists =
            try {
                onNode(matcher, useUnmergedTree = true).fetchSemanticsNode()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: IllegalStateException) {
                false
            }
        if (exists) return true
        Thread.sleep(NODE_POLL_INTERVAL_MS)
    }
    return false
}

fun ComposeTestRule.dismissOnboardingIfPresent(context: Context) {
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
    if (!nodeVisibleWithin(hasText(languageTitle), ONBOARDING_STEP_TIMEOUT_MS)) return

    onNodeWithTag("language_selection_native_field", useUnmergedTree = true).performClick()
    waitForIdle()
    onNodeWithText(context.getString(R.string.language_name_english), useUnmergedTree = true).performClick()
    waitForIdle()

    onNodeWithTag("language_selection_target_field", useUnmergedTree = true).performClick()
    waitForIdle()
    onNodeWithText(context.getString(R.string.language_name_russian), useUnmergedTree = true).performClick()
    waitForIdle()

    onNodeWithText(context.getString(R.string.action_continue), useUnmergedTree = true).performClick()
    waitForIdle()
}
