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
package io.github.xilinjia.krdb.internal

import io.github.xilinjia.krdb.Deleteable
import io.github.xilinjia.krdb.MutableRealm
import io.github.xilinjia.krdb.UpdatePolicy
import io.github.xilinjia.krdb.ext.isValid
import io.github.xilinjia.krdb.schema.RealmClassKind
import io.github.xilinjia.krdb.types.BaseRealmObject
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.TypedRealmObject
import kotlin.reflect.KClass

internal interface InternalMutableRealm : MutableRealm {

    override val configuration: InternalConfiguration
    val realmReference: LiveRealmReference

    override fun <T : BaseRealmObject> findLatest(obj: T): T? {
        return if (!obj.isValid()) {
            null
        } else {
            obj.runIfManaged {
                if (owner == realmReference) {
                    // If already valid, managed and not frozen, it must be live, and thus already
                    // up to date, just return input
                    obj
                } else {
                    return thaw(realmReference)?.toRealmObject()
                }
            } ?: throw IllegalArgumentException(
                "Unmanaged objects must be part of the Realm, before " +
                    "they can be queried this way. Use `MutableRealm.copyToRealm()` to turn it into " +
                    "a managed object."
            )
        }
    }

    override fun <T : RealmObject> copyToRealm(
        instance: T,
        updatePolicy: UpdatePolicy
    ): T {
        return copyToRealm(configuration.mediator, realmReference, instance, updatePolicy)
    }

    override fun delete(deleteable: Deleteable) {
        deleteable.asInternalDeleteable().delete()
    }

    override fun <T : TypedRealmObject> delete(schemaClass: KClass<T>) {
        try {
            delete(query(schemaClass).find())
        } catch (err: IllegalStateException) {
            if (err.message?.contains("not part of this configuration schema") == true) {
                throw IllegalArgumentException(err.message)
            } else {
                throw err
            }
        }
    }

    override fun deleteAll() {
        schema().classes.filter {
            it.kind != RealmClassKind.ASYMMETRIC
        }.forEach {
            val clazz: KClass<out TypedRealmObject>? = realmReference.schemaMetadata[it.name]?.clazz
            if (clazz != null) {
                delete(clazz)
            } else {
                throw IllegalStateException("Could not delete: ${it.name}")
            }
        }
    }
}
