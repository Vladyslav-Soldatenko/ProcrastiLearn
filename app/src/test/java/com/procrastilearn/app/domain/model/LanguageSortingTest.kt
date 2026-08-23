package com.procrastilearn.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.text.Collator
import java.util.Locale

class LanguageSortingTest {
    @Test
    fun sortedByDisplayNameOrdersEntriesByTheProvidedName() {
        val sorted = Language.sortedByDisplayName(Locale.ENGLISH, Language::englishName)

        val names = sorted.map { it.englishName }
        assertThat(names).isEqualTo(names.sortedWith(Collator.getInstance(Locale.ENGLISH)))
    }

    @Test
    fun sortedByDisplayNameExcludesTheGivenLanguage() {
        val sorted =
            Language.sortedByDisplayName(
                locale = Locale.ENGLISH,
                displayName = Language::englishName,
                excluding = Language.JAPANESE,
            )

        assertThat(sorted).doesNotContain(Language.JAPANESE)
        assertThat(sorted).hasSize(Language.entries.size - 1)
    }

    @Test
    fun sortedByDisplayNameContainsEveryLanguageWhenNotExcludingAny() {
        val sorted = Language.sortedByDisplayName(Locale.ENGLISH, Language::englishName)

        assertThat(sorted).containsExactlyElementsIn(Language.entries)
    }

    @Test
    fun sortedByDisplayNameIsCollatedForEverySupportedLanguageLocale() {
        Language.entries.forEach { uiLanguage ->
            val locale = Locale.forLanguageTag(uiLanguage.code)
            val nativeNames =
                Language.entries.associateWith { Locale.forLanguageTag(it.code).getDisplayLanguage(locale) }

            val sorted = Language.sortedByDisplayName(locale, { nativeNames.getValue(it) })

            val actualOrder = sorted.map { nativeNames.getValue(it) }
            val collatedOrder = actualOrder.sortedWith(Collator.getInstance(locale))
            assertThat(actualOrder).isEqualTo(collatedOrder)
        }
    }
}
