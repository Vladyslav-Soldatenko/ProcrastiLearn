package com.example.myapplication.overlay.components


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.overlay.OverlayUiState
import kotlinx.coroutines.launch

@Composable
fun LearningCard(
    state: OverlayUiState,
    onInputChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleShowAnswer: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var focusRequested by remember { mutableStateOf(false) }

    // Request focus once
    LaunchedEffect(Unit) {
        if (!focusRequested) {
            focusRequested = true
            focusRequester.requestFocus()
        }
    }

    // Shake animation
    val shakeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.errorTick) {
        if (state.error && state.errorTick > 0) {
            coroutineScope.launch {
                shakeOffset.snapTo(0f)
                val shakePattern = listOf(-12f, 12f, -8f, 8f, -4f, 4f, 0f)
                for (offset in shakePattern) {
                    shakeOffset.animateTo(offset)
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .wrapContentHeight(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Word prompt
            Text(
                text = state.vocabularyItem.word,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF9FAFB),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )

            // Translation hint
            AnimatedVisibility(
                visible = state.showAnswer,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = "Translation: \"${state.vocabularyItem.translation}\"",
                    color = Color(0xFF93C5FD),
                    fontSize = 16.sp,
                    modifier = Modifier.alpha(0.95f)
                )
            }

            // Input field
            TranslationInputField(
                value = state.input,
                onValueChange = onInputChanged,
                isError = state.error,
                onSubmit = {
                    focusManager.clearFocus(force = true)
                    onSubmit()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            // Error message
            AnimatedVisibility(visible = state.error) {
                Text(
                    text = "Incorrect translation. Try again.",
                    color = Color(0xFFFFA7A7),
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Action buttons
            ActionButtons(
                onSubmit = {
                    focusManager.clearFocus(force = true)
                    onSubmit()
                },
                onToggleShowAnswer = onToggleShowAnswer,
                showingAnswer = state.showAnswer,
                modifier = Modifier.offset(x = shakeOffset.value.dp)
            )

            // Tip
            Text(
                text = "Tip: press Enter on keyboard to submit",
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun TranslationInputField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text("Type translation here…", color = Color(0xFF9CA3AF)) },
        isError = isError,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = modifier.shadow(0.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF60A5FA),
            unfocusedBorderColor = Color(0xFF6B7280),
            cursorColor = Color(0xFF93C5FD),
            focusedTextColor = Color(0xFFF3F4F6),
            unfocusedTextColor = Color(0xFFF3F4F6)
        )
    )
}

@Composable
private fun ActionButtons(
    onSubmit: () -> Unit,
    onToggleShowAnswer: () -> Unit,
    showingAnswer: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Enter", fontSize = 16.sp)
        }

        OutlinedButton(
            onClick = onToggleShowAnswer,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFBFDBFE)
            )
        ) {
            Text(
                text = if (showingAnswer) "Hide translation" else "Show translation",
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}