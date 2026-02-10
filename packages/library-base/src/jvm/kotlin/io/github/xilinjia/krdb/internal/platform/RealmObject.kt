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

package io.github.xilinjia.krdb.internal.platform

import io.github.xilinjia.krdb.internal.RealmObjectCompanion
import io.github.xilinjia.krdb.types.BaseRealmObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

@PublishedApi
internal actual fun <T : Any> realmObjectCompanionOrNull(clazz: KClass<T>): RealmObjectCompanion? {
    // The implementation of this method was changed for performance reasons as it was using
    // kotlin.reflect.full before, and getting companion objects this way on Android is very slow.
    // See this PR: https://github.com/realm/realm-kotlin/pull/1851
    //
    // 1. We optimized this function for the case where it'd be called with `clazz` being a
    // primitive type (boxed or not)
    //
    // 2. We cache the value even if there's no companion object,to avoid calling `Class.forName(…)`
    // too often, because it's still slow reflection (though less slow than what we're replacing).
    //
    // 3. used `ConcurrentHashMap` because we didn't check this function can never be called
    // concurrently, and if it was, the consequences would be tough, surprising, and hard to
    // diagnose, granted it was a basic (and NOT thread-safe `HashMap`).
    // It's not just about doing the lookup twice, but about infinite loops while iterating, i.e.,
    // tough consequences.

    val cachedClass = reflectionCache[clazz]
    if (cachedClass != null) return cachedClass as? RealmObjectCompanion

    val newValue: Any = clazz.javaPrimitiveType ?: (try {
        Class.forName("${clazz.java.name}\$Companion").kotlin
    } catch (_: ClassNotFoundException) {
        try {
            // For Parcelable classes
            Class.forName("${clazz.java.name}\$CREATOR").kotlin
        } catch (_: ClassNotFoundException) {
            null
        }
    }?.objectInstance as? RealmObjectCompanion) ?: clazz

    reflectionCache[clazz] = newValue

    return newValue as? RealmObjectCompanion
}

// Since ConcurrentHashMap doesn't accept null values, we can't use `RealmObjectCompanion?`,
// so we use `Any`, and we actually put a class, or a `RealmObjectCompanion` instance inside.
private val reflectionCache = ConcurrentHashMap<KClass<*>, Any>()

@PublishedApi
internal actual fun <T : BaseRealmObject> realmObjectCompanionOrThrow(clazz: KClass<T>): RealmObjectCompanion =
    realmObjectCompanionOrNull(clazz)
        ?: error("Couldn't find companion object of class '${clazz.simpleName}'.\nA common cause for this is when the `io.realm.kotlin` is not applied to the Gradle module that contains the '${clazz.simpleName}' class.")