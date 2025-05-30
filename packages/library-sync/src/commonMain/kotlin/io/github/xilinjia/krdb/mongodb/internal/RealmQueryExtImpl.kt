/*
 * Copyright 2023 Realm Inc.
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
@file:Suppress("invisible_reference", "invisible_member")
package io.github.xilinjia.krdb.mongodb.internal

import io.github.xilinjia.krdb.Realm
import io.github.xilinjia.krdb.internal.RealmImpl
import io.github.xilinjia.krdb.internal.getRealm
import io.github.xilinjia.krdb.internal.query.ObjectQuery
import io.github.xilinjia.krdb.mongodb.exceptions.BadFlexibleSyncQueryException
import io.github.xilinjia.krdb.mongodb.subscriptions
import io.github.xilinjia.krdb.mongodb.sync.Subscription
import io.github.xilinjia.krdb.mongodb.sync.SubscriptionSet
import io.github.xilinjia.krdb.mongodb.sync.SyncConfiguration
import io.github.xilinjia.krdb.mongodb.sync.WaitForSync
import io.github.xilinjia.krdb.mongodb.syncSession
import io.github.xilinjia.krdb.query.RealmQuery
import io.github.xilinjia.krdb.query.RealmResults
import io.github.xilinjia.krdb.types.RealmObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

internal suspend fun <T : RealmObject> createSubscriptionFromQuery(
    query: RealmQuery<T>,
    name: String?,
    updateExisting: Boolean = false,
    mode: WaitForSync,
    timeout: Duration
): RealmResults<T> {

    if (query !is ObjectQuery<T>) {
        throw IllegalStateException("Only queries on objects are supported. This was: ${query::class}")
    }
    if (query.realmReference.owner !is RealmImpl) {
        throw IllegalStateException("Calling `subscribe()` inside a write transaction is not allowed.")
    }
    val realm: Realm = query.getRealm()
    val subscriptions = realm.subscriptions
    val appDispatcher: CoroutineDispatcher = ((realm.configuration as SyncConfiguration).user.app as AppImpl).appNetworkDispatcher.dispatcher

    return withTimeout(timeout) {
        withContext(appDispatcher) {
            val existingSubscription: Subscription? = findExistingQueryInSubscriptions(name, query, subscriptions)
            if (existingSubscription == null || updateExisting) {
                subscriptions.update {
                    add(query, name, updateExisting)
                }
            }
            if ((mode == WaitForSync.FIRST_TIME || mode == WaitForSync.ALWAYS) && existingSubscription == null) {
                subscriptions.waitForSynchronization()
            } else if (mode == WaitForSync.ALWAYS) {
                // The subscription should already exist, just make sure we downloaded all
                // server data before continuing.
                realm.syncSession.downloadAllServerChanges()
                subscriptions.refresh()
                subscriptions.errorMessage?.let { errorMessage: String ->
                    throw BadFlexibleSyncQueryException(errorMessage, isFatal = false)
                }
            }
            // Rerun the query on the latest Realm version.
            realm.query(query.clazz, query.description()).find()
        }
    }
}

// A subscription only matches if name, type and query all matches
private fun <T : RealmObject> findExistingQueryInSubscriptions(
    name: String?,
    query: ObjectQuery<T>,
    subscriptions: SubscriptionSet<Realm>
): Subscription? {
    return if (name != null) {
        val sub: Subscription? = subscriptions.findByName(name)
        val companion = io.github.xilinjia.krdb.internal.platform.realmObjectCompanionOrThrow(query.clazz)
        val userTypeName = companion.io_realm_kotlin_className
        if (sub?.queryDescription == query.description() && sub.objectType == userTypeName) {
            sub
        } else {
            null
        }
    } else {
        subscriptions.findByQuery(query)
    }
}
