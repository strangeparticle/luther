package com.strangeparticle.luther.core.client.provider

/** Provider-neutral events streamed from [AiProvider.responseStream] for a single assistant turn. */
sealed interface ChatResponseEvent {
    /** Incremental assistant text; concatenate in order for the live message. */
    data class TextDelta(val text: String) : ChatResponseEvent
    /** Terminal, authoritative full result (text + tool calls + stop reason). */
    data class Completed(val response: ChatResponse) : ChatResponseEvent
}
