package io.github.xilinjia.krdb.mongodb.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging

/**
 * Work-around for https://github.com/realm/realm-kotlin/issues/480
 *
 * This allows us to either cache the Client or re-create it pr. request
 * on a platform basis.
 */
internal fun createClient(timeoutMs: Long, customLogger: Logger?): HttpClient {
    // Need to freeze value as it is used inside the client's init lambda block, which also
    // freezes captured objects too, see:
    // https://youtrack.jetbrains.com/issue/KTOR-1223#focus=Comments-27-4618681.0-0
    val frozenTimeout = timeoutMs
    return createPlatformClient {
        // Charset defaults to UTF-8 (https://ktor.io/docs/http-plain-text.html#configuration)

        install(HttpTimeout) {
            connectTimeoutMillis = frozenTimeout
            requestTimeoutMillis = frozenTimeout
            socketTimeoutMillis = frozenTimeout
        }

        customLogger?.let {
            install(Logging) {
                logger = customLogger
                level = LogLevel.ALL
            }
        }

        // We should allow redirects for all types, not just GET and HEAD
        // See https://github.com/ktorio/ktor/issues/1793
        install(HttpRedirect) {
            checkHttpMethod = false
        }
    }
}

internal expect class HttpClientCache(timeoutMs: Long, customLogger: Logger? = null) {
    fun getClient(): HttpClient
    fun close() // Close any resources stored in the cache.
}

public expect fun createPlatformClient(block: HttpClientConfig<*>.() -> Unit): HttpClient
