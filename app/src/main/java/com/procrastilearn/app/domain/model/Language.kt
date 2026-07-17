package com.procrastilearn.app.domain.model

import androidx.annotation.StringRes
import com.procrastilearn.app.R

enum class Language(
    val code: String,
    @StringRes val displayNameRes: Int,
) {
    ENGLISH("en", R.string.language_name_english),
    RUSSIAN("ru", R.string.language_name_russian),
    SPANISH("es", R.string.language_name_spanish),
    FRENCH("fr", R.string.language_name_french),
    GERMAN("de", R.string.language_name_german),
    ITALIAN("it", R.string.language_name_italian),
    PORTUGUESE("pt", R.string.language_name_portuguese),
    CHINESE("zh", R.string.language_name_chinese),
    ;

    companion object {
        fun fromCode(code: String?): Language? = entries.firstOrNull { it.code == code }
    }
}

data class LanguagePair(
    val native: Language,
    val target: Language,
)
