package com.procrastilearn.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LanguageTest {
    @Test
    fun fromCodeResolvesEachKnownCode() {
        assertThat(Language.fromCode("en")).isEqualTo(Language.ENGLISH)
        assertThat(Language.fromCode("ru")).isEqualTo(Language.RUSSIAN)
        assertThat(Language.fromCode("es")).isEqualTo(Language.SPANISH)
        assertThat(Language.fromCode("fr")).isEqualTo(Language.FRENCH)
        assertThat(Language.fromCode("de")).isEqualTo(Language.GERMAN)
        assertThat(Language.fromCode("it")).isEqualTo(Language.ITALIAN)
        assertThat(Language.fromCode("pt")).isEqualTo(Language.PORTUGUESE)
        assertThat(Language.fromCode("zh")).isEqualTo(Language.CHINESE)
        assertThat(Language.fromCode("ja")).isEqualTo(Language.JAPANESE)
        assertThat(Language.fromCode("ko")).isEqualTo(Language.KOREAN)
        assertThat(Language.fromCode("uk")).isEqualTo(Language.UKRAINIAN)
        assertThat(Language.fromCode("pl")).isEqualTo(Language.POLISH)
        assertThat(Language.fromCode("hi")).isEqualTo(Language.HINDI)
        assertThat(Language.fromCode("tr")).isEqualTo(Language.TURKISH)
        assertThat(Language.fromCode("nl")).isEqualTo(Language.DUTCH)
        assertThat(Language.fromCode("vi")).isEqualTo(Language.VIETNAMESE)
        assertThat(Language.fromCode("id")).isEqualTo(Language.INDONESIAN)
        assertThat(Language.fromCode("th")).isEqualTo(Language.THAI)
        assertThat(Language.fromCode("sv")).isEqualTo(Language.SWEDISH)
    }

    @Test
    fun fromCodeReturnsNullForUnknownCode() {
        assertThat(Language.fromCode("xx")).isNull()
        assertThat(Language.fromCode("EN")).isNull()
        assertThat(Language.fromCode("")).isNull()
    }

    @Test
    fun fromCodeReturnsNullForNullInput() {
        assertThat(Language.fromCode(null)).isNull()
    }

    @Test
    fun everyLanguageHasAUniqueCode() {
        val codes = Language.entries.map { it.code }
        assertThat(codes).containsNoDuplicates()
    }

    @Test
    fun everyLanguageHasAUniqueDisplayNameResource() {
        val displayNameResIds = Language.entries.map { it.displayNameRes }
        assertThat(displayNameResIds).containsNoDuplicates()
    }

    @Test
    fun thereAreExactlyNineteenSupportedLanguages() {
        assertThat(Language.entries).hasSize(19)
    }

    @Test
    fun everyLanguageHasAUniqueNonBlankEnglishName() {
        val englishNames = Language.entries.map { it.englishName }

        assertThat(englishNames).containsNoDuplicates()
        englishNames.forEach { assertThat(it).isNotEmpty() }
    }

    @Test
    fun languagePairEqualityIsBasedOnNativeAndTarget() {
        val a = LanguagePair(Language.ENGLISH, Language.RUSSIAN)
        val b = LanguagePair(Language.ENGLISH, Language.RUSSIAN)
        val c = LanguagePair(Language.RUSSIAN, Language.ENGLISH)

        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEqualTo(c)
    }
}
