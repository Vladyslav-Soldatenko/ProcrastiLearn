package com.procrastilearn.app.data.translation

data class AiTranslationRequest(
    val apiKey: String,
    val systemPrompt: String,
    val userPrompt: String,
)

fun interface AiTranslationProvider {
    suspend fun translate(request: AiTranslationRequest): String
}
