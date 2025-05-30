/*
 * Copyright 2022 Realm Inc.
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

package io.github.xilinjia.krdb.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.xilinjia.krdb.test.util.Compiler
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmbeddedTests {

    @Test
    fun `embedded object with primary keys fails`() {
        val result = Compiler.compileFromSource(
            source = SourceFile.kotlin(
                "embeddedRealmObjectWithPrimaryKey.kt",
                """
                    import io.github.xilinjia.krdb.types.EmbeddedRealmObject
                    import io.github.xilinjia.krdb.types.annotations.PrimaryKey

                    class A : EmbeddedRealmObject {
                        @PrimaryKey
                        var primaryKey1: String? = null
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Embedded object is not allowed to have a primary key"))
    }
}
