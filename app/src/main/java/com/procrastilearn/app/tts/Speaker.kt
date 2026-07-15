package com.procrastilearn.app.tts

import java.util.Locale

interface Speaker {
    fun speak(
        text: String,
        locale: Locale,
    )

    fun shutdown()
}
