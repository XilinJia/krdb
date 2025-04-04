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

package io.github.xilinjia.krdb.test.common.migration

import io.github.xilinjia.krdb.MutableRealm
import io.github.xilinjia.krdb.Realm
import io.github.xilinjia.krdb.RealmConfiguration
import io.github.xilinjia.krdb.dynamic.DynamicMutableRealm
import io.github.xilinjia.krdb.dynamic.DynamicMutableRealmObject
import io.github.xilinjia.krdb.dynamic.DynamicRealm
import io.github.xilinjia.krdb.dynamic.DynamicRealmObject
import io.github.xilinjia.krdb.dynamic.getValue
import io.github.xilinjia.krdb.entities.Sample
import io.github.xilinjia.krdb.entities.primarykey.PrimaryKeyString
import io.github.xilinjia.krdb.ext.query
import io.github.xilinjia.krdb.migration.AutomaticSchemaMigration
import io.github.xilinjia.krdb.test.common.utils.assertFailsWithMessage
import io.github.xilinjia.krdb.test.platform.PlatformUtils
import io.github.xilinjia.krdb.test.util.use
import io.github.xilinjia.krdb.types.RealmObject
import kotlinx.atomicfu.atomic
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealmMigrationTests {

    private lateinit var tmpDir: String

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun migrationContext_publicNamesNotAvailable() {
        migration(
            initialSchema = setOf(
                io.github.xilinjia.krdb.entities.schema.SchemaVariations::class,
                io.github.xilinjia.krdb.entities.Sample::class
            ),
            migratedSchema = setOf(io.github.xilinjia.krdb.entities.migration.Sample::class),
            migration = { context ->
                val oldRealm = context.oldRealm
                val newRealm = context.newRealm

                assertNotNull(oldRealm.schema()["Sample"]?.get("persistedStringField"))
                assertNull(oldRealm.schema()["Sample"]?.get("publicStringField"))

                assertNotNull(newRealm.schema()["Sample"]?.get("persistedStringField"))
                assertNull(newRealm.schema()["Sample"]?.get("publicStringField"))
            }
        )
    }

    @Test
    fun migrationContext_schemaVerification() {
        migration(
            initialSchema = setOf(
                io.github.xilinjia.krdb.entities.schema.SchemaVariations::class,
                io.github.xilinjia.krdb.entities.Sample::class
            ),
            migratedSchema = setOf(io.github.xilinjia.krdb.entities.migration.Sample::class),
            migration = { context ->
                val oldRealm = context.oldRealm
                val newRealm = context.newRealm
                assertIs<DynamicRealm>(oldRealm)
                assertIsNot<DynamicMutableRealm>(oldRealm)
                oldRealm.schema().let { oldSchema ->
                    assertEquals(0, oldRealm.schemaVersion())
                    assertEquals(2, oldSchema.classes.size)
                    assertNotNull(oldSchema["Sample"])
                    assertNotNull(oldSchema["SchemaVariations"])
                    assertNull(oldSchema["SampleMigrated"])
                }

                assertIs<DynamicRealm>(newRealm)
                assertIs<DynamicMutableRealm>(newRealm)
                newRealm.schema().let { newSchema ->
                    assertEquals(1, newRealm.schemaVersion())
                    assertEquals(1, newSchema.classes.size)
                    assertNotNull(newSchema["Sample"])
                    assertNull(newSchema["SchemaVariations"])
                }
            }
        ).close()
    }

    // TODO Test all schema modifications (theoretically test core behavior, so postponed for now)
    //  - Keep existing class
    //  - Add class
    //  - Remove class
    //  - Modify class
    //    - Add property
    //    - Remove property
    //    - Rename (remove+add with different type)
    //    - Change property attributes (is this technically a remove and add?)
    //      - Nullability
    //      - Primary key
    //      - Index

    @Test
    fun migration_smokeTest() {
        migration(
            initialSchema = setOf(io.github.xilinjia.krdb.entities.migration.before.MigrationSample::class),
            initialData = { copyToRealm(io.github.xilinjia.krdb.entities.migration.before.MigrationSample()) },
            migratedSchema = setOf(io.github.xilinjia.krdb.entities.migration.after.MigrationSample::class),
            migration = { migrationContext ->
                migrationContext.enumerate("MigrationSample") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                    newObject?.run {
                        // Merge property
                        assertEquals("", getValue("fullName"))
                        set("fullName", "${oldObject.getValue<String>("firstName")} ${ oldObject.getValue<String>("lastName") }")

                        // Rename property
                        assertEquals("", getValue("renamedProperty"))
                        set("renamedProperty", oldObject.getValue<String>("property"))
                        // Change type
                        assertEquals("", getValue("type"))
                        set("type", oldObject.getValue<Long>("type").toString())
                    }
                }
            }
        ).use {
            it.query<io.github.xilinjia.krdb.entities.migration.after.MigrationSample>().find().first().run {
                assertEquals("First Last", fullName)
                assertEquals("Realm", renamedProperty)
                assertEquals("42", type)
            }
        }
    }

    @Test
    fun enumerate() {
        val initialValue = "INITIAL_VALUE"
        val migratedValue = "MIGRATED_VALUE"
        migration(
            initialSchema = setOf(io.github.xilinjia.krdb.entities.Sample::class),
            initialData = { copyToRealm(Sample().apply { stringField = initialValue }) },
            migratedSchema = setOf(io.github.xilinjia.krdb.entities.migration.Sample::class),
            // FIXME Can we get this to have the DataMigrationContext as receiver
            migration = {
                it.enumerate("Sample") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                    assertEquals(initialValue, oldObject.getValue("stringField"))
                    assertEquals(initialValue, newObject?.getValue("stringField"))
                    newObject?.set("stringField", migratedValue)
                }
            }
        ).use {
            assertEquals(
                migratedValue,
                it.query<io.github.xilinjia.krdb.entities.migration.Sample>().find().first().stringField
            )
        }
    }

    @Test
    fun enumerate_deleteNewObject() {
        migration(
            initialSchema = setOf(io.github.xilinjia.krdb.entities.Sample::class),
            initialData = {
                copyToRealm(Sample().apply { intField = 1 })
                copyToRealm(Sample().apply { intField = 2 })
            },
            migratedSchema = setOf(io.github.xilinjia.krdb.entities.migration.Sample::class),
            // FIXME Can we get this to have the DataMigrationContext as receiver
            migration = { migrationContext ->
                migrationContext.enumerate("Sample") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                    if (oldObject.getValue<Long>("intField") == 1L) {
                        // Delete all objects
                        migrationContext.newRealm.run {
                            delete(query("Sample"))
                        }
                    } else {
                        assertNull(newObject)
                    }
                }
            }
        ).close()
    }

    @Test
    fun enumerate_throwsOnInvalidName() {
        val initialValue = "INITIAL_VALUE"
        migration(
            initialSchema = setOf(io.github.xilinjia.krdb.entities.Sample::class),
            initialData = { copyToRealm(Sample().apply { stringField = initialValue }) },
            migratedSchema = setOf(io.github.xilinjia.krdb.entities.migration.Sample::class),
            // FIXME Can we get this to have the DataMigrationContext as receiver
            migration = {
                assertFailsWith<IllegalArgumentException> {
                    it.enumerate("NON_EXISTING_CLASS") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                    }
                }
            }
        ).close()
    }

    @Test
    fun migrationError_throwingCausesMigrationToFail() {
        val configuration = RealmConfiguration.Builder(schema = setOf(io.github.xilinjia.krdb.entities.Sample::class))
            .directory(tmpDir)
            .build()
        Realm.open(configuration).close()

        val newConfiguration =
            RealmConfiguration.Builder(schema = setOf(io.github.xilinjia.krdb.entities.migration.Sample::class))
                .directory(tmpDir)
                .schemaVersion(1)
                .migration(
                    AutomaticSchemaMigration {
                        @Suppress("TooGenericExceptionThrown")
                        throw RuntimeException("User error")
                    }
                )
                .build()

        // TODO Provide better error messages for exception in callbacks
        //  https://github.com/realm/realm-kotlin/issues/665
        assertFailsWithMessage<RuntimeException>("User error") {
            Realm.open(newConfiguration)
        }
    }

    @Test
    fun migrationError_throwsIfVersionIsNotUpdated() {
        val configuration = RealmConfiguration.Builder(schema = setOf(io.github.xilinjia.krdb.entities.Sample::class))
            .directory(tmpDir)
            .build()
        Realm.open(configuration).close()

        val newConfiguration =
            RealmConfiguration.Builder(schema = setOf(io.github.xilinjia.krdb.entities.migration.Sample::class))
                .directory(tmpDir)
                .migration(AutomaticSchemaMigration { })
                .build()

        assertFailsWithMessage<IllegalStateException>("Migration is required") {
            Realm.open(newConfiguration)
        }
    }

    @Test
    fun migrationError_throwsOnDuplicatePrimaryKey() {
        val configuration = RealmConfiguration.Builder(schema = setOf(PrimaryKeyString::class))
            .directory(tmpDir)
            .build()
        Realm.open(configuration).use {
            it.writeBlocking {
                copyToRealm(PrimaryKeyString().apply { primaryKey = "PRIMARY_KEY1" })
                copyToRealm(PrimaryKeyString().apply { primaryKey = "PRIMARY_KEY2" })
            }
        }

        val newConfiguration =
            RealmConfiguration.Builder(schema = setOf(io.github.xilinjia.krdb.entities.Sample::class, PrimaryKeyString::class))
                .directory(tmpDir)
                .schemaVersion(1)
                .migration(
                    AutomaticSchemaMigration {
                        it.enumerate("PrimaryKeyString") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                            assertNotNull(newObject)
                            newObject.set("primaryKey", "PRIMARY_KEY")
                        }
                    }
                )
                .build()

        assertFailsWithMessage<IllegalStateException>("Primary key property 'PrimaryKeyString.primaryKey' has duplicate values after migration.") {
            Realm.open(newConfiguration)
        }
    }

    private fun migration(
        initialSchema: Set<KClass<out RealmObject>>,
        migratedSchema: Set<KClass<out RealmObject>>,
        migration: AutomaticSchemaMigration,
        initialData: MutableRealm.() -> Unit = {}
    ): Realm {
        val migrated = atomic(false)
        val configuration =
            RealmConfiguration.Builder(schema = initialSchema)
                .directory(tmpDir)
                .build()
        Realm.open(configuration).use {
            it.writeBlocking {
                initialData()
            }
        }

        val newConfiguration = RealmConfiguration.Builder(schema = migratedSchema)
            .directory(tmpDir)
            .schemaVersion(1)
            .migration(
                AutomaticSchemaMigration {
                    migration.migrate(it)
                    migrated.value = true
                }
            )
            .build()
        val migratedRealm = Realm.open(newConfiguration)
        assertTrue { migrated.value }
        return migratedRealm
    }
}
