package com.example.myapplication.overlay.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                text = state.vocabularyItem?.word ?:"No word loaded",
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF9FAFB),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )

            // Show translation button
            OutlinedButton(
                onClick = onToggleShowAnswer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFBFDBFE)
                )
            ) {
                Text(
                    text = if (state.showAnswer) "Hide translation" else "Show translation",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Translation (shown when button is clicked)
            AnimatedVisibility(
                visible = state.showAnswer,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = state.vocabularyItem?.translation ?: "No translation loaded",
                        color = Color(0xFF93C5FD),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .alpha(0.95f)
                    )
                }
            }

            // Divider
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = Color(0xFF374151),
                thickness = 1.dp
            )

            // Difficulty buttons
            Text(
                text = "How well did you know this?",
                color = Color(0xFF9CA3AF),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            DifficultyButtons(
                onDifficultySelected = onDifficultySelected,
                enabled = state.showAnswer // Only enable buttons after showing answer
            )
        }
    }
}

@Composable
private fun DifficultyButtons(
    onDifficultySelected: (Rating) -> Unit,
    enabled: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DifficultyButton(
                text = "Again",
                difficulty = Rating.AGAIN,
                color = Color(0xFFEF4444),
                enabled = enabled,
                onClick = { onDifficultySelected(Rating.AGAIN) },
                modifier = Modifier.weight(1f)
            )
            DifficultyButton(
                text = "Hard",
                difficulty = Rating.HARD,
                color = Color(0xFFF59E0B),
                enabled = enabled,
                onClick = { onDifficultySelected(Rating.HARD) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DifficultyButton(
                text = "Good",
                difficulty = Rating.GOOD,
                color = Color(0xFF10B981),
                enabled = enabled,
                onClick = { onDifficultySelected(Rating.GOOD) },
                modifier = Modifier.weight(1f)
            )
            DifficultyButton(
                text = "Easy",
                difficulty = Rating.EASY,
                color = Color(0xFF3B82F6),
                enabled = enabled,
                onClick = { onDifficultySelected(Rating.EASY) },
                modifier = Modifier.weight(1f)
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
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) color else Color(0xFF374151),
            disabledContainerColor = Color(0xFF374151)
        )
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color.White else Color(0xFF6B7280)
        )
    }
}

