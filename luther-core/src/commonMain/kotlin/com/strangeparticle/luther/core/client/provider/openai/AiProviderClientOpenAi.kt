package com.strangeparticle.luther.core.client.provider.openai

import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ChatResponse
import com.strangeparticle.luther.core.client.provider.ChatResponseEvent
import com.strangeparticle.luther.core.client.provider.Model
import com.strangeparticle.luther.core.client.provider.ProviderErrorType
import com.strangeparticle.luther.core.client.provider.ProviderException
import com.strangeparticle.luther.core.client.provider.postWithRetry
import com.strangeparticle.luther.core.client.provider.openai.response.OpenAiStreamAccumulator
import com.strangeparticle.luther.core.client.provider.sse.readSseData
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * REST-based client for OpenAI's chat-completions API.
 *
  * Uses [HttpClient] (provided by the caller so the same impl works under desktop CIO
  * and any other engine) plus OpenAI DTOs / [com.strangeparticle.luther.core.client.provider.openai.response.OpenAiResponseParser]
  * to translate between provider-neutral types and the wire format. No SDK dependency.
 *
 * Per spec §3.3.
 */
internal class AiProviderClientOpenAi(
    private val httpClient: HttpClient,
    private val apiKeyProvider: () -> String?,
    private val baseUrl: String = "https://api.openai.com",
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sendChat(request: ChatRequest): ChatResponse {
        val apiKey = getApiKeyOrThrow()
        // OpenAiChatCompletionRequestTest contains full serialized JSON examples for this DTO boundary.
        val body = json.encodeToString(
            com.strangeparticle.luther.core.client.provider.openai.request.OpenAiChatCompletionRequestDto.Companion.serializer(),
            com.strangeparticle.luther.core.client.provider.openai.request.OpenAiChatCompletionRequestDto.Companion.from(request),
        )
        val response = postOrThrow("$baseUrl/v1/chat/completions", apiKey, body)
        return com.strangeparticle.luther.core.client.provider.openai.response.OpenAiResponseParser.parseSuccess(response.bodyAsText())
    }

    fun responseStream(request: ChatRequest): Flow<ChatResponseEvent> = flow {
        val apiKey = getApiKeyOrThrow()
        val body = json.encodeToString(
            com.strangeparticle.luther.core.client.provider.openai.request.OpenAiChatCompletionRequestDto.Companion.serializer(),
            com.strangeparticle.luther.core.client.provider.openai.request.OpenAiChatCompletionRequestDto.Companion.from(request, stream = true),
        )
        val response = postOrThrow("$baseUrl/v1/chat/completions", apiKey, body)
        val accumulator = OpenAiStreamAccumulator()
        readSseData(response.bodyAsChannel()) { data -> accumulator.onData(data)?.let { emit(it) } }
        emit(accumulator.completed())
    }

    suspend fun listModels(): List<Model> {
        val apiKey = getApiKeyOrThrow()
        val response = try {
            httpClient.get("$baseUrl/v1/models") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                }
            }
        } catch (e: CancellationException) {
            // Cooperative coroutine cancellation must propagate so the surrounding scope
            // unwinds normally — never reclassify as a Network error.
            throw e
        } catch (e: Exception) {
            throw ProviderException(
                ProviderErrorType.Network,
                "Network error while listing OpenAI models: ${e.message}",
                cause = e,
            )
        }
        if (response.status != HttpStatusCode.OK) {
            com.strangeparticle.luther.core.client.provider.openai.response.OpenAiResponseParser.parseErrorAndThrow(response.status.value, response.bodyAsText())
        }
        val parsed = parseJsonOrThrow(response)
        return com.strangeparticle.luther.core.client.provider.openai.OpenAiModelFilter.filterAndMap(parsed)
    }

    private fun getApiKeyOrThrow(): String {
        val key = apiKeyProvider()
        if (key.isNullOrBlank()) {
            throw ProviderException(
                ProviderErrorType.InvalidApiKey,
                "Cannot call OpenAI: API key is missing.",
            )
        }
        return key
    }

    private suspend fun postOrThrow(url: String, apiKey: String, body: String): HttpResponse {
        return postWithRetry(
            performPost = {
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $apiKey")
                    }
                    setBody(body)
                }
            },
            classifyError = { status, responseBody ->
                com.strangeparticle.luther.core.client.provider.openai.response.OpenAiResponseParser.classifyError(status, responseBody)
            },
        )
    }

    private suspend fun parseJsonOrThrow(response: HttpResponse): JsonObject {
        val raw = response.bodyAsText()
        return try {
            json.parseToJsonElement(raw) as JsonObject
        } catch (e: Exception) {
            throw ProviderException(
                ProviderErrorType.MalformedResponse,
                "OpenAI response was not valid JSON: ${e.message}",
                rawProviderMessage = raw,
                cause = e,
            )
        }
    }
}
