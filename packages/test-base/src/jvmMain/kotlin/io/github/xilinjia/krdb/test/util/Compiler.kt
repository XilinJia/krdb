/*
 * Copyright 2021 Realm Inc.
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
@file:OptIn(ExperimentalCompilerApi::class)

package io.github.xilinjia.krdb.test.util

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.xilinjia.krdb.compiler.Registrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

object Compiler {
    fun compileFromSource(
        source: SourceFile,
        plugins: List<Registrar> = listOf(Registrar())
    ): JvmCompilationResult =
        KotlinCompilation().apply {
            sources = listOf(source)
            messageOutputStream = System.out
            @Suppress("deprecation")
            componentRegistrars = plugins
            inheritClassPath = true
            kotlincArguments = listOf("-Xjvm-default=all-compatibility")
        }.compile()
}
