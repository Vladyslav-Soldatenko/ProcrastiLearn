package com.procrastilearn.app.service

import android.content.Context
import android.media.AudioManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class OverlayAccessibilityServiceAudioFocusTest : OverlayAccessibilityServiceTestFixture() {
    @Test
    fun `leaving a blocked app for a legit app ends the gate session and releases audio focus`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)

        advanceRealClockPastDebounceWindow()
        dispatch(LEGIT_PACKAGE)

        verify(exactly = 1) { windowManager.removeView(any()) }

        val audioManager =
            service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assertThat(shadowOf(audioManager).lastAbandonedAudioFocusRequest).isNotNull()
    }

    @Test
    fun `audio focus failure leaves the attached review usable`() {
        val audioManager = mockk<AudioManager>()
        every { audioManager.requestAudioFocus(any()) } throws SecurityException("focus denied")
        ReflectionHelpers.setField(service, "audioManager", audioManager)
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()

        dispatch(BLOCKED_PACKAGE)

        assertThat(service.gateSnapshotForE2eTests().overlayAttached).isTrue()
        verify(exactly = 0) { windowManager.removeView(any()) }
    }

    @Test
    fun `disabled background audio pause attaches review without requesting focus`() {
        val audioManager = mockk<AudioManager>(relaxed = true)
        ReflectionHelpers.setField(service, "audioManager", audioManager)
        pauseBackgroundAudioFlow.value = false
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()

        dispatch(BLOCKED_PACKAGE)

        assertThat(service.gateSnapshotForE2eTests().overlayAttached).isTrue()
        assertThat(service.gateSnapshotForE2eTests().audioFocusAttached).isFalse()
        verify(exactly = 0) { audioManager.requestAudioFocus(any()) }
    }

    @Test
    fun `denied audio focus leaves review attached without recording focus`() {
        val audioManager = mockk<AudioManager>()
        every { audioManager.requestAudioFocus(any()) } returns AudioManager.AUDIOFOCUS_REQUEST_FAILED
        ReflectionHelpers.setField(service, "audioManager", audioManager)
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()

        dispatch(BLOCKED_PACKAGE)

        assertThat(service.gateSnapshotForE2eTests().overlayAttached).isTrue()
        assertThat(service.gateSnapshotForE2eTests().audioFocusAttached).isFalse()
    }
}
