package com.procrastilearn.app.ui.dojo.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.procrastilearn.app.R
import com.procrastilearn.app.ui.theme.MyApplicationTheme

@Composable
fun DojoStatsHeader(
    newQuotaRemaining: Int,
    pendingReviewCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
        )
    }
}
