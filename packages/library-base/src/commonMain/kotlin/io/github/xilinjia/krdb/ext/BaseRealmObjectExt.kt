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

package io.github.xilinjia.krdb.ext

import io.github.xilinjia.krdb.Realm
import io.github.xilinjia.krdb.VersionId
import io.github.xilinjia.krdb.dynamic.DynamicRealmObject
import io.github.xilinjia.krdb.internal.UnmanagedState
import io.github.xilinjia.krdb.internal.checkNotificationsAvailable
import io.github.xilinjia.krdb.internal.interop.RealmInterop
import io.github.xilinjia.krdb.internal.realmObjectReference
import io.github.xilinjia.krdb.internal.runIfManaged
import io.github.xilinjia.krdb.migration.AutomaticSchemaMigration
import io.github.xilinjia.krdb.notifications.DeletedObject
import io.github.xilinjia.krdb.notifications.InitialObject
import io.github.xilinjia.krdb.notifications.ObjectChange
import io.github.xilinjia.krdb.notifications.UpdatedObject
import io.github.xilinjia.krdb.types.BaseRealmObject
import io.github.xilinjia.krdb.types.EmbeddedRealmObject
import io.github.xilinjia.krdb.types.RealmObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Returns whether the object is frozen or not.
 *
 * A frozen object is tied to a specific version of the data in the realm and fields retrieved
 * from this object instance will not update even if the object is updated in the Realm.
 *
 * @return true if the object is frozen, false otherwise.
 */
public fun BaseRealmObject.isFrozen(): Boolean =
    (realmObjectReference ?: UnmanagedState).isFrozen()

/**
 * Returns the Realm version of this object. This version number is tied to the transaction the object was read from.
 */
public fun BaseRealmObject.version(): VersionId =
    (realmObjectReference ?: UnmanagedState).version()

/**
 * Returns whether or not this object is managed by Realm.
 *
 * Managed objects are only valid to use while the Realm is open, but also have access to all Realm API's like
 * queries or change listeners. Unmanaged objects behave like normal Kotlin objects and are completely separate from
 * Realm.
 */
public fun BaseRealmObject.isManaged(): Boolean = realmObjectReference != null

/**
 * Returns true if this object is still valid to use, i.e. the Realm is open and the underlying object has
 * not been deleted. Unmanaged objects are always valid.
 */
public fun BaseRealmObject.isValid(): Boolean = runIfManaged {
    return !objectPointer.isReleased() && RealmInterop.realm_object_is_valid(objectPointer)
} ?: true

/**
 * Observe changes to a Realm object. The flow would emit an [InitialObject] once subscribed and
 * then, on every change to the object an [UpdatedObject]. If the observed object is deleted from
 * the Realm, the flow would emit a [DeletedObject] and then will complete, otherwise it will
 * continue running until canceled.
 *
 * The change calculations will be executed on the thread represented by
 * `Configuration.notificationDispatcher`.
 *
 * The flow has an internal buffer of [Channel.BUFFERED] but if the consumer fails to consume
 * the elements in a timely manner the coroutine scope will be cancelled with a
 * [CancellationException].
 *
 * @param keyPaths An optional list of properties that defines when a change to the object will
 * result in a change being emitted. Nested properties can be defined using a dotted
 * syntax, e.g. `parent.child.name`. Wildcards `*` can be be used to capture all properties at a
 * given level, e.g. `child.*` or `*.*`. If no keypaths are provided, changes to all top-level
 * properties and nested properties 4 levels down will trigger a change.
 * @return a flow representing changes to the object.
 * @throws UnsupportedOperationException if called on a live [RealmObject] or [EmbeddedRealmObject]
 * from a write transaction ([Realm.write]) or on a [DynamicRealmObject] inside a migration
 * ([AutomaticSchemaMigration.migrate]).
 * @throws IllegalArgumentException if an invalid keypath is provided.
 */
public fun <T : BaseRealmObject> T.asFlow(keyPaths: List<String>? = null): Flow<ObjectChange<T>> = runIfManaged {
    checkNotificationsAvailable()
    val keyPathInfo = keyPaths?.let {
        Pair(this.metadata.classKey, keyPaths)
    }

    return owner.owner.registerObserver(this, keyPathInfo) as Flow<ObjectChange<T>>
} ?: throw IllegalStateException("Changes cannot be observed on unmanaged objects.")
