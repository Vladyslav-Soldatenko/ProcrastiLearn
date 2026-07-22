package com.procrastilearn.app.data.local.prefs

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.Language
import org.junit.Test

class OpenAiPromptDefaultsTest {
    @Test
    fun `resolve replaces both placeholders in the forward template`() {
        val resolved =
            resolveOpenAiPrompt(OpenAiPromptDefaults.translationPrompt, Language.SPANISH, Language.FRENCH)

        assertThat(resolved).contains("Spanish")
        assertThat(resolved).contains("French")
        assertThat(resolved).doesNotContain("{{")
    }

    @Test
    fun `resolve replaces both placeholders in the reverse template`() {
        val resolved =
            resolveOpenAiPrompt(OpenAiPromptDefaults.reverseTranslationPrompt, Language.SPANISH, Language.FRENCH)

        assertThat(resolved).contains("Spanish")
        assertThat(resolved).contains("French")
        assertThat(resolved).doesNotContain("{{")
    }

    @Test
    fun `default templates contain no unknown placeholder tokens`() {
        val knownPlaceholders =
            setOf(
                OpenAiPromptDefaults.NATIVE_LANGUAGE_PLACEHOLDER,
                OpenAiPromptDefaults.TARGET_LANGUAGE_PLACEHOLDER,
            )
        val placeholderRegex = Regex("\\{\\{[A-Z_]+\\}\\}")

        listOf(OpenAiPromptDefaults.translationPrompt, OpenAiPromptDefaults.reverseTranslationPrompt).forEach { template ->
            val foundTokens = placeholderRegex.findAll(template).map { it.value }.toSet()
            assertThat(knownPlaceholders).containsAtLeastElementsIn(foundTokens)
        }
    }

    @Test
    fun `resolve handles a same-language pair without error`() {
        val resolved = resolveOpenAiPrompt(OpenAiPromptDefaults.translationPrompt, Language.ENGLISH, Language.ENGLISH)

        assertThat(resolved).doesNotContain("{{")
        assertThat(resolved).contains("English")
    }

    @Test
    fun `resolve substitutes placeholders in a custom user prompt`() {
        val custom =
            "Explain in ${OpenAiPromptDefaults.NATIVE_LANGUAGE_PLACEHOLDER}, " +
                "give examples in ${OpenAiPromptDefaults.TARGET_LANGUAGE_PLACEHOLDER}."

        val resolved = resolveOpenAiPrompt(custom, Language.GERMAN, Language.ITALIAN)

        assertThat(resolved).isEqualTo("Explain in German, give examples in Italian.")
    }

    @Test
    fun `resolve leaves a custom prompt without placeholders unchanged`() {
        val custom = "Just translate the word plainly, no extra formatting."

        val resolved = resolveOpenAiPrompt(custom, Language.GERMAN, Language.ITALIAN)

        assertThat(resolved).isEqualTo(custom)
    }

    @Test
    fun `resolve is idempotent on already-resolved text`() {
        val onceResolved =
            resolveOpenAiPrompt(OpenAiPromptDefaults.translationPrompt, Language.SPANISH, Language.FRENCH)
        val twiceResolved = resolveOpenAiPrompt(onceResolved, Language.SPANISH, Language.FRENCH)

        assertThat(twiceResolved).isEqualTo(onceResolved)
    }

    @Test
    fun `every language has a non-blank english name`() {
        Language.entries.forEach { language ->
            assertThat(language.englishName).isNotEmpty()
        }
    }

    @Test
    fun `every language has a unique english name`() {
        val names = Language.entries.map { it.englishName }

        assertThat(names).containsNoDuplicates()
    }

    @Test
    fun `resolve works cleanly for every supported language as native or target`() {
        Language.entries.forEach { language ->
            val asNative = resolveOpenAiPrompt(OpenAiPromptDefaults.translationPrompt, language, Language.ENGLISH)
            val asTarget = resolveOpenAiPrompt(OpenAiPromptDefaults.translationPrompt, Language.ENGLISH, language)

            assertThat(asNative).doesNotContain("{{")
            assertThat(asNative).contains(language.englishName)
            assertThat(asTarget).doesNotContain("{{")
            assertThat(asTarget).contains(language.englishName)
        }
    }

    @Test
    fun `resolve mentions romanization guidance for a Chinese target`() {
        val resolved = resolveOpenAiPrompt(OpenAiPromptDefaults.translationPrompt, Language.ENGLISH, Language.CHINESE)

        assertThat(resolved).contains("Chinese")
        assertThat(resolved).contains("romanization")
    }
}
