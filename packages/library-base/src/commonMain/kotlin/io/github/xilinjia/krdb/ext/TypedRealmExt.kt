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

import io.github.xilinjia.krdb.TypedRealm
import io.github.xilinjia.krdb.query.RealmQuery
import io.github.xilinjia.krdb.query.TRUE_PREDICATE
import io.github.xilinjia.krdb.types.TypedRealmObject

/**
 * Returns a [RealmQuery] matching the predicate represented by [query].
 *
 * Reified convenience wrapper of [TypedRealm.query].
 *
 * @param query the Realm Query Language predicate to append.
 * @param args Realm values for the predicate.
 */
public inline fun <reified T : TypedRealmObject> TypedRealm.query(
    query: String = TRUE_PREDICATE,
    vararg args: Any?
): RealmQuery<T> = query(T::class, query, *args)
