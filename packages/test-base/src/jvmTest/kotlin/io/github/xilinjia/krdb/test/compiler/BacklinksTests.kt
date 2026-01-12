@file:OptIn(ExperimentalCompilerApi::class)

package io.github.xilinjia.krdb.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class BacklinksTests {
    @Test
    fun `non parameter defined`() {
        val result = createFileAndCompile(
            "nonParameter.kt",
            NON_PARAMETER_BACKLINKS
        )
        if (result.exitCode != KotlinCompilation.ExitCode.COMPILATION_ERROR) {
            println("COMPILATION MESSAGES: ${result.messages}")
        }
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "[Realm] Error in backlinks field nonParameterBacklinks - only direct property references are valid parameters.")
    }

    @Test
    fun `non parameter defined embedded objects`() {
        val result = createFileAndCompile(
            "nonParameter.kt",
            NON_PARAMETER_BACKLINKS_EMBEDDED
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "[Realm] Error in backlinks field nonParameterBacklinks - only direct property references are valid parameters.")
    }

    private val unsupportedTypes = mapOf(
        "String" to "\"hello world\"",
        "List<String>" to "listOf()",
        "RealmList<String>" to "realmListOf()",
        "Set<Int>" to "setOf()",
        "RealmSet<Int>" to "realmSetOf()",
        "Invalid?" to "null"
    )

    @Test
    fun `unsupported types`() {
        unsupportedTypes.forEach { entry ->
            val (type, value) = entry
            println("Testing type: $type, value: $value")

            val result = createFileAndCompile(
                "unsupportedTypes.kt",
                TARGET_INVALID_TYPE.format(type, value)
            )
            println("Exit code: ${result.exitCode}")
            println("Messages: ${result.messages}")

            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertContains(
                result.messages,
                "[Realm] Error in backlinks field 'reference' - target property 'targetField' does not reference 'Referent'."
            )
        }
    }

    @Test
    fun `unsupported types embedded objects`() {
        unsupportedTypes.forEach { entry ->
            val (type, value) = entry

            val result = createFileAndCompile(
                "unsupportedTypes.kt",
                TARGET_INVALID_TYPE_EMBEDDED.format(type, value)
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertContains(
                result.messages,
                "[Realm] Error in backlinks field 'reference' - target property 'targetField' does not reference 'Referent'."
            )
        }
    }
}

private val TARGET_INVALID_TYPE =
    """
    import io.github.xilinjia.krdb.ext.backlinks
    import io.github.xilinjia.krdb.ext.realmListOf
    import io.github.xilinjia.krdb.ext.realmSetOf
    import io.github.xilinjia.krdb.types.RealmObject
    import io.github.xilinjia.krdb.types.RealmList
    import io.github.xilinjia.krdb.types.RealmSet
    
    class Invalid : RealmObject {
        var stringField: String = ""
    }
    
    class Target : RealmObject {
        var targetField: %s = %s
    }
    
    class Referent : RealmObject {
        val reference by backlinks(Target::targetField)
    }
    """.trimIndent()

private val NON_PARAMETER_BACKLINKS =
    """
    import io.github.xilinjia.krdb.ext.backlinks
    import io.github.xilinjia.krdb.types.RealmObject
    
    var childProperty = Parent::child
    
    class Parent : RealmObject {
        var child: Child? = null
    }
    
    class Child : RealmObject {
        val nonParameterBacklinks by backlinks(childProperty)
    }
    """.trimIndent()

private val TARGET_INVALID_TYPE_EMBEDDED =
    """
    import io.github.xilinjia.krdb.ext.backlinks
    import io.github.xilinjia.krdb.ext.realmListOf
    import io.github.xilinjia.krdb.ext.realmSetOf
    import io.github.xilinjia.krdb.types.EmbeddedRealmObject
    import io.github.xilinjia.krdb.types.RealmObject
    import io.github.xilinjia.krdb.types.RealmList
    import io.github.xilinjia.krdb.types.RealmSet
    
    class Invalid : RealmObject {
        var stringField: String = ""
    }
    
    class Target : RealmObject {
        var targetField: %s = %s
    }
    
    class Referent : EmbeddedRealmObject {
        val reference by backlinks(Target::targetField)
    }
    """.trimIndent()

private val NON_PARAMETER_BACKLINKS_EMBEDDED =
    """
    import io.github.xilinjia.krdb.ext.backlinks
    import io.github.xilinjia.krdb.types.EmbeddedRealmObject
    import io.github.xilinjia.krdb.types.RealmObject
    
    var childProperty = Parent::child
    
    class Parent : RealmObject {
        var child: Child? = null
    }
    
    class Child : EmbeddedRealmObject {
        val nonParameterBacklinks by backlinks(childProperty)
    }
    """.trimIndent()
