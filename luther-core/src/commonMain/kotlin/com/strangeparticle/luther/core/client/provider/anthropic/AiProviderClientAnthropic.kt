package com.strangeparticle.luther.core.client.provider.anthropic

import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ChatResponse
import com.strangeparticle.luther.core.client.provider.ChatResponseEvent
import com.strangeparticle.luther.core.client.provider.Model
import com.strangeparticle.luther.core.client.provider.ProviderErrorType
import com.strangeparticle.luther.core.client.provider.ProviderException
import com.strangeparticle.luther.core.client.provider.anthropic.request.AnthropicChatCompletionRequestDto
import com.strangeparticle.luther.core.client.provider.anthropic.response.AnthropicResponseParser
import com.strangeparticle.luther.core.client.provider.anthropic.response.AnthropicStreamAccumulator
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * REST-based client for Anthropic's Messages API.
 *
 * Uses [HttpClient] (provided by the caller so the same impl works under desktop CIO
 * and any other engine) plus Anthropic DTOs / [AnthropicResponseParser] to translate
 * between provider-neutral types and the wire format. No SDK dependency.
 *
 * Per spec §3.3.
 */
internal class AiProviderClientAnthropic(
    private val httpClient: HttpClient,
    private val apiKeyProvider: () -> String?,
    private val baseUrl: String = "https://api.anthropic.com",
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sendChat(request: ChatRequest): ChatResponse {
        val apiKey = getApiKeyOrThrow()
        // AnthropicChatCompletionRequestTest contains full serialized JSON examples for this DTO boundary.
        val body = json.encodeToString(
            AnthropicChatCompletionRequestDto.serializer(),
            AnthropicChatCompletionRequestDto.from(request),
        )
        val response = postOrThrow("$baseUrl/v1/messages", apiKey, body)
        return AnthropicResponseParser.parseSuccess(response.bodyAsText())
    }

    fun responseStream(request: ChatRequest): Flow<ChatResponseEvent> = flow {
        val apiKey = getApiKeyOrThrow()
        val body = json.encodeToString(
            AnthropicChatCompletionRequestDto.serializer(),
            AnthropicChatCompletionRequestDto.from(request, stream = true),
        )
        val response = postOrThrow("$baseUrl/v1/messages", apiKey, body)
        val accumulator = AnthropicStreamAccumulator()
        readSseData(response.bodyAsChannel()) { data -> accumulator.onData(data)?.let { emit(it) } }
        emit(accumulator.completed())
    }

    suspend fun listModels(): List<Model> {
        val apiKey = getApiKeyOrThrow()
        val response = try {
            httpClient.get("$baseUrl/v1/models") {
                headers {
                    append("x-api-key", apiKey)
                    append("anthropic-version", ANTHROPIC_VERSION)
                }
            }
        } catch (e: CancellationException) {
            // Cooperative coroutine cancellation must propagate so the surrounding scope
            // unwinds normally — never reclassify as a Network error.
            throw e
        } catch (e: Exception) {
            throw ProviderException(
                ProviderErrorType.Network,
                "Network error while listing Anthropic models: ${e.message}",
                cause = e,
            )
        }
        if (response.status != HttpStatusCode.OK) {
            AnthropicResponseParser.parseErrorAndThrow(response.status.value, response.bodyAsText())
        }
        return AnthropicModelFilter.filterAndMap(AnthropicResponseParser.parseModelListResponse(response.bodyAsText()))
    }

    private fun getApiKeyOrThrow(): String {
        val key = apiKeyProvider()
        if (key.isNullOrBlank()) {
            throw ProviderException(
                ProviderErrorType.InvalidApiKey,
                "Cannot call Anthropic: API key is missing.",
            )
        }
        return key
    }

    private suspend fun postOrThrow(url: String, apiKey: String, body: String): HttpResponse {
        val response = try {
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                headers {
                    append("x-api-key", apiKey)
                    append("anthropic-version", ANTHROPIC_VERSION)
                }
                setBody(body)
            }
        } catch (e: CancellationException) {
            // Cooperative coroutine cancellation must propagate so the surrounding scope
            // unwinds normally — never reclassify as a Network error.
            throw e
        } catch (e: Exception) {
            throw ProviderException(
                ProviderErrorType.Network,
                "Network error calling Anthropic: ${e.message}",
                cause = e,
            )
        }
        if (response.status != HttpStatusCode.OK) {
            AnthropicResponseParser.parseErrorAndThrow(response.status.value, response.bodyAsText())
        }
        return response
    }

    companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
