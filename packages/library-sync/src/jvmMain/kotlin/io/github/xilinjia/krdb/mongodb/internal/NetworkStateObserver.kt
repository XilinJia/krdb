package io.github.xilinjia.krdb.mongodb.internal

internal actual fun registerSystemNetworkObserver() {
    // Do nothing on JVM.
    // There isn't a great way to detect network connectivity on this platform.
}
