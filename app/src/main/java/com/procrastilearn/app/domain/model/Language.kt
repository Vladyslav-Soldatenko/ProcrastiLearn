package com.procrastilearn.app.domain.model

import androidx.annotation.StringRes
import com.procrastilearn.app.R
import java.text.Collator
import java.util.Locale

enum class Language(
    val code: String,
    val englishName: String,
    @param:StringRes val displayNameRes: Int,
) {
    ENGLISH("en", "English", R.string.language_name_english),
    RUSSIAN("ru", "Russian", R.string.language_name_russian),
    SPANISH("es", "Spanish", R.string.language_name_spanish),
    FRENCH("fr", "French", R.string.language_name_french),
    GERMAN("de", "German", R.string.language_name_german),
    ITALIAN("it", "Italian", R.string.language_name_italian),
    PORTUGUESE("pt", "Portuguese", R.string.language_name_portuguese),
    CHINESE("zh", "Chinese", R.string.language_name_chinese),
    JAPANESE("ja", "Japanese", R.string.language_name_japanese),
    KOREAN("ko", "Korean", R.string.language_name_korean),
    UKRAINIAN("uk", "Ukrainian", R.string.language_name_ukrainian),
    POLISH("pl", "Polish", R.string.language_name_polish),
    HINDI("hi", "Hindi", R.string.language_name_hindi),
    TURKISH("tr", "Turkish", R.string.language_name_turkish),
    DUTCH("nl", "Dutch", R.string.language_name_dutch),
    VIETNAMESE("vi", "Vietnamese", R.string.language_name_vietnamese),
    INDONESIAN("id", "Indonesian", R.string.language_name_indonesian),
    THAI("th", "Thai", R.string.language_name_thai),
    SWEDISH("sv", "Swedish", R.string.language_name_swedish),
    ;

    companion object {
        fun fromCode(code: String?): Language? = entries.firstOrNull { it.code == code }

        fun sortedByDisplayName(
            locale: Locale,
            displayName: (Language) -> String,
            excluding: Language? = null,
        ): List<Language> {
            val collator = Collator.getInstance(locale)
            return entries
                .filter { it != excluding }
                .sortedWith(compareBy(collator) { displayName(it) })
        }
    }
}

data class LanguagePair(
    val native: Language,
    val target: Language,
)
