package com.procrastilearn.app.ui.dojo.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.procrastilearn.app.R
import com.procrastilearn.app.ui.theme.MyApplicationTheme

private const val DISABLED_ALPHA = 0.35f

@Composable
private fun UndoButton(
    canUndo: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onUndo,
        enabled = canUndo,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Undo,
            contentDescription = stringResource(R.string.dojo_undo_content_description),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alpha(if (canUndo) 1f else DISABLED_ALPHA),
        )
    }
}

@Composable
fun DojoStatsHeader(
    newQuotaRemaining: Int,
    pendingReviewCount: Int,
    modifier: Modifier = Modifier,
    canUndo: Boolean = false,
    onUndo: () -> Unit = {},
    skippedCardCount: Int = 0,
    vertical: Boolean = false,
) {
    if (vertical) {
        DojoStatsHeaderVertical(
            newQuotaRemaining = newQuotaRemaining,
            pendingReviewCount = pendingReviewCount,
            canUndo = canUndo,
            onUndo = onUndo,
            skippedCardCount = skippedCardCount,
            modifier = modifier,
        )
    } else {
        DojoStatsHeaderHorizontal(
            newQuotaRemaining = newQuotaRemaining,
            pendingReviewCount = pendingReviewCount,
            canUndo = canUndo,
            onUndo = onUndo,
            skippedCardCount = skippedCardCount,
            modifier = modifier,
        )
    }
}

@Composable
private fun DojoStatsHeaderHorizontal(
    newQuotaRemaining: Int,
    pendingReviewCount: Int,
    canUndo: Boolean,
    onUndo: () -> Unit,
    skippedCardCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        UndoButton(
            canUndo = canUndo,
            onUndo = onUndo,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // New words remaining
            Text(
                text = newQuotaRemaining.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = " ${stringResource(R.string.dojo_stats_new_remaining)}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = " / ",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            // Reviews due
            Text(
                text = pendingReviewCount.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = " ${stringResource(R.string.dojo_stats_reviews_due)}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (skippedCardCount > 0) {
                Text(
                    text = " · $skippedCardCount ${stringResource(R.string.dojo_stats_skipped)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DojoStatsHeaderVertical(
    newQuotaRemaining: Int,
    pendingReviewCount: Int,
    canUndo: Boolean,
    onUndo: () -> Unit,
    skippedCardCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.width(72.dp).padding(vertical = 8.dp),
    ) {
        UndoButton(
            canUndo = canUndo,
            onUndo = onUndo,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatColumn(
                    count = newQuotaRemaining,
                    label = stringResource(R.string.dojo_stats_new_remaining),
                    color = MaterialTheme.colorScheme.error,
                )
                HorizontalDivider(modifier = Modifier.width(28.dp))
                StatColumn(
                    count = pendingReviewCount,
                    label = stringResource(R.string.dojo_stats_reviews_due),
                    color = MaterialTheme.colorScheme.tertiary,
                )
                if (skippedCardCount > 0) {
                    HorizontalDivider(modifier = Modifier.width(28.dp))
                    StatColumn(
                        count = skippedCardCount,
                        label = stringResource(R.string.dojo_stats_skipped),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(
    count: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Light Mode")
@Preview(name = "Dark Mode", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DojoStatsHeaderPreview() {
    MyApplicationTheme {
        DojoStatsHeader(
            newQuotaRemaining = 17,
            pendingReviewCount = 10,
            canUndo = true,
        )
    }
}

@Preview(name = "Zero State")
@Composable
private fun DojoStatsHeaderZeroPreview() {
    MyApplicationTheme {
        DojoStatsHeader(
            newQuotaRemaining = 0,
            pendingReviewCount = 0,
            canUndo = false,
        )
    }
}

@Preview(name = "Vertical • Light")
@Preview(name = "Vertical • Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DojoStatsHeaderVerticalPreview() {
    MyApplicationTheme {
        DojoStatsHeader(
            newQuotaRemaining = 15,
            pendingReviewCount = 670,
            canUndo = true,
            skippedCardCount = 2,
            vertical = true,
        )
    }
}
