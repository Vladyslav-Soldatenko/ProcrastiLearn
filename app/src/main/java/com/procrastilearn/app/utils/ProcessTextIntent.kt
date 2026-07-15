package com.procrastilearn.app.utils

import android.content.Intent

/**
 * Extracts the selected text from an ACTION_PROCESS_TEXT intent (the "ProcrastiLearn this"
 * text-selection action), or null if the intent doesn't carry one.
 */
fun extractProcessText(intent: Intent?): String? {
    if (intent?.action != Intent.ACTION_PROCESS_TEXT) return null
    val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
    return selectedText?.takeIf { it.isNotBlank() }
}
