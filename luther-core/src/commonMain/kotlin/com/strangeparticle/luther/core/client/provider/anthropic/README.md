# Anthropic Provider

Implementation of `AiClient` for Anthropic's Messages API. See `client/README.md` for the general provider integration strategy this implementation follows.

---

## Authentication headers

Every request requires two headers that replace OpenAI's `Authorization: Bearer` pattern:

```
x-api-key: <key>
anthropic-version: 2023-06-01
```

`ANTHROPIC_VERSION` is a constant on `AiClientAnthropic`. The version string must be updated when migrating to a new stable API version.

---

## Message role mapping

Anthropic uses only `"user"` and `"assistant"` roles in the messages array. The system prompt is a separate top-level `system` field, not a message.

| `AiClientMessage` subtype | Anthropic role | Content shape |
|---|---|---|
| `AiClientMessageForUser(text)` | `"user"` | Plain string `"text"` |
| `AiClientMessageForSystemState(snapshotJson)` | `"user"` | Plain string `"<current_state>{json}</current_state>"` |
| `AiClientMessageForAssistant(text, toolCalls)` | `"assistant"` | Plain string when text-only; `[text_block?, tool_use_block+]` array when tool calls present |
| `ToolCallProviderClientMessage(toolCallId, content)` | `"user"` | `[{"type":"tool_result","tool_use_id":"...","content":"..."}]` array |

### Consecutive user-turn merging

Anthropic's API requires strictly alternating user/assistant roles. Two patterns in the message history produce back-to-back user turns that must be merged into one message:

1. **State injection + user message** — `AiClientMessageForSystemState` immediately before `AiClientMessageForUser`. Both are user-role; they become a single user message with a content array: `[{"type":"text","text":"<current_state>…"},{"type":"text","text":"<user message>"}]`.
2. **Multiple tool results** — Consecutive `ToolCallProviderClientMessage` instances all map to user role. They become one user message with multiple `tool_result` content blocks.

When a single text block is the only content, Anthropic accepts (and prefers) a plain string rather than a one-element array. The factory emits a plain string in that case.

---

## Tool wire format

Anthropic's tool definition is a flat object — no `{type:"function"}` wrapper:

```json
{
  "name": "add_app",
  "description": "Add a new app to the springboard.",
  "input_schema": { "type": "object", "properties": { … } }
}
```

`input_schema` corresponds to `AiToolCallDefinition.schema`. OpenAI uses `parameters` inside a `{type:"function", function:{…}}` envelope — Anthropic does not.

Tool choice is an object, not a string: `{"type":"auto"}` when tools are present.

---

## Tool call arguments: native JSON object

Unlike OpenAI (which pre-serializes arguments as a JSON-encoded string), Anthropic sends the tool use `input` field as a real JSON object:

```json
{
  "type": "tool_use",
  "id": "toolu_01",
  "name": "add_app",
  "input": { "tab_id": "tab-1", "id": "grafana", "name": "Grafana", "display_message": "Added" }
}
```

`AnthropicResponseContentBlockDto.ToolUse.input` is typed as `JsonObject` because the structure is tool-specific and unknown at the provider level. The parser serializes it to a string via `Json.encodeToString(JsonObject.serializer(), block.input)` to produce `ToolCall.argumentsAsJsonString`.

---

## Stop reason mapping

| Anthropic `stop_reason` | `AiClientStopReason` |
|---|---|
| `end_turn` | `Stop` |
| `stop_sequence` | `Stop` |
| `tool_use` | `ToolUse` |
| `max_tokens` | `MaxTokens` |
| anything else | `Other` |

---

## `max_tokens`

Anthropic requires `max_tokens` in every request. `AiClientAnthropic` uses `request.maxTokens ?: 8192` — the default covers most Claude models comfortably. Callers that need a different budget set `AiClientRequest.maxTokens` explicitly.

---

## Error type mappings

Classification checks `error.type` first, then falls back to HTTP status.

| Anthropic `error.type` | `AiClientErrorType` |
|---|---|
| `authentication_error` | `InvalidApiKey` |
| `permission_error` | `InvalidApiKey` |
| `rate_limit_error` | `RateLimit` |
| `api_error` | `ProviderUnavailable` |
| `overloaded_error` | `ProviderUnavailable` |
| `invalid_request_error` + "context"/"token" in message | `ContextTooLarge` |
| `invalid_request_error` (other) | `Unknown` |

HTTP status fallback: 401/403 → `InvalidApiKey`; 429 → `RateLimit`; 529 → `ProviderUnavailable`; 5xx → `ProviderUnavailable`; else → `Unknown`.

Note: Anthropic uses HTTP 529 (non-standard) for its overloaded state — this is classified as `ProviderUnavailable`.

---

## Model filtering

`AnthropicModelFilter` keeps entries from `/v1/models` where `type == "model"` and `id` starts with `"claude-"`. Anthropic's model list is already scoped to Claude models, so no deny-list is needed. Results are sorted by id descending so newer models surface first. `display_name` from the response is used directly as `AiClientModelInfo.displayName`.
