package com.procrastilearn.app.utils

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProcessTextIntentTest {
    @Test
    fun `returns the selected text for a PROCESS_TEXT intent`() {
        val intent =
            Intent(Intent.ACTION_PROCESS_TEXT).apply {
                putExtra(Intent.EXTRA_PROCESS_TEXT, "Haus")
            }

        assertThat(extractProcessText(intent)).isEqualTo("Haus")
    }

    @Test
    fun `returns null when the intent action is not PROCESS_TEXT`() {
        val intent =
            Intent(Intent.ACTION_MAIN).apply {
                putExtra(Intent.EXTRA_PROCESS_TEXT, "Haus")
            }

        assertThat(extractProcessText(intent)).isNull()
    }

    @Test
    fun `returns null when the intent is null`() {
        assertThat(extractProcessText(null)).isNull()
    }

    @Test
    fun `returns null when the extra is missing`() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT)

        assertThat(extractProcessText(intent)).isNull()
    }

    @Test
    fun `returns null when the extra is blank`() {
        val intent =
            Intent(Intent.ACTION_PROCESS_TEXT).apply {
                putExtra(Intent.EXTRA_PROCESS_TEXT, "   ")
            }

        assertThat(extractProcessText(intent)).isNull()
    }
}
