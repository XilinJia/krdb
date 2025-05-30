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

package io.github.xilinjia.krdb.notifications.internal

import io.github.xilinjia.krdb.notifications.DeletedMap
import io.github.xilinjia.krdb.notifications.InitialMap
import io.github.xilinjia.krdb.notifications.MapChangeSet
import io.github.xilinjia.krdb.notifications.UpdatedMap
import io.github.xilinjia.krdb.types.RealmMap

internal class InitialMapImpl<K, V>(override val map: RealmMap<K, V>) : InitialMap<K, V>
internal typealias InitialDictionaryImpl<V> = InitialMapImpl<String, V>

internal class UpdatedMapImpl<K, V>(
    override val map: RealmMap<K, V>,
    mapChangeSet: MapChangeSet<K>
) : UpdatedMap<K, V>, MapChangeSet<K> by mapChangeSet
internal typealias UpdatedDictionaryImpl<V> = UpdatedMapImpl<String, V>

internal class DeletedMapImpl<K, V>(override val map: RealmMap<K, V>) : DeletedMap<K, V>
internal typealias DeletedDictionaryImpl<V> = DeletedMapImpl<String, V>
