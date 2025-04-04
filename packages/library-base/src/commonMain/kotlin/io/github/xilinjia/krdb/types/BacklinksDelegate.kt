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

import io.github.xilinjia.krdb.ext.backlinks
import io.github.xilinjia.krdb.query.RealmResults
import kotlin.reflect.KProperty

/**
 * Delegate for backlinks collections. Backlinks are used to establish reverse relationships
 * between Realm models.
 *
 * See [RealmObject.backlinks] on how to define inverse relationships in your model.
 */
public interface BacklinksDelegate<T : BaseRealmObject> {
    public operator fun getValue(
        reference: RealmObject,
        targetProperty: KProperty<*>
    ): RealmResults<T>
}

/**
 * Delegate for backlinks on [EmbeddedRealmObject]. Backlinks are used to establish reverse relationships
 * between Realm models.
 *
 * See [EmbeddedRealmObject.backlinks] on how to define inverse relationships in your model.
 */
public interface EmbeddedBacklinksDelegate<T : BaseRealmObject> {
    public operator fun getValue(
        reference: EmbeddedRealmObject,
        targetProperty: KProperty<*>
    ): T
}
