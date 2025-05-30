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
package io.github.xilinjia.krdb.test.common

import io.github.xilinjia.krdb.Realm
import io.github.xilinjia.krdb.RealmConfiguration
import io.github.xilinjia.krdb.entities.Nullability
import io.github.xilinjia.krdb.ext.query
import io.github.xilinjia.krdb.test.platform.PlatformUtils
import io.github.xilinjia.krdb.test.util.TypeDescriptor
import io.github.xilinjia.krdb.types.MutableRealmInt
import io.github.xilinjia.krdb.types.RealmAny
import io.github.xilinjia.krdb.types.RealmInstant
import io.github.xilinjia.krdb.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NullabilityTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(
            schema = setOf(Nullability::class)
        ).directory(tmpDir).build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun nullability() {
        realm.writeBlocking {
            val nullability = copyToRealm(Nullability())
            assertNull(nullability.stringNullable)
            assertNotNull(nullability.stringNonNullable)

            nullability.stringNullable = "Realm"
            assertNotNull(nullability.stringNullable)
            nullability.stringNullable = null
            assertNull(nullability.stringNullable)

            // Should we try to verify that compiler will break on this
            // nullability.stringNonNullable = null
            // We could assert that the C-API fails by internals API with
            // io.github.xilinjia.krdb.internal.RealmObjectHelper.realm_set_value(nullability as RealmObjectInternal, Nullability::stringNonNullable, null)
            // but that would require
            // implementation("io.github.xilinjia.krdb:cinterop:${Realm.version}")
            //  https://github.com/realm/realm-kotlin/issues/134

            nullability.stringNonNullable = "Realm"
        }

        val nullabilityAfter = realm.query<Nullability>().find()[0]
        assertNull(nullabilityAfter.stringNullable)
        assertNotNull(nullabilityAfter.stringNonNullable)
    }

    @Test
    fun safeNullGetterAndSetter() {
        realm.writeBlocking {
            val nullableFieldTypes = TypeDescriptor.allSingularFieldTypes
                .map { it.elementType }
                .filter { it.nullable }
                .map { it.classifier }
                .toMutableSet()

            copyToRealm(Nullability()).also { nullableObj: Nullability ->
                fun <T> testProperty(property: KMutableProperty1<Nullability, T?>, value: T) {
                    assertNull(property.get(nullableObj))
                    property.set(nullableObj, value)
                    if (value is ByteArray) {
                        assertContentEquals(value, property.get(nullableObj) as ByteArray)
                    } else {
                        assertEquals(value, property.get(nullableObj))
                    }
                    property.set(nullableObj, null)
                    assertNull(property.get(nullableObj))
                    nullableFieldTypes.remove(property.returnType.classifier)
                }
                testProperty(Nullability::stringNullable, "Realm")
                testProperty(Nullability::booleanNullable, true)
                testProperty(Nullability::byteNullable, 0xA)
                testProperty(Nullability::charNullable, 'a')
                testProperty(Nullability::shortNullable, 123)
                testProperty(Nullability::intNullable, 123)
                testProperty(Nullability::longNullability, 123L)
                testProperty(Nullability::floatNullable, 123.456f)
                testProperty(Nullability::doubleField, 123.456)
                testProperty(Nullability::decimal128Field, Decimal128("123.456"))
                testProperty(Nullability::objectField, null)
                testProperty(Nullability::timestampField, RealmInstant.from(42, 420))
                testProperty(Nullability::bsonObjectIdField, BsonObjectId("507f191e810c19729de860ea"))
                testProperty(Nullability::uuidField, RealmUUID.random())
                testProperty(Nullability::binaryField, byteArrayOf(42))
                testProperty(Nullability::mutableRealmIntField, MutableRealmInt.create(42))
                testProperty(Nullability::realmAnyField, RealmAny.create(42))
                // Manually removing RealmObject as nullableFieldTypes is not referencing the
                // explicit subtype (Nullability). Don't know how to make the linkage without
                // so it also works on Native.
                nullableFieldTypes.remove(io.github.xilinjia.krdb.types.RealmObject::class)
            }
            assertTrue(nullableFieldTypes.isEmpty(), "Untested fields: $nullableFieldTypes")
        }
    }
}
