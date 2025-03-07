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

package io.github.xilinjia.krdb.test.mongodb.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.xilinjia.krdb.test.util.Compiler
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
class AsymmetricObjectTests {

    @Test
    fun `cannot reference asymmetric objects`() {
        val result = Compiler.compileFromSource(
            source = SourceFile.kotlin(
                "referenceAsymmetricObjects.kt",
                """
                    import io.github.xilinjia.krdb.types.AsymmetricRealmObject
                    import io.github.xilinjia.krdb.types.EmbeddedRealmObject
                    import io.github.xilinjia.krdb.types.RealmObject
                    import io.github.xilinjia.krdb.types.annotations.PrimaryKey

                    class A : AsymmetricRealmObject {
                        @PrimaryKey
                        var _id: String = ""
                        var child: A? = null
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("AsymmetricObjects can only reference EmbeddedRealmObject classes"))
    }

    @Test
    fun `cannot reference asymmetric objects in collections`() {
        val result = Compiler.compileFromSource(
            source = SourceFile.kotlin(
                "referenceAsymmetricObjects.kt",
                """
                    import io.github.xilinjia.krdb.ext.realmDictionaryOf
                    import io.github.xilinjia.krdb.ext.realmListOf
                    import io.github.xilinjia.krdb.ext.realmSetOf
                    import io.github.xilinjia.krdb.types.AsymmetricRealmObject
                    import io.github.xilinjia.krdb.types.EmbeddedRealmObject
                    import io.github.xilinjia.krdb.types.RealmDictionary
                    import io.github.xilinjia.krdb.types.RealmList
                    import io.github.xilinjia.krdb.types.RealmObject
                    import io.github.xilinjia.krdb.types.RealmSet
                    import io.github.xilinjia.krdb.types.annotations.PrimaryKey

                    class A : AsymmetricRealmObject {
                        @PrimaryKey
                        var _id: String = ""
                        var children1: RealmList<A> = realmListOf()
                        var children2: RealmSet<A> = realmSetOf()
                        var children3: RealmDictionary<A> = realmDictionaryOf()
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unsupported type for RealmList: 'A'"))
        assertTrue(result.messages.contains("Unsupported type for RealmSet: 'A'"))
        assertTrue(result.messages.contains("Unsupported type for RealmDictionary: 'A'"))
    }

    @Test
    fun `cannot reference standard realmobjects`() {
        val result = Compiler.compileFromSource(
            source = SourceFile.kotlin(
                "referenceRealmObjects.kt",
                """
                    import io.github.xilinjia.krdb.ext.realmListOf
                    import io.github.xilinjia.krdb.types.AsymmetricRealmObject
                    import io.github.xilinjia.krdb.types.EmbeddedRealmObject
                    import io.github.xilinjia.krdb.types.RealmList
                    import io.github.xilinjia.krdb.types.RealmObject
                    import io.github.xilinjia.krdb.types.annotations.PrimaryKey

                    class B : RealmObject {
                        var _id: String = ""
                    }

                    class A : AsymmetricRealmObject {
                        @PrimaryKey
                        var _id: String = ""
                        var child: B? = null
                        var children: RealmList<B> = realmListOf()
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("AsymmetricObjects can only reference EmbeddedRealmObject classes"))
    }

    @Test
    fun `embedded objects cannot reference asymmetric objects`() {
        val result = Compiler.compileFromSource(
            source = SourceFile.kotlin(
                "embeddedCannotReferenceAsymmetric.kt",
                """
                    import io.github.xilinjia.krdb.ext.realmListOf
                    import io.github.xilinjia.krdb.types.AsymmetricRealmObject
                    import io.github.xilinjia.krdb.types.EmbeddedRealmObject
                    import io.github.xilinjia.krdb.types.RealmList
                    import io.github.xilinjia.krdb.types.RealmObject
                    import io.github.xilinjia.krdb.types.annotations.PrimaryKey

                    class B : AsymmetricRealmObject {
                        @PrimaryKey
                        var _id: String = ""
                    }

                    class A : EmbeddedRealmObject {
                        var child: B? = null
                        var children: RealmList<B> = realmListOf()
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmObjects and EmbeddedRealmObjects cannot reference AsymmetricRealmObjects"))
        assertTrue(result.messages.contains("Unsupported type for RealmList: 'B'"))
    }

    @Test
    fun `realmobjects cannot reference asymmetric objects`() {
        val result = Compiler.compileFromSource(
            source = SourceFile.kotlin(
                "embeddedCannotReferenceAsymmetric.kt",
                """
                    import io.github.xilinjia.krdb.ext.realmListOf
                    import io.github.xilinjia.krdb.types.AsymmetricRealmObject
                    import io.github.xilinjia.krdb.types.RealmList
                    import io.github.xilinjia.krdb.types.RealmObject
                    import io.github.xilinjia.krdb.types.annotations.PrimaryKey

                    class B : AsymmetricRealmObject {
                        @PrimaryKey
                        var _id: String = ""
                    }

                    class A : RealmObject {
                        var child: B? = null
                        var children: RealmList<B> = realmListOf()
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmObjects and EmbeddedRealmObjects cannot reference AsymmetricRealmObjects"))
        assertTrue(result.messages.contains("Unsupported type for RealmList: 'B'"))
    }
}
