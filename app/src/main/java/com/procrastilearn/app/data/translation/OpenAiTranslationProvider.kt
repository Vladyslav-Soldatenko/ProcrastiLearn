package com.procrastilearn.app.data.translation

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.ReasoningEffort
import com.openai.models.chat.completions.ChatCompletionCreateParams
import javax.inject.Inject

class OpenAiTranslationProvider @Inject constructor(
    private val clientFactory: OpenAiClientFactory,
) : AiTranslationProvider {
    override suspend fun translate(request: AiTranslationRequest): String {
        val client: OpenAIClient = clientFactory.create(request.apiKey)

        val params =
            ChatCompletionCreateParams
                .builder()
                .model(ChatModel.GPT_5_MINI)
                .reasoningEffort(ReasoningEffort.MINIMAL)
                .addSystemMessage(request.systemPrompt)
                .addUserMessage(request.userPrompt)
                .maxCompletionTokens(1500)
                .build()

        val completion = client.chat().completions().create(params)
        val text =
            completion
                .choices()
                .firstOrNull()
                ?.message()
                ?.content()
                ?.orElse("")
                ?.trim()
                .orEmpty()

        if (text.isBlank()) error("OpenAI returned an empty response")
        return text
    }
}

class OpenAiClientFactory @Inject constructor() {
    fun create(apiKey: String): OpenAIClient =
        OpenAIOkHttpClient
            .builder()
            .apiKey(apiKey)
            .build()
}
