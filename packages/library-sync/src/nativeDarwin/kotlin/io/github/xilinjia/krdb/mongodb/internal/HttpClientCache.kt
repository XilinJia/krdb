package io.github.xilinjia.krdb.mongodb.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.logging.Logger

/**
 * Cache HttpClient on iOS.
 */
internal actual class HttpClientCache actual constructor(timeoutMs: Long, customLogger: Logger?) {
    private val client: HttpClient by lazy { createClient(timeoutMs, customLogger) }
    actual fun getClient(): HttpClient {
        return client
    }
    actual fun close() {
        client.close()
    }
}

public actual fun createPlatformClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
    return HttpClient(Darwin, block)
}
