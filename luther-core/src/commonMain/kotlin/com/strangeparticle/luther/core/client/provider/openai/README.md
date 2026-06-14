# OpenAI Provider

Implementation of `AiProviderClient` for OpenAI's chat-completions API. See `client/README.md` for the general provider integration strategy this implementation follows.

---

## Message role mapping

OpenAI uses `"system"`, `"user"`, `"assistant"`, and `"tool"` roles. The mapping from `AiClientMessage` subtypes:

| `AiClientMessage` subtype | OpenAI role | Notes |
|---|---|---|
| `AiClientMessageForUser(text)` | `"user"` | Plain text content |
| `AiClientMessageForAssistant(text?, toolCalls)` | `"assistant"` | `content` is null when tool calls are present; `tool_calls` array carries the invocations |
| `ToolCallProviderClientMessage(toolCallId, content)` | `"tool"` | Requires `tool_call_id` to match the assistant's prior invocation |
| `AiClientMessageForSystemState(snapshotJson)` | `"user"` | Wrapped as `<current_state>{json}</current_state>` |

`AiClientMessageForSystemState` maps to `"user"` instead of `"system"` because OpenAI's API does not accept structured content inside a `system` message during a tool-use sequence. The `<current_state>` wrapper tag distinguishes machine-injected state from real user turns.

The system prompt is always prepended as the first message with role `"system"` before any history messages.

---

## Tool wire format

Each `AiToolCallDefinition` is sent as:

```json
{
  "type": "function",
  "function": {
    "name": "…",
    "description": "…",
    "parameters": { … }   ← inline JSON Schema object, not a string
  }
}
```

When the tools list is non-empty, `tool_choice` is set to `"auto"` (model may invoke tools but is not forced to).

---

## Tool call arguments: string-inside-JSON

OpenAI sends `tool_calls[].function.arguments` as a JSON-encoded string, not a nested JSON object. `OpenAiResponseParser.parseToolCall` validates that the string is a valid JSON object before passing it through as `ToolCall.argumentsAsJsonString`. If the string is not valid JSON or is not an object, it throws `AiProviderClientException(MalformedResponse, …)`.

---

## Error code mappings

`OpenAiResponseParser` classifies errors by consulting the provider's `error.code` field first, then `error.type`, then HTTP status. OpenAI-specific codes that affect classification:

| OpenAI `error.code` | `AiProviderClientErrorType` |
|---|---|
| `context_length_exceeded` | `ContextTooLarge` |
| `insufficient_quota` | `QuotaExceeded` |
| `rate_limit_exceeded` | `RateLimit` |
| `invalid_api_key`, `invalid_token` | `InvalidApiKey` |

OpenAI `error.type = "insufficient_quota"` also maps to `QuotaExceeded`. `error.type = "invalid_request_error"` covers many unrelated cases and falls through to HTTP-status classification.

HTTP status fallback (when no matching error code/type):

| Status | `AiProviderClientErrorType` |
|---|---|
| 401, 403 | `InvalidApiKey` |
| 429 | `RateLimit` |
| 500–599 | `ProviderUnavailable` |
| anything else | `Unknown` |

---

## Model filtering

`OpenAiModelFilter.filterAndMap` is applied to the `/v1/models` response. It keeps models whose id starts with `gpt-` and does not contain any of: `embedding`, `moderation`, `whisper`, `tts`, `dall-e`, `babbage`, `davinci`, `audio` (case-insensitive substring match). The filtered list is returned sorted by id descending so newer-named models surface first in the UI dropdown.

---

## HttpClient injection

`AiProviderClientOpenAi` takes an `HttpClient` constructor parameter rather than creating one internally. This lets the same implementation run under desktop CIO or any other engine, and keeps tests straightforward — pass a `MockEngine`-backed client to script HTTP responses without network access.
