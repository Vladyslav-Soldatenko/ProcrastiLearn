package com.procrastilearn.app.utils

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.service.OverlayAccessibilityService
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class AccessibilityUtilsTest {
    private lateinit var context: Context
    private lateinit var expectedComponent: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        expectedComponent = ComponentName(context, OverlayAccessibilityService::class.java).flattenToString()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, null)
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
    }

    @Test
    fun `returns false when no accessibility services are enabled`() {
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, null)

        val result = isPermissionsGranted(context)

        assertThat(result).isFalse()
    }

    @Test
    fun `returns false when accessibility toggle is off`() {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, expectedComponent)
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)

        val result = isPermissionsGranted(context)

        assertThat(result).isFalse()
    }

    @Test
    fun `returns true when service is enabled`() {
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, expectedComponent)

        val result = isPermissionsGranted(context)

        assertThat(result).isTrue()
    }

    @Test
    fun `returns true when service is present in enabled list ignoring case`() {
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        val mixedCase = expectedComponent.uppercase()
        val enabledList = "other.service/${'$'}id:$mixedCase"
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabledList)

        val result = isPermissionsGranted(context)

        assertThat(result).isTrue()
    }
}
