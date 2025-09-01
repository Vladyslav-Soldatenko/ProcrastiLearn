@file:Suppress("MagicNumber")
package com.procrastilearn.app.overlay.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.procrastilearn.app.R
import com.procrastilearn.app.overlay.OverlayUiState
import com.procrastilearn.app.overlay.theme.OverlayThemeTokens
import io.github.openspacedrepetition.Rating

@Suppress("LongMethod")
@Composable
fun LearningCard(
    state: OverlayUiState,
    onToggleShowAnswer: () -> Unit,
    onDifficultySelected: (Rating) -> Unit,
) {
    val titleAnnotated =
        buildAnnotatedString {
            append(state.vocabularyItem?.word ?: stringResource(R.string.learning_no_word))
            if (state.vocabularyItem?.isNew == true) {
                append(" ")
                withStyle(
                    SpanStyle(
                        color = OverlayThemeTokens.colors.newBadgeColor,
                        fontSize = 12.sp, // smaller than main title
                        fontWeight = FontWeight.SemiBold, // a bit “elevated” emphasis
                        baselineShift = BaselineShift.Superscript,
                    ),
                ) { append("NEW") }
            }
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(1f),
        colors = CardDefaults.cardColors(containerColor = OverlayThemeTokens.colors.cardContainer),
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
                text = titleAnnotated,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = OverlayThemeTokens.colors.titleColor,
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
                    colors = CardDefaults.cardColors(containerColor = OverlayThemeTokens.colors.innerCardContainer),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text =
                                state.vocabularyItem?.translation ?: stringResource(
                                    R.string.learning_no_translation,
                                ),
                            color = OverlayThemeTokens.colors.translationText,
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
                            contentColor = OverlayThemeTokens.colors.showButtonContent,
                        ),
                ) {
                    Text(
                        stringResource(R.string.learning_show_translation),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // Bottom: divider + help + difficulty buttons (replaces the Show button)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = OverlayThemeTokens.colors.divider,
                    thickness = 1.dp,
                )
                Text(
                    text = stringResource(R.string.learning_question),
                    color = OverlayThemeTokens.colors.helpText,
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
                stringResource(R.string.rating_again),
                containerColor = OverlayThemeTokens.colors.difficultyAgainContainer,
                contentColor = OverlayThemeTokens.colors.difficultyAgainContent,
                enabled,
                onClick = { onDifficultySelected(Rating.AGAIN) },
                modifier = Modifier.weight(1f),
            )
            DifficultyButton(
                stringResource(R.string.rating_hard),
                containerColor = OverlayThemeTokens.colors.difficultyHardContainer,
                contentColor = OverlayThemeTokens.colors.difficultyHardContent,
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
                stringResource(R.string.rating_good),
                containerColor = OverlayThemeTokens.colors.difficultyGoodContainer,
                contentColor = OverlayThemeTokens.colors.difficultyGoodContent,
                enabled,
                onClick = { onDifficultySelected(Rating.GOOD) },
                modifier = Modifier.weight(1f),
            )
            DifficultyButton(
                stringResource(R.string.rating_easy),
                containerColor = OverlayThemeTokens.colors.difficultyEasyContainer,
                contentColor = OverlayThemeTokens.colors.difficultyEasyContent,
                enabled,
                onClick = { onDifficultySelected(Rating.EASY) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun DifficultyButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
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
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = OverlayThemeTokens.colors.disabledContainer,
                disabledContentColor = OverlayThemeTokens.colors.disabledContent,
            ),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) contentColor else OverlayThemeTokens.colors.disabledContent,
        )
    }
}
