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

package io.github.xilinjia.krdb.types

import io.github.xilinjia.krdb.Deleteable
import io.github.xilinjia.krdb.RealmConfiguration
import io.github.xilinjia.krdb.notifications.InitialSet
import io.github.xilinjia.krdb.notifications.SetChange
import io.github.xilinjia.krdb.notifications.UpdatedSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow

/**
 * RealmSet is a collection that contains no duplicate elements.
 *
 * Similarly to [RealmList]s, a RealmSet can operate in `managed` and `unmanaged` modes. In
 * managed mode a RealmSet persists all its contents inside a realm whereas in unmanaged mode
 * it functions like a [MutableSet].
 *
 * Managed RealmSets can only be created by Realm and will automatically update their content
 * whenever the underlying Realm is updated. Managed RealmSets can only be accessed using the getter
 * that points to a RealmSet field of a [RealmObject].
 *
 * @param E the type of elements contained in the RealmSet.
 */
public interface RealmSet<E> : MutableSet<E>, Deleteable {

    /**
     * Observes changes to the RealmSet. The [Flow] will emit [InitialSet] once subscribed, and
     * then [UpdatedSet] on every change to the set. The flow will continue running indefinitely
     * until canceled or until the parent object is deleted.
     *
     * The change calculations will run on the thread represented by
     * [RealmConfiguration.Builder.notificationDispatcher].
     *
     * The flow has an internal buffer of [Channel.BUFFERED] but if the consumer fails to consume
     * the elements in a timely manner the coroutine scope will be cancelled with a
     * [CancellationException].
     *
     * @param keyPaths An optional list of model class properties that defines when a change to
     * objects inside the set will result in a change being emitted. Nested properties can be
     * defined using a dotted syntax, e.g. `parent.child.name`. Wildcards `*` can be be used
     * to capture all properties at a given level, e.g. `child.*` or `*.*`. If no keypaths
     * are provided, changes to all top-level properties and nested properties up to 4 levels down
     * will trigger a change. Keypaths are only supported for sets containing realm objects.
     * @return a flow representing changes to the set.
     * @throws IllegalArgumentException if an invalid keypath is provided or the set does not
     * contain realm objects.
     * @throws CancellationException if the stream produces changes faster than the consumer can
     * consume them and results in a buffer overflow.
     */
    public fun asFlow(keyPaths: List<String>? = null): Flow<SetChange<E>>
}
