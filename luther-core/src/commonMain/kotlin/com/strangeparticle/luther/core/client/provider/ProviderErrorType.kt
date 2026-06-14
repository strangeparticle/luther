package com.strangeparticle.luther.core.client.provider

enum class ProviderErrorType {
    InvalidApiKey, RateLimit, QuotaExceeded, ContextTooLarge,
    Network, ProviderUnavailable, MalformedResponse, Unknown,
}
