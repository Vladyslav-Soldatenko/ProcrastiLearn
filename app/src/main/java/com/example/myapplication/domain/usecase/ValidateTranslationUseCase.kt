package com.example.myapplication.domain.usecase

import javax.inject.Inject

class ValidateTranslationUseCase @Inject constructor() {

    operator fun invoke(
        input: String,
        expectedTranslation: String
    ): ValidationResult {
        val normalizedInput = input.trim().lowercase()
        val normalizedExpected = expectedTranslation.trim().lowercase()

        return when {
            normalizedInput.isEmpty() -> ValidationResult.Empty
            normalizedInput == normalizedExpected -> ValidationResult.Correct
            isCloseMatch(normalizedInput, normalizedExpected) -> ValidationResult.Close
            else -> ValidationResult.Incorrect
        }
    }

    private fun isCloseMatch(input: String, expected: String): Boolean {
        return input.length >= expected.length - 1 &&
                expected.contains(input)
    }

    sealed class ValidationResult {
        object Correct : ValidationResult()
        object Incorrect : ValidationResult()
        object Close : ValidationResult()
        object Empty : ValidationResult()
    }
}