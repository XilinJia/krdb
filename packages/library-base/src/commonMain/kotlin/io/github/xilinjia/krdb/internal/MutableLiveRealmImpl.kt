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

import io.github.xilinjia.krdb.internal.interop.LiveRealmPointer
import io.github.xilinjia.krdb.query.RealmQuery
import io.github.xilinjia.krdb.types.TypedRealmObject
import kotlin.reflect.KClass

/**
 * Minimal live Realm implementation that only allows queries and write transactions without being
 * able to observe for changes.
 */
public class MutableLiveRealmImpl(
    dbPointer: LiveRealmPointer,
    configuration: InternalConfiguration,
) : InternalTypedRealm, InternalMutableRealm, WriteTransactionManager, BaseRealmImpl(configuration) {

    override val realmReference: LiveRealmReference = LiveRealmReference(this, dbPointer)

    override fun cancelWrite() {
        super.cancelWrite()
    }

    override fun <T : TypedRealmObject> query(
        clazz: KClass<T>,
        query: String,
        vararg args: Any?
    ): RealmQuery<T> = super.query(clazz, query, *args)
}
