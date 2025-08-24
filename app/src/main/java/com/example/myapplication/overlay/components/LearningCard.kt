package com.example.myapplication.overlay.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.overlay.OverlayUiState
import io.github.openspacedrepetition.Rating

@Composable
fun LearningCard(
    state: OverlayUiState,
    onToggleShowAnswer: () -> Unit,
    onDifficultySelected: (Rating) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(1f),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 22.dp)
                    .paddingFromBaseline(top = 45.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title
            Text(
                text = state.vocabularyItem?.word ?: "No word loaded",
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF9FAFB),
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .padding(top = 6.dp, bottom = 10.dp)
                        .fillMaxWidth(),
            )

            // Translation area (middle). Scrollable when shown.
            val scrollState = rememberScrollState()
            LaunchedEffect(state.vocabularyItem?.id, state.showAnswer) {
                if (state.showAnswer) scrollState.scrollTo(0)
            }

            AnimatedVisibility(
                visible = state.showAnswer,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier =
                    Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 8.dp),
            ) {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = state.vocabularyItem?.translation ?: "No translation loaded",
                            color = Color(0xFF93C5FD),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Start,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .alpha(0.95f),
                            lineHeight = 26.sp,
                        )
                    }
                }
            }

            // When translation is hidden, push footer to the bottom.
            if (!state.showAnswer) Spacer(modifier = Modifier.weight(1f))

            // Footer swaps content in-place:
            if (!state.showAnswer) {
                // Bottom: Show translation button
                OutlinedButton(
                    onClick = onToggleShowAnswer,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .navigationBarsPadding(),
                    shape = RoundedCornerShape(14.dp),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFBFDBFE),
                        ),
                ) {
                    Text("Show translation", fontSize = 16.sp, textAlign = TextAlign.Center)
                }
            } else {
                // Bottom: divider + help + difficulty buttons (replaces the Show button)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = Color(0xFF374151),
                    thickness = 1.dp,
                )
                Text(
                    text = "How well did you know this?",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp,
                    modifier =
                        Modifier
                            .padding(top = 2.dp, bottom = 8.dp)
                            .fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                DifficultyButtons(
                    onDifficultySelected = onDifficultySelected,
                    enabled = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                )
            }
        }
    }
}

@Composable
private fun DifficultyButtons(
    onDifficultySelected: (Rating) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DifficultyButton(
                "Again",
                Rating.AGAIN,
                Color(0xFFEF4444),
                enabled,
                onClick = { onDifficultySelected(Rating.AGAIN) },
                modifier = Modifier.weight(1f),
            )
            DifficultyButton(
                "Hard",
                Rating.HARD,
                Color(0xFFF59E0B),
                enabled,
                onClick = { onDifficultySelected(Rating.HARD) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DifficultyButton(
                "Good",
                Rating.GOOD,
                Color(0xFF10B981),
                enabled,
                onClick = { onDifficultySelected(Rating.GOOD) },
                modifier = Modifier.weight(1f),
            )
            DifficultyButton(
                "Easy",
                Rating.EASY,
                Color(0xFF3B82F6),
                enabled,
                onClick = { onDifficultySelected(Rating.EASY) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DifficultyButton(
    text: String,
    difficulty: Rating,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (enabled) color else Color(0xFF374151),
                disabledContainerColor = Color(0xFF374151),
            ),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color.White else Color(0xFF6B7280),
        )
    }
}
