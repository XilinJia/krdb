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

package io.github.xilinjia.krdb.internal.dynamic

import io.github.xilinjia.krdb.Deleteable
import io.github.xilinjia.krdb.UpdatePolicy
import io.github.xilinjia.krdb.dynamic.DynamicMutableRealm
import io.github.xilinjia.krdb.dynamic.DynamicMutableRealmObject
import io.github.xilinjia.krdb.dynamic.DynamicRealmObject
import io.github.xilinjia.krdb.ext.isValid
import io.github.xilinjia.krdb.internal.BaseRealmImpl
import io.github.xilinjia.krdb.internal.InternalConfiguration
import io.github.xilinjia.krdb.internal.LiveRealmReference
import io.github.xilinjia.krdb.internal.WriteTransactionManager
import io.github.xilinjia.krdb.internal.asInternalDeleteable
import io.github.xilinjia.krdb.internal.interop.LiveRealmPointer
import io.github.xilinjia.krdb.internal.query.ObjectQuery
import io.github.xilinjia.krdb.internal.runIfManaged
import io.github.xilinjia.krdb.internal.schema.RealmSchemaImpl
import io.github.xilinjia.krdb.internal.toRealmObject
import io.github.xilinjia.krdb.query.RealmQuery
import io.github.xilinjia.krdb.schema.RealmClass
import io.github.xilinjia.krdb.schema.RealmClassKind
import io.github.xilinjia.krdb.schema.RealmSchema

// Public due to tests needing to access `close` and trying to make the class visible through
// annotations didn't work for some reason.
public open class DynamicMutableRealmImpl(
    configuration: InternalConfiguration,
    dbPointer: LiveRealmPointer
) :
    BaseRealmImpl(configuration),
    DynamicMutableRealm,
    WriteTransactionManager {

    internal constructor(
        configuration: InternalConfiguration,
        realm: Pair<LiveRealmPointer, Boolean>
    ) : this(configuration, realm.first)

    override val realmReference: LiveRealmReference = LiveRealmReference(this, dbPointer)

    override fun query(
        className: String,
        query: String,
        vararg args: Any?
    ): RealmQuery<DynamicMutableRealmObject> {
        checkAsymmetric(className, "Queries on asymmetric objects are not allowed: $className")
        return ObjectQuery(
            realmReference,
            realmReference.schemaMetadata.getOrThrow(className).classKey,
            DynamicMutableRealmObject::class,
            configuration.mediator,
            query,
            args
        )
    }

    // Type system doesn't prevent copying embedded objects, but theres not really a good way to
    // differentiate the dynamic objects without bloating the type space
    override fun copyToRealm(
        obj: DynamicRealmObject,
        updatePolicy: UpdatePolicy
    ): DynamicMutableRealmObject {
        checkAsymmetric(obj.type, "Asymmetric Realm objects can only be added using the `insert()` method.")
        return io.github.xilinjia.krdb.internal.copyToRealm(configuration.mediator, realmReference, obj, updatePolicy, mutableMapOf()) as DynamicMutableRealmObject
    }

    // This implementation should be aligned with InternalMutableRealm to ensure that we have same
    // semantics/error reporting
    override fun findLatest(obj: DynamicRealmObject): DynamicMutableRealmObject? {
        return if (!obj.isValid()) {
            null
        } else {
            obj.runIfManaged {
                if (owner == realmReference) {
                    obj as DynamicMutableRealmObject?
                } else {
                    return thaw(realmReference, DynamicMutableRealmObject::class)
                        ?.toRealmObject() as DynamicMutableRealmObject?
                }
            } ?: throw IllegalArgumentException("Cannot lookup unmanaged object")
        }
    }

    override fun delete(deleteable: Deleteable) {
        deleteable.asInternalDeleteable().delete()
    }

    override fun delete(className: String) {
        checkAsymmetric(className, "Asymmetric Realm objects cannot be deleted manually: $className")
        delete(query(className).find())
    }

    private fun checkAsymmetric(className: String, errorMessage: String) {
        if (realmReference.owner.schema()[className]?.kind == RealmClassKind.ASYMMETRIC) {
            throw IllegalArgumentException(errorMessage)
        }
    }

    override fun deleteAll() {
        schema().let { schema: RealmSchema ->
            for (schemaClass: RealmClass in schema.classes) {
                if (schema[schemaClass.name]?.kind != RealmClassKind.ASYMMETRIC) {
                    delete(schemaClass.name)
                }
            }
        }
    }

    // FIXME Currently constructs a new instance on each invocation. We could cache this pr. schema
    //  update, but requires that we initialize it all on the actual schema update to allow freezing
    //  it. If we make the schema backed by the actual realm_class_info_t/realm_property_info_t
    //  initialization it would probably be acceptable to initialize on schema updates
    override fun schema(): RealmSchema {
        return RealmSchemaImpl.fromDynamicRealm(realmReference.dbPointer)
    }

    public override fun close() {
        super.close()
    }
}
