package com.procrastilearn.app.domain.model

import androidx.annotation.StringRes
import com.procrastilearn.app.R

enum class Language(
    val code: String,
    val englishName: String,
    @StringRes val displayNameRes: Int,
) {
    ENGLISH("en", "English", R.string.language_name_english),
    RUSSIAN("ru", "Russian", R.string.language_name_russian),
    SPANISH("es", "Spanish", R.string.language_name_spanish),
    FRENCH("fr", "French", R.string.language_name_french),
    GERMAN("de", "German", R.string.language_name_german),
    ITALIAN("it", "Italian", R.string.language_name_italian),
    PORTUGUESE("pt", "Portuguese", R.string.language_name_portuguese),
    CHINESE("zh", "Chinese", R.string.language_name_chinese),
    ;

    companion object {
        fun fromCode(code: String?): Language? = entries.firstOrNull { it.code == code }
    }
}

data class LanguagePair(
    val native: Language,
    val target: Language,
)
