# Client / Provider System

This package (`com.strangeparticle.luther.core.client`) defines the provider-neutral client interface and all the types that cross the boundary between Luther's session layer and a concrete AI provider. Provider implementations live under `client/provider/<name>/`.

---

## Core principle: direct HTTP, no vendor SDKs

Provider implementations use Ktor's `HttpClient` directly. There is no dependency on any vendor's own SDK or client library. This is a deliberate choice:

- Vendor SDKs add transitive dependencies, version-coupling risk, and platform constraints that conflict with Kotlin Multiplatform targets.
- REST APIs are stable contracts. The endpoints we use (`/v1/chat/completions`, `/v1/models`) are mature and change rarely.
- Keeping the HTTP layer thin means a provider implementation is just DTO serialization + one `post` or `get` call. The whole implementation is readable and testable without any vendor-specific knowledge.

The cost is that we write our own DTO types (which we'd write anyway to control the mapping) and our own error classification (which gives us more precision than vendor SDKs typically provide).

---

## The seam: `AiClient`

Every provider implements:

```kotlin
interface AiClient {
    suspend fun sendAiRequest(request: AiClientRequest): AiClientResponse
    suspend fun listModels(apiKey: String): List<AiClientModelInfo>
}
```

`AiClientRequest` and `AiClientResponse` are the provider-neutral types defined here in `com.strangeparticle.luther.core.client`. Nothing provider-specific ever escapes the implementation package. The caller (the session manager) has no knowledge of wire formats, HTTP status codes, or provider error envelopes.

### `AiClientRequest` fields

```kotlin
data class AiClientRequest(
    val modelId: String,
    val systemPrompt: String,
    val history: List<AiClientMessage>,
    val tools: List<AiToolCallDefinition>,
    val maxTokens: Int? = null,  // required by Anthropic; passed through by OpenAI when set
)
```

`maxTokens` is optional here because OpenAI does not require it. Anthropic does — `AiClientAnthropic` uses `maxTokens ?: 8192` as its default. Set it explicitly when the session requires a specific output budget.

---

## DTO-based mapping

The mapping between provider-neutral types and a provider's JSON wire format goes through `@Serializable` data classes. The pattern has three parts:

**Request path:**
```
AiProviderClientRequest
    → ProviderChatRequest.from(request)    // companion factory maps domain → DTO
    → Json.encodeToString(serializer, dto) // kotlinx.serialization produces the body
    → HttpClient.post(url, body)
```

**Response path:**
```
HttpResponse.bodyAsText()
    → Json.decodeFromString<ProviderChatResponse>(body) // DTO absorbs the JSON
    → manual mapping of DTO fields → AiProviderClientResponse
```

**Error path:**
```
non-2xx response
    → try Json.decodeFromString<ProviderErrorResponse>(body)
    → classify by error code/type fields → AiProviderClientErrorType
    → fallback to HTTP status classification
    → throw AiProviderClientException(classified, message)
```

Key constraints for every provider's DTOs:

- Fields use `@SerialName` whenever the wire field is snake_case and the Kotlin name is camelCase.
- All response DTOs use `= null` defaults on optional fields so that `ignoreUnknownKeys = true` JSON parsing is resilient to vendor additions.
- No shared DTO types across providers. Each provider's DTOs model that provider's wire format exactly. Structural similarities are a coincidence, not a reason to share.
- DTOs are not domain types. They carry no behavior and make no decisions. Mapping logic lives in the companion factory (request side) and parser (response/error side), not in the DTOs themselves.

---

## Package structure for a provider

```
client/provider/<provider-name>/
├── AiProviderClient<Name>.kt      ← implements AiProviderClient; does HTTP I/O only
├── <Name>ModelFilter.kt           ← filters listModels results to chat-capable models
├── request/
│   ├── <Name>ChatRequest.kt       ← top-level request DTO with from(AiProviderClientRequest)
│   ├── <Name>Message.kt           ← message DTO (role + content + optional tool arrays)
│   ├── <Name>Tool.kt              ← tool wrapper DTO
│   └── … supporting DTOs
├── response/
│   ├── <Name>ChatResponse.kt      ← top-level response DTO
│   ├── <Name>ResponseParser.kt    ← pure parsing/error logic; no IO
│   └── … supporting DTOs
└── error/
    ├── <Name>Error.kt             ← error detail fields from the provider's error envelope
    └── <Name>ErrorResponse.kt     ← top-level error envelope
```

The orchestrating client (`AiProviderClient<Name>`) only does HTTP. `parseSuccess`, `parseErrorAndThrow`, and model-list filtering are pure functions tested independently.

---

## Conversation message mapping

Every `AiClientMessage` subtype must be mapped to the provider's role schema and content shape. The four subtypes that exist are:

- `AiClientMessageForUser` — the user's typed message
- `AiClientMessageForAssistant` — the model's reply, optionally containing tool calls
- `ToolCallProviderClientMessage` — the result of a tool call, returned to the model
- `AiClientMessageForSystemState` — a synthetic state-injection turn injected by the session manager

Different providers use different role names and content schemas. The mapping lives in the provider's request companion factory, not in the message type itself. A new provider that uses a different role vocabulary only needs to change its own request factory — the `AiClientMessage` types don't change.

Provider-specific README files document the concrete mapping for each provider.

---

## Error classification

`AiProviderClientErrorType` is the provider-neutral error vocabulary. Classification should consult the provider's own error detail fields before falling back to HTTP status, because the same HTTP status can mean different things:

- 429 can mean transient rate limiting or quota exhaustion — providers often distinguish these in error detail fields.
- 400 can mean context-too-large, invalid input, or unsupported model — not all warrant the same `AiProviderClientErrorType`.

Classification priority:
1. Provider error `code` field (most specific)
2. Provider error `type` field (broader category)
3. HTTP status (last resort)

Provider-specific README files document the concrete error code mappings.

---

## CancellationException handling

Every `try { httpClient.get/post(…) } catch (e: Exception)` block must re-throw `CancellationException` before reclassifying the exception as a network error:

```kotlin
} catch (e: CancellationException) {
    throw e   // cooperative coroutine cancellation must propagate
} catch (e: Exception) {
    throw AiProviderClientException(AiProviderClientErrorType.Network, …, cause = e)
}
```

Swallowing `CancellationException` prevents the surrounding coroutine scope from cancelling normally and is a correctness bug, not a style issue.
