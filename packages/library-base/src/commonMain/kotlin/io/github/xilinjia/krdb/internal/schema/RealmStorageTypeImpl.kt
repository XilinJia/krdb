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

package io.github.xilinjia.krdb.internal.schema

import io.github.xilinjia.krdb.dynamic.DynamicMutableRealmObject
import io.github.xilinjia.krdb.dynamic.DynamicRealmObject
import io.github.xilinjia.krdb.internal.RealmAnyImpl
import io.github.xilinjia.krdb.internal.RealmInstantImpl
import io.github.xilinjia.krdb.internal.RealmUUIDImpl
import io.github.xilinjia.krdb.internal.dynamic.DynamicUnmanagedRealmObject
import io.github.xilinjia.krdb.internal.interop.PropertyType
import io.github.xilinjia.krdb.schema.RealmStorageType
import io.github.xilinjia.krdb.types.BaseRealmObject
import io.github.xilinjia.krdb.types.RealmAny
import io.github.xilinjia.krdb.types.RealmInstant
import io.github.xilinjia.krdb.types.RealmUUID
import kotlin.reflect.KClass

internal object RealmStorageTypeImpl {
    @Suppress("ComplexMethod")
    fun fromCorePropertyType(type: PropertyType): RealmStorageType {
        return when (type) {
            PropertyType.RLM_PROPERTY_TYPE_INT -> RealmStorageType.INT
            PropertyType.RLM_PROPERTY_TYPE_BOOL -> RealmStorageType.BOOL
            PropertyType.RLM_PROPERTY_TYPE_STRING -> RealmStorageType.STRING
            PropertyType.RLM_PROPERTY_TYPE_BINARY -> RealmStorageType.BINARY
            PropertyType.RLM_PROPERTY_TYPE_MIXED -> RealmStorageType.ANY
            PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP -> RealmStorageType.TIMESTAMP
            PropertyType.RLM_PROPERTY_TYPE_FLOAT -> RealmStorageType.FLOAT
            PropertyType.RLM_PROPERTY_TYPE_DOUBLE -> RealmStorageType.DOUBLE
            PropertyType.RLM_PROPERTY_TYPE_OBJECT -> RealmStorageType.OBJECT
            PropertyType.RLM_PROPERTY_TYPE_LINKING_OBJECTS -> RealmStorageType.OBJECT
            PropertyType.RLM_PROPERTY_TYPE_DECIMAL128 -> RealmStorageType.DECIMAL128
            PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID -> RealmStorageType.OBJECT_ID
            PropertyType.RLM_PROPERTY_TYPE_UUID -> RealmStorageType.UUID
            else -> error("Unknown storage type: $type")
        }
    }
}

internal fun <T : Any> KClass<T>.realmStorageType(): KClass<*> = when (this) {
    RealmUUIDImpl::class -> RealmUUID::class
    RealmInstantImpl::class -> RealmInstant::class
    DynamicRealmObject::class,
    DynamicUnmanagedRealmObject::class,
    DynamicMutableRealmObject::class -> BaseRealmObject::class
    RealmAnyImpl::class -> RealmAny::class
    else -> this
}
