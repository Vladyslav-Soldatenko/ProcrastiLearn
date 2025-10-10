package com.procrastilearn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.ui.WordListViewModel
import com.procrastilearn.app.ui.theme.MyApplicationTheme
import io.github.oikvpqya.compose.fastscroller.VerticalScrollbar
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter


@Composable
fun WordListScreen(viewModel: WordListViewModel = hiltViewModel()) {
    val words by viewModel.words.collectAsState()

    WordListContent(
        words = words,
        onDelete = viewModel::deleteWord,
        onEdit = viewModel::updateWord,
    )
}

@Composable
@Suppress("LongMethod")
private fun WordListContent(
    words: List<VocabularyItem>,
    onDelete: (VocabularyItem) -> Unit,
    onEdit: (VocabularyItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        // Header
        Text(
            text = stringResource(R.string.word_list_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (words.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.word_list_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // Remember list state so both list & scrollbar share it
            val listState = rememberLazyListState()

            // Take the remaining height under the header and overlay the scrollbar
            Box(
                modifier = Modifier
                    .weight(1f)          // occupy remaining height in the Column
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = words,
                        key = { "${it.word}_${it.translation}" },
                    ) { item ->
                        WordListItem(
                            item = item,
                            onDelete = { onDelete(item) },
                            onEdit = { editedItem -> onEdit(editedItem) },
                        )
                    }
                }

                // Draggable + pressable scrollbar on the right edge
                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 2.dp), // tiny inset from the edge
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle(),     // matches M3 theme
                    enablePressToScroll = true                   // tap track to jump/scroll
                )
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
fun WordListItem(
    item: VocabularyItem,
    onDelete: () -> Unit,
    onEdit: (VocabularyItem) -> Unit,
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = item.word,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row {
                IconButton(
                    onClick = { showEditDialog = true },
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.action_edit),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                IconButton(
                    onClick = { showDeleteDialog = true },
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.word_list_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        EditWordDialog(
            item = item,
            onDismiss = { showEditDialog = false },
            onConfirm = { editedItem ->
                onEdit(editedItem)
                showEditDialog = false
            },
        )
    }

    if (showDeleteDialog) {
        DeleteWordDialog(
            item = item,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                onDelete()
                showDeleteDialog = false
            },
        )
    }
}

@Composable
@Suppress("LongMethod")
private fun EditWordDialog(
    item: VocabularyItem,
    onDismiss: () -> Unit,
    onConfirm: (VocabularyItem) -> Unit,
) {
    var word by remember { mutableStateOf(item.word) }
    var translation by remember { mutableStateOf(item.translation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.edit_word_title))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    label = { Text(stringResource(R.string.add_word_label_word)) },
                    placeholder = { Text(stringResource(R.string.add_word_placeholder_word)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = translation,
                    onValueChange = { translation = it },
                    label = { Text(stringResource(R.string.add_word_label_translation)) },
                    placeholder = { Text(stringResource(R.string.add_word_placeholder_translation)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 240.dp),
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (word.isNotBlank() && translation.isNotBlank()) {
                        onConfirm(item.copy(word = word, translation = translation))
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun DeleteWordDialog(
    item: VocabularyItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.word_list_delete_confirm_title))
        },
        text = {
            Text(text = stringResource(R.string.word_list_delete_confirm_message, item.word))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun WordListContentPreview() {
    MyApplicationTheme {
        WordListContent(
            words =
                listOf(
                    VocabularyItem(id = 1, word = "Serendipity", translation = "Happy accident; pleasant surprise", isNew = true),
                    VocabularyItem(id = 2, word = "Ephemeral", translation = "Lasting for a very short time", isNew = false),
                    VocabularyItem(id = 3, word = "Peregrinate", translation = "To travel or wander around", isNew = false),
                ),
            onDelete = {},
            onEdit = {},
        )
    }
}
