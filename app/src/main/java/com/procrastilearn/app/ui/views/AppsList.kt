package com.procrastilearn.app.ui.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.AppInfo

@Composable
fun AppsList(
    apps: List<AppInfo>,
    selectedKeys: Set<String>,
    isEnabled: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onToggleEnabled: (Boolean) -> Unit,
    onToggle: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            EnableProcrastilearnRow(
                enabled = isEnabled,
                onEnabledChange = onToggleEnabled,
            )
        }

        when {
            isLoading -> {
                item {
                    Box(
                        modifier =
                            Modifier
                                .fillParentMaxSize()
                                .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.testTag("apps_list_loading_indicator"),
                        )
                    }
                }
            }
            errorMessage != null -> {
                item {
                    Box(
                        modifier =
                            Modifier
                                .fillParentMaxSize()
                                .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.apps_list_error_message, errorMessage),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("apps_list_error_text"),
                        )
                    }
                }
            }
            else -> {
                items(
                    items = apps,
                    key = { it.packageName },
                ) { app ->
                    AppRow(
                        app = app,
                        checked = selectedKeys.contains(app.packageName),
                        enabled = isEnabled,
                        onCheckedChange = { onToggle(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EnableProcrastilearnRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor =
        if (enabled) {
            colorScheme.secondaryContainer
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.4f)
        }
    val contentColor =
        if (enabled) {
            colorScheme.onSecondaryContainer
        } else {
            colorScheme.onSurfaceVariant
        }
    val dividerColor =
        if (enabled) {
            colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
        } else {
            colorScheme.outline.copy(alpha = 0.3f)
        }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("apps_list_enable_toggle")
                .clickable { onEnabledChange(!enabled) },
        color = backgroundColor,
        contentColor = contentColor,
        tonalElevation = if (enabled) 4.dp else 0.dp,
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.apps_list_enable_procrastilearn_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )

                Checkbox(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.testTag("apps_list_enable_checkbox"),
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = colorScheme.onSecondaryContainer,
                            uncheckedColor = colorScheme.onSurfaceVariant,
                        ),
                )
            }

            HorizontalDivider(
                color = dividerColor,
                thickness = 1.dp,
            )
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("app_row_${app.packageName}")
                .clickable(enabled = enabled) { onCheckedChange(!checked) },
        color =
            when {
                !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                checked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.surface
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .alpha(if (enabled) 1f else 0.6f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // App icon
            app.icon?.let { drawable ->
                Image(
                    painter = rememberDrawablePainter(drawable),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }

            // App name
            Text(
                text = app.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Checkbox
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.testTag("app_checkbox_${app.packageName}"),
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                    ),
            )
        }
    }

    // Divider between items
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    )
}

// Helper composable to convert Drawable to Painter
@Composable
fun rememberDrawablePainter(drawable: Drawable): Painter =
    remember(drawable) {
        drawable.toBitmap().asImageBitmap().let { BitmapPainter(it) }
    }

// Extension function to convert Drawable to Bitmap
fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) {
        return bitmap
    }

    val bitmap =
        Bitmap.createBitmap(
            intrinsicWidth.coerceAtLeast(1),
            intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )

    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)

    return bitmap
}
