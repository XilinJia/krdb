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

package io.github.xilinjia.krdb.internal

import io.github.xilinjia.krdb.internal.RealmObjectHelper.NOT_IN_A_TRANSACTION_MSG
import io.github.xilinjia.krdb.internal.interop.PropertyKey
import io.github.xilinjia.krdb.internal.interop.RealmInterop
import io.github.xilinjia.krdb.internal.interop.RealmInterop.realm_get_value
import io.github.xilinjia.krdb.internal.interop.getterScope
import io.github.xilinjia.krdb.internal.interop.inputScope
import io.github.xilinjia.krdb.types.BaseRealmObject
import io.github.xilinjia.krdb.types.MutableRealmInt

internal class ManagedMutableRealmInt(
    private val obj: RealmObjectReference<out BaseRealmObject>,
    private val propertyKey: PropertyKey,
    private val converter: RealmValueConverter<Long>
) : MutableRealmInt() {

    override fun get(): Long = getterScope {
        obj.checkValid()
        val transport = realm_get_value(obj.objectPointer, propertyKey)
        with(converter) {
            realmValueToPublic(transport)!!
        }
    }

    override fun set(value: Number) = operationInternal("Cannot set", value) {
        inputScope {
            with(converter) {
                val convertedValue = publicToRealmValue(value.toLong())
                RealmInterop.realm_set_value(
                    obj.objectPointer,
                    propertyKey,
                    convertedValue,
                    false
                )
            }
        }
    }

    override fun increment(value: Number) = operationInternal("Cannot increment/decrement", value) {
        RealmInterop.realm_object_add_int(
            obj.objectPointer,
            propertyKey,
            value.toLong()
        )
    }

    override fun decrement(value: Number) = increment(-value.toLong())

    private inline fun operationInternal(message: String, value: Number, block: () -> Unit) {
        obj.checkValid()
        try {
            block()
        } catch (exception: IllegalStateException) {
            throw IllegalStateException(
                "$message `${obj.className}.$${obj.metadata[propertyKey]!!.name}` with passed value `$value`: $NOT_IN_A_TRANSACTION_MSG",
                exception
            )
        }
    }
}

internal class UnmanagedMutableRealmInt(
    private var value: Long = 0
) : MutableRealmInt() {

    override fun get(): Long = value

    override fun set(value: Number) {
        this.value = value.toLong()
    }

    override fun increment(value: Number) {
        this.value = this.value + value.toLong()
    }

    override fun decrement(value: Number) = increment(-value.toLong())
}
