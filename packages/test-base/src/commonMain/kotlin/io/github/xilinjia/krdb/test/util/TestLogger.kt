package io.github.xilinjia.krdb.test.util

import io.github.xilinjia.krdb.log.LogCategory
import io.github.xilinjia.krdb.log.LogLevel
import io.github.xilinjia.krdb.log.RealmLogger

/**
 * Logger implementation that track latest log event, so we are able to inspect it.
 */
class TestLogger : RealmLogger {
    var logLevel: LogLevel = LogLevel.NONE
    var throwable: Throwable? = null
    var message: String? = null
    var args: Array<out Any?> = arrayOf()
    private lateinit var category: LogCategory

    override fun log(category: LogCategory, level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?) {
        this.category = category
        this.logLevel = level
        this.throwable = throwable
        this.message = message
        this.args = args
    }
}
