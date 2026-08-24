package com.procrastilearn.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MainActivityTest {
    @Test
    fun `not loaded overrides every other state`() {
        val step =
            resolveOnboardingStep(
                preferencesLoaded = false,
                isAccessibilityEnabled = true,
                hasSkippedAccessibility = true,
                hasOverlayPermission = true,
                hasSkippedOverlay = true,
                hasLanguagePair = true,
            )

        assertThat(step).isEqualTo(OnboardingStep.LOADING)
    }

    @Test
    fun `accessibility disabled and not skipped shows the accessibility step`() {
        val step =
            resolveOnboardingStep(
                preferencesLoaded = true,
                isAccessibilityEnabled = false,
                hasSkippedAccessibility = false,
                hasOverlayPermission = true,
                hasSkippedOverlay = true,
                hasLanguagePair = true,
            )

        assertThat(step).isEqualTo(OnboardingStep.ACCESSIBILITY)
    }

    @Test
    fun `accessibility disabled but skipped falls through to the overlay check`() {
        val step =
            resolveOnboardingStep(
                preferencesLoaded = true,
                isAccessibilityEnabled = false,
                hasSkippedAccessibility = true,
                hasOverlayPermission = false,
                hasSkippedOverlay = false,
                hasLanguagePair = true,
            )

        assertThat(step).isEqualTo(OnboardingStep.OVERLAY)
    }

    @Test
    fun `overlay missing and not skipped shows the overlay step`() {
        val step =
            resolveOnboardingStep(
                preferencesLoaded = true,
                isAccessibilityEnabled = true,
                hasSkippedAccessibility = true,
                hasOverlayPermission = false,
                hasSkippedOverlay = false,
                hasLanguagePair = true,
            )

        assertThat(step).isEqualTo(OnboardingStep.OVERLAY)
    }

    @Test
    fun `overlay missing but skipped falls through to the language check`() {
        val step =
            resolveOnboardingStep(
                preferencesLoaded = true,
                isAccessibilityEnabled = true,
                hasSkippedAccessibility = true,
                hasOverlayPermission = false,
                hasSkippedOverlay = true,
                hasLanguagePair = false,
            )

        assertThat(step).isEqualTo(OnboardingStep.LANGUAGE)
    }

    @Test
    fun `no language pair shows the language step once permissions are settled`() {
        val step =
            resolveOnboardingStep(
                preferencesLoaded = true,
                isAccessibilityEnabled = true,
                hasSkippedAccessibility = true,
                hasOverlayPermission = true,
                hasSkippedOverlay = true,
                hasLanguagePair = false,
            )

        assertThat(step).isEqualTo(OnboardingStep.LANGUAGE)
    }

    @Test
    fun `everything satisfied reaches the main app content`() {
        val step =
            resolveOnboardingStep(
                preferencesLoaded = true,
                isAccessibilityEnabled = true,
                hasSkippedAccessibility = false,
                hasOverlayPermission = true,
                hasSkippedOverlay = false,
                hasLanguagePair = true,
            )

        assertThat(step).isEqualTo(OnboardingStep.MAIN)
    }

    @Test
    fun `accessibility takes priority over a simultaneously unresolved overlay step`() {
        val step =
            resolveOnboardingStep(
                preferencesLoaded = true,
                isAccessibilityEnabled = false,
                hasSkippedAccessibility = false,
                hasOverlayPermission = false,
                hasSkippedOverlay = false,
                hasLanguagePair = false,
            )

        assertThat(step).isEqualTo(OnboardingStep.ACCESSIBILITY)
    }
}
