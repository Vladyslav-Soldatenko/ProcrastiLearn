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
    fun thereAreExactlyEightSupportedLanguages() {
        assertThat(Language.entries).hasSize(8)
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
