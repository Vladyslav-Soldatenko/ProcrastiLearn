package com.procrastilearn.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GateTimingSettingsTest {
    @Test
    fun zeroRepeatAllowsIndependentCooldown() {
        assertThat(validateGateTiming(GateTimingSettings(cooldownMinutes = 20, repeatIntervalMinutes = 0))).isNull()
    }

    @Test
    fun equalityIsValid() {
        assertThat(validateGateTiming(GateTimingSettings(cooldownMinutes = 5, repeatIntervalMinutes = 5))).isNull()
    }

    @Test
    fun cooldownAboveRepeatIsRejected() {
        assertThat(validateGateTiming(GateTimingSettings(cooldownMinutes = 6, repeatIntervalMinutes = 5)))
            .isEqualTo(GateTimingValidationError.CooldownExceedsRepeat(6, 5))
    }

    @Test
    fun negativeAndOverMaximumValuesAreRejected() {
        assertThat(validateGateTiming(GateTimingSettings(cooldownMinutes = -1, repeatIntervalMinutes = 0)))
            .isEqualTo(GateTimingValidationError.OutOfRange(GateTimingField.COOLDOWN))
        assertThat(validateGateTiming(GateTimingSettings(cooldownMinutes = 0, repeatIntervalMinutes = 2001)))
            .isEqualTo(GateTimingValidationError.OutOfRange(GateTimingField.REPEAT_INTERVAL))
        assertThat(validateGateTiming(GateTimingSettings(cooldownMinutes = 2001, repeatIntervalMinutes = 0)))
            .isEqualTo(GateTimingValidationError.OutOfRange(GateTimingField.COOLDOWN))
    }
}
