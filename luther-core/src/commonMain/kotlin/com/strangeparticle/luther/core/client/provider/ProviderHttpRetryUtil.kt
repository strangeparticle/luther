package com.strangeparticle.luther.core.client.provider

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Configuration for [postWithRetry]'s backoff behavior.
 *
 * @param maxAttempts total number of attempts (including the first, non-retry, attempt).
 * @param baseDelay the delay before the first retry, before jitter is applied.
 * @param multiplier the exponential growth factor applied per additional attempt.
 * @param maxDelay the ceiling on the computed exponential backoff delay.
 * @param maxRetryAfter the ceiling applied to a server-supplied `Retry-After` delay.
 * @param jitter when true, applies full jitter (a random delay in `[0, computedDelay)`)
 *   to the computed exponential backoff delay. Does not affect `Retry-After` delays.
 */
internal data class RetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelay: Duration = 500.milliseconds,
    val multiplier: Double = 2.0,
    val maxDelay: Duration = 30.seconds,
    val maxRetryAfter: Duration = 60.seconds,
    val jitter: Boolean = true,
) {
    companion object {
        val Default = RetryPolicy()
    }
}

private val RETRYABLE_ERROR_TYPES = setOf(
    ProviderErrorType.RateLimit,
    ProviderErrorType.ProviderUnavailable,
    ProviderErrorType.Network,
)

/**
 * Runs [performPost] with retry-with-backoff, honoring a server-supplied `Retry-After` header
 * when present. This is engine-agnostic: luther-core never builds the [io.ktor.client.HttpClient]
 * itself (callers own their engine/config), so retries cannot be implemented as a ktor
 * HttpRequestRetry plugin installed on the client. Instead this helper wraps the caller's post
 * call directly and re-invokes it according to [policy].
 *
 * Retries only on responses/exceptions that [classifyError] (or a wrapped transport exception)
 * classifies as [ProviderErrorType.RateLimit], [ProviderErrorType.ProviderUnavailable], or
 * [ProviderErrorType.Network]. Any other classification is thrown immediately without retrying.
 *
 * @param performPost issues one HTTP POST attempt and returns the raw response.
 * @param classifyError maps a non-OK status code and response body to a [ProviderException];
 *   called once per non-OK attempt, after the `Retry-After` header has already been read.
 */
internal suspend fun postWithRetry(
    policy: RetryPolicy = RetryPolicy.Default,
    performPost: suspend () -> HttpResponse,
    classifyError: (status: Int, body: String) -> ProviderException,
    random: Random = Random,
): HttpResponse {
    for (attemptIndex in 0 until policy.maxAttempts) {
        val isLastAttempt = attemptIndex == policy.maxAttempts - 1

        val response: HttpResponse
        try {
            response = performPost()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (transportException: Exception) {
            val wrapped = ProviderException(
                ProviderErrorType.Network,
                "Network error: ${transportException.message}",
                cause = transportException,
            )
            // A wrapped transport exception is always classified Network, which is always
            // retryable, so the only decision left is whether attempts remain.
            if (!isLastAttempt) {
                delay(delayFor(attemptIndex, retryAfter = null, policy, random))
                continue
            } else {
                throw wrapped
            }
        }

        if (response.status == HttpStatusCode.OK) {
            return response
        }

        // Retry-After must be read before bodyAsText() consumes the response body.
        val retryAfter = parseRetryAfter(response.headers[HttpHeaders.RetryAfter])
        val body = response.bodyAsText()
        val exception = classifyError(response.status.value, body)

        if (RETRYABLE_ERROR_TYPES.contains(exception.classified) && !isLastAttempt) {
            delay(delayFor(attemptIndex, retryAfter, policy, random))
            continue
        } else {
            throw exception
        }
    }

    error("unreachable: loop over maxAttempts must return or throw")
}

private fun delayFor(attemptIndex: Int, retryAfter: Duration?, policy: RetryPolicy, random: Random): Duration {
    if (retryAfter != null) {
        return minOf(retryAfter, policy.maxRetryAfter)
    }

    val base = minOf(policy.baseDelay * policy.multiplier.pow(attemptIndex), policy.maxDelay)
    return if (policy.jitter) {
        base * random.nextDouble()
    } else {
        base
    }
}

/**
 * Parses an HTTP `Retry-After` header value. Supports the numeric delta-seconds form
 * (e.g. `"120"`). The HTTP-date form (e.g. `"Wed, 21 Oct 2026 07:28:00 GMT"`) is attempted
 * best-effort via [Instant.parse], which understands ISO-8601 but not RFC 1123 HTTP-dates;
 * in practice this means real HTTP-date values fall through to null today. Any parse failure
 * falls back to null so the caller computes an exponential backoff delay instead. Numeric
 * delta-seconds is the form that matters and is fully supported.
 */
@OptIn(ExperimentalTime::class)
internal fun parseRetryAfter(headerValue: String?): Duration? {
    if (headerValue.isNullOrBlank()) {
        return null
    }

    headerValue.toLongOrNull()?.let { return it.seconds }

    return try {
        val target = Instant.parse(headerValue)
        val remaining = target - Clock.System.now()
        if (remaining.isNegative()) Duration.ZERO else remaining
    } catch (_: Exception) {
        null
    }
}
