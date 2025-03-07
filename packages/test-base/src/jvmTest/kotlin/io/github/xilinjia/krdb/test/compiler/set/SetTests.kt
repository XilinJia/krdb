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

package io.github.xilinjia.krdb.test.compiler.set

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.xilinjia.krdb.compiler.CollectionType
import io.github.xilinjia.krdb.test.compiler.CollectionTests
import io.github.xilinjia.krdb.test.compiler.EMBEDDED_CLASS
import io.github.xilinjia.krdb.test.compiler.OBJECT_CLASS
import io.github.xilinjia.krdb.test.compiler.createFileAndCompile
import io.github.xilinjia.krdb.test.compiler.getTestCodeForCollection
import io.github.xilinjia.krdb.test.compiler.globalNonNullableTypes
import io.github.xilinjia.krdb.test.util.Compiler.compileFromSource
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetTests : CollectionTests(
    CollectionType.SET,
    globalNonNullableTypes.plus(OBJECT_CLASS) // Add object class manually - see name in class code strings in Utils.kt
) {

    // ------------------------------------------------
    // RealmSet<E>
    // ------------------------------------------------

    // - Embedded objects fail
    @Test
    fun `unsupported type in set - EmbeddedRealmObject fails`() {
        val result = compileFromSource(
            SourceFile.kotlin(
                "unsupportedEmbeddedRealmObjectSet.kt",
                getTestCodeForCollection(
                    collectionType = CollectionType.SET,
                    elementType = EMBEDDED_CLASS,
                    nullableElementType = false,
                    nullableField = false
                )
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSet does not support embedded realm objects element types"))
    }

    // ------------------------------------------------
    // RealmSet<E?>
    // ------------------------------------------------

    // - RealmObject fails
    @Test
    fun `nullable RealmObject set - fails`() {
        val result = createFileAndCompile(
            "nullableRealmObjectSet.kt",
            getTestCodeForCollection(
                collectionType = CollectionType.SET,
                elementType = OBJECT_CLASS,
                nullableElementType = true,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSet does not support nullable realm objects element types"))
    }

    // - nullable Embedded objects fail
    @Test
    fun `nullable EmbeddedRealmObject - fails`() {
        val result = compileFromSource(
            SourceFile.kotlin(
                "unsupportedEmbeddedRealmObjectSet.kt",
                getTestCodeForCollection(
                    collectionType = CollectionType.SET,
                    elementType = EMBEDDED_CLASS,
                    nullableElementType = true,
                    nullableField = false
                )
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSet does not support embedded realm objects element types"))
    }
}
