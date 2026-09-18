/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.xilinjia.krdb.compiler

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.MessageCollectorAccess
import org.jetbrains.kotlin.config.messageCollector

// Logging to console/IDE (Build Output)
var compilerConfig: CompilerConfiguration? = null

@OptIn(MessageCollectorAccess::class)
fun CompilerConfiguration.logger(message: String, severity: CompilerMessageSeverity = CompilerMessageSeverity.WARNING, location: CompilerMessageSourceLocation? = null) {
    val formattedMessage by lazy { "[Realm] $message" }
    messageCollector.report(severity, formattedMessage, location)
}

fun logInfo(message: String) = compilerConfig?.logger(message, severity = CompilerMessageSeverity.INFO)

fun logDebug(message: String) = compilerConfig?.logger(message, severity = CompilerMessageSeverity.LOGGING)

fun logWarn(message: String, location: CompilerMessageSourceLocation? = null) = compilerConfig?.logger(message, severity = CompilerMessageSeverity.WARNING, location = location)

fun logError(message: String, location: CompilerMessageSourceLocation? = null) = compilerConfig?.logger(message, severity = CompilerMessageSeverity.ERROR, location = location)